package com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice;

import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IntegerChoiceTrainerTest {
    @Test
    public void burdenTrainerSamplesTheActualModelClassLabels() {
        BurdenTrainer trainer = new BurdenTrainer();
        Classifier<double[]> model = mock(Classifier.class);
        when(model.classes()).thenReturn(new int[]{0, 5});
        doAnswer(invocation -> {
            double[] probabilities = invocation.getArgument(1);
            probabilities[0] = 0.9;
            probabilities[1] = 0.1;
            return 5;
        }).when(model).predict(any(double[].class), any(double[].class));

        ModelRegistry registry = new ModelRegistry();
        registry.registerModel(BurdenTrainer.class, model);
        GameState gameState = mock(GameState.class);
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getText()).thenReturn("Choose a number of burdens to bid");
        when(decision.getDecisionParameters()).thenReturn(Map.of("min", new String[]{"5"}));
        RLGameStateFeatures features = mock(RLGameStateFeatures.class);
        when(features.extractFeatures(gameState, decision, "bot")).thenReturn(new double[]{1.0});

        assertEquals("5", trainer.getAnswer(gameState, decision, "bot", features, registry));
    }

    @Test
    public void burdenBidCapIsAppliedWithoutDroppingTheEngineMinimum() {
        BurdenTrainer trainer = new BurdenTrainer(1);
        Classifier<double[]> model = mock(Classifier.class);
        when(model.classes()).thenReturn(new int[]{0, 1, 5});
        doAnswer(invocation -> {
            double[] probabilities = invocation.getArgument(1);
            probabilities[0] = 0.0;
            probabilities[1] = 0.1;
            probabilities[2] = 0.9;
            return 5;
        }).when(model).predict(any(double[].class), any(double[].class));

        ModelRegistry registry = new ModelRegistry();
        registry.registerModel(BurdenTrainer.class, model);
        GameState gameState = mock(GameState.class);
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getText()).thenReturn("Choose a number of burdens to bid");
        when(decision.getDecisionParameters()).thenReturn(Map.of("min", new String[]{"0"}));
        RLGameStateFeatures features = mock(RLGameStateFeatures.class);
        when(features.extractFeatures(gameState, decision, "bot")).thenReturn(new double[]{1.0});

        assertEquals("1", trainer.getAnswer(gameState, decision, "bot", features, registry));

        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"5"}, "max", new String[]{"9"}));
        assertEquals("5", trainer.getAnswer(gameState, decision, "bot", features, registry));
    }

    @Test
    public void burdenBidCapAlsoConstrainsFallbackAnswers() {
        BurdenTrainer trainer = new BurdenTrainer(1);
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getText()).thenReturn("Choose a number of burdens to bid");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"0"}, "max", new String[]{"9"}));

        assertEquals("1", trainer.applyMaximumBid("8", decision));

        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"5"}, "max", new String[]{"9"}));
        assertEquals("5", trainer.applyMaximumBid("8", decision));
    }

    @Test
    public void bidCapDiagnosticsUseOneSharedQuantileForTheUncappedChoice() {
        TestBurdenTrainer trainer = new TestBurdenTrainer();

        var sample = trainer.sample(
                new int[]{0, 1, 5}, new double[]{0.0, 0.1, 0.9}, 0, 1, 9, 0.5);

        assertEquals(1, sample.selectedValue());
        assertEquals(5, sample.alternativeValue());
    }

    @Test
    public void engineMinimumAboveBidCapIsReportedSeparatelyFromAConstrainedBid() {
        BurdenTrainer trainer = new BurdenTrainer(1);
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getText()).thenReturn("Choose a number of burdens to bid");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"5"}, "max", new String[]{"9"}));

        assertEquals(true, trainer.engineMinimumExceedsMaximumBid(decision));
        assertEquals("5", trainer.applyMaximumBid("8", decision));
    }

    @Test(expected = IllegalArgumentException.class)
    public void burdenBidCapRejectsValuesOutsideTheRulesRange() {
        new BurdenTrainer(10);
    }

    private static final class TestBurdenTrainer extends BurdenTrainer {
        private IntegerChoiceSample sample(
                int[] classes, double[] probabilities, int minimum, int maximum,
                int alternativeMaximum, double quantile) {
            return sampleClassWithAlternativeMaximum(
                    classes, probabilities, minimum, maximum, alternativeMaximum, quantile);
        }
    }
}
