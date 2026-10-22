package com.gempukku.lotro.bots.deckbuilding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.MovieBlockDeckFactory;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.simulation.GameResult;
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.training.TrainingProgress;
import com.gempukku.lotro.bots.training.TrainingThreadFactory;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.logic.vo.LotroDeck;
import smile.classification.Classifier;
import smile.classification.LogisticRegression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeckBuilderTrainer {
    private static final int MINIMUM_EXAMPLES_PER_CLASS = 2;
    private static final int VALIDATION_HASH_MODULUS = 5;
    private static final int DEFAULT_CANDIDATE_POOL_SIZE = 16;
    private static final int QUALITY_SCREEN_CANDIDATE_POOL_SIZE = 8;
    private static final double WIN_PREDICTION_WEIGHT = 0.7;

    private DeckBuilderTrainer() {
    }

    public static TrainingResult train(
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            List<LotroDeck> referenceDecks,
            Classifier<double[]> incumbentModel,
            ModelRegistry decisionModels,
            RLGameStateFeatures decisionFeatures,
            Path modelDirectory,
            Path dataDirectory,
            int candidatesPerGeneration,
            int gamesPerCandidate,
            int requestedThreads,
            int progressInterval,
            TrainingProgress progress,
            int generation,
            long seed) {
        if (referenceDecks.isEmpty())
            throw new IllegalArgumentException("Deck-builder training requires at least one reference deck");
        if (decisionModels == null || decisionFeatures == null)
            throw new IllegalArgumentException("Deck-builder evaluation requires the incumbent decision policy");
        if (candidatesPerGeneration < 2)
            throw new IllegalArgumentException("At least two deck-builder candidates are required");
        if (gamesPerCandidate < 2 || gamesPerCandidate % 2 != 0)
            throw new IllegalArgumentException("Deck-builder games per candidate must be an even number of at least two");
        if (requestedThreads < 1 || progressInterval < 1)
            throw new IllegalArgumentException("Deck-builder worker and progress intervals must be positive");

        List<LotroDeck> candidates = new ArrayList<>(candidatesPerGeneration);
        for (int candidate = 0; candidate < candidatesPerGeneration; candidate++) {
            long candidateSeed = seed + 31L * candidate;
            LotroDeck deck = incumbentModel != null && candidate % 3 == 2
                    ? createDeck(format, library, incumbentModel, DEFAULT_CANDIDATE_POOL_SIZE, candidateSeed)
                    : createDeck(format, library, null, QUALITY_SCREEN_CANDIDATE_POOL_SIZE, candidateSeed);
            candidates.add(deck);
        }
        List<DeckDescriptor> referenceDescriptors = referenceDecks.stream()
                .map(deck -> new DeckDescriptor(deck, fingerprint(format.getCode(), deck),
                        DeckFeatureExtractor.extract(deck, library)))
                .toList();

        int totalGames = candidatesPerGeneration * gamesPerCandidate;
        if (progress != null)
            progress.beginStage(format.getCode() + " generation " + generation + " deck-builder-evaluation",
                    generation, totalGames);
        AtomicInteger completedGames = new AtomicInteger();
        int workerCount = Math.min(candidatesPerGeneration, requestedThreads);
        ExecutorService executor = Executors.newFixedThreadPool(
                workerCount, new TrainingThreadFactory("deck-builder-" + format.getCode() + "-evaluation"));
        CompletionService<CandidateEvaluation> completionService = new ExecutorCompletionService<>(executor);
        List<Future<CandidateEvaluation>> futures = new ArrayList<>(candidatesPerGeneration);
        try {
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                int index = candidateIndex;
                LotroDeck candidateDeck = candidates.get(candidateIndex);
                double[] features = DeckFeatureExtractor.extract(candidateDeck, library);
                String deckKey = fingerprint(format.getCode(), candidateDeck);
                futures.add(completionService.submit(() -> evaluateCandidate(
                        format, library, referenceDecks, referenceDescriptors, decisionModels, decisionFeatures,
                        candidateDeck, features, deckKey, index,
                        gamesPerCandidate, completedGames, totalGames, progressInterval, progress)));
            }

            List<DeckTrainingExample> newExamples = new ArrayList<>(totalGames * 2);
            int candidateWins = 0;
            int candidateLosses = 0;
            int skippedGames = 0;
            int candidateProgressInterval = Math.max(1, (candidatesPerGeneration + 3) / 4);
            for (int completedCandidates = 1; completedCandidates <= candidatesPerGeneration; completedCandidates++) {
                CandidateEvaluation evaluation = completionService.take().get();
                newExamples.addAll(evaluation.examples());
                candidateWins += evaluation.wins();
                candidateLosses += evaluation.losses();
                skippedGames += evaluation.skippedGames();
                if (completedCandidates % candidateProgressInterval == 0
                        || completedCandidates == candidatesPerGeneration) {
                    System.out.printf(Locale.ROOT,
                            "  Deck builder %s: %d/%d candidates evaluated, aggregate record %d-%d%n",
                            format.getCode(), completedCandidates, candidatesPerGeneration,
                            candidateWins, candidateLosses);
                }
            }

            double averageDeckQuality = candidates.stream()
                    .mapToDouble(deck -> DeckQualityEvaluator.evaluate(deck, library).score())
                    .average()
                    .orElseThrow();
            long playableDecks = candidates.stream()
                    .filter(deck -> DeckQualityEvaluator.evaluate(deck, library).unplayableCardCount() == 0)
                    .count();
            System.out.printf(Locale.ROOT,
                    "  Deck builder %s composition: average quality %.2f; %d/%d candidates have no "
                            + "unmet named-card requirements%n",
                    format.getCode(), averageDeckQuality, playableDecks, candidates.size());

            Path dataFile = dataDirectory.resolve("deck-builder-outcomes-v4.jsonl");
            appendExamples(dataFile, newExamples);
            List<DeckTrainingExample> allExamples = loadExamples(dataFile,
                    DeckFeatureExtractor.extract(candidates.getFirst(), library).length);
            TrainingSplit split = splitByDeckFingerprint(allExamples);
            int trainingWins = countWins(split.training());
            int trainingLosses = split.training().size() - trainingWins;
            int validationWins = countWins(split.validation());
            int validationLosses = split.validation().size() - validationWins;

            Classifier<double[]> selectedModel = incumbentModel;
            boolean promoted = false;
            double validationAccuracy = Double.NaN;
            double incumbentAccuracy = incumbentModel == null ? Double.NaN
                    : accuracy(incumbentModel, split.validation());
            String selectedAlgorithm = "not-ready";
            if (trainingWins >= MINIMUM_EXAMPLES_PER_CLASS
                    && trainingLosses >= MINIMUM_EXAMPLES_PER_CLASS
                    && validationWins > 0 && validationLosses > 0) {
                ModelFit fit = fit(split.training(), split.validation());
                selectedAlgorithm = fit.algorithm();
                if (fit.model() != null) {
                    validationAccuracy = fit.validationAccuracy();
                    if ((incumbentModel == null && validationAccuracy > 50.0)
                            || (incumbentModel != null && validationAccuracy > incumbentAccuracy + 1.0)) {
                        ModelIO.saveModel(DeckBuilderTrainer.class, fit.model(), modelDirectory);
                        selectedModel = fit.model();
                        promoted = true;
                    }
                }
            }

            if (Double.isFinite(validationAccuracy)) {
                System.out.printf(Locale.ROOT,
                        "  Deck builder %s summary: %d candidates, %d games, %d-%d candidate record, "
                                + "%d skipped, %d train / %d held-out examples, %s held-out accuracy %.1f%% "
                                + "(incumbent %.1f%%); %s%n",
                        format.getCode(), candidatesPerGeneration, totalGames - skippedGames,
                        candidateWins, candidateLosses, skippedGames,
                        split.training().size(), split.validation().size(), selectedAlgorithm,
                        validationAccuracy, incumbentAccuracy,
                        promoted ? "model promoted" : "incumbent retained");
            } else {
                System.out.printf(Locale.ROOT,
                        "  Deck builder %s summary: %d candidates, %d games, %d-%d candidate record, "
                                + "%d skipped, %d accumulated examples; %s%n",
                        format.getCode(), candidatesPerGeneration, totalGames - skippedGames,
                        candidateWins, candidateLosses, skippedGames,
                        allExamples.size(), selectedAlgorithm.equals("not-ready")
                                ? "more wins and losses are required for held-out fitting"
                                : selectedAlgorithm);
            }
            List<LotroDeck> generatedDecks =
                    selectTrainingDecks(candidates, selectedModel, library, seed ^ 0x37D4E18L);
            TrainingResult trainingResult = new TrainingResult(
                    selectedModel, generatedDecks, candidatesPerGeneration, totalGames - skippedGames,
                    candidateWins, candidateLosses, allExamples.size(), split.training().size(),
                    split.validation().size(), selectedAlgorithm, validationAccuracy, incumbentAccuracy,
                    promoted, skippedGames);
            if (progress != null)
                progress.deckBuilderCompleted(trainingResult);
            return trainingResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Deck-builder training was interrupted for " + format.getCode(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            if (cause instanceof Error error)
                throw error;
            throw new IllegalStateException("Deck-builder simulation failed for " + format.getCode(), cause);
        } finally {
            for (Future<CandidateEvaluation> future : futures)
                future.cancel(true);
            executor.shutdownNow();
        }
    }

    public static LotroDeck createDeck(
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            Classifier<double[]> model,
            int candidatePoolSize,
            long seed) {
        if (candidatePoolSize < 1)
            throw new IllegalArgumentException("At least one generated deck candidate is required");
        List<LotroDeck> candidates = new ArrayList<>(candidatePoolSize);
        for (int candidate = 0; candidate < candidatePoolSize; candidate++)
            candidates.add(MovieBlockDeckFactory.createRandomLegalDeck(
                    format.getCode(), format, library, seed + 31L * candidate));

        List<ScoredDeck> scored = candidates.stream()
                .map(deck -> scoreDeck(deck, library, model))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<ScoredDeck> playable = scored.stream()
                .filter(deck -> deck.unplayableCardCount() == 0)
                .toList();
        if (!playable.isEmpty())
            scored = new ArrayList<>(playable);
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));
        int selectionPool = Math.max(1, (scored.size() + 4) / 5);
        return scored.get(new Random(seed).nextInt(selectionPool)).deck();
    }

    private static List<LotroDeck> selectTrainingDecks(
            List<LotroDeck> candidates,
            Classifier<double[]> model,
            LotroCardBlueprintLibrary library,
            long seed) {
        List<ScoredDeck> scored = candidates.stream()
                .map(deck -> scoreDeck(deck, library, model))
                .filter(deck -> deck.unplayableCardCount() == 0)
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
        if (scored.isEmpty())
            throw new IllegalStateException("Deck-builder produced no playable training decks");

        int selectedCount = Math.max(1, (scored.size() + 4) / 5);
        List<ScoredDeck> selected = new ArrayList<>(scored.subList(0, selectedCount));
        java.util.Collections.shuffle(selected, new Random(seed));
        return selected.stream().map(ScoredDeck::deck).toList();
    }

    private static ScoredDeck scoreDeck(
            LotroDeck deck,
            LotroCardBlueprintLibrary library,
            Classifier<double[]> model) {
        DeckQualityEvaluator.Evaluation quality = DeckQualityEvaluator.evaluate(deck, library);
        double score = model == null
                ? quality.score()
                : WIN_PREDICTION_WEIGHT * positiveProbability(model, DeckFeatureExtractor.extract(deck, library))
                        + (1.0 - WIN_PREDICTION_WEIGHT) * quality.score();
        return new ScoredDeck(deck, score, quality.unplayableCardCount());
    }

    private static CandidateEvaluation evaluateCandidate(
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            List<LotroDeck> availableReferenceDecks,
            List<DeckDescriptor> referenceDecks,
            ModelRegistry decisionModels,
            RLGameStateFeatures decisionFeatures,
            LotroDeck candidateDeck,
            double[] features,
            String deckKey,
            int candidateIndex,
            int gamesPerCandidate,
            AtomicInteger completedGames,
            int totalGames,
            int progressInterval,
            TrainingProgress progress) {
        MovieBlockSimulation simulation = new MovieBlockSimulation(format, availableReferenceDecks, library, progress);
        List<DeckTrainingExample> examples = new ArrayList<>(gamesPerCandidate * 2);
        int wins = 0;
        int skippedGames = 0;

        for (int game = 0; game < gamesPerCandidate; game++) {
            DeckDescriptor reference = referenceDecks.get((candidateIndex + game) % referenceDecks.size());
            boolean candidateFirst = game % 2 == 0;
            String botName = "~deck-train-" + format.getCode() + "-" + candidateIndex + "-" + game;
            FotrStarterBot firstBot = new FotrStarterBot(
                    decisionFeatures, botName + "-one", decisionModels, null, progress);
            FotrStarterBot secondBot = new FotrStarterBot(
                    decisionFeatures, botName + "-two", decisionModels, null, progress);
            GameResult result;
            try {
                result = simulation.simulateGame(firstBot, secondBot,
                        candidateFirst ? candidateDeck : reference.deck(),
                        candidateFirst ? reference.deck() : candidateDeck);
            } catch (MovieBlockSimulation.GameDecisionLimitException
                     | MovieBlockSimulation.UnresolvableDecisionCycleException e) {
                skippedGames++;
                String message = "Skipping deck-builder game for candidate " + candidateIndex
                        + ", game " + game + " after "
                        + (e instanceof MovieBlockSimulation.UnresolvableDecisionCycleException
                        ? "an unresolvable decision cycle." : "the decision limit.");
                System.err.println(message);
                e.printStackTrace(System.err);
                if (progress != null)
                    progress.recordEvent(Severity.ERROR,
                            e instanceof MovieBlockSimulation.UnresolvableDecisionCycleException
                                    ? "deck-builder-game-unresolvable-cycle"
                                    : "deck-builder-game-decision-limit",
                            "DeckBuilderTrainer", message, e);
                reportGameCompleted(completedGames, totalGames, progressInterval, progress, format.getCode());
                continue;
            }
            boolean candidateWon = candidateFirst ? result == GameResult.P1_WON : result == GameResult.P2_WON;
            if (candidateWon)
                wins++;
            examples.add(new DeckTrainingExample(deckKey, features, candidateWon));

            reportGameCompleted(completedGames, totalGames, progressInterval, progress, format.getCode());
        }
        return new CandidateEvaluation(examples, wins, gamesPerCandidate - skippedGames - wins, skippedGames);
    }

    private static void reportGameCompleted(
            AtomicInteger completedGames,
            int totalGames,
            int progressInterval,
            TrainingProgress progress,
            String formatCode) {
        int completed = completedGames.incrementAndGet();
        if (progress != null)
            progress.gameCompleted();
        if (completed % progressInterval == 0 || completed == totalGames)
            System.out.printf(Locale.ROOT, "  Deck builder %s: %d/%d games%n",
                    formatCode, completed, totalGames);
    }

    private static ModelFit fit(
            List<DeckTrainingExample> training,
            List<DeckTrainingExample> validation) {
        StandardizedTrainingData data = standardize(training);
        Classifier<double[]> model = new StandardizedClassifier(
                LogisticRegression.fit(data.features(), data.labels()), data.means(), data.scales());
        return new ModelFit(model, accuracy(model, validation), "Smile 7 logistic");
    }

    private static StandardizedTrainingData standardize(List<DeckTrainingExample> examples) {
        double[][] raw = examples.stream().map(DeckTrainingExample::features).toArray(double[][]::new);
        int[] labels = examples.stream().mapToInt(example -> example.won() ? 1 : 0).toArray();
        int featureCount = raw[0].length;
        double[] means = new double[featureCount];
        double[] scales = new double[featureCount];

        for (double[] sample : raw) {
            for (int feature = 0; feature < featureCount; feature++)
                means[feature] += sample[feature];
        }
        for (int feature = 0; feature < featureCount; feature++)
            means[feature] /= raw.length;
        for (double[] sample : raw) {
            for (int feature = 0; feature < featureCount; feature++) {
                double difference = sample[feature] - means[feature];
                scales[feature] += difference * difference;
            }
        }
        for (int feature = 0; feature < featureCount; feature++) {
            scales[feature] = Math.sqrt(scales[feature] / raw.length);
            if (scales[feature] < 1e-9)
                scales[feature] = 1.0;
        }

        double[][] standardized = new double[raw.length][featureCount];
        for (int row = 0; row < raw.length; row++) {
            for (int feature = 0; feature < featureCount; feature++)
                standardized[row][feature] = (raw[row][feature] - means[feature]) / scales[feature];
        }
        return new StandardizedTrainingData(standardized, labels, means, scales);
    }

    private static double accuracy(Classifier<double[]> model, List<DeckTrainingExample> examples) {
        if (examples.isEmpty())
            return Double.NaN;
        int correct = 0;
        for (DeckTrainingExample example : examples) {
            boolean predictedWin = positiveProbability(model, example.features()) >= 0.5;
            if (predictedWin == example.won())
                correct++;
        }
        return correct * 100.0 / examples.size();
    }

    private static double positiveProbability(Classifier<double[]> model, double[] features) {
        double[] probabilities = new double[model.numClasses()];
        model.predict(features, probabilities);
        int[] classes = model.classes();
        for (int i = 0; i < classes.length; i++) {
            if (classes[i] == 1)
                return probabilities[i];
        }
        return model.predict(features) == 1 ? 1.0 : 0.0;
    }

    private static void appendExamples(Path file, List<DeckTrainingExample> examples) {
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (DeckTrainingExample example : examples) {
                    JSONObject object = new JSONObject();
                    object.put("schema", 4);
                    object.put("deckKey", example.deckKey());
                    object.put("features", example.features());
                    object.put("won", example.won());
                    writer.write(JSON.toJSONString(object));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to save deck-builder outcomes to " + file, e);
        }
    }

    private static List<DeckTrainingExample> loadExamples(Path file, int expectedFeatureCount) {
        if (!Files.isRegularFile(file))
            return List.of();

        List<DeckTrainingExample> examples = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank())
                    continue;
                try {
                    JSONObject object = JSON.parseObject(line);
                    if (object.getIntValue("schema") != 4)
                        throw new IllegalArgumentException("Unsupported deck outcome schema");
                    String deckKey = object.getString("deckKey");
                    double[] features = object.getObject("features", double[].class);
                    if (deckKey == null || features == null || features.length != expectedFeatureCount)
                        throw new IllegalArgumentException("Invalid deck outcome shape");
                    for (double feature : features) {
                        if (!Double.isFinite(feature))
                            throw new IllegalArgumentException("Non-finite deck feature");
                    }
                    examples.add(new DeckTrainingExample(
                            deckKey, features, object.getBooleanValue("won")));
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Invalid deck outcome at " + file + ":" + lineNumber, e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load deck-builder outcomes from " + file, e);
        }
        return examples;
    }

    static TrainingSplit splitByDeckFingerprint(List<DeckTrainingExample> examples) {
        List<DeckTrainingExample> training = new ArrayList<>();
        List<DeckTrainingExample> validation = new ArrayList<>();
        for (DeckTrainingExample example : examples) {
            if (Math.floorMod(example.deckKey().hashCode(), VALIDATION_HASH_MODULUS) == 0)
                validation.add(example);
            else
                training.add(example);
        }
        return new TrainingSplit(training, validation);
    }

    private static int countWins(List<DeckTrainingExample> examples) {
        return (int) examples.stream().filter(DeckTrainingExample::won).count();
    }

    private static String fingerprint(String formatCode, LotroDeck deck) {
        List<String> sortedCards = new ArrayList<>(deck.getDrawDeckCards());
        sortedCards.sort(String::compareTo);
        String canonical = formatCode + "|" + deck.getRingBearer() + "|" + deck.getRing() + "|"
                + String.join(",", sortedCards) + "|" + String.join(",", deck.getSites());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable for deck outcome grouping", e);
        }
    }

    public record TrainingResult(
            Classifier<double[]> model,
            List<LotroDeck> generatedDecks,
            int candidates,
            int games,
            int wins,
            int losses,
            int accumulatedExamples,
            int trainingExamples,
            int validationExamples,
            String algorithm,
            double validationAccuracy,
            double incumbentAccuracy,
            boolean promoted,
            int skippedGames) {
        public TrainingResult {
            generatedDecks = List.copyOf(generatedDecks);
        }
    }

    record DeckTrainingExample(String deckKey, double[] features, boolean won) {
    }

    private record CandidateEvaluation(List<DeckTrainingExample> examples, int wins, int losses, int skippedGames) {
    }

    private record ScoredDeck(LotroDeck deck, double score, int unplayableCardCount) {
    }

    private record DeckDescriptor(LotroDeck deck, String deckKey, double[] features) {
    }

    record TrainingSplit(List<DeckTrainingExample> training, List<DeckTrainingExample> validation) {
    }

    private record StandardizedTrainingData(double[][] features, int[] labels, double[] means, double[] scales) {
    }

    private record ModelFit(Classifier<double[]> model, double validationAccuracy, String algorithm) {
    }
}
