package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.semanticaction.CardSelectionAssignedAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class CardSelectionAssignedActionTest {
    @Test
    public void emptySelectionIsValidWhenNothingWasChosen() {
        CardSelectionAssignedAction action = new CardSelectionAssignedAction(
                "", mock(AwaitingDecision.class), mock(GameState.class));

        assertTrue(action.getChosenBlueprintIds().isEmpty());
        assertTrue(action.getMinionsOnChosen().isEmpty());
        assertTrue(action.getStrengthOfMinionsOnChosen().isEmpty());

        CardSelectionAssignedAction restored = CardSelectionAssignedAction.fromJson(action.toJson());
        assertTrue(restored.getChosenBlueprintIds().isEmpty());
        assertTrue(restored.getMinionsOnChosen().isEmpty());
    }
}
