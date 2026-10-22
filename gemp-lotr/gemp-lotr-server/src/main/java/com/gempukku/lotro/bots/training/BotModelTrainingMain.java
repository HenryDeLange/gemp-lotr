package com.gempukku.lotro.bots.training;

import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.MovieBlockDeckFactory;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.ReplayBuffer;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStartersLearningStepsPersistence;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.simulation.SimpleBatchSimulationRunner;
import com.gempukku.lotro.bots.simulation.SimulationStats;
import com.gempukku.lotro.game.DefaultAdventureLibrary;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.gempukku.lotro.packs.ProductLibrary;
import smile.classification.Classifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public final class BotModelTrainingMain {
    private static final String TRAINING_CONFIG_ENVIRONMENT_VARIABLE = "GEMP_BOT_TRAINING_CONFIG";
    private static final int REPLAY_BUFFER_CAPACITY = 100_000;
    private static final int REPLAY_DRAIN_THRESHOLD = 10_000;
    private static final long FINAL_DASHBOARD_GRACE_PERIOD_MILLIS = 3_000;
    private static final int MINIMUM_FORMAT_WIN_RATE_PERCENT = 45;
    private static final double MAX_EVALUATION_SKIP_RATE = 0.01;
    private static final List<String> SUPPORTED_FORMATS =
            List.of("fotr_block", "ttt_block", "king_block", "movie");

    private BotModelTrainingMain() {
    }

    public static void main(String[] args) throws IOException {
        TrainingConfig config = TrainingConfig.load(args);
        String previousModelDirectory = System.getProperty(ModelIO.MODEL_DIRECTORY_PROPERTY);
        String previousTrainingDataDirectory =
                System.getProperty(FotrStartersLearningStepsPersistence.DATA_DIRECTORY_PROPERTY);

        TrainingModelWorkspace workspace = TrainingModelWorkspace.create(config.modelDirectory());
        TrainingProgress progress = new TrainingProgress();
        progress.start();
        List<Trainer> trainers = FotrStarterTrainerCatalog.createAll();
        TrainingProgressServer dashboard = null;
        boolean trainingSucceeded = false;
        try (workspace;
             TrainingRunMetricsWriter metricsWriter =
                     new TrainingRunMetricsWriter(config.metricsDirectory(), trainingMetadata(config))) {
            try {
                progress.setMetricsWriter(metricsWriter);
                progress.setPlan(trainingPlan(config, trainers.size()));
                System.out.println("Training metrics: " + metricsWriter.file());
                System.setProperty(ModelIO.MODEL_DIRECTORY_PROPERTY, workspace.getStagingDirectory().toString());
                System.setProperty(FotrStartersLearningStepsPersistence.DATA_DIRECTORY_PROPERTY,
                        config.trainingDataDirectory().toString());
                if (config.dashboardPort() > 0) {
                    dashboard = new TrainingProgressServer(
                            config.dashboardHost(), config.dashboardPort(), progress);
                    dashboard.start();
                    System.out.println("Training UI is available at http://localhost:"
                            + dashboard.getPort() + "/ (JSON API: /api/status)");
                } else {
                    System.out.println("Training UI is disabled (training.dashboard.port=0).");
                }
    
                validateCardResources(config.cardResourcesDirectory());
                LotroCardBlueprintLibrary cardLibrary = new LotroCardBlueprintLibrary(
                        config.cardResourcesDirectory().resolve("cards").toFile(),
                        config.cardResourcesDirectory().resolve("blueprintMapping.txt").toFile(),
                        config.cardResourcesDirectory().resolve("setConfig.hjson").toFile(),
                        config.cardResourcesDirectory().resolve("rarities").toFile());
                LotroFormatLibrary formatLibrary = new LotroFormatLibrary(
                        new DefaultAdventureLibrary(),
                        cardLibrary,
                        config.cardResourcesDirectory().resolve("lotrFormats.hjson").toFile(),
                        config.cardResourcesDirectory().resolve("sealed").toFile());
                ProductLibrary productLibrary = new ProductLibrary(
                        cardLibrary, config.cardResourcesDirectory().resolve("product").toFile());
                CardFeatures.init(cardLibrary);
    
                System.out.println("Training configuration: " + config.configFile());
                System.out.println("Card resources: " + config.cardResourcesDirectory());
                System.out.println("Model output (published only after successful training): " + config.modelDirectory());
                System.out.println("Training workspace: " + workspace.getStagingDirectory());
                System.out.println("Training data: " + config.trainingDataDirectory());
                System.out.println("CPU workers: " + config.threads());
                System.out.println("Model-fitting workers: " + config.modelThreads());
                System.out.println("Maximum historical training records per model and format: "
                        + config.maxTrainingRecordsPerFormat());
                System.out.println("Formats: " + String.join(", ", config.formats()));
                System.out.printf(Locale.ROOT, "Minimum decision-model check-example match rate: %.1f%%%n",
                        config.minimumValidationAccuracy());
                System.out.println("All four formats share one policy model trained from pooled, format-tagged examples.");
                System.out.println("Individual evaluation checks up to "
                        + config.maxPolicyChangesPerGeneration() + " policy changes with "
                        + (config.evaluationGamesPerSeat() / config.maxPolicyChangesPerGeneration())
                        + " games per seat and format per change.");
                System.out.println("Random-baseline games are diagnostic only and do not contribute training decisions.");
                System.out.println("Deck builder: " + config.deckBuilderCandidatesPerGeneration()
                        + " candidates x " + config.deckBuilderGamesPerCandidate()
                        + " games per format and generation; evaluated with the incumbent decision policy");
    
                Path versionedDataDirectory = config.trainingDataDirectory()
                        .resolve(ModelIO.TRAINING_DATA_SCHEMA_DIRECTORY);
                List<FotrStartersLearningStepsPersistence> trainingDataSources =
                        trainingDataSourcesFor(versionedDataDirectory, progress);
                List<FormatTrainingState> formatTrainingStates = new ArrayList<>();
                for (String formatCode : config.formats()) {
                    progress.beginFormat(formatCode);
                    LotroFormat format = formatLibrary.getFormat(formatCode);
                    if (format == null)
                        throw new IllegalArgumentException("Unknown training format: " + formatCode);
    
                    Path modelDirectory = modelDirectoryFor(workspace.getStagingDirectory(), formatCode);
                    Path dataDirectory = versionedDataDirectory.resolve(formatCode);
                    FotrStartersLearningStepsPersistence persistence =
                            new FotrStartersLearningStepsPersistence(dataDirectory);
                    RLGameStateFeatures features = featuresFor(formatCode);
                    List<LotroDeck> starterDecks = MovieBlockDeckFactory.createStarterDecks(
                            formatCode, format, cardLibrary, productLibrary);
                    Path deckBuilderDirectory =
                            ModelIO.resolveDeckBuilderDirectory(workspace.getStagingDirectory(), formatCode);
                    Classifier<double[]> deckBuilderModel = loadDeckBuilderModel(deckBuilderDirectory, formatCode);
                    formatTrainingStates.add(new FormatTrainingState(
                            format, starterDecks, features, persistence, modelDirectory,
                            deckBuilderDirectory, dataDirectory, deckBuilderModel));
                }
    
                for (int generation = 1; generation <= config.generations(); generation++) {
                    System.out.println("=== Generation " + generation + "/" + config.generations()
                            + " across " + formatTrainingStates.size() + " formats ===");
                    ModelRegistry incumbent = loadModels(trainers,
                            formatTrainingStates.getFirst().modelDirectory);
                    boolean hasIncumbentModels = !incumbent.getRegisteredTrainerTypes().isEmpty();
                    String explorationProfile = config.resolvedExplorationProfile(hasIncumbentModels);
                    double explorationRate = config.explorationRate(generation, hasIncumbentModels);
                    System.out.printf(Locale.ROOT,
                            "  Training action exploration: %s profile, %.1f%% random legal decisions; "
                                    + "exploratory examples weighted %.2f%n",
                            explorationProfile, explorationRate * 100.0, config.explorationExampleWeight());
                    for (FormatTrainingState trainingState : formatTrainingStates) {
                        progress.beginFormat(trainingState.format.getCode(), generation);
                        trainFormatGeneration(trainingState, cardLibrary, incumbent,
                                config, progress, generation, explorationRate);
                    }
                    trainSharedPolicyGeneration(formatTrainingStates, trainers, trainingDataSources, cardLibrary,
                            incumbent, config, progress, generation);
                }
                TrainingModelWorkspace.PublicationResult publication = workspace.publish();
                System.out.println("Published " + publication.sharedPolicyModels()
                        + " shared policy models; removed " + publication.removedLegacyModels()
                        + " legacy model files.");
                progress.finish("completed");
                trainingSucceeded = true;
            } catch (IOException | RuntimeException e) {
                trainingSucceeded = false;
                logTrainingFailure(progress, e);
                try {
                    progress.finish("failed");
                } catch (java.io.UncheckedIOException metricsFailure) {
                    e.addSuppressed(metricsFailure);
                }
                throw e;
            }
        } finally {
            if (previousModelDirectory == null)
                System.clearProperty(ModelIO.MODEL_DIRECTORY_PROPERTY);
            else
                System.setProperty(ModelIO.MODEL_DIRECTORY_PROPERTY, previousModelDirectory);
            if (previousTrainingDataDirectory == null)
                System.clearProperty(FotrStartersLearningStepsPersistence.DATA_DIRECTORY_PROPERTY);
            else
                System.setProperty(FotrStartersLearningStepsPersistence.DATA_DIRECTORY_PROPERTY,
                        previousTrainingDataDirectory);
            if (dashboard != null) {
                String outcome = trainingSucceeded ? "completed successfully" : "did not complete successfully";
                System.out.println("Training " + outcome
                        + "; keeping the progress UI available briefly for its final update.");
                try {
                    Thread.sleep(FINAL_DASHBOARD_GRACE_PERIOD_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                dashboard.close();
            }
        }
    }

    static void logTrainingFailure(TrainingProgress progress, Throwable failure) {
        try {
            progress.recordEvent(Severity.ERROR, "training-failure", null,
                    failure.getClass().getName() + ": " + failure.getMessage(), failure);
        } catch (java.io.UncheckedIOException metricsFailure) {
            failure.addSuppressed(metricsFailure);
        }
        var snapshot = progress.snapshot();
        System.err.printf("Bot model training failed during %s, generation %s, stage %s.%n",
                snapshot.get("format"), snapshot.get("generation"), snapshot.get("stage"));
        failure.printStackTrace(System.err);
    }

    private static Map<String, Object> trainingMetadata(TrainingConfig config) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("configFile", config.configFile().toString());
        metadata.put("formats", config.formats());
        metadata.put("gamesPerGeneration", config.gamesPerGeneration());
        metadata.put("randomBaselinePercent", config.randomBaselinePercent());
        metadata.put("randomBaselineExampleWeight", config.randomBaselineExampleWeight());
        metadata.put("generations", config.generations());
        metadata.put("explorationProfile", config.explorationProfile());
        metadata.put("freshExplorationStartPercent", config.freshExplorationStartPercent());
        metadata.put("freshExplorationEndPercent", config.freshExplorationEndPercent());
        metadata.put("refinementExplorationStartPercent", config.refinementExplorationStartPercent());
        metadata.put("refinementExplorationEndPercent", config.refinementExplorationEndPercent());
        metadata.put("explorationDecayGenerations", config.explorationDecayGenerations());
        metadata.put("explorationExampleWeight", config.explorationExampleWeight());
        metadata.put("modelObservationSchema", ModelIO.OBSERVATION_SCHEMA_DIRECTORY);
        metadata.put("trainingDataSchema", ModelIO.TRAINING_DATA_SCHEMA_DIRECTORY);
        metadata.put("cpuThreads", config.threads());
        metadata.put("modelThreads", config.modelThreads());
        metadata.put("maxTrainingRecordsPerFormat", config.maxTrainingRecordsPerFormat());
        metadata.put("evaluationGamesPerSeat", config.evaluationGamesPerSeat());
        metadata.put("maxPolicyChangesPerGeneration", config.maxPolicyChangesPerGeneration());
        metadata.put("exploratoryPolicyChanges", config.exploratoryPolicyChanges());
        metadata.put("maximumValidationRegressionPercent", config.maximumValidationRegressionPercent());
        metadata.put("deckBuilderCandidatesPerGeneration", config.deckBuilderCandidatesPerGeneration());
        metadata.put("deckBuilderGamesPerCandidate", config.deckBuilderGamesPerCandidate());
        metadata.put("evaluationSeed", config.evaluationSeed());
        metadata.put("minimumValidationAccuracy", config.minimumValidationAccuracy());
        metadata.put("modelsDirectory", config.modelDirectory().toString());
        metadata.put("trainingDataDirectory", config.trainingDataDirectory().toString());
        metadata.put("metricsDirectory", config.metricsDirectory().toString());
        return metadata;
    }

    private static void trainFormatGeneration(
            FormatTrainingState trainingState,
            LotroCardBlueprintLibrary cardLibrary,
            ModelRegistry incumbent,
            TrainingConfig config,
            TrainingProgress progress,
            int generation,
            double explorationRate) throws IOException {
        LotroFormat format = trainingState.format;
        String formatCode = format.getCode();
        RLGameStateFeatures features = trainingState.features;
        FotrStartersLearningStepsPersistence persistence = trainingState.persistence;
        System.out.println("--- " + format.getName() + " (" + formatCode + ") · generation "
                + generation + "/" + config.generations() + " ---");

        DeckBuilderTrainer.TrainingResult deckBuilderResult = DeckBuilderTrainer.train(
                format, cardLibrary, trainingState.deckBuilderReferenceDecks(),
                trainingState.deckBuilderModel, incumbent, features,
                trainingState.deckBuilderDirectory, trainingState.dataDirectory,
                config.deckBuilderCandidatesPerGeneration(), config.deckBuilderGamesPerCandidate(),
                config.threads(), config.progressInterval(), progress, generation,
                formatCode.hashCode() ^ (31L * generation) ^ 0x51A7B1L);
        trainingState.deckBuilderModel = deckBuilderResult.model();
        trainingState.generatedDecks = deckBuilderResult.generatedDecks();
        System.out.println("  Deck builder generated " + trainingState.generatedDecks.size()
                + " decks for policy training using the current incumbent policy.");

        TrainingGameCounts gameCounts = trainingGameCounts(
                config.gamesPerGeneration(), config.randomBaselinePercent());
        SimulationStats versusRandomStarters = runTrainingBatch(
                format, trainingState.starterDecks, trainingState.generatedDecks,
                cardLibrary, features, incumbent, persistence, gameCounts.versusRandomStarterGames(),
                config.threads(), config.progressInterval(), generation, true,
                TrainingDeckPairMode.SAME_STARTER, "vs-random-starter", progress, config, explorationRate);
        System.out.println("  random-opponent, same starter result (policy decisions weighted "
                + config.randomBaselineExampleWeight() + "): "
                + versusRandomStarters);

        SimulationStats versusRandomGenerated = runTrainingBatch(
                format, trainingState.starterDecks, trainingState.generatedDecks,
                cardLibrary, features, incumbent, persistence, gameCounts.versusRandomGeneratedGames(),
                config.threads(), config.progressInterval(), generation, true,
                TrainingDeckPairMode.GENERATED, "vs-random-generated", progress, config, explorationRate);
        System.out.println("  random-opponent, generated decks result (policy decisions weighted "
                + config.randomBaselineExampleWeight() + "): "
                + versusRandomGenerated);

        SimulationStats selfPlaySameStarter = runTrainingBatch(
                format, trainingState.starterDecks, trainingState.generatedDecks,
                cardLibrary, features, incumbent, persistence, gameCounts.selfPlaySameStarterGames(),
                config.threads(), config.progressInterval(), generation, false,
                TrainingDeckPairMode.SAME_STARTER, "self-play-same-starter", progress, config, explorationRate);
        System.out.println("  self-play, same starter result: " + selfPlaySameStarter);

        SimulationStats selfPlayDifferentStarters = runTrainingBatch(
                format, trainingState.starterDecks, trainingState.generatedDecks,
                cardLibrary, features, incumbent, persistence, gameCounts.selfPlayDifferentStarterGames(),
                config.threads(), config.progressInterval(), generation, false,
                TrainingDeckPairMode.DIFFERENT_STARTERS, "self-play-different-starters", progress, config,
                explorationRate);
        System.out.println("  self-play, different starters result: " + selfPlayDifferentStarters);

        SimulationStats selfPlayGenerated = runTrainingBatch(
                format, trainingState.starterDecks, trainingState.generatedDecks,
                cardLibrary, features, incumbent, persistence, gameCounts.selfPlayGeneratedGames(),
                config.threads(), config.progressInterval(), generation, false,
                TrainingDeckPairMode.GENERATED, "self-play-generated", progress, config, explorationRate);
        System.out.println("  self-play, generated decks result: " + selfPlayGenerated);

    }

    private static void trainSharedPolicyGeneration(
            List<FormatTrainingState> formatTrainingStates,
            List<Trainer> trainers,
            List<FotrStartersLearningStepsPersistence> trainingDataSources,
            LotroCardBlueprintLibrary cardLibrary,
            ModelRegistry incumbent,
            TrainingConfig config,
            TrainingProgress progress,
            int generation) throws IOException {
        progress.beginFormat("shared-policy", generation);
        ModelRegistry fittedCandidates = incumbent.copy();
        List<TrainedModel> trainedModels = trainModels(
                trainers, trainingDataSources, fittedCandidates, incumbent, config.modelThreads(),
                config.maxTrainingRecordsPerFormat(), generation,
                config.minimumValidationAccuracy(), progress);
        Comparator<TrainedModel> candidateOrder =
                Comparator.comparingDouble(BotModelTrainingMain::validationImprovement).reversed()
                        .thenComparing(model -> model.trainer().getClass().getSimpleName());
        List<TrainedModel> promisingModels = selectPolicyCandidates(
                trainedModels, config.maxPolicyChangesPerGeneration(), config.exploratoryPolicyChanges(),
                config.maximumValidationRegressionPercent(), BotModelTrainingMain::validationImprovement,
                candidateOrder);
        int gamesPerPolicyPerSeat = config.evaluationGamesPerSeat()
                / config.maxPolicyChangesPerGeneration();
        ModelRegistry workingPolicy = incumbent.copy();
        List<TrainedModel> selectedModels = new ArrayList<>();

        if (promisingModels.isEmpty()) {
            System.out.println("No decision models met the data and validation requirements; incumbent retained.");
            skipPolicyEvaluationStages(formatTrainingStates, generation,
                    config.maxPolicyChangesPerGeneration(), progress, "No eligible individual policy changes");
            return;
        }

        for (int slot = 1; slot <= config.maxPolicyChangesPerGeneration(); slot++) {
            if (slot > promisingModels.size()) {
                skipPolicyEvaluationSlot(formatTrainingStates, generation, slot, progress,
                        "No additional promising policy changes");
                continue;
            }

            TrainedModel model = promisingModels.get(slot - 1);
            ModelRegistry candidate = workingPolicy.copy();
            candidate.registerModel(model.trainer().getClass(), model.result().model());
            PolicyEvaluation evaluation = evaluate(
                    formatTrainingStates, cardLibrary, candidate, workingPolicy,
                    gamesPerPolicyPerSeat, config.threads(), config.progressInterval(), generation,
                    config.evaluationSeed() + 1009L * slot, slot, progress);
            double lowerBound = wilsonLowerBoundPercent(evaluation.candidateWins(), evaluation.completedGames());
            int attemptedGames = evaluation.completedGames() + evaluation.skippedGames();
            double skippedRate = attemptedGames == 0 ? 1.0
                    : evaluation.skippedGames() / (double) attemptedGames;
            boolean formatsAvoidExcessiveSkips = evaluation.formats().stream()
                    .allMatch(format -> format.skippedRate() <= MAX_EVALUATION_SKIP_RATE);
            boolean formatsAvoidMaterialRegression = evaluation.formats().stream()
                    .allMatch(format -> wilsonUpperBoundPercent(format.candidateWins(), format.completedGames())
                            >= MINIMUM_FORMAT_WIN_RATE_PERCENT);
            boolean selected = skippedRate <= MAX_EVALUATION_SKIP_RATE
                    && formatsAvoidExcessiveSkips
                    && formatsAvoidMaterialRegression
                    && lowerBound > 50.0;

            String candidateType = validationImprovement(model) < 0.0
                    ? " exploratory challenger"
                    : "";
            System.out.printf(Locale.ROOT,
                    "Individual%s policy %s: %d/%d wins (%.1f%%; 95%% lower bound %.1f%%), %d skipped; %s%n",
                    candidateType, model.trainer().getClass().getSimpleName(), evaluation.candidateWins(),
                    evaluation.completedGames(), evaluation.winRate(), lowerBound, evaluation.skippedGames(),
                    selected ? "selected" : "current policy kept");
            for (FormatEvaluation format : evaluation.formats()) {
                System.out.printf(Locale.ROOT,
                        "  %s: %.1f%% (%d/%d; 95%% interval %.1f-%.1f%%)%n",
                        format.formatCode(), format.winRate(), format.candidateWins(), format.completedGames(),
                        wilsonLowerBoundPercent(format.candidateWins(), format.completedGames()),
                        wilsonUpperBoundPercent(format.candidateWins(), format.completedGames()));
                progress.beginFormat(format.formatCode(), generation);
                progress.policyModelComparisonCompleted(
                        model.trainer().getClass().getSimpleName(), slot,
                        model.result().validationAccuracy(), model.result().incumbentValidationAccuracy(),
                        format.candidateWins(), format.completedGames(), format.skippedGames(),
                        format.winRate(), evaluation.winRate(), lowerBound, selected,
                        generation == config.generations());
            }
            if (selected) {
                workingPolicy = candidate;
                selectedModels.add(model);
            }
        }

        if (!selectedModels.isEmpty()) {
            Path modelDirectory = formatTrainingStates.getFirst().modelDirectory;
            for (TrainedModel model : selectedModels)
                ModelIO.saveModel(model.trainer().getClass(), model.result().model(), modelDirectory);
            System.out.println("Selected " + selectedModels.size()
                    + " individual policy change(s) for the next generation.");
        } else {
            System.out.println("No individual policy changes passed the paired cross-format gate; "
                    + "the incumbent policy remains active.");
        }
    }

    private static double validationImprovement(TrainedModel model) {
        Double incumbentAccuracy = model.result().incumbentValidationAccuracy();
        return incumbentAccuracy == null
                ? model.result().validationAccuracy()
                : model.result().validationAccuracy() - incumbentAccuracy;
    }

    static double explorationRateForGeneration(
            int generation,
            int decayGenerations,
            double startPercent,
            double endPercent) {
        if (generation < 1 || decayGenerations < 1
                || !Double.isFinite(startPercent) || startPercent < 0.0 || startPercent > 100.0
                || !Double.isFinite(endPercent) || endPercent < 0.0 || endPercent > 100.0)
            throw new IllegalArgumentException("Invalid generation exploration schedule");

        double progress = Math.min(1.0, (generation - 1) / (double) decayGenerations);
        return (startPercent + (endPercent - startPercent) * progress) / 100.0;
    }

    static <T> List<T> selectPolicyCandidates(
            List<T> candidates,
            int maxChanges,
            int exploratoryChanges,
            double maximumValidationRegressionPercent,
            ToDoubleFunction<T> validationImprovement,
            Comparator<T> candidateOrder) {
        if (maxChanges < 1 || exploratoryChanges < 0 || exploratoryChanges > maxChanges
                || !Double.isFinite(maximumValidationRegressionPercent)
                || maximumValidationRegressionPercent < 0.0
                || maximumValidationRegressionPercent > 100.0)
            throw new IllegalArgumentException("Invalid policy candidate selection settings");

        List<T> stableCandidates = candidates.stream()
                .filter(candidate -> validationImprovement.applyAsDouble(candidate) >= 0.0)
                .sorted(candidateOrder)
                .toList();
        List<T> exploratoryCandidates = candidates.stream()
                .filter(candidate -> {
                    double improvement = validationImprovement.applyAsDouble(candidate);
                    return improvement < 0.0 && improvement >= -maximumValidationRegressionPercent;
                })
                .sorted(candidateOrder)
                .toList();

        List<T> selected = new ArrayList<>(maxChanges);
        int reservedExploratoryChanges = Math.min(exploratoryChanges, maxChanges);
        int stableLimit = maxChanges - reservedExploratoryChanges;
        int stableIndex = Math.min(stableLimit, stableCandidates.size());
        int exploratoryIndex = Math.min(reservedExploratoryChanges, exploratoryCandidates.size());
        selected.addAll(stableCandidates.subList(0, stableIndex));
        selected.addAll(exploratoryCandidates.subList(0, exploratoryIndex));

        while (selected.size() < maxChanges && stableIndex < stableCandidates.size())
            selected.add(stableCandidates.get(stableIndex++));
        while (selected.size() < maxChanges && exploratoryIndex < exploratoryCandidates.size())
            selected.add(exploratoryCandidates.get(exploratoryIndex++));
        return List.copyOf(selected);
    }

    private static void skipPolicyEvaluationStages(
            List<FormatTrainingState> states,
            int generation,
            int maxPolicyChanges,
            TrainingProgress progress,
            String reason) {
        for (int slot = 1; slot <= maxPolicyChanges; slot++)
            skipPolicyEvaluationSlot(states, generation, slot, progress, reason);
    }

    private static void skipPolicyEvaluationSlot(
            List<FormatTrainingState> states,
            int generation,
            int slot,
            TrainingProgress progress,
            String reason) {
        for (FormatTrainingState state : states) {
            String formatCode = state.format.getCode();
            progress.beginFormat(formatCode, generation);
            progress.skipStage(formatCode + " generation " + generation
                    + " evaluation policy-change-" + slot + "-candidate-first", generation, reason);
            progress.skipStage(formatCode + " generation " + generation
                    + " evaluation policy-change-" + slot + "-candidate-second", generation, reason);
        }
    }

    private static RLGameStateFeatures featuresFor(String formatCode) {
        return new MovieTrilogyRLGameStateFeatures(formatCode);
    }

    private static Path modelDirectoryFor(Path baseDirectory, String formatCode) {
        return ModelIO.resolveModelDirectory(baseDirectory, formatCode);
    }

    private static List<FotrStartersLearningStepsPersistence> trainingDataSourcesFor(
            Path versionedDataDirectory,
            TrainingProgress progress) {
        return SUPPORTED_FORMATS.stream()
                .map(sourceFormat -> new FotrStartersLearningStepsPersistence(
                        versionedDataDirectory.resolve(sourceFormat),
                        compaction -> progress.trainingDataCompacted(
                                sourceFormat, compaction.trainerName(), compaction.fileName(),
                                compaction.recordsBefore(), compaction.recordsAfter())))
                .toList();
    }

    private static ModelRegistry loadModels(List<Trainer> trainers, Path directory) {
        ModelRegistry registry = new ModelRegistry();
        List<String> unavailable = new ArrayList<>();
        for (Trainer trainer : trainers) {
            try {
                registry.registerModel(trainer.getClass(), ModelIO.loadModel(trainer.getClass(), directory));
            } catch (IllegalStateException e) {
                unavailable.add(trainer.getClass().getSimpleName());
            }
        }
        System.out.println("  Loaded " + registry.getRegisteredTrainerTypes().size() + "/" + trainers.size()
                + " baseline models from " + directory);
        if (!unavailable.isEmpty())
            System.out.println("  Missing models use base decisions: " + String.join(", ", unavailable));
        return registry;
    }

    private static Classifier<double[]> loadDeckBuilderModel(Path directory, String formatCode) {
        try {
            Classifier<double[]> model = ModelIO.loadModel(DeckBuilderTrainer.class, directory);
            System.out.println("  Loaded deck-builder model for " + formatCode + " from " + directory);
            return model;
        } catch (IllegalStateException e) {
            System.out.println("  Deck-builder model for " + formatCode
                    + " is unavailable; generated legal decks use random sampling (" + e.getMessage() + ")");
            return null;
        }
    }

    private static SimulationStats runTrainingBatch(
            LotroFormat format,
            List<LotroDeck> starterDecks,
            List<LotroDeck> generatedDecks,
            LotroCardBlueprintLibrary cardLibrary,
            RLGameStateFeatures features,
            ModelRegistry models,
            FotrStartersLearningStepsPersistence persistence,
            int games,
            int threads,
            int progressInterval,
            int generation,
            boolean versusRandom,
            TrainingDeckPairMode deckPairMode,
            String label,
            TrainingProgress progress,
            TrainingConfig config,
            double explorationRate) {
        Supplier<MovieBlockSimulation.DeckPair> deckPairProvider =
                trainingDeckPairProvider(deckPairMode, starterDecks, generatedDecks);
        return runParallelBatch(format, deckPairProvider, cardLibrary, games, threads, progressInterval,
                format.getCode() + " generation " + generation + " " + label, generation, progress, worker -> {
                    double episodeExampleWeight = versusRandom
                            ? config.randomBaselineExampleWeight()
                            : 1.0;
                    ReplayBuffer replayBuffer = versusRandom && episodeExampleWeight == 0.0
                            ? null
                            : new ReplayBuffer(REPLAY_BUFFER_CAPACITY);
                    if (replayBuffer != null) {
                        replayBuffer.addListener(REPLAY_DRAIN_THRESHOLD,
                                buffer -> persistence.save(buffer.drain()));
                    }
                    String botOneName = trainingBotName(format.getCode(), generation, worker, "one");
                    String botTwoName = trainingBotName(format.getCode(), generation, worker, "two");
                    BotPlayer botOne = new FotrStarterBot(
                            features, botOneName, models, replayBuffer, progress,
                            explorationRate, config.explorationExampleWeight(), episodeExampleWeight);
                    BotPlayer botTwo = versusRandom
                            ? new RandomDecisionBot(botTwoName)
                            : new FotrStarterBot(
                                    features, botTwoName, models, replayBuffer, progress,
                                    explorationRate, config.explorationExampleWeight(), 1.0);
                    return new BotPair(botOne, botTwo, replayBuffer == null
                            ? () -> {
                            }
                            : () -> persistence.save(replayBuffer.drain()));
                });
    }

    private static PolicyEvaluation evaluate(
            List<FormatTrainingState> formatTrainingStates,
            LotroCardBlueprintLibrary cardLibrary,
            ModelRegistry candidate,
            ModelRegistry incumbent,
            int gamesPerSeat,
            int threads,
            int progressInterval,
            int generation,
            long evaluationSeed,
            int evaluationSlot,
            TrainingProgress progress) {
        List<FormatEvaluation> evaluations = new ArrayList<>(formatTrainingStates.size());
        for (FormatTrainingState trainingState : formatTrainingStates) {
            String formatCode = trainingState.format.getCode();
            progress.beginFormat(formatCode, generation);
            List<MovieBlockSimulation.DeckPair> deckPairs = evaluationDeckPairs(
                    trainingState.starterDecks, trainingState.generatedDecks, gamesPerSeat,
                    evaluationSeed + 31L * generation + formatCode.hashCode() + 97L * evaluationSlot);
            String labelPrefix = "policy-change-" + evaluationSlot + "-";
            SimulationStats candidateFirst = runEvaluationBatch(
                    trainingState, cardLibrary, deckPairs, false, candidate, incumbent,
                    threads, progressInterval, generation, labelPrefix + "candidate-first", progress);
            SimulationStats candidateSecond = runEvaluationBatch(
                    trainingState, cardLibrary, deckPairs, true, incumbent, candidate,
                    threads, progressInterval, generation, labelPrefix + "candidate-second", progress);
            int candidateWins = candidateFirst.getBot1Wins() + candidateSecond.getBot2Wins();
            int completedGames = candidateFirst.getTotalGames() + candidateSecond.getTotalGames();
            int skippedGames = candidateFirst.getSkippedGames() + candidateSecond.getSkippedGames();
            if (completedGames == 0)
                throw new IllegalStateException("No evaluation games completed successfully for "
                        + formatCode + " generation " + generation);
            evaluations.add(new FormatEvaluation(formatCode, candidateWins, completedGames, skippedGames));
        }
        return new PolicyEvaluation(List.copyOf(evaluations));
    }

    static List<MovieBlockSimulation.DeckPair> evaluationDeckPairs(
            List<LotroDeck> starterDecks,
            List<LotroDeck> generatedDecks,
            int games,
            long seed) {
        requireAtLeast(starterDecks, 2, "starter");
        requireAtLeast(generatedDecks, 1, "generated");
        Random random = new Random(seed);
        List<MovieBlockSimulation.DeckPair> pairs = new ArrayList<>(games);
        for (int game = 0; game < games; game++) {
            pairs.add(switch (Math.floorMod(game, 4)) {
                case 0 -> {
                    LotroDeck starter = randomDeck(starterDecks, random);
                    yield new MovieBlockSimulation.DeckPair(starter, starter);
                }
                case 1 -> {
                    int firstIndex = random.nextInt(starterDecks.size());
                    int secondIndex = random.nextInt(starterDecks.size() - 1);
                    if (secondIndex >= firstIndex)
                        secondIndex++;
                    yield new MovieBlockSimulation.DeckPair(
                            starterDecks.get(firstIndex), starterDecks.get(secondIndex));
                }
                default -> new MovieBlockSimulation.DeckPair(
                        randomDeck(generatedDecks, random), randomDeck(generatedDecks, random));
            });
        }
        return List.copyOf(pairs);
    }

    private static SimulationStats runEvaluationBatch(
            FormatTrainingState trainingState,
            LotroCardBlueprintLibrary cardLibrary,
            List<MovieBlockSimulation.DeckPair> deckPairs,
            boolean swapDecks,
            ModelRegistry firstModels,
            ModelRegistry secondModels,
            int threads,
            int progressInterval,
            int generation,
            String label,
            TrainingProgress progress) {
        AtomicInteger nextDeckPair = new AtomicInteger();
        return runParallelBatch(trainingState.format, () -> {
                    MovieBlockSimulation.DeckPair pair = deckPairs.get(nextDeckPair.getAndIncrement());
                    return swapDecks
                            ? new MovieBlockSimulation.DeckPair(pair.second(), pair.first())
                            : pair;
                },
                cardLibrary,
                deckPairs.size(), threads, progressInterval,
                trainingState.format.getCode() + " generation " + generation + " evaluation " + label,
                generation, progress, worker -> {
                    String prefix = "~eval-" + trainingState.format.getCode()
                            + "-" + generation + "-" + label + "-" + worker;
                    BotPlayer first = new FotrStarterBot(
                            trainingState.features, prefix + "-one", firstModels, null, progress);
                    BotPlayer second = new FotrStarterBot(
                            trainingState.features, prefix + "-two", secondModels, null, progress);
                    return new BotPair(first, second, () -> {
                    });
                });
    }

    static double wilsonLowerBoundPercent(int wins, int games) {
        return wilsonBoundPercent(wins, games, false);
    }

    static double wilsonUpperBoundPercent(int wins, int games) {
        return wilsonBoundPercent(wins, games, true);
    }

    private static double wilsonBoundPercent(int wins, int games, boolean upperBound) {
        if (games <= 0 || wins < 0 || wins > games)
            return Double.NaN;
        double z = 1.96;
        double proportion = wins / (double) games;
        double zSquared = z * z;
        double denominator = 1.0 + zSquared / games;
        double center = proportion + zSquared / (2.0 * games);
        double margin = z * Math.sqrt(
                proportion * (1.0 - proportion) / games
                        + zSquared / (4.0 * games * games));
        return (center + (upperBound ? margin : -margin)) / denominator * 100.0;
    }

    private static SimulationStats runParallelBatch(
            LotroFormat format,
            Supplier<MovieBlockSimulation.DeckPair> deckPairProvider,
            LotroCardBlueprintLibrary cardLibrary,
            int totalGames,
            int requestedThreads,
            int progressInterval,
            String progressLabel,
            int generation,
            TrainingProgress progress,
            IntFunction<BotPair> botPairFactory) {
        if (totalGames <= 0)
            return new SimulationStats(0, 0, 0);

        if (progress != null)
            progress.beginStage(progressLabel, generation, totalGames);
        int workerCount = Math.min(totalGames, requestedThreads);
        ExecutorService executor = Executors.newFixedThreadPool(
                workerCount, new TrainingThreadFactory("simulation-" + progressLabel));
        List<Future<SimulationStats>> workers = new ArrayList<>(workerCount);
        AtomicInteger completedGames = new AtomicInteger();
        AtomicInteger nextGame = new AtomicInteger();

        try {
            for (int worker = 0; worker < workerCount; worker++) {
                int workerIndex = worker;
                workers.add(executor.submit(() -> {
                    BotPair pair = botPairFactory.apply(workerIndex);
                    Runnable gameCompleted = () -> {
                        int completed = completedGames.incrementAndGet();
                        if (progress != null)
                            progress.gameCompleted();
                        if (completed % progressInterval == 0 || completed == totalGames)
                            System.out.println("  " + progressLabel + ": " + completed + "/" + totalGames + " games");
                    };
                    try {
                        return new SimpleBatchSimulationRunner(
                                new MovieBlockSimulation(format, cardLibrary, deckPairProvider, progress),
                                pair.first(), pair.second(), totalGames, gameCompleted, progress)
                                .runClaimedGames(() -> nextGame.getAndIncrement() < totalGames);
                    } finally {
                        pair.flush().run();
                    }
                }));
            }

            SimulationStats aggregateStats = new SimulationStats(0, 0, 0);
            for (Future<SimulationStats> worker : workers) {
                aggregateStats = aggregateStats.plus(worker.get());
            }
            if (aggregateStats.getSkippedGames() > 0) {
                System.err.printf(Locale.ROOT,
                        "Training batch %s completed with %d skipped game(s): %d of %d games completed. "
                                + "See the preceding stack traces for details.%n",
                        progressLabel, aggregateStats.getSkippedGames(), aggregateStats.getTotalGames(),
                        aggregateStats.getTotalGames() + aggregateStats.getSkippedGames());
            }
            if (progress != null)
                progress.batchCompleted(progressLabel, aggregateStats);
            return aggregateStats;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bot training was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            if (cause instanceof Error error)
                throw error;
            throw new IllegalStateException("Bot simulation worker failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<TrainedModel> trainModels(
            List<Trainer> trainers,
            List<FotrStartersLearningStepsPersistence> trainingDataSources,
            ModelRegistry candidate,
            ModelRegistry incumbent,
            int requestedThreads,
            int maxTrainingRecordsPerFormat,
            int generation,
            double minimumValidationAccuracy,
            TrainingProgress progress) {
        int workerCount = Math.min(trainers.size(), requestedThreads);
        ExecutorService executor = Executors.newFixedThreadPool(
                workerCount, new TrainingThreadFactory("model-fitting-generation-" + generation));
        CompletionService<TrainerTrainingResult> completions = new ExecutorCompletionService<>(executor);
        if (progress != null)
            progress.beginStage("decision-model-fitting", generation, trainers.size());
        try {
            for (Trainer trainer : trainers) {
                if (progress != null)
                    progress.modelQueued(trainer.getClass().getSimpleName());
                completions.submit(() -> {
                    if (progress != null)
                        progress.modelStarted(trainer.getClass().getSimpleName());
                    if (!(trainer instanceof AbstractTrainer abstractTrainer))
                        throw new IllegalStateException("Unsupported trainer type: " + trainer.getClass().getName());
                    AbstractTrainer.TrainingAttempt attempt =
                            abstractTrainer.trainIfReadyWithMetrics(
                                    loadTrainingSteps(trainer, trainingDataSources, maxTrainingRecordsPerFormat),
                                    incumbent.getModel(trainer.getClass()));
                    return new TrainerTrainingResult(trainer, attempt);
                });
            }

            List<TrainedModel> trainedModels = new ArrayList<>();
            for (int completed = 1; completed <= trainers.size(); completed++) {
                TrainerTrainingResult trainingResult = completions.take().get();
                AbstractTrainer.TrainingAttempt attempt = trainingResult.attempt();
                if (attempt.result().isEmpty()) {
                    if (progress != null)
                        progress.modelCompleted(trainingResult.trainer().getClass().getSimpleName(),
                                "retained-" + attempt.status(), attempt.totalExamples(),
                                attempt.positiveExamples(), attempt.negativeExamples(),
                                attempt.trainingExamples(), attempt.validationExamples(),
                                attempt.trainingAccuracy(), attempt.validationAccuracy(),
                                attempt.trainingMillis());
                    System.out.println("  Model training " + completed + "/" + trainers.size() + ": "
                            + trainingResult.trainer().getClass().getSimpleName()
                            + " retained (" + attempt.status() + "; " + attempt.totalExamples() + " examples; "
                            + attempt.positiveExamples() + " positive, "
                            + attempt.negativeExamples() + " negative)");
                    continue;
                }

                AbstractTrainer.TrainingResult result = attempt.result().orElseThrow();
                if (result.validationAccuracy() < minimumValidationAccuracy) {
                    if (progress != null)
                        progress.modelCompleted(trainingResult.trainer().getClass().getSimpleName(),
                                "retained-below-accuracy-threshold", attempt.totalExamples(),
                                attempt.positiveExamples(), attempt.negativeExamples(),
                                result.trainingExamples(), result.validationExamples(),
                                result.trainingAccuracy(), result.validationAccuracy(),
                                result.incumbentValidationAccuracy(),
                                attempt.trainingMillis());
                    System.out.printf(Locale.ROOT,
                            "  Model training %d/%d: %s retained (train %.1f%%, validation %.1f%% is below %.1f%%; "
                                    + "train=%d, validation=%d)%n",
                            completed, trainers.size(), trainingResult.trainer().getClass().getSimpleName(),
                            result.trainingAccuracy(), result.validationAccuracy(), minimumValidationAccuracy,
                            result.trainingExamples(), result.validationExamples());
                    continue;
                }

                candidate.registerModel(trainingResult.trainer().getClass(), result.model());
                trainedModels.add(new TrainedModel(trainingResult.trainer(), result));
                if (progress != null)
                    progress.modelCompleted(trainingResult.trainer().getClass().getSimpleName(),
                            "trained", attempt.totalExamples(), attempt.positiveExamples(),
                            attempt.negativeExamples(), result.trainingExamples(), result.validationExamples(),
                            result.trainingAccuracy(), result.validationAccuracy(),
                            result.incumbentValidationAccuracy(), attempt.trainingMillis());
                System.out.printf(Locale.ROOT,
                        "  Model training %d/%d: %s train %.1f%% / validation %.1f%% vs incumbent %s "
                                + "(train=%d, validation=%d; "
                                + "%d positive, %d negative)%n",
                        completed, trainers.size(), trainingResult.trainer().getClass().getSimpleName(),
                        result.trainingAccuracy(), result.validationAccuracy(),
                        result.incumbentValidationAccuracy() == null
                                ? "not available"
                                : String.format(Locale.ROOT, "%.1f%%", result.incumbentValidationAccuracy()),
                        result.trainingExamples(), result.validationExamples(),
                        result.positiveExamples(), result.negativeExamples());
            }
            return trainedModels;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model training was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            if (cause instanceof Error error)
                throw error;
            throw new IllegalStateException("Bot model trainer failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<LearningStep> loadTrainingSteps(
            Trainer trainer,
            List<FotrStartersLearningStepsPersistence> trainingDataSources,
            int maxRecordsPerFormat) {
        List<LearningStep> steps = new ArrayList<>();
        for (FotrStartersLearningStepsPersistence source : trainingDataSources)
            steps.addAll(source.load(trainer, maxRecordsPerFormat));
        return steps;
    }

    static TrainingGameCounts trainingGameCounts(int gamesPerGeneration, int randomBaselinePercent) {
        if (gamesPerGeneration < 1)
            throw new IllegalArgumentException("Games per generation must be positive");
        if (randomBaselinePercent < 0 || randomBaselinePercent > 20)
            throw new IllegalArgumentException("Random-baseline percentage must be between 0 and 20");

        int versusRandomGames = randomBaselinePercent == 0
                ? 0
                : Math.min(gamesPerGeneration, Math.max(1, gamesPerGeneration * randomBaselinePercent / 100));
        int selfPlayGames = gamesPerGeneration - versusRandomGames;
        int versusRandomStarterGames = (versusRandomGames + 1) / 2;
        int selfPlayStarterGames = (selfPlayGames + 1) / 2;
        int selfPlaySameStarterGames = (selfPlayStarterGames + 1) / 2;
        return new TrainingGameCounts(
                versusRandomStarterGames,
                versusRandomGames - versusRandomStarterGames,
                selfPlaySameStarterGames,
                selfPlayStarterGames - selfPlaySameStarterGames,
                selfPlayGames - selfPlayStarterGames);
    }

    static Supplier<MovieBlockSimulation.DeckPair> trainingDeckPairProvider(
            TrainingDeckPairMode mode,
            List<LotroDeck> starterDecks,
            List<LotroDeck> generatedDecks) {
        return switch (mode) {
            case SAME_STARTER -> {
                requireAtLeast(starterDecks, 1, "starter");
                yield () -> {
                    LotroDeck starter = randomDeck(starterDecks);
                    return new MovieBlockSimulation.DeckPair(starter, starter);
                };
            }
            case DIFFERENT_STARTERS -> {
                requireAtLeast(starterDecks, 2, "starter");
                yield () -> {
                    int firstIndex = ThreadLocalRandom.current().nextInt(starterDecks.size());
                    int secondIndex = ThreadLocalRandom.current().nextInt(starterDecks.size() - 1);
                    if (secondIndex >= firstIndex)
                        secondIndex++;
                    return new MovieBlockSimulation.DeckPair(
                            starterDecks.get(firstIndex), starterDecks.get(secondIndex));
                };
            }
            case GENERATED -> {
                requireAtLeast(generatedDecks, 1, "generated");
                yield () -> new MovieBlockSimulation.DeckPair(
                        randomDeck(generatedDecks), randomDeck(generatedDecks));
            }
        };
    }

    private static LotroDeck randomDeck(List<LotroDeck> decks) {
        return decks.get(ThreadLocalRandom.current().nextInt(decks.size()));
    }

    private static LotroDeck randomDeck(List<LotroDeck> decks, Random random) {
        return decks.get(random.nextInt(decks.size()));
    }

    private static void requireAtLeast(List<LotroDeck> decks, int minimum, String deckType) {
        if (decks.size() < minimum)
            throw new IllegalArgumentException("At least " + minimum + " " + deckType
                    + " deck(s) are required for this training stage");
    }

    private static String trainingBotName(String formatCode, int generation, int worker, String side) {
        return "~train-" + formatCode + "-" + generation + "-" + worker + "-" + side;
    }

    enum TrainingDeckPairMode {
        SAME_STARTER,
        DIFFERENT_STARTERS,
        GENERATED
    }

    record TrainingGameCounts(
            int versusRandomStarterGames,
            int versusRandomGeneratedGames,
            int selfPlaySameStarterGames,
            int selfPlayDifferentStarterGames,
            int selfPlayGeneratedGames) {
        int totalGames() {
            return versusRandomStarterGames + versusRandomGeneratedGames
                    + selfPlaySameStarterGames + selfPlayDifferentStarterGames + selfPlayGeneratedGames;
        }
    }

    private record FormatEvaluation(
            String formatCode,
            int candidateWins,
            int completedGames,
            int skippedGames) {
        private double winRate() {
            return candidateWins * 100.0 / completedGames;
        }

        private double skippedRate() {
            int attemptedGames = completedGames + skippedGames;
            return attemptedGames == 0 ? 1.0 : skippedGames / (double) attemptedGames;
        }
    }

    private record PolicyEvaluation(List<FormatEvaluation> formats) {
        private int candidateWins() {
            return formats.stream().mapToInt(FormatEvaluation::candidateWins).sum();
        }

        private int completedGames() {
            return formats.stream().mapToInt(FormatEvaluation::completedGames).sum();
        }

        private int skippedGames() {
            return formats.stream().mapToInt(FormatEvaluation::skippedGames).sum();
        }

        private double winRate() {
            return candidateWins() * 100.0 / completedGames();
        }
    }

    private static void validateCardResources(Path resourcesDirectory) {
        for (String required : List.of("cards", "blueprintMapping.txt", "setConfig.hjson", "rarities",
                "lotrFormats.hjson", "sealed")) {
            if (!Files.exists(resourcesDirectory.resolve(required)))
                throw new IllegalArgumentException("Missing card resource " + resourcesDirectory.resolve(required));
        }
    }

    private static List<TrainingProgress.PlannedStage> trainingPlan(
            TrainingConfig config,
            int trainerCount) {
        int deckGames = Math.multiplyExact(config.deckBuilderCandidatesPerGeneration(),
                config.deckBuilderGamesPerCandidate());
        return trainingPlan(config.formats(), config.generations(), config.gamesPerGeneration(),
                config.randomBaselinePercent(), config.evaluationGamesPerSeat(),
                config.maxPolicyChangesPerGeneration(), deckGames, trainerCount);
    }

    static List<TrainingProgress.PlannedStage> trainingPlan(
            List<String> formats,
            int generations,
            int gamesPerGeneration,
            int randomBaselinePercent,
            int evaluationGamesPerSeat,
            int deckGames,
            int trainerCount) {
        return trainingPlan(formats, generations, gamesPerGeneration, randomBaselinePercent,
                evaluationGamesPerSeat, 1, deckGames, trainerCount);
    }

    static List<TrainingProgress.PlannedStage> trainingPlan(
            List<String> formats,
            int generations,
            int gamesPerGeneration,
            int randomBaselinePercent,
            int evaluationGamesPerSeat,
            int maxPolicyChangesPerGeneration,
            int deckGames,
            int trainerCount) {
        if (evaluationGamesPerSeat < 1 || maxPolicyChangesPerGeneration < 1
                || maxPolicyChangesPerGeneration > evaluationGamesPerSeat)
            throw new IllegalArgumentException("Policy evaluation budget must provide at least one game per seat "
                    + "for every individual policy change");
        int gamesPerPolicyPerSeat = evaluationGamesPerSeat / maxPolicyChangesPerGeneration;
        List<TrainingProgress.PlannedStage> plan = new ArrayList<>();
        for (int generation = 1; generation <= generations; generation++) {
            for (String formatCode : formats) {
                plan.add(new TrainingProgress.PlannedStage(formatCode,
                        formatCode + " generation " + generation + " deck-builder-evaluation",
                        generation, deckGames));
                TrainingGameCounts counts = trainingGameCounts(gamesPerGeneration, randomBaselinePercent);
                addPlannedStage(plan, formatCode, generation, "vs-random-starter",
                        counts.versusRandomStarterGames());
                addPlannedStage(plan, formatCode, generation, "vs-random-generated",
                        counts.versusRandomGeneratedGames());
                addPlannedStage(plan, formatCode, generation, "self-play-same-starter",
                        counts.selfPlaySameStarterGames());
                addPlannedStage(plan, formatCode, generation, "self-play-different-starters",
                        counts.selfPlayDifferentStarterGames());
                addPlannedStage(plan, formatCode, generation, "self-play-generated",
                        counts.selfPlayGeneratedGames());
            }
            plan.add(new TrainingProgress.PlannedStage(
                    "shared-policy", "decision-model-fitting", generation, trainerCount));
            for (int slot = 1; slot <= maxPolicyChangesPerGeneration; slot++) {
                for (String formatCode : formats) {
                    String label = formatCode + " generation " + generation
                            + " evaluation policy-change-" + slot + "-";
                    plan.add(new TrainingProgress.PlannedStage(
                            formatCode, label + "candidate-first", generation, gamesPerPolicyPerSeat));
                    plan.add(new TrainingProgress.PlannedStage(
                            formatCode, label + "candidate-second", generation, gamesPerPolicyPerSeat));
                }
            }
        }
        return List.copyOf(plan);
    }

    private static void addPlannedStage(
            List<TrainingProgress.PlannedStage> plan,
            String formatCode,
            int generation,
            String stage,
            int games) {
        if (games > 0) {
            plan.add(new TrainingProgress.PlannedStage(
                    formatCode, formatCode + " generation " + generation + " " + stage, generation, games));
        }
    }

    private static final class FormatTrainingState {
        private final LotroFormat format;
        private final List<LotroDeck> starterDecks;
        private final RLGameStateFeatures features;
        private final FotrStartersLearningStepsPersistence persistence;
        private final Path modelDirectory;
        private final Path deckBuilderDirectory;
        private final Path dataDirectory;
        private Classifier<double[]> deckBuilderModel;
        private List<LotroDeck> generatedDecks = List.of();

        private FormatTrainingState(
                LotroFormat format,
                List<LotroDeck> starterDecks,
                RLGameStateFeatures features,
                FotrStartersLearningStepsPersistence persistence,
                Path modelDirectory,
                Path deckBuilderDirectory,
                Path dataDirectory,
                Classifier<double[]> deckBuilderModel) {
            this.format = format;
            this.starterDecks = List.copyOf(starterDecks);
            this.features = features;
            this.persistence = persistence;
            this.modelDirectory = modelDirectory;
            this.deckBuilderDirectory = deckBuilderDirectory;
            this.dataDirectory = dataDirectory;
            this.deckBuilderModel = deckBuilderModel;
        }

        private List<LotroDeck> deckBuilderReferenceDecks() {
            List<LotroDeck> decks = new ArrayList<>(starterDecks);
            decks.addAll(generatedDecks);
            return List.copyOf(decks);
        }
    }

    private record BotPair(BotPlayer first, BotPlayer second, Runnable flush) {
    }

    private record TrainedModel(Trainer trainer, AbstractTrainer.TrainingResult result) {
    }

    private record TrainerTrainingResult(Trainer trainer, AbstractTrainer.TrainingAttempt attempt) {
    }

    private record TrainingConfig(
            Path configFile,
            Path cardResourcesDirectory,
            Path modelDirectory,
            Path trainingDataDirectory,
            Path metricsDirectory,
            List<String> formats,
            int gamesPerGeneration,
            int randomBaselinePercent,
            int generations,
            int threads,
            int modelThreads,
            int maxTrainingRecordsPerFormat,
            int evaluationGamesPerSeat,
            int maxPolicyChangesPerGeneration,
            int exploratoryPolicyChanges,
            double maximumValidationRegressionPercent,
            String explorationProfile,
            int freshExplorationStartPercent,
            int freshExplorationEndPercent,
            int refinementExplorationStartPercent,
            int refinementExplorationEndPercent,
            int explorationDecayGenerations,
            double explorationExampleWeight,
            double randomBaselineExampleWeight,
            long evaluationSeed,
            int deckBuilderCandidatesPerGeneration,
            int deckBuilderGamesPerCandidate,
            double minimumValidationAccuracy,
            int progressInterval,
            String dashboardHost,
            int dashboardPort) {
        private static TrainingConfig load(String[] args) throws IOException {
            Path configFile = configPath(args);
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read training config " + configFile, e);
            }

            Path baseDirectory = configFile.getParent();
            int threads = integerProperty(properties, "training.cpu.threads", 0);
            if (threads == 0)
                threads = Runtime.getRuntime().availableProcessors();
            int modelThreads = integerProperty(properties, "training.model.threads", 1);
            int maxTrainingRecordsPerFormat =
                    integerProperty(properties, "training.model.max.records.per.format", 5000);
            List<String> formats = List.of(properties.getProperty("training.formats",
                            String.join(",", SUPPORTED_FORMATS)).split(","))
                    .stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
            if (formats.isEmpty() || formats.stream().anyMatch(format -> !SUPPORTED_FORMATS.contains(format)))
                throw new IllegalArgumentException("training.formats must contain only "
                        + String.join(", ", SUPPORTED_FORMATS));
            if (!formats.containsAll(SUPPORTED_FORMATS))
                throw new IllegalArgumentException("Publishing shared movie-trilogy models requires training all formats: "
                        + String.join(", ", SUPPORTED_FORMATS));

            int games = integerProperty(properties, "training.games.per.generation", 1000);
            int randomBaselinePercent = integerProperty(properties, "training.random.baseline.percent", 5);
            int generations = integerProperty(properties, "training.generations", 1);
            int evaluationGames = integerProperty(properties, "training.evaluation.games.per.seat", 50);
            int maxPolicyChanges = integerProperty(
                    properties, "training.evaluation.max.policy.changes", 5);
            long evaluationSeed = longProperty(properties, "training.evaluation.seed", 1L);
            int deckBuilderCandidates = integerProperty(
                    properties, "training.deckbuilder.candidates.per.generation", 24);
            int deckBuilderGamesPerCandidate = integerProperty(
                    properties, "training.deckbuilder.games.per.candidate", 6);
            double minimumValidationAccuracy = decimalProperty(
                    properties, "training.models.minimum.validation.accuracy", 0.0);
            int exploratoryPolicyChanges = integerProperty(
                    properties, "training.evaluation.exploratory.changes", 2);
            double maximumValidationRegressionPercent = decimalProperty(
                    properties, "training.evaluation.max.validation.regression", 3.0);
            String explorationProfile = properties.getProperty("training.exploration.profile", "auto")
                    .trim().toLowerCase(Locale.ROOT);
            int freshExplorationStartPercent = integerProperty(
                    properties, "training.exploration.fresh.start.percent", 20);
            int freshExplorationEndPercent = integerProperty(
                    properties, "training.exploration.fresh.end.percent", 5);
            int refinementExplorationStartPercent = integerProperty(
                    properties, "training.exploration.refinement.start.percent", 8);
            int refinementExplorationEndPercent = integerProperty(
                    properties, "training.exploration.refinement.end.percent", 2);
            int explorationDecayGenerations = integerProperty(
                    properties, "training.exploration.decay.generations", 10);
            double explorationExampleWeight = decimalProperty(
                    properties, "training.exploration.example.weight", 0.25);
            double randomBaselineExampleWeight = decimalProperty(
                    properties, "training.random.baseline.example.weight", 0.25);
            int progressInterval = integerProperty(properties, "training.progress.interval.games", 100);
            String dashboardHost = System.getenv("GEMP_BOT_TRAINING_DASHBOARD_HOST");
            if (dashboardHost == null || dashboardHost.isBlank())
                dashboardHost = properties.getProperty("training.dashboard.host", "127.0.0.1").trim();
            dashboardHost = dashboardHost.trim();
            int dashboardPort = integerProperty(properties, "training.dashboard.port", 8765);
            String metricsDirectory = properties.getProperty(
                    "training.metrics.directory", "bot-training-runs").trim();
            if (games < 1 || randomBaselinePercent < 0 || randomBaselinePercent > 20
                    || generations < 1 || threads < 1 || modelThreads < 1
                    || maxTrainingRecordsPerFormat < 1 || evaluationGames < 1
                    || maxPolicyChanges < 1 || maxPolicyChanges > evaluationGames
                    || exploratoryPolicyChanges < 0 || exploratoryPolicyChanges > maxPolicyChanges
                    || !Double.isFinite(maximumValidationRegressionPercent)
                    || maximumValidationRegressionPercent < 0.0 || maximumValidationRegressionPercent > 100.0
                    || !List.of("auto", "fresh", "refinement").contains(explorationProfile)
                    || freshExplorationStartPercent < 0 || freshExplorationStartPercent > 100
                    || freshExplorationEndPercent < 0 || freshExplorationEndPercent > 100
                    || refinementExplorationStartPercent < 0 || refinementExplorationStartPercent > 100
                    || refinementExplorationEndPercent < 0 || refinementExplorationEndPercent > 100
                    || explorationDecayGenerations < 1
                    || !Double.isFinite(explorationExampleWeight)
                    || explorationExampleWeight <= 0.0 || explorationExampleWeight > 1.0
                    || !Double.isFinite(randomBaselineExampleWeight)
                    || randomBaselineExampleWeight < 0.0 || randomBaselineExampleWeight > 1.0
                    || deckBuilderCandidates < 2 || deckBuilderGamesPerCandidate < 2
                    || deckBuilderGamesPerCandidate % 2 != 0 || progressInterval < 1
                    || !Double.isFinite(minimumValidationAccuracy)
                    || minimumValidationAccuracy < 0.0 || minimumValidationAccuracy > 100.0
                    || dashboardHost.isBlank() || dashboardPort < 0 || dashboardPort > 65535
                    || metricsDirectory.isBlank())
                throw new IllegalArgumentException("Training counts must be positive, random-baseline percentage "
                        + "must be 0-20, exploration profile must be auto/fresh/refinement, exploration percentages "
                        + "must be 0-100, example weights must be in their allowed ranges, dashboard port must be 0-65535, "
                        + "minimum validation accuracy must be 0-100 and dashboard host and metrics directory "
                        + "must be non-empty.");

            return new TrainingConfig(
                    configFile,
                    resolvePath(baseDirectory, properties.getProperty(
                            "cards.resources.directory", "gemp-lotr-cards/src/main/resources")),
                    resolvePath(baseDirectory, properties.getProperty("models.directory", "bot-models")),
                    resolvePath(baseDirectory, properties.getProperty("training.data.directory", "bot-training-data")),
                    resolvePath(baseDirectory, metricsDirectory),
                    formats, games, randomBaselinePercent, generations, threads, modelThreads,
                    maxTrainingRecordsPerFormat, evaluationGames, maxPolicyChanges,
                    exploratoryPolicyChanges, maximumValidationRegressionPercent, explorationProfile,
                    freshExplorationStartPercent, freshExplorationEndPercent,
                    refinementExplorationStartPercent, refinementExplorationEndPercent,
                    explorationDecayGenerations, explorationExampleWeight, randomBaselineExampleWeight,
                    evaluationSeed,
                    deckBuilderCandidates, deckBuilderGamesPerCandidate,
                    minimumValidationAccuracy, progressInterval,
                    dashboardHost, dashboardPort);
        }

        private String resolvedExplorationProfile(boolean hasIncumbentModels) {
            return switch (explorationProfile) {
                case "auto" -> hasIncumbentModels ? "refinement" : "fresh";
                case "fresh", "refinement" -> explorationProfile;
                default -> throw new IllegalStateException("Unsupported exploration profile: " + explorationProfile);
            };
        }

        private double explorationRate(int generation, boolean hasIncumbentModels) {
            boolean freshProfile = resolvedExplorationProfile(hasIncumbentModels).equals("fresh");
            return explorationRateForGeneration(
                    generation,
                    explorationDecayGenerations,
                    freshProfile ? freshExplorationStartPercent : refinementExplorationStartPercent,
                    freshProfile ? freshExplorationEndPercent : refinementExplorationEndPercent);
        }

        private static Path configPath(String[] args) {
            String configured = System.getenv("GEMP_BOT_TRAINING_CONFIG");
            for (String argument : args) {
                if (!argument.startsWith("--config="))
                    throw new IllegalArgumentException("Only --config=<path> is accepted; configure training in the properties file.");
                configured = argument.substring("--config=".length());
            }
            if (configured == null || configured.isBlank())
                configured = "bot-training.properties";
            return Path.of(configured).toAbsolutePath().normalize();
        }

        private static int integerProperty(Properties properties, String key, int defaultValue) {
            String value = properties.getProperty(key);
            if (value == null)
                return defaultValue;
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer setting " + key + "=" + value, e);
            }
        }

        private static double decimalProperty(Properties properties, String key, double defaultValue) {
            String value = properties.getProperty(key);
            if (value == null)
                return defaultValue;
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid decimal setting " + key + "=" + value, e);
            }
        }

        private static long longProperty(Properties properties, String key, long defaultValue) {
            String value = properties.getProperty(key);
            if (value == null)
                return defaultValue;
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid long setting " + key + "=" + value, e);
            }
        }

        private static Path resolvePath(Path baseDirectory, String value) {
            Path path = Path.of(value.trim());
            return (path.isAbsolute() ? path : baseDirectory.resolve(path)).toAbsolutePath().normalize();
        }
    }
}
