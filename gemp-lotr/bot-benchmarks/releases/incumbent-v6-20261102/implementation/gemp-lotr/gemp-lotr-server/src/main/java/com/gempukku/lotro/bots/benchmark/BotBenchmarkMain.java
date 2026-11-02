package com.gempukku.lotro.bots.benchmark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
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
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.simulation.SimulationOutcome;
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
        boolean genericPassGateEnabled = options.containsKey("generic-pass-gate")
                ? booleanOption(options, "generic-pass-gate")
                : FotrStarterBot.configuredGenericPassGate();
        Path output;
        Path manifestFile = null;
        Path modelDirectory = null;
        int pairs = 0;
        int generated = 0;
        if (creatingManifest) {
            rejectOptions(options, "manifest", "models", "output", "pairs", "generic-pass-gate");
            output = requiredPath(options, "create-manifest");
            generated = Integer.parseInt(options.getOrDefault("generated", "4"));
            if (generated < 2)
                throw new IllegalArgumentException("At least two generated decks per format are required");
        } else {
            rejectOptions(options, "generated");
            manifestFile = requiredPath(options, "manifest");
            modelDirectory = requiredPath(options, "models").toRealPath();
            output = requiredPath(options, "output");
            if (output.getParent().toRealPath().startsWith(modelDirectory))
                throw new IllegalArgumentException("Benchmark output must be outside the model directory");
            pairs = Integer.parseInt(options.getOrDefault("pairs", "50"));
            if (pairs < 1)
                throw new IllegalArgumentException("At least one deck pair per format is required");
        }
        if (Files.exists(output))
            throw new java.nio.file.FileAlreadyExistsException(output.toString());

        LotroCardBlueprintLibrary cards = new LotroCardBlueprintLibrary(
                resources.resolve("cards").toFile(), resources.resolve("blueprintMapping.txt").toFile(),
                resources.resolve("setConfig.hjson").toFile(), resources.resolve("rarities").toFile());
        LotroFormatLibrary formats = new LotroFormatLibrary(new DefaultAdventureLibrary(), cards,
                resources.resolve("lotrFormats.hjson").toFile(), resources.resolve("sealed").toFile());

        if (creatingManifest) {
            ProductLibrary products = new ProductLibrary(cards, resources.resolve("product").toFile());
            BenchmarkDeckManifest manifest = BenchmarkDeckManifest.create(formats, cards, products, generated, seed);
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
        CodeVersion code = codeVersion();
        CardFeatures.init(cards);
        Instant started = Instant.now();
        long startNanos = System.nanoTime();
        List<FormatResult> results =
                run(manifest, formats, cards, models.registry(), pairs, seed, genericPassGateEnabled);
        WinSummary total = combined(results);
        boolean failed = results.stream().anyMatch(result -> result.combined().completedGames() == 0);
        String status = failed ? "failed" : total.skippedGames() > 0 ? "completed-with-skips" : "completed";
        Report report = new Report(1, status, started.toString(), (System.nanoTime() - startNanos) / 1_000_000,
                code, System.getProperty("java.version"), resources.toString(), manifestFile.toString(), sha256(manifestBytes),
                modelDirectory.toString(), models.fingerprints(), seed, pairs,
                FotrStarterBot.configuredTransferGuard(), genericPassGateEnabled,
                "RandomDecisionBot", total, results,
                List.of("Only deck-pair selection is seeded; shuffles and bot randomness are not.",
                        "Candidate-as-bot1/bot2 refers to simulator argument order; bidding and game seating are not controlled.",
                        "Starters were used in training; generated decks are not verified against historical data.",
                        "The opponent is the current random bot, not an independently frozen policy implementation.",
                        "Wilson intervals are nominal per-game estimates; paired decks and repeated matchups can reduce independence.",
                        "Skipped games do not contribute game-end or decision metrics.",
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
        return run(manifest, formats, cards, models, pairs, seed,
                FotrStarterBot.configuredGenericPassGate());
    }

    static List<FormatResult> run(
            BenchmarkDeckManifest manifest, LotroFormatLibrary formats, LotroCardBlueprintLibrary cards,
            ModelRegistry models, int pairs, long seed, boolean genericPassGateEnabled) {
        String previousValue = System.getProperty(FotrStarterBot.GENERIC_PASS_GATE_PROPERTY);
        System.setProperty(FotrStarterBot.GENERIC_PASS_GATE_PROPERTY, Boolean.toString(genericPassGateEnabled));
        try {
            return runWithConfiguredPassGate(manifest, formats, cards, models, pairs, seed);
        } finally {
            if (previousValue == null)
                System.clearProperty(FotrStarterBot.GENERIC_PASS_GATE_PROPERTY);
            else
                System.setProperty(FotrStarterBot.GENERIC_PASS_GATE_PROPERTY, previousValue);
        }
    }

    private static List<FormatResult> runWithConfiguredPassGate(
            BenchmarkDeckManifest manifest, LotroFormatLibrary formats, LotroCardBlueprintLibrary cards,
            ModelRegistry models, int pairs, long seed) {
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
            for (PlannedMatchup pair : plan(pool, pairs, seed)) {
                LotroDeck candidate = pair.candidateDeck().toDeck(pool.format(), format);
                LotroDeck opponent = pair.opponentDeck().toDeck(pool.format(), format);
                String firstOutcome = play(simulation, features, models, candidate, opponent, true, events, first);
                String secondOutcome = play(simulation, features, models, candidate, opponent, false, events, second);
                matchups.add(new Matchup(pair.pool(), pair.candidateDeck().fingerprint(),
                        pair.opponentDeck().fingerprint(), firstOutcome, secondOutcome));
            }
            results.add(new FormatResult(pool.format(), first.summary(), second.summary(),
                    winSummary(first.wins + second.wins, first.completed + second.completed,
                            first.skipped + second.skipped),
                    Map.copyOf(events.counts), List.copyOf(matchups)));
            System.out.println("Finished benchmark format " + pool.format() + ": "
                    + (first.completed + second.completed) + " completed, "
                    + (first.skipped + second.skipped) + " skipped");
        }
        return List.copyOf(results);
    }

    static List<PlannedMatchup> plan(BenchmarkDeckManifest.DeckPool pool, int pairs, long seed) {
        Random random = new Random(seed + 31L * pool.format().hashCode());
        List<PlannedMatchup> result = new ArrayList<>();
        for (int pair = 0; pair < pairs; pair++) {
            boolean starter = pair % 2 == 0;
            List<BenchmarkDeckManifest.DeckEntry> entries = starter ? pool.starters() : pool.generated();
            result.add(new PlannedMatchup(starter ? "starter" : "generated",
                    entries.get(random.nextInt(entries.size())), entries.get(random.nextInt(entries.size()))));
        }
        return List.copyOf(result);
    }

    private static String play(
            MovieBlockSimulation simulation, MovieTrilogyRLGameStateFeatures features, ModelRegistry models,
            LotroDeck candidateDeck, LotroDeck opponentDeck, boolean candidateFirst,
            EventCounts events, SeatAccumulator seat) {
        FotrStarterBot candidate = new FotrStarterBot(features,
                candidateFirst ? "~benchmark-one" : "~benchmark-two", models, null, events);
        RandomDecisionBot opponent = new RandomDecisionBot(
                candidateFirst ? "~benchmark-two" : "~benchmark-one");
        try {
            SimulationOutcome outcome = candidateFirst
                    ? simulation.simulateGameWithMetrics(candidate, opponent, candidateDeck, opponentDeck)
                    : simulation.simulateGameWithMetrics(opponent, candidate, opponentDeck, candidateDeck);
            boolean candidateWon = outcome.result() == (candidateFirst ? GameResult.P1_WON : GameResult.P2_WON);
            seat.completed++;
            if (candidateWon)
                seat.wins++;
            seat.metrics = seat.metrics.plus(outcome.tacticalMetrics().orElseThrow());
            return candidateWon ? "win" : "loss";
        } catch (MovieBlockSimulation.GameDecisionLimitException
                 | MovieBlockSimulation.UnresolvableDecisionCycleException e) {
            seat.skipped++;
            String reason = e instanceof MovieBlockSimulation.GameDecisionLimitException
                    ? "decision-limit" : "unresolvable-cycle";
            events.recordEvent(TrainingEventReporter.Severity.ERROR, reason, null, e.getMessage(), e);
            System.err.println("Skipped benchmark game (" + reason + "): " + e.getMessage());
            return "skipped-" + reason;
        }
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
            if (!List.of("resources", "create-manifest", "manifest", "models", "output", "generated", "pairs",
                    "seed", "generic-pass-gate")
                    .contains(key) || result.putIfAbsent(key, arg.substring(equals + 1)) != null)
                throw new IllegalArgumentException("Unknown or repeated benchmark option: " + key);
        }
        return result;
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

    private static void rejectOptions(Map<String, String> options, String... keys) {
        for (String key : keys) {
            if (options.containsKey(key))
                throw new IllegalArgumentException("Option --" + key + " is not valid in this mode");
        }
    }

    private static final class EventCounts implements TrainingEventReporter {
        private final Map<String, Long> counts = new TreeMap<>();

        @Override
        public void recordEvent(Severity severity, String type, String model, String message, Throwable failure) {
            counts.merge(severity.name().toLowerCase(Locale.ROOT) + ":" + type, 1L, Long::sum);
        }
    }

    private static final class SeatAccumulator {
        private int wins;
        private int completed;
        private int skipped;
        private BatchTacticalMetrics metrics = BatchTacticalMetrics.empty();

        private SeatResult summary() {
            return new SeatResult(winSummary(wins, completed, skipped), metrics);
        }
    }

    record ModelSnapshot(ModelRegistry registry, Map<String, String> fingerprints) { }
    record CodeVersion(String revision, boolean dirty) { }
    record WinSummary(int wins, int completedGames, int skippedGames, Double winRate,
                      Double wilsonLower95, Double wilsonUpper95) { }
    record SeatResult(WinSummary wins, BatchTacticalMetrics tacticalMetrics) { }
    record PlannedMatchup(String pool, BenchmarkDeckManifest.DeckEntry candidateDeck,
                          BenchmarkDeckManifest.DeckEntry opponentDeck) { }
    record Matchup(String pool, String candidateDeck, String opponentDeck,
                   String candidateAsBot1, String candidateAsBot2) { }
    record FormatResult(String format, SeatResult candidateAsBot1, SeatResult candidateAsBot2,
                        WinSummary combined, Map<String, Long> events, List<Matchup> matchups) { }
    record Report(int version, String status, String startedAt, long elapsedMillis, CodeVersion code, String javaVersion,
                  String resourcesPath, String manifestPath, String manifestSha256, String modelDirectory,
                  Map<String, String> modelSha256, long pairingSeed, int pairsPerFormat,
                  boolean transferGuardEnabled, boolean genericActionPassGateEnabled,
                  String opponent, WinSummary combined, List<FormatResult> formats, List<String> limitations) { }
}
