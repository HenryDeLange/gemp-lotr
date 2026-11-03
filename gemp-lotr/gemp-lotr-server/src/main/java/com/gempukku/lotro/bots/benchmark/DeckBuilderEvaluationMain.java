package com.gempukku.lotro.bots.benchmark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.gempukku.lotro.bots.MovieBlockDeckFactory;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.simulation.GameResult;
import com.gempukku.lotro.bots.simulation.GameSeating;
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.simulation.SimulationOutcome;
import com.gempukku.lotro.game.DefaultAdventureLibrary;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DeckBuilderEvaluationMain {
    private static final int CANDIDATE_POOL_SIZE = 16;
    private static final int MAX_GENERATION_ATTEMPTS = 100;

    private DeckBuilderEvaluationMain() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parseOptions(args);
        Path resources = requiredPath(options, "resources");
        Path manifestFile = requiredPath(options, "reference-manifest");
        Path policyDirectory = requiredPath(options, "policy-models").toRealPath();
        Path rankerDirectory = requiredPath(options, "ranker-models").toRealPath();
        Path output = requiredPath(options, "output");
        long seed = Long.parseLong(options.getOrDefault("seed", "1"));
        int candidatesPerStyle = Integer.parseInt(options.getOrDefault("candidates-per-style", "4"));
        if (candidatesPerStyle < 1)
            throw new IllegalArgumentException("At least one candidate per culture style is required");
        if (Files.exists(output))
            throw new FileAlreadyExistsException(output.toString());
        Path outputParent = output.getParent();
        if (outputParent == null)
            throw new IllegalArgumentException("Deck-builder evaluation output must have a parent directory");
        Files.createDirectories(outputParent);
        Path resolvedOutputParent = outputParent.toRealPath();
        if (resolvedOutputParent.startsWith(policyDirectory) || resolvedOutputParent.startsWith(rankerDirectory))
            throw new IllegalArgumentException("Evaluation output must be outside both model directories");

        LotroCardBlueprintLibrary cards = new LotroCardBlueprintLibrary(
                resources.resolve("cards").toFile(), resources.resolve("blueprintMapping.txt").toFile(),
                resources.resolve("setConfig.hjson").toFile(), resources.resolve("rarities").toFile());
        LotroFormatLibrary formats = new LotroFormatLibrary(new DefaultAdventureLibrary(), cards,
                resources.resolve("lotrFormats.hjson").toFile(), resources.resolve("sealed").toFile());
        BenchmarkDeckManifest manifest = BenchmarkDeckManifest.read(manifestFile, formats);
        byte[] manifestBytes = Files.readAllBytes(manifestFile);
        BotBenchmarkMain.ModelSnapshot policySnapshot = BotBenchmarkMain.loadModels(policyDirectory);
        ModelRegistry frozenPolicy = policySnapshot.registry();
        CardFeatures.init(cards);

        boolean transferGuard = FotrStarterBot.configuredTransferGuard();
        boolean passGate = FotrStarterBot.configuredGenericPassGate();
        int maximumBurdensToBid = FotrStarterBot.configuredMaximumBurdensToBid();
        Instant startedAt = Instant.now();
        List<CandidateReport> candidateReports = new ArrayList<>();
        List<FormatStyleSummary> summaries = new ArrayList<>();
        Map<String, String> rankerFingerprints = new LinkedHashMap<>();

        for (BenchmarkDeckManifest.DeckPool pool : manifest.pools()) {
            LotroFormat format = formats.getFormat(pool.format());
            MovieTrilogyRLGameStateFeatures features =
                    new MovieTrilogyRLGameStateFeatures(pool.format());
            List<LotroDeck> references = new ArrayList<>();
            pool.starters().stream().map(entry -> entry.toDeck(pool.format(), format)).forEach(references::add);
            pool.generated().stream().map(entry -> entry.toDeck(pool.format(), format)).forEach(references::add);
            Set<String> heldOutFingerprints = manifest.allFingerprints();
            Set<String> usedCandidateFingerprints = new java.util.HashSet<>(heldOutFingerprints);

            Path rankerModelDirectory = ModelIO.resolveDeckBuilderDirectory(rankerDirectory, pool.format());
            Path rankerModelFile =
                    rankerModelDirectory.resolve(DeckBuilderTrainer.class.getSimpleName() + ".model");
            if (!Files.isRegularFile(rankerModelFile))
                throw new IllegalArgumentException("Missing deck-builder ranker for " + pool.format()
                        + ": " + rankerModelFile);
            rankerFingerprints.put(pool.format(), BotBenchmarkMain.sha256(Files.readAllBytes(rankerModelFile)));
            var ranker = ModelIO.loadModel(DeckBuilderTrainer.class, rankerModelDirectory);
            MovieBlockSimulation simulation = new MovieBlockSimulation(format, cards,
                    () -> { throw new IllegalStateException("Deck-builder evaluation requires explicit decks"); });
            List<GamePairing> stylePairings = new ArrayList<>();

            for (MovieBlockDeckFactory.CultureComposition composition : List.of(
                    MovieBlockDeckFactory.CultureComposition.FOCUSED,
                    MovieBlockDeckFactory.CultureComposition.MULTICULTURE)) {
                for (int candidateIndex = 0; candidateIndex < candidatesPerStyle; candidateIndex++) {
                    long candidateSeed = seed + 31L * pool.format().hashCode()
                            + 997L * composition.ordinal() + 7919L * candidateIndex;
                    LotroDeck candidate = createDistinctCandidate(
                            format, cards, ranker, composition, candidateSeed, usedCandidateFingerprints);
                    String candidateFingerprint = DeckBuilderTrainer.fingerprint(pool.format(), candidate);
                    BenchmarkDeckManifest.DeckEntry candidateEntry =
                            BenchmarkDeckManifest.DeckEntry.from(pool.format(), candidate);
                    List<GamePairing> candidatePairings = new ArrayList<>(2 * references.size());
                    for (int referenceIndex = 0; referenceIndex < references.size(); referenceIndex++) {
                        LotroDeck reference = references.get(referenceIndex);
                        String referenceFingerprint =
                                DeckBuilderTrainer.fingerprint(pool.format(), reference);
                        candidatePairings.add(play(simulation, features, frozenPolicy, candidate, reference,
                                candidateFingerprint, referenceFingerprint, composition,
                                candidateIndex, referenceIndex, true, transferGuard, passGate,
                                maximumBurdensToBid));
                        candidatePairings.add(play(simulation, features, frozenPolicy, candidate, reference,
                                candidateFingerprint, referenceFingerprint, composition,
                                candidateIndex, referenceIndex, false, transferGuard, passGate,
                                maximumBurdensToBid));
                    }
                    candidateReports.add(new CandidateReport(
                            pool.format(), composition.name(), candidateIndex, candidateEntry,
                            summarize(candidatePairings), List.copyOf(candidatePairings)));
                    stylePairings.addAll(candidatePairings);
                }
            }
            for (MovieBlockDeckFactory.CultureComposition composition : List.of(
                    MovieBlockDeckFactory.CultureComposition.FOCUSED,
                    MovieBlockDeckFactory.CultureComposition.MULTICULTURE)) {
                List<GamePairing> pairings = stylePairings.stream()
                        .filter(pairing -> pairing.cultureComposition().equals(composition.name()))
                        .toList();
                summaries.add(new FormatStyleSummary(pool.format(), composition.name(), summarize(pairings)));
            }
            System.out.printf(Locale.ROOT, "Evaluated both deck-builder styles in %s: %d candidates, %d references%n",
                    pool.format(), 2 * candidatesPerStyle, references.size());
        }

        List<String> limitations = List.of(
                "Each generated candidate is evaluated against every reference deck in the supplied held-out manifest.",
                "Each candidate/reference pairing uses both simulator argument orders.",
                "Game-engine bidding and tie-breaking randomness is not seeded; actual seats and starting players are recorded.");
        EvaluationReport report = new EvaluationReport(
                1, Instant.now().toString(), startedAt.toString(), resources.toString(),
                manifestFile.toString(), BotBenchmarkMain.sha256(manifestBytes),
                policyDirectory.toString(), policySnapshot.fingerprints(), rankerDirectory.toString(),
                Map.copyOf(rankerFingerprints), seed, candidatesPerStyle, CANDIDATE_POOL_SIZE,
                transferGuard, passGate, maximumBurdensToBid, List.copyOf(summaries),
                List.copyOf(candidateReports), limitations);
        Files.writeString(output, JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        System.out.println("Saved deck-builder style evaluation: " + output);
    }

    private static LotroDeck createDistinctCandidate(
            LotroFormat format,
            LotroCardBlueprintLibrary cards,
            smile.classification.Classifier<double[]> ranker,
            MovieBlockDeckFactory.CultureComposition composition,
            long seed,
            Set<String> usedFingerprints) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            LotroDeck deck = DeckBuilderTrainer.createDeck(
                    format, cards, ranker, CANDIDATE_POOL_SIZE, seed + 31L * attempt, composition);
            String fingerprint = DeckBuilderTrainer.fingerprint(format.getCode(), deck);
            if (usedFingerprints.add(fingerprint))
                return deck;
        }
        throw new IllegalStateException("Could not generate a distinct " + composition
                + " deck outside the held-out reference pool for " + format.getCode());
    }

    private static GamePairing play(
            MovieBlockSimulation simulation,
            MovieTrilogyRLGameStateFeatures features,
            ModelRegistry frozenPolicy,
            LotroDeck candidate,
            LotroDeck reference,
            String candidateFingerprint,
            String referenceFingerprint,
            MovieBlockDeckFactory.CultureComposition composition,
            int candidateIndex,
            int referenceIndex,
            boolean candidateAsP1,
            boolean transferGuard,
            boolean passGate,
            int maximumBurdensToBid) {
        String argumentPosition = candidateAsP1 ? "P1" : "P2";
        String name = "~deck-builder-eval-" + composition.name().toLowerCase(Locale.ROOT)
                + "-" + candidateIndex + "-" + referenceIndex + "-" + argumentPosition;
        FotrStarterBot p1 = new FotrStarterBot(features, name + "-one", frozenPolicy, null, null,
                0.0, 1.0, 1.0, transferGuard, passGate, maximumBurdensToBid);
        FotrStarterBot p2 = new FotrStarterBot(features, name + "-two", frozenPolicy, null, null,
                0.0, 1.0, 1.0, transferGuard, passGate, maximumBurdensToBid);
        try {
            SimulationOutcome outcome = simulation.simulateGameWithSeating(p1, p2,
                    candidateAsP1 ? candidate : reference,
                    candidateAsP1 ? reference : candidate);
            GameSeating seating = outcome.seating().orElseThrow(
                    () -> new IllegalStateException("Deck-builder evaluation did not return actual seating"));
            int candidateSeatIndex = candidateAsP1 ? seating.p1SeatIndex() : 1 - seating.p1SeatIndex();
            boolean candidateStartsFirst = candidateAsP1 == seating.p1StartsFirst();
            boolean candidateWon = outcome.result()
                    == (candidateAsP1 ? GameResult.P1_WON : GameResult.P2_WON);
            return new GamePairing(composition.name(), candidateIndex, referenceIndex,
                    candidateFingerprint, referenceFingerprint, argumentPosition,
                    candidateSeatIndex, candidateStartsFirst, candidateWon,
                    candidateWon ? "win" : "loss", null);
        } catch (MovieBlockSimulation.GameDecisionLimitException
                 | MovieBlockSimulation.UnresolvableDecisionCycleException e) {
            String reason = e instanceof MovieBlockSimulation.GameDecisionLimitException
                    ? "decision-limit" : "unresolvable-cycle";
            return new GamePairing(composition.name(), candidateIndex, referenceIndex,
                    candidateFingerprint, referenceFingerprint, argumentPosition,
                    null, null, null, "skipped", reason);
        }
    }

    private static StyleSummary summarize(List<GamePairing> pairings) {
        int completed = 0;
        int wins = 0;
        int skipped = 0;
        int seatZeroGames = 0;
        int seatZeroWins = 0;
        int seatOneGames = 0;
        int seatOneWins = 0;
        int startsFirstGames = 0;
        int startsFirstWins = 0;
        int startsSecondGames = 0;
        int startsSecondWins = 0;
        for (GamePairing pairing : pairings) {
            if (pairing.skipReason() != null) {
                skipped++;
                continue;
            }
            completed++;
            if (pairing.candidateWon())
                wins++;
            if (pairing.candidateSeatIndex() == 0) {
                seatZeroGames++;
                if (pairing.candidateWon())
                    seatZeroWins++;
            } else {
                seatOneGames++;
                if (pairing.candidateWon())
                    seatOneWins++;
            }
            if (pairing.candidateStartsFirst()) {
                startsFirstGames++;
                if (pairing.candidateWon())
                    startsFirstWins++;
            } else {
                startsSecondGames++;
                if (pairing.candidateWon())
                    startsSecondWins++;
            }
        }
        DeckBuilderTrainer.StratumStats seatZero =
                new DeckBuilderTrainer.StratumStats(seatZeroGames, seatZeroWins);
        DeckBuilderTrainer.StratumStats seatOne =
                new DeckBuilderTrainer.StratumStats(seatOneGames, seatOneWins);
        DeckBuilderTrainer.StratumStats startsFirst =
                new DeckBuilderTrainer.StratumStats(startsFirstGames, startsFirstWins);
        DeckBuilderTrainer.StratumStats startsSecond =
                new DeckBuilderTrainer.StratumStats(startsSecondGames, startsSecondWins);
        return new StyleSummary(pairings.size(), completed, wins, skipped,
                seatZero, seatOne, DeckBuilderTrainer.balancedWinRate(seatZero, seatOne),
                startsFirst, startsSecond, DeckBuilderTrainer.balancedWinRate(startsFirst, startsSecond));
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (!arg.startsWith("--") || equals <= 2 || equals == arg.length() - 1)
                throw new IllegalArgumentException("Expected --name=value: " + arg);
            String key = arg.substring(2, equals);
            if (!List.of("resources", "reference-manifest", "policy-models", "ranker-models", "output",
                    "seed", "candidates-per-style").contains(key)
                    || result.putIfAbsent(key, arg.substring(equals + 1)) != null)
                throw new IllegalArgumentException("Unknown or repeated deck-builder evaluation option: " + key);
        }
        return result;
    }

    private static Path requiredPath(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing --" + key);
        return Path.of(value).toAbsolutePath().normalize();
    }

    public record EvaluationReport(
            int version,
            String completedAt,
            String startedAt,
            String resourcesPath,
            String referenceManifest,
            String referenceManifestSha256,
            String frozenPolicyDirectory,
            Map<String, String> frozenPolicySha256,
            String rankerDirectory,
            Map<String, String> rankerSha256,
            long candidateSeed,
            int candidatesPerStyleAndFormat,
            int rankerCandidatePoolSize,
            boolean transferGuardEnabled,
            boolean genericActionPassGateEnabled,
            int maximumBurdensToBid,
            List<FormatStyleSummary> summaries,
            List<CandidateReport> candidates,
            List<String> limitations) {
    }

    public record FormatStyleSummary(String format, String cultureComposition, StyleSummary summary) {
    }

    public record CandidateReport(
            String format,
            String cultureComposition,
            int candidateIndex,
            BenchmarkDeckManifest.DeckEntry deck,
            StyleSummary summary,
            List<GamePairing> pairings) {
    }

    public record GamePairing(
            String cultureComposition,
            int candidateIndex,
            int referenceIndex,
            String candidateDeckFingerprint,
            String referenceDeckFingerprint,
            String candidateArgumentPosition,
            Integer candidateSeatIndex,
            Boolean candidateStartsFirst,
            Boolean candidateWon,
            String outcome,
            String skipReason) {
    }

    public record StyleSummary(
            int gamesPlanned,
            int completedGames,
            int wins,
            int skippedGames,
            DeckBuilderTrainer.StratumStats candidateSeatZero,
            DeckBuilderTrainer.StratumStats candidateSeatOne,
            Double seatBalancedWinRate,
            DeckBuilderTrainer.StratumStats candidateStartsFirst,
            DeckBuilderTrainer.StratumStats candidateStartsSecond,
            Double startingPlayerBalancedWinRate) {
    }
}
