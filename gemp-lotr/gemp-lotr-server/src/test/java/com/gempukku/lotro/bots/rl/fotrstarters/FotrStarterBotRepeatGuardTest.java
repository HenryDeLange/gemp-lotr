package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericCardActionChoiceTrainer;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FotrStarterBotRepeatGuardTest {
    @Test
    public void repeatHistoryDoesNotLeakAcrossEpisodesOrTurns() {
        Fixture fixture = new Fixture();
        fixture.bot.beginEpisode("first");
        fixture.choose("0", "0", "0");
        fixture.bot.beginEpisode("second");
        fixture.choose("0", "0", "0");
        when(fixture.state.getTurnNumber()).thenReturn(2);
        fixture.choose("0", "0", "0");
        when(fixture.state.getCurrentPhase()).thenReturn(Phase.SHADOW);
        fixture.choose("0", "0", "0", "");
    }

    @Test
    public void changingAnswersStartsANewConsecutiveAnswerStreak() {
        Fixture fixture = new Fixture();
        fixture.choose("0", "0");
        fixture.preferred.set(1);
        fixture.choose("1", "1", "1", "");
    }

    @Test
    public void emergencyPassClearsTheGuardInsteadOfSticking() {
        Fixture fixture = new Fixture();
        for (int i = 0; i < 6; i++) {
            fixture.preferred.set(i % 2);
            fixture.choose(String.valueOf(i % 2));
        }
        fixture.preferred.set(0);
        fixture.choose("");
        fixture.preferred.set(1);
        fixture.choose("1");
    }

    private static final class Fixture {
        private final GameState state = mock(GameState.class);
        private final AtomicInteger preferred = new AtomicInteger();
        private final CardActionSelectionDecision decision = new CardActionSelectionDecision(
                1, "Choose a generic action", List.of("10", "20"), List.of("1_1", "1_2"),
                List.of("Use first action", "Use second action")) {
            @Override
            public void decisionMade(String result) {
            }
        };
        private final FotrStarterBot bot;

        private Fixture() {
            when(state.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
            when(state.getTurnNumber()).thenReturn(1);
            Classifier<double[]> model = new Classifier<>() {
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
                    probabilities[1] = Math.abs(features[1] - preferred.get() / 2.0) < 0.001 ? 0.9 : 0.1;
                    probabilities[0] = 1 - probabilities[1];
                    return probabilities[1] > 0.5 ? 1 : 0;
                }

                @Override
                public int predict(double[] features) {
                    return predict(features, new double[2]);
                }
            };
            ModelRegistry registry = new ModelRegistry();
            registry.registerModel(GenericCardActionChoiceTrainer.class, model);
            bot = new FotrStarterBot((gameState, awaitingDecision, playerId) -> new double[]{0},
                    "~test", registry, null);
        }

        private void choose(String... expectedAnswers) {
            for (String expected : expectedAnswers)
                assertEquals(expected, bot.chooseAction(state, decision));
        }
    }
}
