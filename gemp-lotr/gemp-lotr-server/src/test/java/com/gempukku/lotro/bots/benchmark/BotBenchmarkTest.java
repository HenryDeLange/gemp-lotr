package com.gempukku.lotro.bots.benchmark;

import com.alibaba.fastjson2.JSON;
import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import smile.classification.Classifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BotBenchmarkTest extends AbstractAtTest {
    private static BenchmarkDeckManifest manifest;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void createDecks() {
        CardFeatures.init(_cardLibrary);
        manifest = BenchmarkDeckManifest.create(_formatLibrary, _cardLibrary, _productLibrary, 2, 1234L);
    }

    @Test
    public void manifestRoundTripsAllLegalDecksWithoutOverwriting() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("decks.json");
        manifest.writeNew(file);
        byte[] before = Files.readAllBytes(file);
        assertEquals(manifest, BenchmarkDeckManifest.read(file, _formatLibrary));
        try {
            manifest.writeNew(file);
            fail("An existing manifest must never be replaced");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            assertArrayEquals(before, Files.readAllBytes(file));
        }
        assertEquals(BenchmarkDeckManifest.FORMATS, manifest.pools().stream()
                .map(BenchmarkDeckManifest.DeckPool::format).toList());
        assertEquals(manifest.pools().stream().flatMap(pool -> pool.generated().stream())
                        .map(BenchmarkDeckManifest.DeckEntry::fingerprint).collect(java.util.stream.Collectors.toSet()),
                manifest.generatedFingerprints());
    }

    @Test
    public void rejectsChangedDecksAndOverlappingPools() {
        BenchmarkDeckManifest.DeckPool pool = manifest.pools().getFirst();
        BenchmarkDeckManifest.DeckEntry original = pool.generated().getFirst();
        var changed = new BenchmarkDeckManifest.DeckEntry(original.name(), original.contents(), "incorrect");
        List<BenchmarkDeckManifest.DeckPool> pools = new ArrayList<>(manifest.pools());
        pools.set(0, new BenchmarkDeckManifest.DeckPool(pool.format(), pool.starters(),
                List.of(changed, pool.generated().get(1))));
        try {
            new BenchmarkDeckManifest(1, manifest.generationSeed(), pools).validate(_formatLibrary);
            fail("A changed deck must fail fingerprint validation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("fingerprint mismatch"));
        }
        pools.set(0, new BenchmarkDeckManifest.DeckPool(pool.format(), pool.starters(),
                List.of(pool.starters().getFirst(), pool.generated().getFirst())));
        try {
            new BenchmarkDeckManifest(1, manifest.generationSeed(), pools).validate(_formatLibrary);
            fail("Generated decks must not overlap starter decks");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("overlapping"));
        }
    }

    @Test
    public void runsBothArgumentOrdersAndPoolTypesInEveryFormat() {
        List<BotBenchmarkMain.FormatResult> results =
                BotBenchmarkMain.run(manifest, _formatLibrary, _cardLibrary, new ModelRegistry(), 2, 4321L);
        assertEquals(4, results.size());
        for (BotBenchmarkMain.FormatResult result : results) {
            assertEquals(List.of("starter", "generated"),
                    result.matchups().stream().map(BotBenchmarkMain.Matchup::pool).toList());
            assertEquals(2, result.candidateAsBot1().wins().completedGames()
                    + result.candidateAsBot1().wins().skippedGames());
            assertEquals(2, result.candidateAsBot2().wins().completedGames()
                    + result.candidateAsBot2().wins().skippedGames());
            assertEquals(result.candidateAsBot1().wins().completedGames(),
                    result.candidateAsBot1().tacticalMetrics().decisionMetrics().gamesMeasured());
            assertEquals(result.candidateAsBot2().wins().completedGames(),
                    result.candidateAsBot2().tacticalMetrics().decisionMetrics().gamesMeasured());
            assertEquals(result.candidateAsBot1().wins().wins() + result.candidateAsBot2().wins().wins(),
                    result.combined().wins());
            assertEquals(result.combined().completedGames(),
                    result.actualSeating().candidateSeatZeroGames()
                            + result.actualSeating().candidateSeatOneGames());
            assertEquals(result.combined().completedGames(),
                    result.actualSeating().candidateStartingGames()
                            + result.actualSeating().opponentStartingGames());
            var pool = manifest.pools().stream().filter(item -> item.format().equals(result.format())).findFirst().orElseThrow();
            var plan = BotBenchmarkMain.plan(pool, 2, 4321L);
            assertEquals(plan, BotBenchmarkMain.plan(pool, 2, 4321L));
            for (int index = 0; index < plan.size(); index++) {
                assertEquals(plan.get(index).candidateDeck().fingerprint(), result.matchups().get(index).candidateDeck());
                assertEquals(plan.get(index).opponentDeck().fingerprint(), result.matchups().get(index).opponentDeck());
            }
        }
        assertEquals(16, BotBenchmarkMain.combined(results).completedGames()
                + BotBenchmarkMain.combined(results).skippedGames());
        var parsed = JSON.parseArray(JSON.toJSONString(results));
        assertEquals(4, parsed.size());
        var firstSeat = parsed.getJSONObject(0).getJSONObject("candidateAsBot1");
        assertNotNull(firstSeat.getJSONObject("tacticalMetrics").getJSONObject("decisionMetrics")
                .get("p95Decisions"));
        assertNotNull(parsed.getJSONObject(0).getJSONArray("matchups").getJSONObject(0).getString("candidateDeck"));
    }

    @Test
    public void generatedOnlyPlanUsesOnlyManifestGeneratedDecks() {
        var pool = manifest.pools().getFirst();
        var plan = BotBenchmarkMain.plan(pool, 5, 987L, BotBenchmarkMain.DeckPoolSelection.GENERATED);

        assertEquals(5, plan.size());
        assertTrue(plan.stream().allMatch(matchup -> matchup.pool().equals("generated")));
        assertTrue(plan.stream().allMatch(matchup ->
                pool.generated().stream().map(BenchmarkDeckManifest.DeckEntry::fingerprint)
                        .toList().contains(matchup.candidateDeck().fingerprint())
                        && pool.generated().stream().map(BenchmarkDeckManifest.DeckEntry::fingerprint)
                        .toList().contains(matchup.opponentDeck().fingerprint())));
    }

    @Test
    public void computesNominalWilsonIntervalAndLeavesEmptyRatesUnset() {
        var summary = BotBenchmarkMain.winSummary(50, 100, 4);
        assertEquals(0.5, summary.winRate(), 0.000001);
        assertEquals(0.4038315, summary.wilsonLower95(), 0.000001);
        assertEquals(0.5961685, summary.wilsonUpper95(), 0.000001);
        assertNull(BotBenchmarkMain.winSummary(0, 0, 2).winRate());
    }

    @Test
    public void commandProducesReportWithoutChangingModelFilesOrManifest() throws Exception {
        Path modelDirectory = temporaryFolder.newFolder("models").toPath();
        Path opponentModelDirectory = temporaryFolder.newFolder("frozen-opponent-models").toPath();
        for (var trainer : FotrStarterTrainerCatalog.createAll())
            ModelIO.saveModel(trainer.getClass(), new ConstantClassifier(), modelDirectory);
        for (var trainer : FotrStarterTrainerCatalog.createAll())
            ModelIO.saveModel(trainer.getClass(), new ConstantClassifier(), opponentModelDirectory);
        Path manifestFile = temporaryFolder.getRoot().toPath().resolve("decks.json");
        Path output = temporaryFolder.getRoot().toPath().resolve("result.json");
        manifest.writeNew(manifestFile);
        byte[] manifestBefore = Files.readAllBytes(manifestFile);
        var before = BotBenchmarkMain.loadModels(modelDirectory).fingerprints();
        var opponentBefore = BotBenchmarkMain.loadModels(opponentModelDirectory).fingerprints();
        Path resources = Path.of("..", "gemp-lotr-cards", "src", "main", "resources").toAbsolutePath().normalize();

        BotBenchmarkMain.main(new String[]{
                "--resources=" + resources, "--manifest=" + manifestFile,
                "--models=" + modelDirectory, "--output=" + output, "--pairs=1", "--seed=55",
                "--opponent-models=" + opponentModelDirectory, "--opponent-label=frozen-test",
                "--deck-pool=generated",
                "--candidate-transfer-guard=false", "--candidate-generic-pass-gate=true",
                "--opponent-transfer-guard=true", "--opponent-generic-pass-gate=false"
        });

        var report = JSON.parseObject(Files.readString(output));
        assertEquals(3, report.getIntValue("version"));
        assertEquals(43, report.getJSONObject("modelSha256").size());
        assertEquals(43, report.getJSONObject("opponentModelSha256").size());
        assertEquals("frozen-test", report.getString("opponent"));
        assertEquals(opponentModelDirectory.toRealPath().toString(), report.getString("opponentModelDirectory"));
        assertEquals("generated", report.getString("deckPoolSelection"));
        assertFalse(report.getBooleanValue("candidateTransferGuardEnabled"));
        assertTrue(report.getBooleanValue("candidateGenericActionPassGateEnabled"));
        assertTrue(report.getBooleanValue("opponentTransferGuardEnabled"));
        assertFalse(report.getBooleanValue("opponentGenericActionPassGateEnabled"));
        assertEquals(4, report.getJSONArray("formats").size());
        var firstFormat = report.getJSONArray("formats").getJSONObject(0);
        assertTrue(firstFormat.containsKey("candidatePassGateVetoesByPhase"));
        assertTrue(firstFormat.containsKey("candidateTransferGuardConstraints"));
        var firstMatchup = firstFormat.getJSONArray("matchups").getJSONObject(0);
        assertTrue(firstMatchup.containsKey("candidatePassGateVetoesAsBot1"));
        assertTrue(firstMatchup.containsKey("candidateTransferGuardConstraintsAsBot2"));
        assertEquals(BotBenchmarkMain.sha256(manifestBefore), report.getString("manifestSha256"));
        assertEquals(8, report.getJSONObject("combined").getIntValue("completedGames")
                + report.getJSONObject("combined").getIntValue("skippedGames"));
        var actualSeating = report.getJSONArray("formats").getJSONObject(0).getJSONObject("actualSeating");
        assertEquals(report.getJSONArray("formats").getJSONObject(0).getJSONObject("combined")
                        .getIntValue("completedGames"),
                actualSeating.getIntValue("candidateStartingGames")
                        + actualSeating.getIntValue("opponentStartingGames"));
        assertArrayEquals(manifestBefore, Files.readAllBytes(manifestFile));
        assertEquals(before, BotBenchmarkMain.loadModels(modelDirectory).fingerprints());
        assertEquals(opponentBefore, BotBenchmarkMain.loadModels(opponentModelDirectory).fingerprints());
        try {
            BotBenchmarkMain.main(new String[]{
                    "--resources=" + resources, "--manifest=" + manifestFile,
                    "--models=" + modelDirectory, "--output=" + output, "--pairs=1"
            });
            fail("An existing benchmark report must never be replaced");
        } catch (java.nio.file.FileAlreadyExistsException expected) {
            assertEquals(4, JSON.parseObject(Files.readString(output)).getJSONArray("formats").size());
        }
    }

    @Test
    public void missingModelsAreReportedInsteadOfUsingBaseDecisions() throws Exception {
        Path modelDirectory = temporaryFolder.newFolder("missing-models").toPath();
        try {
            BotBenchmarkMain.loadModels(modelDirectory);
            fail("A benchmark must not silently replace missing policies with base decisions");
        } catch (java.nio.file.NoSuchFileException expected) {
            assertTrue(expected.getFile().endsWith(".model"));
        }
    }

    private static final class ConstantClassifier implements Classifier<double[]> {
        @Override
        public int numClasses() {
            return 2;
        }

        @Override
        public int[] classes() {
            return new int[]{0, 1};
        }

        @Override
        public int predict(double[] features, double[] posterior) {
            posterior[0] = 1;
            posterior[1] = 0;
            return 0;
        }

        @Override
        public int predict(double[] features) {
            return 0;
        }
    }
}
