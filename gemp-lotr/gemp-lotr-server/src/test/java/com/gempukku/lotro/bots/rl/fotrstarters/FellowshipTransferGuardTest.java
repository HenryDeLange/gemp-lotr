package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.ReplayBuffer;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericCardActionChoiceTrainer;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FellowshipTransferGuardTest {
    @Test
    public void limitsOnlyACompletedVoluntaryTransferAndMapsOtherOptions() {
        Fixture f = new Fixture();
        f.guard.observe(f.state, f.actions);
        assertFalse(f.guard.prepare(f.state, f.actions).filtered());
        f.guard.selected(f.state, f.actions, "0");
        f.guard.observe(f.state, f.targetChoice);
        assertFalse(f.guard.prepare(f.state, f.actions).filtered());

        when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
        f.guard.observe(f.state, f.actions);
        var prepared = f.guard.prepare(f.state, f.actions);
        assertTrue(prepared.filtered());
        assertArrayEquals(new String[]{"Use item", "Transfer some other card"},
                prepared.decision().getDecisionParameters().get("actionText"));
        assertEquals("1", prepared.toOriginalAnswer("0"));
        assertEquals("2", prepared.toOriginalAnswer("1"));
        assertEquals("", prepared.toOriginalAnswer(""));

        when(f.state.getTurnNumber()).thenReturn(2);
        f.guard.observe(f.state, f.actions);
        assertFalse(f.guard.prepare(f.state, f.actions).filtered());
    }

    @Test
    public void unfinishedTransferAndNonFellowshipChoicesAreNotBlocked() {
        Fixture f = new Fixture();
        f.guard.observe(f.state, f.actions);
        f.guard.selected(f.state, f.actions, "0");
        f.guard.observe(f.state, f.actions);
        assertFalse(f.guard.prepare(f.state, f.actions).filtered());
        f.guard.selected(f.state, f.actions, "0");
        when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
        f.guard.observe(f.state, f.actions);
        when(f.state.getCurrentPhase()).thenReturn(Phase.SHADOW);
        f.guard.observe(f.state, f.actions);
        assertFalse(f.guard.prepare(f.state, f.actions).filtered());
        when(f.state.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
        f.guard.observe(f.state, f.actions);
        assertFalse(f.guard.prepare(f.state, f.actions).filtered());
    }

    @Test
    public void cardEffectMovementIsNotAttributedToTheVoluntaryTransferGuard() {
        Fixture f = new Fixture();
        CardActionSelectionDecision cardEffect = menu(
                List.of("10"), List.of("1_1"), List.of("Move item using a card effect"));

        f.guard.observe(f.state, cardEffect);
        f.guard.selected(f.state, cardEffect, "0");
        when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
        f.guard.observe(f.state, f.actions);

        assertFalse(f.guard.prepare(f.state, f.actions).filtered());
    }

    @Test
    public void artifactVoluntaryTransfersAreAttributedLikePossessions() {
        Fixture f = new Fixture();
        when(f.blueprint.getCardType()).thenReturn(CardType.ARTIFACT);

        f.guard.observe(f.state, f.actions);
        f.guard.selected(f.state, f.actions, "0");
        when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
        f.guard.observe(f.state, f.actions);

        assertTrue(f.guard.prepare(f.state, f.actions).filtered());
    }

    @Test
    public void botPreservesFirstTransferAndDoesNotTrainOnConstrainedChoices() {
        Fixture f = new Fixture();
        ModelRegistry models = new ModelRegistry();
        models.registerModel(GenericCardActionChoiceTrainer.class, new FirstChoiceModel());
        ReplayBuffer buffer = new ReplayBuffer(100);
        FotrStarterBot bot = new FotrStarterBot((state, decision, player) -> new double[]{0},
                "~test", models, buffer, null, 0, 0.25, 1, true);
        CardActionSelectionDecision transferOnly = menu(
                List.of("10"), List.of("1_1"), List.of("Transfer Test item"));

        bot.beginEpisode("game");
        assertEquals("0", bot.chooseAction(f.state, transferOnly));
        when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
        assertEquals("", bot.chooseAction(f.state, transferOnly));
        bot.discardLastDecision();
        bot.endEpisode(10);
        assertEquals("The guard-constrained choice must not remove or add training examples", 1, buffer.size());
    }

    @Test
    public void disabledGuardAllowsASecondTransfer() {
        Fixture f = new Fixture();
        ModelRegistry models = new ModelRegistry();
        models.registerModel(GenericCardActionChoiceTrainer.class, new FirstChoiceModel());
        FotrStarterBot bot = new FotrStarterBot((state, decision, player) -> new double[]{0},
                "~test", models, null, null, 0, 0.25, 1, false);
        CardActionSelectionDecision transferOnly = menu(
                List.of("10"), List.of("1_1"), List.of("Transfer Test item"));

        assertEquals("0", bot.chooseAction(f.state, transferOnly));
        when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
        assertEquals("0", bot.chooseAction(f.state, transferOnly));
    }

    private static CardActionSelectionDecision menu(
            List<String> cards, List<String> blueprints, List<String> texts) {
        return new CardActionSelectionDecision(1, "Play Fellowship action or Pass", cards, blueprints, texts) {
            @Override
            public void decisionMade(String result) {
            }
        };
    }

    private static final class Fixture {
        private final GameState state = mock(GameState.class);
        private final PhysicalCard item = mock(PhysicalCard.class);
        private final PhysicalCard firstBearer = mock(PhysicalCard.class);
        private final PhysicalCard secondBearer = mock(PhysicalCard.class);
        private final LotroCardBlueprint blueprint = mock(LotroCardBlueprint.class);
        private final AwaitingDecision targetChoice = mock(AwaitingDecision.class);
        private final FellowshipTransferGuard guard = new FellowshipTransferGuard("~test", null);
        private final CardActionSelectionDecision actions = menu(
                List.of("10", "10", "10"), List.of("1_1", "1_1", "1_1"),
                List.of("Transfer Test item", "Use item", "Transfer some other card"));

        private Fixture() {
            when(state.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
            when(state.getTurnNumber()).thenReturn(1);
            when(state.getCurrentPlayerId()).thenReturn("~test");
            when(state.findCardById(10)).thenReturn(item);
            doReturn(List.of(item)).when(state).getInPlay();
            when(item.getCardId()).thenReturn(10);
            when(item.getOwner()).thenReturn("~test");
            when(item.getBlueprint()).thenReturn(blueprint);
            when(item.getBlueprintId()).thenReturn("1_1");
            when(item.getAttachedTo()).thenReturn(firstBearer);
            when(firstBearer.getCardId()).thenReturn(20);
            when(firstBearer.getBlueprintId()).thenReturn("1_2");
            when(secondBearer.getCardId()).thenReturn(30);
            when(secondBearer.getBlueprintId()).thenReturn("1_3");
            when(blueprint.getCardType()).thenReturn(CardType.POSSESSION);
            when(blueprint.getFullName()).thenReturn("Test item");
            when(targetChoice.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_SELECTION);
        }

        @Test
        public void recordsAnAttachmentReturningToAPreviouslyUsedBearerWithoutCardIdentity() {
            Fixture f = new Fixture();
            List<String> eventMessages = new ArrayList<>();
            TrainingEventReporter reporter = new TrainingEventReporter() {
                @Override
                public void recordEvent(Severity severity, String type, String model, String message, Throwable failure) {
                    if (type.equals("fellowship-transfer-reversal"))
                        eventMessages.add(message);
                }
            };
            FellowshipTransferGuard guard = new FellowshipTransferGuard("~test", reporter);

            guard.observe(f.state, f.actions);
            guard.selected(f.state, f.actions, "0");
            when(f.item.getAttachedTo()).thenReturn(f.secondBearer);
            guard.observe(f.state, f.actions);
            guard.selected(f.state, f.actions, "0");
            when(f.item.getAttachedTo()).thenReturn(f.firstBearer);
            guard.observe(f.state, f.actions);

            assertEquals(1, eventMessages.size());
            assertFalse(eventMessages.getFirst().contains("10"));
        }
    }

    private static final class FirstChoiceModel implements Classifier<double[]> {
        @Override public int numClasses() { return 2; }
        @Override public int[] classes() { return new int[]{0, 1}; }
        @Override public int predict(double[] features, double[] probabilities) {
            probabilities[0] = 0.1;
            probabilities[1] = 0.9;
            return 1;
        }
        @Override public int predict(double[] features) { return 1; }
    }
}
