package com.gempukku.lotro.bots.training;

import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.training.TrainingProgress.PlannedStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import smile.classification.Classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotModelTrainingPlanTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void trainingSafetySettingsAreExplicitAndRejectInvalidBooleans() throws Exception {
        Path file = temporaryDirectory.resolve("training.properties");
        Files.writeString(file, "training.data.compact.on.load=false\n"
                + "training.transfer.guard.enabled=true\ntraining.action.pass.gate.enabled=true\n"
                + "training.require.incumbent.models=true\n"
                + "training.deckbuilder.max.history.records=1234\n"
                + "models.directory=isolated-models\n");
        var config = BotModelTrainingMain.TrainingConfig.load(new String[]{"--config=" + file});
        assertFalse(config.compactTrainingData());
        assertTrue(config.transferGuardEnabled());
        assertTrue(config.genericPassGateEnabled());
        assertTrue(config.requireIncumbentModels());
        assertEquals(1234, config.deckBuilderMaxHistoryRecords());
        assertEquals(temporaryDirectory.resolve("isolated-models").toAbsolutePath(), config.modelDirectory());

        Files.writeString(file, "training.data.compact.on.load=true\n"
                + "training.transfer.guard.enabled=false\ntraining.action.pass.gate.enabled=false\n");
        config = BotModelTrainingMain.TrainingConfig.load(new String[]{"--config=" + file});
        assertTrue(config.compactTrainingData());
        assertFalse(config.transferGuardEnabled());
        assertFalse(config.genericPassGateEnabled());
        assertEquals(DeckBuilderTrainer.DEFAULT_MAX_HISTORY_RECORDS,
                config.deckBuilderMaxHistoryRecords());

        Files.writeString(temporaryDirectory.resolve("heldout.json"), "manifest");
        Files.writeString(file, "training.holdout.deck.manifest=heldout.json\n");
        config = BotModelTrainingMain.TrainingConfig.load(new String[]{"--config=" + file});
        assertEquals(temporaryDirectory.resolve("heldout.json").toAbsolutePath(), config.holdoutDeckManifest());

        Files.writeString(file, "training.data.compact.on.load=true\n"
                + "training.holdout.deck.manifest=heldout.json\n");
        assertThrows(IllegalArgumentException.class,
                () -> BotModelTrainingMain.TrainingConfig.load(new String[]{"--config=" + file}));

        Files.writeString(file, "training.data.compact.on.load=maybe\n");
        assertThrows(IllegalArgumentException.class,
                () -> BotModelTrainingMain.TrainingConfig.load(new String[]{"--config=" + file}));

        Files.writeString(file, "training.deckbuilder.max.history.records=0\n");
        assertThrows(IllegalArgumentException.class,
                () -> BotModelTrainingMain.TrainingConfig.load(new String[]{"--config=" + file}));
    }

    @Test
    void compactionRefusesCompressedDecisionDataBeforeTrainingStarts() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("corpus");
        Path compressedDecisionFile = dataDirectory.resolve(
                "public-observation-v8/fotr_block/movie-block-example.jsonl.gz");
        Files.createDirectories(compressedDecisionFile.getParent());
        Files.write(compressedDecisionFile, new byte[]{1});
        Path configFile = temporaryDirectory.resolve("compact.properties");
        Files.writeString(configFile, "training.data.directory=corpus\n"
                + "training.data.compact.on.load=true\n");

        assertThrows(IllegalArgumentException.class,
                () -> BotModelTrainingMain.TrainingConfig.load(
                        new String[]{"--config=" + configFile}));
        assertEquals(1, Files.size(compressedDecisionFile));
    }

    @Test
    void requiredIncumbentsRejectMissingOrCorruptSnapshot() throws Exception {
        List<String> formats = List.of("movie");
        assertThrows(IllegalStateException.class,
                () -> BotModelTrainingMain.validateRequiredIncumbentModels(temporaryDirectory, formats));
        Path policy = ModelIO.resolveModelDirectory(temporaryDirectory, "movie");
        var trainers = FotrStarterTrainerCatalog.createAll();
        for (var trainer : trainers)
            ModelIO.saveModel(trainer.getClass(), new ConstantModel(), policy);
        ModelIO.saveModel(DeckBuilderTrainer.class, new ConstantModel(),
                ModelIO.resolveDeckBuilderDirectory(temporaryDirectory, "movie"));
        BotModelTrainingMain.validateRequiredIncumbentModels(temporaryDirectory, formats);

        Files.writeString(policy.resolve(trainers.getFirst().getClass().getSimpleName() + ".model"), "corrupt");
        assertThrows(IllegalStateException.class,
                () -> BotModelTrainingMain.validateRequiredIncumbentModels(temporaryDirectory, formats));
    }

    @Test
    void configuredIncumbentSnapshotCanBeLoadedWhenRequested() {
        String path = System.getProperty("gemp.test.bot.incumbent");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "No incumbent snapshot supplied");
        BotModelTrainingMain.validateRequiredIncumbentModels(
                Path.of(path), List.of("fotr_block", "ttt_block", "king_block", "movie"));
    }

    private static final class ConstantModel implements Classifier<double[]> {
        @Override public int numClasses() { return 2; }
        @Override public int[] classes() { return new int[]{0, 1}; }
        @Override public int predict(double[] features) { return 0; }
    }

    @Test
    void plansAllFormatGamesBeforeOneSharedFitAndCrossFormatEvaluation() {
        List<PlannedStage> plan = BotModelTrainingMain.trainingPlan(
                List.of("fotr_block", "ttt_block"), 2, 1000, 5, 2, 1, 4, 1);

        int firstGenerationTwoStage = indexOfGeneration(plan, 2);
        assertTrue(firstGenerationTwoStage >= 0);
        assertEquals("fotr_block generation 1 deck-builder-evaluation", plan.getFirst().name());
        assertEquals("fotr_block generation 1 vs-random-starter", plan.get(1).name());
        assertEquals(25, plan.get(1).total());
        assertEquals("fotr_block generation 1 vs-random-generated", plan.get(2).name());
        assertEquals(25, plan.get(2).total());
        assertEquals("fotr_block generation 1 self-play-same-starter", plan.get(3).name());
        assertEquals(238, plan.get(3).total());
        assertEquals("fotr_block generation 1 self-play-different-starters", plan.get(4).name());
        assertEquals(237, plan.get(4).total());
        assertEquals("fotr_block generation 1 self-play-generated", plan.get(5).name());
        assertEquals(475, plan.get(5).total());
        assertEquals("ttt_block generation 1 deck-builder-evaluation", plan.get(6).name());
        assertEquals("decision-model-fitting", plan.get(12).name());
        assertEquals("shared-policy", plan.get(12).format());
        assertEquals("fotr_block generation 1 evaluation policy-change-1-candidate-first", plan.get(13).name());
        assertEquals("ttt_block generation 1 evaluation policy-change-1-candidate-second", plan.get(16).name());
        assertEquals(1, plan.subList(0, firstGenerationTwoStage).stream()
                .filter(stage -> stage.name().equals("decision-model-fitting")).count());
        assertTrue(plan.subList(0, firstGenerationTwoStage).stream()
                .allMatch(stage -> stage.generation() < 2));
        assertTrue(plan.subList(firstGenerationTwoStage, plan.size()).stream()
                .allMatch(stage -> stage.generation() == 2));
    }

    @Test
    void dividesGenerationEvaluationBudgetAcrossIndividualPolicyChanges() {
        List<PlannedStage> plan = BotModelTrainingMain.trainingPlan(
                List.of("fotr_block"), 1, 1000, 5, 10, 3, 4, 1);

        List<PlannedStage> policyChecks = plan.stream()
                .filter(stage -> stage.name().contains("evaluation policy-change-"))
                .toList();

        assertEquals(6, policyChecks.size());
        assertTrue(policyChecks.stream().allMatch(stage -> stage.total() == 3));
        assertEquals(9, policyChecks.stream()
                .filter(stage -> stage.name().endsWith("candidate-first"))
                .mapToInt(PlannedStage::total)
                .sum());
    }

    @Test
    void trainingGameCountsFollowStarterAndGeneratedDeckSplit() {
        var counts = BotModelTrainingMain.trainingGameCounts(1000, 5);

        assertEquals(1000, counts.totalGames());
        assertEquals(25, counts.versusRandomStarterGames());
        assertEquals(25, counts.versusRandomGeneratedGames());
        assertEquals(238, counts.selfPlaySameStarterGames());
        assertEquals(237, counts.selfPlayDifferentStarterGames());
        assertEquals(475, counts.selfPlayGeneratedGames());
    }

    @Test
    void randomBaselineCanBeDisabledWithoutReducingSelfPlayGames() {
        var counts = BotModelTrainingMain.trainingGameCounts(1000, 0);

        assertEquals(1000, counts.totalGames());
        assertEquals(0, counts.versusRandomStarterGames());
        assertEquals(0, counts.versusRandomGeneratedGames());
        assertEquals(1000, counts.selfPlaySameStarterGames()
                + counts.selfPlayDifferentStarterGames() + counts.selfPlayGeneratedGames());
    }

    @Test
    void promotionConfidenceBoundsRejectMarginalAndMateriallyRegressedResults() {
        assertTrue(BotModelTrainingMain.wilsonLowerBoundPercent(422, 800) < 50.0);
        assertTrue(BotModelTrainingMain.wilsonLowerBoundPercent(460, 800) > 50.0);
        assertTrue(BotModelTrainingMain.wilsonUpperBoundPercent(320, 800) < 45.0);
        assertFalse(BotModelTrainingMain.wilsonUpperBoundPercent(380, 800) < 45.0);
    }

    @Test
    void explorationDecaysFromTheConfiguredStartRateToTheEndRate() {
        assertEquals(0.20, BotModelTrainingMain.explorationRateForGeneration(1, 10, 20, 5), 0.0001);
        assertEquals(0.125, BotModelTrainingMain.explorationRateForGeneration(6, 10, 20, 5), 0.0001);
        assertEquals(0.05, BotModelTrainingMain.explorationRateForGeneration(11, 10, 20, 5), 0.0001);
        assertEquals(0.05, BotModelTrainingMain.explorationRateForGeneration(20, 10, 20, 5), 0.0001);
    }

    @Test
    void policyCandidateSelectionReservesSlotsForBoundedValidationRegressions() {
        List<PolicyCandidate> candidates = List.of(
                new PolicyCandidate("stable-best", 2.0),
                new PolicyCandidate("stable-second", 1.0),
                new PolicyCandidate("stable-third", 0.5),
                new PolicyCandidate("experimental-best", -0.5),
                new PolicyCandidate("experimental-worse", -2.5),
                new PolicyCandidate("too-regressive", -3.1));

        List<PolicyCandidate> selected = BotModelTrainingMain.selectPolicyCandidates(
                candidates, 5, 2, 3.0,
                PolicyCandidate::validationImprovement,
                Comparator.comparingDouble(PolicyCandidate::validationImprovement).reversed()
                        .thenComparing(PolicyCandidate::name));

        assertEquals(List.of(
                "stable-best", "stable-second", "stable-third", "experimental-best", "experimental-worse"),
                selected.stream().map(PolicyCandidate::name).toList());
    }

    @Test
    void evaluationDeckPairsAreDeterministicAndBalancedBySeed() {
        var starterOne = new com.gempukku.lotro.logic.vo.LotroDeck("starter one");
        var starterTwo = new com.gempukku.lotro.logic.vo.LotroDeck("starter two");
        var generatedOne = new com.gempukku.lotro.logic.vo.LotroDeck("generated one");
        var generatedTwo = new com.gempukku.lotro.logic.vo.LotroDeck("generated two");
        var starters = List.of(starterOne, starterTwo);
        var generated = List.of(generatedOne, generatedTwo);

        var first = BotModelTrainingMain.evaluationDeckPairs(starters, generated, 8, 1234L);
        var second = BotModelTrainingMain.evaluationDeckPairs(starters, generated, 8, 1234L);

        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            assertSame(first.get(index).first(), second.get(index).first());
            assertSame(first.get(index).second(), second.get(index).second());
        }
        assertSame(first.get(0).first(), first.get(0).second());
        assertNotSame(first.get(1).first(), first.get(1).second());
        assertTrue(generated.contains(first.get(2).first()));
        assertTrue(generated.contains(first.get(2).second()));
    }

    @Test
    void deckPairModesDoNotMixStarterAndGeneratedDecks() {
        var starterOne = new com.gempukku.lotro.logic.vo.LotroDeck("starter one");
        var starterTwo = new com.gempukku.lotro.logic.vo.LotroDeck("starter two");
        var generatedOne = new com.gempukku.lotro.logic.vo.LotroDeck("generated one");
        var generatedTwo = new com.gempukku.lotro.logic.vo.LotroDeck("generated two");
        var starters = List.of(starterOne, starterTwo);
        var generated = List.of(generatedOne, generatedTwo);

        var sameStarter = BotModelTrainingMain.trainingDeckPairProvider(
                BotModelTrainingMain.TrainingDeckPairMode.SAME_STARTER, starters, generated).get();
        var differentStarters = BotModelTrainingMain.trainingDeckPairProvider(
                BotModelTrainingMain.TrainingDeckPairMode.DIFFERENT_STARTERS, starters, generated).get();
        var generatedPair = BotModelTrainingMain.trainingDeckPairProvider(
                BotModelTrainingMain.TrainingDeckPairMode.GENERATED, starters, generated).get();

        assertSame(sameStarter.first(), sameStarter.second());
        assertNotSame(differentStarters.first(), differentStarters.second());
        assertTrue(generated.contains(generatedPair.first()));
        assertTrue(generated.contains(generatedPair.second()));
    }

    private static int indexOfGeneration(List<PlannedStage> plan, int generation) {
        for (int index = 0; index < plan.size(); index++) {
            if (plan.get(index).generation() == generation)
                return index;
        }
        return -1;
    }

    private record PolicyCandidate(String name, double validationImprovement) {
    }
}
