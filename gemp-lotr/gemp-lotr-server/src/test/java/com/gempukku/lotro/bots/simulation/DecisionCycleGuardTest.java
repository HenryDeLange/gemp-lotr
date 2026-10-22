package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotDecisionFallback;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DecisionCycleGuardTest {
    @Test
    public void detectsRepeatedAlternatingActionAndSelectionCycle() {
        DecisionCycleGuard guard = new DecisionCycleGuard();
        String actionMenu = "fp/FELLOWSHIP/CARD_ACTION_CHOICE:Play action or pass";
        String targetB = "fp/FELLOWSHIP/CARD_SELECTION:Choose target|cardId=[B]";
        String targetA = "fp/FELLOWSHIP/CARD_SELECTION:Choose target|cardId=[A]";

        for (int repetition = 0; repetition < 4; repetition++) {
            guard.record(actionMenu, AwaitingDecisionType.CARD_ACTION_CHOICE, "transfer item", "transfer");
            guard.record(targetB, AwaitingDecisionType.CARD_SELECTION, "choose target", "B");
            guard.record(actionMenu, AwaitingDecisionType.CARD_ACTION_CHOICE, "transfer item", "transfer");
            guard.record(targetA, AwaitingDecisionType.CARD_SELECTION, "choose target", "A");
            if (repetition < 3)
                assertFalse(guard.findRepeatedCycle(actionMenu).isPresent());
        }

        Optional<DecisionCycleGuard.RepeatedCycle> cycle = guard.findRepeatedCycle(actionMenu);
        assertTrue(cycle.isPresent());
        assertEquals(4, cycle.orElseThrow().length());
        assertEquals("transfer", cycle.orElseThrow().repeatedAnswer());
    }

    @Test
    public void doesNotDetectNonRepeatingDecisionSequence() {
        DecisionCycleGuard guard = new DecisionCycleGuard();
        guard.record("decision-1", AwaitingDecisionType.CARD_SELECTION, "first", "A");
        guard.record("decision-2", AwaitingDecisionType.CARD_SELECTION, "second", "B");
        guard.record("decision-3", AwaitingDecisionType.CARD_SELECTION, "third", "C");

        assertFalse(guard.findRepeatedCycle("decision-4").isPresent());
    }

    @Test
    public void repeatedCycleWithoutOptionalActionChoiceIsRecognizedAsUnsafeToPass() {
        DecisionCycleGuard guard = new DecisionCycleGuard();
        for (int repetition = 0; repetition < 4; repetition++) {
            guard.record("selection", AwaitingDecisionType.CARD_SELECTION, "select", "A");
            guard.record("integer", AwaitingDecisionType.INTEGER, "choose number", "1");
        }

        Optional<DecisionCycleGuard.RepeatedCycle> cycle = guard.findRepeatedCycle("selection");
        assertTrue(cycle.isPresent());
        assertEquals("A", cycle.orElseThrow().repeatedAnswer());
    }

    @Test
    public void repeatedPromptAndAnswerWithChangingVisibleStateIsNotTreatedAsCycle() {
        DecisionCycleGuard guard = new DecisionCycleGuard();
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.MULTIPLE_CHOICE);
        when(decision.getText()).thenReturn("Would you like to continue discarding?");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "results", new String[]{"Yes", "No"}));

        for (int remainingDeckCards = 5; remainingDeckCards > 0; remainingDeckCards--) {
            String signature = DecisionCycleGuard.signature(
                    "fp", "MANEUVER", decision, "deckCount=" + remainingDeckCards);
            guard.record(signature, AwaitingDecisionType.MULTIPLE_CHOICE,
                    "Would you like to continue discarding? -> Yes", "0");
            assertFalse(guard.findRepeatedCycle(signature).isPresent());
        }
    }

    @Test
    public void prefersDecliningOptionalMultipleChoiceWhenBreakingCycle() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.MULTIPLE_CHOICE);
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "results", new String[]{"Yes", "No"}));

        assertEquals("1", BotDecisionFallback.alternativeAnswer(null, "fp", decision, "0"));
    }

    @Test
    public void selectsAvailableCardActionWhenPassWasRepeated() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_ACTION_CHOICE);
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "actionId", new String[]{"0", "1"}));

        assertEquals("0", BotDecisionFallback.alternativeAnswer(null, "fp", decision, ""));
    }

    @Test
    public void selectsACardWhenEmptyArbitraryCardSelectionWasRepeated() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "cardId", new String[]{"temp0", "temp1"},
                "selectable", new String[]{"true", "true"},
                "min", new String[]{"0"},
                "max", new String[]{"1"}));

        assertEquals("temp0", BotDecisionFallback.alternativeAnswer(null, "fp", decision, ""));
    }

    @Test
    public void returnsNoAlternativeWhenOnlyTheRepeatedArbitraryCardSelectionIsLegal() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "cardId", new String[]{"temp0"},
                "selectable", new String[]{"true"},
                "min", new String[]{"1"},
                "max", new String[]{"1"}));

        assertNull(BotDecisionFallback.alternativeAnswer(null, "fp", decision, "temp0"));
    }

    @Test
    public void defersCycleEscapeToNextActionDecisionWhenCurrentAnswerCannotChange() {
        DecisionCycleGuard guard = new DecisionCycleGuard();
        String passOnlyDecision = "player-two/pass";
        String repeatingActionDecision = "player-one/action";

        for (int repetition = 0; repetition < 4; repetition++) {
            guard.record(passOnlyDecision, AwaitingDecisionType.CARD_ACTION_CHOICE, "pass", "");
            guard.record(repeatingActionDecision, AwaitingDecisionType.CARD_ACTION_CHOICE, "use action", "0");
        }

        DecisionCycleGuard.RepeatedCycle cycle = guard.findRepeatedCycle(passOnlyDecision).orElseThrow();
        DecisionCycleGuard.DeferredEscape escape = cycle.deferredEscape().orElseThrow();
        assertEquals(repeatingActionDecision, escape.signature());
        assertEquals("0", escape.repeatedAnswer());

        guard.clear();
        guard.deferEscape(escape);
        assertTrue(guard.takeDeferredEscape(repeatingActionDecision).isPresent());
        assertFalse(guard.takeDeferredEscape(repeatingActionDecision).isPresent());
    }
}
