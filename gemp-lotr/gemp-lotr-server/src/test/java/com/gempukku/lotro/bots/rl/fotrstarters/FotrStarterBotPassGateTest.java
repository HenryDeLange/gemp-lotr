package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.GenericArbitraryCardsTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.FellowshipCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericCardActionChoiceTrainer;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import org.junit.Before;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FotrStarterBotPassGateTest extends AbstractAtTest {
    @Before
    public void initializeCardFeatures() {
        CardFeatures.init(_cardLibrary);
    }

    @Test
    public void sharedModelCanVetoSpecializedActionWithPass() {
        assertEquals("", choose(true, true, null));
    }

    @Test
    public void disabledGatePreservesSpecializedAction() {
        assertEquals("0", choose(false, true, null));
    }

    @Test
    public void gateDoesNotVetoWhenSharedModelPrefersAnAction() {
        assertEquals("0", choose(true, false, null));
    }

    @Test
    public void missingSharedModelDoesNotVetoSpecializedAction() {
        assertEquals("0", choose(true, null, null));
    }

    @Test
    public void recordsPassVetoForRunMetrics() {
        List<String> eventTypes = new ArrayList<>();
        List<Phase> phases = new ArrayList<>();
        TrainingEventReporter reporter = new TrainingEventReporter() {
            @Override
            public void recordEvent(Severity severity, String type, String model, String message, Throwable failure) {
                eventTypes.add(type);
            }

            @Override
            public void recordPassGateVeto(Phase phase) {
                phases.add(phase);
                TrainingEventReporter.super.recordPassGateVeto(phase);
            }
        };
        assertEquals("", choose(true, true, reporter));
        assertEquals(List.of("generic-action-pass-veto"), eventTypes);
        assertEquals(List.of(Phase.FELLOWSHIP), phases);
    }

    @Test
    public void optionalZeroCardSelectionIsNotConvertedIntoAPassGateVeto() {
        GameState state = mock(GameState.class);
        when(state.getCurrentPlayerId()).thenReturn("~pass-gate-zero-card");
        when(state.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
        when(state.getTurnNumber()).thenReturn(1);
        doReturn(List.of()).when(state).getInPlay();
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(decision.getText()).thenReturn("Choose up to one card");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "cardId", new String[]{"10"},
                "blueprintId", new String[]{"1_299"},
                "selectable", new String[]{"true"},
                "min", new String[]{"0"},
                "max", new String[]{"1"}));

        ModelRegistry models = new ModelRegistry();
        models.registerModel(GenericArbitraryCardsTrainer.class, new FixedProbabilityModel(0.1));
        models.registerModel(GenericCardActionChoiceTrainer.class,
                new PassChoiceModel(true, MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT));
        FotrStarterBot bot = new FotrStarterBot(
                (game, awaiting, player) ->
                        new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT],
                "~pass-gate-zero-card", models, null, null, 0, 0.25, 1, false, true);

        assertEquals("", bot.chooseAction(state, decision));
    }

    private static String choose(
            boolean gateEnabled,
            Boolean sharedModelPrefersPass,
            TrainingEventReporter reporter) {
        GameState state = mock(GameState.class);
        when(state.getCurrentPlayerId()).thenReturn("~pass-gate-test");
        when(state.getCurrentPhase()).thenReturn(com.gempukku.lotro.common.Phase.FELLOWSHIP);
        when(state.getTurnNumber()).thenReturn(1);
        doReturn(List.of()).when(state).getInPlay();
        CardActionSelectionDecision decision = new CardActionSelectionDecision(
                1, "Play Fellowship action or Pass",
                List.of("10"), List.of("1_299"), List.of("Use item")) {
            @Override
            public void decisionMade(String result) {
            }
        };

        ModelRegistry models = new ModelRegistry();
        models.registerModel(FellowshipCardActionAnswerer.FellowshipUseCardTrainer.class,
                new FixedProbabilityModel(0.9));
        int stateLength = MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;
        if (sharedModelPrefersPass != null)
            models.registerModel(GenericCardActionChoiceTrainer.class,
                    new PassChoiceModel(sharedModelPrefersPass, stateLength));
        assertEquals("0", new FellowshipCardActionAnswerer().getAnswer(
                state, decision, "~pass-gate-test",
                (game, awaiting, player) -> new double[stateLength], models));
        FotrStarterBot bot = new FotrStarterBot(
                (game, awaiting, player) -> new double[stateLength],
                "~pass-gate-test", models, null, reporter, 0, 0.25, 1, false, gateEnabled);
        return bot.chooseAction(state, decision);
    }

    private static class FixedProbabilityModel implements Classifier<double[]> {
        private final double positiveProbability;

        private FixedProbabilityModel(double positiveProbability) {
            this.positiveProbability = positiveProbability;
        }

        @Override public int numClasses() { return 2; }
        @Override public int[] classes() { return new int[]{0, 1}; }
        @Override public int predict(double[] features, double[] probabilities) {
            probabilities[0] = 1 - positiveProbability;
            probabilities[1] = positiveProbability;
            return positiveProbability > 0.5 ? 1 : 0;
        }
        @Override public int predict(double[] features) { return positiveProbability > 0.5 ? 1 : 0; }
    }

    private static final class PassChoiceModel implements Classifier<double[]> {
        private final boolean passPreferred;
        private final int stateLength;

        private PassChoiceModel(boolean passPreferred, int stateLength) {
            this.passPreferred = passPreferred;
            this.stateLength = stateLength;
        }

        @Override public int numClasses() { return 2; }
        @Override public int[] classes() { return new int[]{0, 1}; }
        @Override public int predict(double[] features, double[] probabilities) {
            double positiveProbability = (features[stateLength] > 0.5) == passPreferred ? 0.9 : 0.1;
            probabilities[0] = 1 - positiveProbability;
            probabilities[1] = positiveProbability;
            return positiveProbability > 0.5 ? 1 : 0;
        }
        @Override public int predict(double[] features) {
            return (features[stateLength] > 0.5) == passPreferred ? 1 : 0;
        }
    }
}
