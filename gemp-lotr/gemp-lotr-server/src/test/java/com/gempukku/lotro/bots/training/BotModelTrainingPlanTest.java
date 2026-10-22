package com.gempukku.lotro.bots.training;

import com.gempukku.lotro.bots.training.TrainingProgress.PlannedStage;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotModelTrainingPlanTest {
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
