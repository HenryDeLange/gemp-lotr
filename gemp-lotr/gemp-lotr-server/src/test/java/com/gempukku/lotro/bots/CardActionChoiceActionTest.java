package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.semanticaction.CardActionChoiceAction;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.AbstractPlayUseCardTrainer;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.timing.Action;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CardActionChoiceActionTest {
    @Test
    public void resolvesVirtualActionBlueprintFromDecisionMetadata() {
        GameState gameState = mock(GameState.class);
        PhysicalCard sourceCard = physicalCard(43, "1_55");
        when(gameState.findCardById(43)).thenReturn(sourceCard);
        when(gameState.getInPlay()).thenReturn(List.of());
        AwaitingDecision decision = cardActionDecision();

        CardActionChoiceAction action = new CardActionChoiceAction("0", decision, gameState);

        assertEquals("1_2", action.getSourceBlueprint());
        assertEquals(List.of("1_55"), action.getNotChosenBlueprints());
        verify(gameState, never()).getBlueprintId(42);
    }

    @Test
    public void nonVirtualDecisionUsesGameStateFallback() {
        GameState gameState = mock(GameState.class);
        when(gameState.getInPlay()).thenReturn(List.of());
        PhysicalCard sourceCard = physicalCard(42, "1_55");
        Action sourceAction = mock(Action.class);
        when(sourceAction.isVirtualCardAction()).thenReturn(false);
        when(sourceAction.getActionAttachedToCard()).thenReturn(sourceCard);
        when(sourceAction.getText(null)).thenReturn("Use in-play card");
        CardActionSelectionDecision decision =
                new CardActionSelectionDecision(null, 1, "Optional responses", List.of(sourceAction)) {
                    @Override
                    public void decisionMade(String result) throws DecisionResultInvalidException {
                    }
                };

        CardActionChoiceAction action = new CardActionChoiceAction("0", decision, gameState);

        assertEquals("inPlay", decision.getDecisionParameters().get("blueprintId")[0]);
        assertNull(action.getSourceBlueprint());
        verify(gameState).findCardById(42);
    }

    @Test
    public void missingInPlaySourceDoesNotCrashAndDuplicateTextsKeepSelectedIndex() {
        GameState gameState = mock(GameState.class);
        when(gameState.getInPlay()).thenReturn(List.of());
        AwaitingDecision decision = new CardActionSelectionDecision(
                1,
                "Optional responses",
                List.of("42", "43"),
                List.of("inPlay", "1_2"),
                List.of("Use card", "Use card")) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
            }
        };

        CardActionChoiceAction action = new CardActionChoiceAction("0", decision, gameState);
        CardActionChoiceAction duplicateTextAction = new CardActionChoiceAction("1", decision, gameState);

        assertNull(action.getSourceBlueprint());
        assertEquals("1", duplicateTextAction.toDecisionString(decision, gameState));
        assertEquals("1", CardActionChoiceAction.fromJson(duplicateTextAction.toJson())
                .toDecisionString(decision, gameState));
        assertEquals(List.of("Use card"), action.getNotChosenActions());
    }

    @Test
    public void cardActionTrainerSkipsMissingInPlaySource() throws Exception {
        GameState gameState = mock(GameState.class);
        when(gameState.getInPlay()).thenReturn(List.of());
        AwaitingDecision decision = new CardActionSelectionDecision(
                1,
                "Optional responses",
                List.of("42"),
                List.of("inPlay"),
                List.of("Play card")) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
            }
        };
        TestCardActionTrainer trainer = new TestCardActionTrainer();
        Classifier<double[]> model = mock(Classifier.class);
        doAnswer(invocation -> {
            double[] probabilities = invocation.getArgument(1);
            probabilities[1] = 0.9;
            return 0;
        }).when(model).predict(any(double[].class), any(double[].class));
        ModelRegistry models = new ModelRegistry();
        models.registerModel(TestCardActionTrainer.class, model);
        RLGameStateFeatures features = (state, awaitingDecision, playerId) ->
                new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];

        String answer = trainer.getAnswer(gameState, decision, "bot", features, models);

        assertEquals("", answer);
        assertEquals(0, trainer.cardFeatureRequests);
    }

    @Test
    public void cardActionTrainerUsesVirtualBlueprintFromDecisionMetadata() throws Exception {
        GameState gameState = mock(GameState.class);
        when(gameState.getInPlay()).thenReturn(List.of());
        AwaitingDecision decision = cardActionDecision();
        TestCardActionTrainer trainer = new TestCardActionTrainer();
        Classifier<double[]> model = mock(Classifier.class);
        AtomicInteger predictions = new AtomicInteger();
        doAnswer(invocation -> {
            double[] probabilities = invocation.getArgument(1);
            probabilities[1] = predictions.getAndIncrement() == 0 ? 0.9 : 0.1;
            return 0;
        }).when(model).predict(any(double[].class), any(double[].class));
        ModelRegistry models = new ModelRegistry();
        models.registerModel(TestCardActionTrainer.class, model);
        RLGameStateFeatures features = (state, awaitingDecision, playerId) ->
                new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];

        String answer = trainer.getAnswer(gameState, decision, "bot", features, models);

        assertEquals("0", answer);
        verify(gameState, never()).getBlueprintId(42);
    }

    @Test
    public void resolvesVirtualBlueprintWhenPassingAllActions() {
        GameState gameState = mock(GameState.class);
        PhysicalCard sourceCard = physicalCard(43, "1_55");
        when(gameState.findCardById(43)).thenReturn(sourceCard);
        AwaitingDecision decision = cardActionDecision();

        CardActionChoiceAction action = new CardActionChoiceAction("", decision, gameState);

        assertEquals(List.of("1_2", "1_55"), action.getNotChosenBlueprints());
        verify(gameState, never()).getBlueprintId(42);
    }

    private static PhysicalCard physicalCard(int cardId, String blueprintId) {
        PhysicalCard card = mock(PhysicalCard.class);
        when(card.getCardId()).thenReturn(cardId);
        when(card.getBlueprintId()).thenReturn(blueprintId);
        return card;
    }

    private static AwaitingDecision cardActionDecision() {
        return new CardActionSelectionDecision(
                1,
                "Optional responses",
                List.of("42", "43"),
                List.of("1_2", "inPlay"),
                List.of("Play discarded card", "Use in-play card")) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
            }
        };
    }

    private static final class TestCardActionTrainer extends AbstractPlayUseCardTrainer {
        private int cardFeatureRequests;

        @Override
        protected String getTextTrigger() {
            return "responses";
        }

        @Override
        protected boolean isPlayTrainer() {
            return true;
        }

        @Override
        protected boolean isUseTrainer() {
            return false;
        }

        @Override
        protected boolean isTransferTrainer() {
            return false;
        }

        @Override
        protected boolean isHealTrainer() {
            return false;
        }

        @Override
        protected double[] getCardFeatures(
                String blueprintId, int wounds, GameState gameState, String physicalId) {
            cardFeatureRequests++;
            return new double[CardStrategyFeatures.FEATURE_COUNT];
        }

        @Override
        protected double[] getPassCardFeatures() {
            return new double[CardStrategyFeatures.FEATURE_COUNT];
        }
    }
}
