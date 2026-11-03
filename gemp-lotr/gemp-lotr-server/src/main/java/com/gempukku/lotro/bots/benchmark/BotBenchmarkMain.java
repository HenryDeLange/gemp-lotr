package com.gempukku.lotro.bots.benchmark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.MovieBlockDeckFactory;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import com.gempukku.lotro.bots.simulation.BatchTacticalMetrics;
import com.gempukku.lotro.bots.simulation.GameResult;
import com.gempukku.lotro.bots.simulation.GameTacticalMetrics;
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.simulation.SimulationOutcome;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.BurdenTrainer;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.DefaultAdventureLibrary;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.gempukku.lotro.packs.ProductLibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public final class BotBenchmarkMain {
    private BotBenchmarkMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Map<String, String> options = parseOptions(args);
        Path resources = requiredPath(options, "resources");
        long seed = Long.parseLong(options.getOrDefault("seed", "1"));
        boolean creatingManifest = options.containsKey("create-manifest");
        boolean independentReferencePool = booleanOption(options, "independent-reference-pool", false);
        MovieBlockDeckFactory.CultureComposition composition = parseComposition(
                options.getOrDefault("composition", "mixed"));
        Path referenceManifestFile = options.containsKey("reference-manifest")
                ? requiredPath(options, "reference-manifest") : null;
        if (options.containsKey("generic-pass-gate")
                && (options.containsKey("candidate-generic-pass-gate")
                || options.containsKey("opponent-generic-pass-gate")))
            throw new IllegalArgumentException("--generic-pass-gate cannot be combined with candidate/opponent Pass-gate options");
        BotPolicy configuredPolicy = configuredPolicy();
        boolean sharedPassGate = options.containsKey("generic-pass-gate")
                ? booleanOption(options, "generic-pass-gate") : configuredPolicy.genericPassGateEnabled();
        BotPolicy candidatePolicy = new BotPolicy(
                booleanOption(options, "candidate-transfer-guard", configuredPolicy.transferGuardEnabled()),
                booleanOption(options, "candidate-generic-pass-gate", sharedPassGate),
                bidCapOption(options, "candidate-burden-bid-cap",
                        configuredPolicy.maximumBurdensToBid()));
        BotPolicy opponentPolicy = new BotPolicy(
                booleanOption(options, "opponent-transfer-guard", configuredPolicy.transferGuardEnabled()),
                booleanOption(options, "opponent-generic-pass-gate", sharedPassGate),
                bidCapOption(options, "opponent-burden-bid-cap",
                        configuredPolicy.maximumBurdensToBid()));
        Path output;
        Path manifestFile = null;
        Path modelDirectory = null;
        Path opponentModelDirectory = null;
        int pairs = 0;
        int generated = 0;
        DeckPoolSelection deckPoolSelection = DeckPoolSelection.MIXED;
        String opponentLabel;
        if (creatingManifest) {
            rejectOptions(options, "manifest", "models", "opponent-models", "opponent-label", "output",
                    "pairs", "generic-pass-gate", "candidate-generic-pass-gate", "opponent-generic-pass-gate",
                    "candidate-transfer-guard", "opponent-transfer-guard", "candidate-burden-bid-cap",
                    "opponent-burden-bid-cap", "deck-pool");
            output = requiredPath(options, "create-manifest");
            generated = Integer.parseInt(options.getOrDefault("generated", "4"));
            if (generated < 2)
                throw new IllegalArgumentException("At least two generated decks per format are required");
        } else {
            rejectOptions(options, "generated", "create-manifest", "reference-manifest", "composition",
                    "independent-reference-pool");
            manifestFile = requiredPath(options, "manifest");
            modelDirectory = requiredPath(options, "models").toRealPath();
            if (options.containsKey("opponent-models")) {
                opponentModelDirectory = requiredPath(options, "opponent-models").toRealPath();
                if (opponentModelDirectory.equals(modelDirectory))
                    throw new IllegalArgumentException("Candidate and frozen-opponent models must use separate directories");
            }
            if (opponentModelDirectory == null && options.containsKey("opponent-burden-bid-cap"))
                throw new IllegalArgumentException("--opponent-burden-bid-cap requires --opponent-models");
            deckPoolSelection = DeckPoolSelection.parse(options.getOrDefault("deck-pool", "mixed"));
            output = requiredPath(options, "output");
            if (output.getParent().toRealPath().startsWith(modelDirectory))
                throw new IllegalArgumentException("Benchmark output must be outside the model directory");
            if (opponentModelDirectory != null && output.getParent().toRealPath().startsWith(opponentModelDirectory))
                throw new IllegalArgumentException("Benchmark output must be outside the frozen-opponent model directory");
            pairs = Integer.parseInt(options.getOrDefault("pairs", "50"));
            if (pairs < 1)
                throw new IllegalArgumentException("At least one deck pair per format is required");
        }
        opponentLabel = options.getOrDefault("opponent-label", opponentModelDirectory == null
                ? "RandomDecisionBot" : opponentModelDirectory.getFileName().toString());
        if (opponentLabel.isBlank())
            throw new IllegalArgumentException("Opponent label must not be blank");
        if (Files.exists(output))
            throw new java.nio.file.FileAlreadyExistsException(output.toString());

        LotroCardBlueprintLibrary cards = new LotroCardBlueprintLibrary(
                resources.resolve("cards").toFile(), resources.resolve("blueprintMapping.txt").toFile(),
                resources.resolve("setConfig.hjson").toFile(), resources.resolve("rarities").toFile());
        LotroFormatLibrary formats = new LotroFormatLibrary(new DefaultAdventureLibrary(), cards,
                resources.resolve("lotrFormats.hjson").toFile(), resources.resolve("sealed").toFile());

        if (creatingManifest) {
            ProductLibrary products = new ProductLibrary(cards, resources.resolve("product").toFile());
            BenchmarkDeckManifest referenceManifest = referenceManifestFile == null ? null
                    : BenchmarkDeckManifest.read(referenceManifestFile, formats);
            if (independentReferencePool && referenceManifest == null)
                throw new IllegalArgumentException(
                        "--independent-reference-pool=true requires --reference-manifest");
            BenchmarkDeckManifest manifest = independentReferencePool
                    ? BenchmarkDeckManifest.createIndependentReferenceManifest(
                    formats, cards, generated, seed, referenceManifest.allFingerprints(), composition)
                    : BenchmarkDeckManifest.create(formats, cards, products, generated, seed,
                    composition, referenceManifest);
            manifest.writeNew(output);
            System.out.println("Created benchmark deck manifest: " + output);
            return;
        }

        byte[] manifestBytes = Files.readAllBytes(manifestFile);
        BenchmarkDeckManifest manifest = JSON.parseObject(manifestBytes, BenchmarkDeckManifest.class);
        if (manifest == null)
            throw new IllegalArgumentException("Empty benchmark deck manifest: " + manifestFile);
        manifest.validate(formats);
        ModelSnapshot models = loadModels(modelDirectory);
        ModelSnapshot opponentModels = opponentModelDirectory == null ? null : loadModels(opponentModelDirectory);
        CodeVersion code = codeVersion();
        CardFeatures.init(cards);
        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        List<FormatResult> results = run(manifest, formats, cards, models.registry(),
                opponentModels == null ? null : opponentModels.registry(),
                pairs, seed, candidatePolicy, opponentPolicy, deckPoolSelection);
        WinSummary total = combined(results);
        boolean failed = results.stream().anyMatch(result -> result.combined().completedGames() == 0);
        String status = failed ? "failed" : total.skippedGames() > 0 ? "completed-with-skips" : "completed";
        Report report = new Report(5, status, started.toString(), (System.nanoTime() - startNanos) / 1_000_000,
                code, System.getProperty("java.version"), resources.toString(), manifestFile.toString(), sha256(manifestBytes),
                modelDirectory.toString(), models.fingerprints(), seed, pairs,
                candidatePolicy.transferGuardEnabled(), candidatePolicy.genericPassGateEnabled(),
                candidatePolicy.maximumBurdensToBid(),
                opponentPolicy.transferGuardEnabled(), opponentPolicy.genericPassGateEnabled(),
                opponentModels == null ? null : opponentPolicy.maximumBurdensToBid(),
                opponentLabel,
                opponentModelDirectory == null ? null : opponentModelDirectory.toString(),
                opponentModels == null ? Map.of() : opponentModels.fingerprints(),
                deckPoolSelection.commandValue(), total, results,
                List.of("Only deck-pair selection is seeded; shuffles and bot randomness are not.",
                        "Candidate-as-bot1/bot2 refers to simulator argument order; actual seats and starting-player strata are recorded after bidding.",
                        "A generated-only benchmark uses the manifest's generated pool; historical overlap is controlled only when training uses that manifest as its holdout.",
                        opponentModels == null
                                ? "The opponent is the current random bot, not an independently frozen policy implementation."
                                : "The opponent policy is loaded from a separate model directory and its model hashes are recorded.",
                        "Wilson intervals are nominal per-game estimates; paired decks and repeated matchups can reduce independence.",
                        "Skipped games do not contribute game-end or decision metrics.",
                        "Per-matchup safeguard counts describe interventions, not counterfactual action utility.",
                        "Bid-cap constraints use a shared-quantile uncapped choice for model bids and the observed pre-cap fallback answer for fallback bids.",
                        "Cycle recoveries are game-level counts across both players; rates should use attempted games, including skipped games.",
                        "Transfer reversals identify a voluntary Fellowship transfer back to a previously used bearer; card identities are not reported.",
                        deckPoolSelection == DeckPoolSelection.GENERATED_VS_REFERENCE
                                ? "Generated-vs-reference mode compares generated candidates against the manifest reference pool, stored in the starter slot."
                                : "Generated-vs-reference mode was not used.",
                        "The Git revision and dirty flag do not identify the exact contents of uncommitted changes."));
        Files.writeString(output, JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        System.out.println("Saved read-only benchmark report: " + output);
        if (failed)
            throw new IllegalStateException("No games completed in at least one benchmark format; see " + output);
    }

    static List<FormatResult> run(
            BenchmarkDeckManifest manifest, LotroFormatLibrary formats, LotroCardBlueprintLibrary cards,
            ModelRegistry models, int pairs, long seed) {
        BotPolicy policy = configuredPolicy();
        return run(manifest, formats, cards, models, null, pairs, seed, policy, policy,
                DeckPoolSelection.MIXED);
    }

    static List<FormatResult> run(
            BenchmarkDeckManifest manifest, LotroFormatLibrary formats, LotroCardBlueprintLibrary cards,
            ModelRegistry models, int pairs, long seed, boolean genericPassGateEnabled) {
        BotPolicy policy = new BotPolicy(FotrStarterBot.configuredTransferGuard(), genericPassGateEnabled);
        return run(manifest, formats, cards, models, null, pairs, seed, policy, policy,
                DeckPoolSelection.MIXED);
    }

    static List<FormatResult> run(
            BenchmarkDeckManifest manifest,
            LotroFormatLibrary formats,
            LotroCardBlueprintLibrary cards,
            ModelRegistry candidateModels,
            ModelRegistry opponentModels,
            int pairs,
            long seed,
            boolean genericPassGateEnabled,
            DeckPoolSelection deckPoolSelection) {
        BotPolicy policy = new BotPolicy(FotrStarterBot.configuredTransferGuard(), genericPassGateEnabled);
        return run(manifest, formats, cards, candidateModels, opponentModels, pairs, seed, policy, policy,
                deckPoolSelection);
    }

    static List<FormatResult> run(
            BenchmarkDeckManifest manifest,
            LotroFormatLibrary formats,
            LotroCardBlueprintLibrary cards,
            ModelRegistry candidateModels,
            ModelRegistry opponentModels,
            int pairs,
            long seed,
            BotPolicy candidatePolicy,
            BotPolicy opponentPolicy,
            DeckPoolSelection deckPoolSelection) {
        return runWithPolicies(manifest, formats, cards, candidateModels, opponentModels,
                pairs, seed, candidatePolicy, opponentPolicy, deckPoolSelection);
    }

    private static List<FormatResult> runWithPolicies(
            BenchmarkDeckManifest manifest,
            LotroFormatLibrary formats,
            LotroCardBlueprintLibrary cards,
            ModelRegistry candidateModels,
            ModelRegistry opponentModels,
            int pairs,
            long seed,
            BotPolicy candidatePolicy,
            BotPolicy opponentPolicy,
            DeckPoolSelection deckPoolSelection) {
        if (pairs < 1)
            throw new IllegalArgumentException("At least one deck pair per format is required");
        List<FormatResult> results = new ArrayList<>();
        for (BenchmarkDeckManifest.DeckPool pool : manifest.pools()) {
            LotroFormat format = formats.getFormat(pool.format());
            EventCounts events = new EventCounts();
            MovieBlockSimulation simulation = new MovieBlockSimulation(format, cards,
                    () -> { throw new IllegalStateException("Benchmark requires explicit decks"); }, events);
            MovieTrilogyRLGameStateFeatures features = new MovieTrilogyRLGameStateFeatures(pool.format());
            SeatAccumulator first = new SeatAccumulator();
            SeatAccumulator second = new SeatAccumulator();
            List<Matchup> matchups = new ArrayList<>();
            for (PlannedMatchup pair : plan(pool, pairs, seed, deckPoolSelection)) {
                LotroDeck candidate = pair.candidateDeck().toDeck(pool.format(), format);
                LotroDeck opponent = pair.opponentDeck().toDeck(pool.format(), format);
                PlayResult firstGame = play(simulation, features, candidateModels, opponentModels,
                        candidatePolicy, opponentPolicy, candidate, opponent, true, events, first);
                PlayResult secondGame = play(simulation, features, candidateModels, opponentModels,
                        candidatePolicy, opponentPolicy, candidate, opponent, false, events, second);
                matchups.add(new Matchup(pair.pool(), pair.candidateDeck().fingerprint(),
                        pair.opponentDeck().fingerprint(), firstGame.outcome(), secondGame.outcome(),
                        firstGame.passGateVetoesByPhase(), secondGame.passGateVetoesByPhase(),
                        firstGame.transferGuardConstraints(), secondGame.transferGuardConstraints(),
                        firstGame.transferReversals(), secondGame.transferReversals(),
                        firstGame.cycleRecoveries(), secondGame.cycleRecoveries(),
                        firstGame.candidateBid(), secondGame.candidateBid(),
                        firstGame.opponentBid(), secondGame.opponentBid()));
            }
            Map<String, Long> candidateVetoesByPhase = new TreeMap<>();
            long candidateTransferGuardConstraints = 0;
            long candidateTransferReversals = 0;
            long cycleRecoveries = 0;
            for (Matchup matchup : matchups) {
                matchup.candidatePassGateVetoesAsBot1().forEach(
                        (phase, count) -> candidateVetoesByPhase.merge(phase, count.longValue(), Long::sum));
                matchup.candidatePassGateVetoesAsBot2().forEach(
                        (phase, count) -> candidateVetoesByPhase.merge(phase, count.longValue(), Long::sum));
                candidateTransferGuardConstraints += matchup.candidateTransferGuardConstraintsAsBot1()
                        + matchup.candidateTransferGuardConstraintsAsBot2();
                candidateTransferReversals += matchup.candidateTransferReversalsAsBot1()
                        + matchup.candidateTransferReversalsAsBot2();
                cycleRecoveries += matchup.cycleRecoveriesAsBot1() + matchup.cycleRecoveriesAsBot2();
            }
            results.add(new FormatResult(pool.format(), first.summary(), second.summary(),
                    winSummary(first.wins + second.wins, first.completed + second.completed,
                            first.skipped + second.skipped),
                    first.seatingSummary().plus(second.seatingSummary()),
                    Map.copyOf(events.counts), Map.copyOf(candidateVetoesByPhase),
                    candidateTransferGuardConstraints, candidateTransferReversals, cycleRecoveries,
                    bidSummary(matchups, true), bidSummary(matchups, false), List.copyOf(matchups)));
            System.out.println("Finished benchmark format " + pool.format() + ": "
                    + (first.completed + second.completed) + " completed, "
                    + (first.skipped + second.skipped) + " skipped");
        }
        return List.copyOf(results);
    }

    private static BidSummary bidSummary(List<Matchup> matchups, boolean candidate) {
        Map<String, Long> bidAmounts = new TreeMap<>();
        long observedGames = 0;
        long capAssessedGames = 0;
        long capConstrainedGames = 0;
        long engineMinimumExceedsCapGames = 0;
        for (Matchup matchup : matchups) {
            FotrStarterBot.BurdenBidDiagnostics[] bids = candidate
                    ? new FotrStarterBot.BurdenBidDiagnostics[]{
                    matchup.candidateBidAsBot1(), matchup.candidateBidAsBot2()}
                    : new FotrStarterBot.BurdenBidDiagnostics[]{
                    matchup.opponentBidAsBot1(), matchup.opponentBidAsBot2()};
            for (FotrStarterBot.BurdenBidDiagnostics bid : bids) {
                if (bid == null)
                    continue;
                observedGames++;
                if (bid.bidAmount() != null)
                    bidAmounts.merge(bid.bidAmount().toString(), 1L, Long::sum);
                if (bid.capConstrained() != null) {
                    capAssessedGames++;
                    if (bid.capConstrained())
                        capConstrainedGames++;
                }
                if (bid.engineMinimumExceedsCap())
                    engineMinimumExceedsCapGames++;
            }
        }
        return new BidSummary(observedGames, capAssessedGames, capConstrainedGames,
                engineMinimumExceedsCapGames, Map.copyOf(bidAmounts));
    }

    static List<PlannedMatchup> plan(BenchmarkDeckManifest.DeckPool pool, int pairs, long seed) {
        return plan(pool, pairs, seed, DeckPoolSelection.MIXED);
    }

    static List<PlannedMatchup> plan(
            BenchmarkDeckManifest.DeckPool pool, int pairs, long seed, DeckPoolSelection deckPoolSelection) {
        if (pairs < 1)
            throw new IllegalArgumentException("At least one deck pair per format is required");
        Random random = new Random(seed + 31L * pool.format().hashCode());
        List<PlannedMatchup> result = new ArrayList<>();
        int referenceOffset = deckPoolSelection == DeckPoolSelection.GENERATED_VS_REFERENCE
                ? random.nextInt(pool.starters().size()) : 0;
        for (int pair = 0; pair < pairs; pair++) {
            if (deckPoolSelection == DeckPoolSelection.GENERATED_VS_REFERENCE) {
                int candidateIndex = pair % pool.generated().size();
                int referenceIndex = (pair / pool.generated().size() + candidateIndex + referenceOffset)
                        % pool.starters().size();
                result.add(new PlannedMatchup("generated-vs-reference",
                        pool.generated().get(candidateIndex), pool.starters().get(referenceIndex)));
                continue;
            }
            boolean starter = switch (deckPoolSelection) {
                case MIXED -> pair % 2 == 0;
                case STARTER -> true;
                case GENERATED -> false;
                case GENERATED_VS_REFERENCE -> throw new IllegalStateException(
                        "Generated-vs-reference matchups are planned separately");
            };
            List<BenchmarkDeckManifest.DeckEntry> entries = starter ? pool.starters() : pool.generated();
            result.add(new PlannedMatchup(starter ? "starter" : "generated",
                    entries.get(random.nextInt(entries.size())), entries.get(random.nextInt(entries.size()))));
        }
        return List.copyOf(result);
    }

    private static PlayResult play(
            MovieBlockSimulation simulation,
            MovieTrilogyRLGameStateFeatures features,
            ModelRegistry candidateModels,
            ModelRegistry opponentModels,
            BotPolicy candidatePolicy,
            BotPolicy opponentPolicy,
            LotroDeck candidateDeck, LotroDeck opponentDeck, boolean candidateFirst,
            EventCounts events, SeatAccumulator seat) {
        String candidateName = candidateFirst ? "~benchmark-one" : "~benchmark-two";
        String opponentName = candidateFirst ? "~benchmark-two" : "~benchmark-one";
        EventCounts candidateEvents = new EventCounts();
        EventCounts opponentEvents = new EventCounts();
        FotrStarterBot candidate = new FotrStarterBot(features, candidateName, candidateModels, null,
                candidateEvents, 0.0, 0.25, 1.0,
                candidatePolicy.transferGuardEnabled(), candidatePolicy.genericPassGateEnabled(),
                candidatePolicy.maximumBurdensToBid());
        FotrStarterBot opponentPolicyBot = opponentModels == null ? null
                : new FotrStarterBot(features, opponentName, opponentModels, null,
                opponentEvents, 0.0, 0.25, 1.0,
                opponentPolicy.transferGuardEnabled(), opponentPolicy.genericPassGateEnabled(),
                opponentPolicy.maximumBurdensToBid());
        BotPlayer opponent = opponentPolicyBot == null
                ? new RandomDecisionBot(opponentName) : opponentPolicyBot;
        String outcome;
        long cycleRecoveriesBefore = events.eventCount("warning:repeated-decision-cycle");
        try {
            SimulationOutcome gameOutcome = candidateFirst
                    ? simulation.simulateGameWithMetrics(candidate, opponent, candidateDeck, opponentDeck)
                    : simulation.simulateGameWithMetrics(opponent, candidate, opponentDeck, candidateDeck);
            boolean candidateWon = gameOutcome.result() == (candidateFirst ? GameResult.P1_WON : GameResult.P2_WON);
            seat.completed++;
            if (candidateWon)
                seat.wins++;
            GameTacticalMetrics metrics = gameOutcome.tacticalMetrics().orElseThrow();
            seat.record(candidateFirst, candidateWon, metrics);
            seat.metrics = seat.metrics.plus(metrics);
            outcome = candidateWon ? "win" : "loss";
        } catch (MovieBlockSimulation.GameDecisionLimitException
                 | MovieBlockSimulation.UnresolvableDecisionCycleException e) {
            seat.skipped++;
            String reason = e instanceof MovieBlockSimulation.GameDecisionLimitException
                    ? "decision-limit" : "unresolvable-cycle";
            events.recordEvent(TrainingEventReporter.Severity.ERROR, reason, null, e.getMessage(), e);
            System.err.println("Skipped benchmark game (" + reason + "): " + e.getMessage());
            outcome = "skipped-" + reason;
        } finally {
            events.add(candidateEvents);
            events.add(opponentEvents);
        }
        return new PlayResult(outcome, candidateEvents.passGateVetoesByPhase(),
                candidateEvents.transferGuardConstraints(), candidateEvents.transferReversals(),
                events.eventCount("warning:repeated-decision-cycle") - cycleRecoveriesBefore,
                candidate.burdenBidDiagnostics(),
                opponentPolicyBot == null ? null : opponentPolicyBot.burdenBidDiagnostics());
    }

    static WinSummary winSummary(int wins, int completed, int skipped) {
        if (wins < 0 || wins > completed || skipped < 0)
            throw new IllegalArgumentException("Invalid benchmark game counts");
        if (completed == 0)
            return new WinSummary(wins, completed, skipped, null, null, null);
        double p = (double) wins / completed;
        double z = 1.959963984540054;
        double denominator = 1 + z * z / completed;
        double centre = (p + z * z / (2 * completed)) / denominator;
        double margin = z * Math.sqrt(p * (1 - p) / completed + z * z / (4.0 * completed * completed))
                / denominator;
        return new WinSummary(wins, completed, skipped, p, Math.max(0, centre - margin),
                Math.min(1, centre + margin));
    }

    static WinSummary combined(List<FormatResult> results) {
        int wins = 0;
        int completed = 0;
        int skipped = 0;
        for (FormatResult result : results) {
            wins += result.combined().wins();
            completed += result.combined().completedGames();
            skipped += result.combined().skippedGames();
        }
        return winSummary(wins, completed, skipped);
    }

    static ModelSnapshot loadModels(Path directory) throws IOException {
        List<Trainer> trainers = FotrStarterTrainerCatalog.createAll();
        Map<String, String> fingerprints = new TreeMap<>();
        for (Trainer trainer : trainers) {
            String name = trainer.getClass().getSimpleName() + ".model";
            fingerprints.put(name, sha256(Files.readAllBytes(directory.resolve(name))));
        }
        ModelRegistry registry = new ModelRegistry();
        for (Trainer trainer : trainers)
            registry.registerModel(trainer.getClass(), ModelIO.loadModel(trainer.getClass(), directory));
        for (Map.Entry<String, String> model : fingerprints.entrySet()) {
            if (!model.getValue().equals(sha256(Files.readAllBytes(directory.resolve(model.getKey())))))
                throw new IllegalStateException("Model release changed while loading: " + model.getKey());
        }
        return new ModelSnapshot(registry, Map.copyOf(fingerprints));
    }

    private static CodeVersion codeVersion() throws IOException, InterruptedException {
        String revision = git("rev-parse", "HEAD").trim();
        boolean dirty = !git("status", "--porcelain", "--untracked-files=normal").isEmpty();
        return new CodeVersion(revision, dirty);
    }

    private static String git(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "--no-pager"));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0)
            throw new IllegalStateException("Failed to identify benchmark code with git: " + output);
        return output;
    }

    static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (!arg.startsWith("--") || equals <= 2 || equals == arg.length() - 1)
                throw new IllegalArgumentException("Expected --name=value: " + arg);
            String key = arg.substring(2, equals);
            if (!List.of("resources", "create-manifest", "manifest", "models", "opponent-models",
                    "reference-manifest", "composition", "opponent-label", "output", "generated", "pairs",
                    "seed", "generic-pass-gate",
                    "candidate-generic-pass-gate", "opponent-generic-pass-gate",
                    "candidate-transfer-guard", "opponent-transfer-guard",
                    "candidate-burden-bid-cap", "opponent-burden-bid-cap", "deck-pool",
                    "independent-reference-pool")
                    .contains(key) || result.putIfAbsent(key, arg.substring(equals + 1)) != null)
                throw new IllegalArgumentException("Unknown or repeated benchmark option: " + key);
        }
        return result;
    }

    private static BotPolicy configuredPolicy() {
        return new BotPolicy(FotrStarterBot.configuredTransferGuard(),
                FotrStarterBot.configuredGenericPassGate(), FotrStarterBot.configuredMaximumBurdensToBid());
    }

    private static Path requiredPath(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing required --" + key + "=<path>");
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static boolean booleanOption(Map<String, String> options, String key) {
        String value = options.get(key);
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
            throw new IllegalArgumentException("Invalid boolean option --" + key + "=" + value);
        return Boolean.parseBoolean(value);
    }

    private static MovieBlockDeckFactory.CultureComposition parseComposition(String value) {
        try {
            return MovieBlockDeckFactory.CultureComposition.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("composition must be mixed, focused, or multiculture: " + value, e);
        }
    }

    private static boolean booleanOption(Map<String, String> options, String key, boolean defaultValue) {
        return options.containsKey(key) ? booleanOption(options, key) : defaultValue;
    }

    private static int bidCapOption(Map<String, String> options, String key, int defaultValue) {
        String value = options.get(key);
        if (value == null)
            return defaultValue;
        try {
            return BurdenTrainer.validateMaximumBurdensToBid(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer option --" + key + "=" + value, e);
        }
    }

    private static void rejectOptions(Map<String, String> options, String... keys) {
        for (String key : keys) {
            if (options.containsKey(key))
                throw new IllegalArgumentException("Option --" + key + " is not valid in this mode");
        }
    }

    private static final class EventCounts implements TrainingEventReporter {
        private final Map<String, Long> counts = new TreeMap<>();
        private final Map<String, Integer> passGateVetoesByPhase = new TreeMap<>();

        @Override
        public void recordEvent(Severity severity, String type, String model, String message, Throwable failure) {
            counts.merge(severity.name().toLowerCase(Locale.ROOT) + ":" + type, 1L, Long::sum);
        }

        @Override
        public void recordPassGateVeto(Phase phase) {
            TrainingEventReporter.super.recordPassGateVeto(phase);
            passGateVetoesByPhase.merge(phase.name(), 1, Integer::sum);
        }

        private void add(EventCounts other) {
            other.counts.forEach((type, count) -> counts.merge(type, count, Long::sum));
            other.passGateVetoesByPhase.forEach(
                    (phase, count) -> passGateVetoesByPhase.merge(phase, count, Integer::sum));
        }

        private long eventCount(String eventType) {
            return counts.getOrDefault(eventType, 0L);
        }

        private Map<String, Integer> passGateVetoesByPhase() {
            return Map.copyOf(passGateVetoesByPhase);
        }

        private int transferGuardConstraints() {
            return Math.toIntExact(counts.getOrDefault("success:transfer-guard-constrained-decision", 0L));
        }

        private int transferReversals() {
            return Math.toIntExact(counts.getOrDefault("success:fellowship-transfer-reversal", 0L));
        }
    }

    private static final class SeatAccumulator {
        private int wins;
        private int completed;
        private int skipped;
        private int candidateSeatZeroGames;
        private int candidateSeatOneGames;
        private int candidateStartingGames;
        private int opponentStartingGames;
        private int candidateWinsWhenStarting;
        private int candidateWinsWhenOpponentStarts;
        private BatchTacticalMetrics metrics = BatchTacticalMetrics.empty();

        private void record(boolean candidateIsP1, boolean candidateWon, GameTacticalMetrics metrics) {
            int candidateSeat = candidateIsP1 ? metrics.p1SeatIndex() : 1 - metrics.p1SeatIndex();
            if (candidateSeat == 0)
                candidateSeatZeroGames++;
            else if (candidateSeat == 1)
                candidateSeatOneGames++;
            else
                throw new IllegalStateException("Benchmark game reported invalid candidate seat " + candidateSeat);

            boolean candidateStarted = candidateIsP1 == metrics.p1StartsFirst();
            if (candidateStarted) {
                candidateStartingGames++;
                if (candidateWon)
                    candidateWinsWhenStarting++;
            } else {
                opponentStartingGames++;
                if (candidateWon)
                    candidateWinsWhenOpponentStarts++;
            }
        }

        private SeatingSummary seatingSummary() {
            Double balancedWinRate = candidateStartingGames == 0 || opponentStartingGames == 0
                    ? null
                    : 0.5 * (candidateWinsWhenStarting / (double) candidateStartingGames
                    + candidateWinsWhenOpponentStarts / (double) opponentStartingGames);
            return new SeatingSummary(candidateSeatZeroGames, candidateSeatOneGames,
                    candidateStartingGames, opponentStartingGames, candidateWinsWhenStarting,
                    candidateWinsWhenOpponentStarts, balancedWinRate);
        }

        private SeatResult summary() {
            return new SeatResult(winSummary(wins, completed, skipped), metrics, seatingSummary());
        }
    }

    record ModelSnapshot(ModelRegistry registry, Map<String, String> fingerprints) { }
    record BotPolicy(boolean transferGuardEnabled, boolean genericPassGateEnabled, int maximumBurdensToBid) {
        BotPolicy(boolean transferGuardEnabled, boolean genericPassGateEnabled) {
            this(transferGuardEnabled, genericPassGateEnabled, FotrStarterBot.configuredMaximumBurdensToBid());
        }

        BotPolicy {
            BurdenTrainer.validateMaximumBurdensToBid(maximumBurdensToBid);
        }
    }
    record CodeVersion(String revision, boolean dirty) { }
    record WinSummary(int wins, int completedGames, int skippedGames, Double winRate,
                      Double wilsonLower95, Double wilsonUpper95) { }
    record SeatResult(WinSummary wins, BatchTacticalMetrics tacticalMetrics, SeatingSummary actualSeating) { }
    record SeatingSummary(int candidateSeatZeroGames, int candidateSeatOneGames,
                         int candidateStartingGames, int opponentStartingGames,
                         int candidateWinsWhenStarting, int candidateWinsWhenOpponentStarts,
                         Double seatBalancedWinRate) {
        SeatingSummary plus(SeatingSummary other) {
            int candidateStarts = candidateStartingGames + other.candidateStartingGames;
            int opponentStarts = opponentStartingGames + other.opponentStartingGames;
            int winsWhenStarting = candidateWinsWhenStarting + other.candidateWinsWhenStarting;
            int winsWhenOpponentStarts = candidateWinsWhenOpponentStarts
                    + other.candidateWinsWhenOpponentStarts;
            Double balancedWinRate = candidateStarts == 0 || opponentStarts == 0
                    ? null
                    : 0.5 * (winsWhenStarting / (double) candidateStarts
                    + winsWhenOpponentStarts / (double) opponentStarts);
            return new SeatingSummary(candidateSeatZeroGames + other.candidateSeatZeroGames,
                    candidateSeatOneGames + other.candidateSeatOneGames, candidateStarts, opponentStarts,
                    winsWhenStarting, winsWhenOpponentStarts, balancedWinRate);
        }
    }
    record PlannedMatchup(String pool, BenchmarkDeckManifest.DeckEntry candidateDeck,
                          BenchmarkDeckManifest.DeckEntry opponentDeck) { }
    record Matchup(String pool, String candidateDeck, String opponentDeck,
                   String candidateAsBot1, String candidateAsBot2,
                   Map<String, Integer> candidatePassGateVetoesAsBot1,
                   Map<String, Integer> candidatePassGateVetoesAsBot2,
                   int candidateTransferGuardConstraintsAsBot1,
                   int candidateTransferGuardConstraintsAsBot2,
                   int candidateTransferReversalsAsBot1,
                   int candidateTransferReversalsAsBot2,
                   long cycleRecoveriesAsBot1,
                   long cycleRecoveriesAsBot2,
                   FotrStarterBot.BurdenBidDiagnostics candidateBidAsBot1,
                   FotrStarterBot.BurdenBidDiagnostics candidateBidAsBot2,
                   FotrStarterBot.BurdenBidDiagnostics opponentBidAsBot1,
                   FotrStarterBot.BurdenBidDiagnostics opponentBidAsBot2) { }
    record FormatResult(String format, SeatResult candidateAsBot1, SeatResult candidateAsBot2,
                        WinSummary combined, SeatingSummary actualSeating,
                        Map<String, Long> events,
                        Map<String, Long> candidatePassGateVetoesByPhase,
                        long candidateTransferGuardConstraints,
                        long candidateTransferReversals,
                        long cycleRecoveries,
                        BidSummary candidateBids,
                        BidSummary opponentBids,
                        List<Matchup> matchups) { }
    record BidSummary(long observedGames, long capAssessedGames, long capConstrainedGames,
                      long engineMinimumExceedsCapGames, Map<String, Long> bidAmounts) { }
    private record PlayResult(String outcome, Map<String, Integer> passGateVetoesByPhase,
                              int transferGuardConstraints, int transferReversals,
                              long cycleRecoveries, FotrStarterBot.BurdenBidDiagnostics candidateBid,
                              FotrStarterBot.BurdenBidDiagnostics opponentBid) { }
    record Report(int version, String status, String startedAt, long elapsedMillis, CodeVersion code, String javaVersion,
                  String resourcesPath, String manifestPath, String manifestSha256, String modelDirectory,
                  Map<String, String> modelSha256, long pairingSeed, int pairsPerFormat,
                  boolean candidateTransferGuardEnabled, boolean candidateGenericActionPassGateEnabled,
                  int candidateMaximumBurdensToBid, boolean opponentTransferGuardEnabled,
                  boolean opponentGenericActionPassGateEnabled, Integer opponentMaximumBurdensToBid,
                  String opponent, String opponentModelDirectory, Map<String, String> opponentModelSha256,
                  String deckPoolSelection, WinSummary combined, List<FormatResult> formats,
                  List<String> limitations) { }

    enum DeckPoolSelection {
        MIXED,
        STARTER,
        GENERATED,
        GENERATED_VS_REFERENCE;

        private static DeckPoolSelection parse(String value) {
            try {
                return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "deck-pool must be mixed, starter, generated, or generated-vs-reference: " + value, e);
            }
        }

        private String commandValue() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }
}
