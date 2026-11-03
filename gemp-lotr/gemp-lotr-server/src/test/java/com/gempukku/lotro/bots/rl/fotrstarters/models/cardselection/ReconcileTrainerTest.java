package com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.CardSelectionAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReconcileTrainerTest {
    private String previousOptionalPassExperiment;

    private static final String PLAYER = "~reconcile-test";
    private static final double[] STATE =
            new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];
    private static final RLGameStateFeatures FEATURES = (gameState, decision, playerId) -> STATE;

    @Before
    public void enableOptionalPassExperiment() {
        previousOptionalPassExperiment = System.getProperty(ReconcileTrainer.OPTIONAL_PASS_EXPERIMENT_PROPERTY);
        System.setProperty(ReconcileTrainer.OPTIONAL_PASS_EXPERIMENT_PROPERTY, "true");
    }

    @After
    public void restoreOptionalPassExperiment() {
        if (previousOptionalPassExperiment == null)
            System.clearProperty(ReconcileTrainer.OPTIONAL_PASS_EXPERIMENT_PROPERTY);
        else
            System.setProperty(ReconcileTrainer.OPTIONAL_PASS_EXPERIMENT_PROPERTY, previousOptionalPassExperiment);
    }

    @Test
    public void optionalCyclingIsChosenOnlyWhenTheCardScoresAbovePass() {
        assertEquals("", answer(0.4, 0.6, 0));
        assertEquals("10", answer(0.6, 0.4, 0));
        assertEquals("", answer(0.5, 0.5, 0));
    }

    @Test
    public void mandatoryReconciliationStillMeetsItsMinimumWhenPassScoresHigher() {
        assertEquals("10", answer(0.1, 0.9, 1));
    }

    @Test
    public void passScoringIsDisabledUntilTheModelIsRetrained() {
        System.clearProperty(ReconcileTrainer.OPTIONAL_PASS_EXPERIMENT_PROPERTY);

        assertEquals("10", answer(0.1, 0.9, 0));
    }

    @Test
    public void trainingLabelsTheOptionalPassAgainstSuccessfulReconciliationChoices() {
        AwaitingDecision decision = decision(0);
        LearningStep passStep = step("", decision, "pass-game");
        LearningStep discardStep = step("card", decision, "discard-game");
        passStep.reward = 1.0;
        discardStep.reward = 1.0;

        List<LabeledPoint> points = new TestTrainer().extractTrainingData(List.of(passStep, discardStep));

        assertEquals(4, points.size());
        assertEquals(0, points.get(0).y());
        assertEquals(1, points.get(1).y());
        assertEquals(1, points.get(2).y());
        assertEquals(0, points.get(3).y());
        assertEquals(1.0, points.get(0).x()[STATE.length], 0.0);
        assertEquals(0.0, points.get(1).x()[STATE.length], 0.0);
        assertEquals(1.0, points.get(2).x()[STATE.length], 0.0);
        assertEquals(0.0, points.get(3).x()[STATE.length], 0.0);
    }

    @Test
    public void failedOptionalPassIsLabeledAsARejectedChoice() {
        AwaitingDecision decision = decision(0);
        LearningStep passStep = step("", decision, "failed-pass-game");

        List<LabeledPoint> points = new TestTrainer().extractTrainingData(List.of(passStep));

        assertEquals(1, points.size());
        assertEquals(0, points.getFirst().y());
        assertEquals(0.0, points.getFirst().x()[STATE.length], 0.0);
    }

    private static String answer(double cardScore, double passScore, int minimum) {
        GameState gameState = mock(GameState.class);
        when(gameState.getBlueprintId(10)).thenReturn("card");
        doReturn(List.of()).when(gameState).getInPlay();
        ModelRegistry models = new ModelRegistry();
        models.registerModel(TestTrainer.class, new CandidateProbabilityModel(cardScore, passScore));
        return new TestTrainer().getAnswer(
                gameState, decision(minimum), PLAYER, FEATURES, models);
    }

    private static LearningStep step(String selected, AwaitingDecision decision, String gameId) {
        String[] chosen = selected.isEmpty() ? new String[0] : new String[]{selected};
        String[] notChosen = selected.isEmpty() ? new String[]{"card"} : new String[0];
        Integer[] chosenWounds = selected.isEmpty() ? new Integer[0] : new Integer[]{0};
        Integer[] notChosenWounds = selected.isEmpty() ? new Integer[]{0} : new Integer[0];
        CardSelectionAction action = new CardSelectionAction(
                chosen, notChosen, chosenWounds, notChosenWounds, "Hand");
        return new LearningStep(STATE, action, true, decision, gameId);
    }

    private static AwaitingDecision decision(int minimum) {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_SELECTION);
        when(decision.getText()).thenReturn("Reconcile - choose card to discard or press DONE");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{Integer.toString(minimum)},
                "max", new String[]{"1"},
                "cardId", new String[]{"10"}));
        return decision;
    }

    private static final class TestTrainer extends ReconcileTrainer {
        @Override
        protected double[] getCardVector(String blueprintId, int wounds) {
            double[] features = new double[22 + CardStrategyFeatures.FEATURE_COUNT];
            features[0] = CardFeatures.PASS.equals(blueprintId) ? 0.0 : 1.0;
            return features;
        }
    }

    private static final class CandidateProbabilityModel implements Classifier<double[]> {
        private final double cardScore;
        private final double passScore;

        private CandidateProbabilityModel(double cardScore, double passScore) {
            this.cardScore = cardScore;
            this.passScore = passScore;
        }

        @Override
        public int numClasses() {
            return 2;
        }

        @Override
        public int[] classes() {
            return new int[]{0, 1};
        }

        @Override
        public int predict(double[] features, double[] probabilities) {
            double score = features[STATE.length] > 0.5 ? cardScore : passScore;
            probabilities[0] = 1.0 - score;
            probabilities[1] = score;
            return score > 0.5 ? 1 : 0;
        }

        @Override
        public int predict(double[] features) {
            return features[STATE.length] > 0.5 ? (cardScore > 0.5 ? 1 : 0) : (passScore > 0.5 ? 1 : 0);
        }
    }
}
