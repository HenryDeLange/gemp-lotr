package com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.ChooseFromArbitraryCardsAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StartingFellowshipTrainerTest {
    private static final String PLAYER = "~starting-fellowship-test";
    private static final double[] STATE = new double[48];
    private static final RLGameStateFeatures FEATURES = (gameState, decision, playerId) -> STATE;

    @Test
    public void passesWhenNoLegalStartingCardMeetsTheLearnedThreshold() {
        assertEquals("", answer(0.49, true));
    }

    @Test
    public void choosesOnlySelectableStartingCardsAboveTheThreshold() {
        assertEquals("card-1", answer(0.51, true));
        assertEquals("", answer(0.9, false));
    }

    @Test
    public void trustsTheEngineOfferedOptionsForUnusualStartingCardTypes() {
        ModelRegistry models = new ModelRegistry();
        models.registerModel(TestTrainer.class, new FixedProbabilityModel(0.9));
        AwaitingDecision decision = decision(
                new String[]{"card-1", "card-2"},
                new String[]{"ordinary-companion", "format-enabled-possession"},
                new String[]{"false", "true"});

        assertEquals("card-2", new TestTrainer().getAnswer(
                mock(GameState.class), decision, PLAYER, FEATURES, models));
    }

    @Test
    public void trainingLabelsThePassChoiceAgainstStartingCards() {
        AwaitingDecision decision = decision(true);
        LearningStep passStep = step("", decision, "pass-game");
        LearningStep playStep = step("card-1", decision, "play-game");
        passStep.reward = 1.0;
        playStep.reward = 1.0;

        List<LabeledPoint> points = new TestTrainer().extractTrainingData(List.of(passStep, playStep));

        assertEquals(4, points.size());
        assertEquals(0, points.get(0).y());
        assertEquals(1, points.get(1).y());
        assertEquals(1, points.get(2).y());
        assertEquals(0, points.get(3).y());
        assertEquals(1.0, points.get(0).x()[STATE.length + 2], 0.0);
        assertEquals(0.0, points.get(2).x()[STATE.length + 2], 0.0);
        assertEquals(0.0, points.get(3).x()[STATE.length + 2], 0.0);
        assertTrue(points.stream().allMatch(point -> point.x().length > STATE.length));
    }

    private static String answer(double score, boolean selectable) {
        ModelRegistry models = new ModelRegistry();
        models.registerModel(TestTrainer.class, new FixedProbabilityModel(score));
        return new TestTrainer().getAnswer(
                mock(GameState.class), decision(selectable), PLAYER, FEATURES, models);
    }

    private static LearningStep step(String answer, AwaitingDecision decision, String gameId) {
        return new LearningStep(STATE, new ChooseFromArbitraryCardsAction(answer, decision),
                true, decision, gameId);
    }

    private static AwaitingDecision decision(boolean selectable) {
        return decision(
                new String[]{"card-1"},
                new String[]{"companion"},
                new String[]{Boolean.toString(selectable)});
    }

    private static AwaitingDecision decision(String[] cardIds, String[] blueprintIds, String[] selectable) {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(decision.getText()).thenReturn("Starting fellowship - Choose next character or press DONE");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"0"},
                "max", new String[]{"1"},
                "cardId", cardIds,
                "blueprintId", blueprintIds,
                "selectable", selectable));
        return decision;
    }

    private static final class TestTrainer extends StartingFellowshipTrainer {
        @Override
        protected double[] getCandidateVector(double[] state, String blueprintId) {
            return CardCandidateFeatures.append(state, getCardVector(blueprintId));
        }

        @Override
        protected double[] getCardVector(String blueprintId) {
            double[] features = new double[22 + CardStrategyFeatures.FEATURE_COUNT];
            if (!CardFeatures.PASS.equals(blueprintId)) {
                features[2] = 1.0;
                features[17] = 2.0;
                features[18] = 6.0;
                features[19] = 3.0;
            }
            return features;
        }
    }

    private static final class FixedProbabilityModel implements Classifier<double[]> {
        private final double positiveProbability;

        private FixedProbabilityModel(double positiveProbability) {
            this.positiveProbability = positiveProbability;
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
            probabilities[0] = 1.0 - positiveProbability;
            probabilities[1] = positiveProbability;
            return positiveProbability > 0.5 ? 1 : 0;
        }

        @Override
        public int predict(double[] features) {
            return positiveProbability > 0.5 ? 1 : 0;
        }
    }
}
