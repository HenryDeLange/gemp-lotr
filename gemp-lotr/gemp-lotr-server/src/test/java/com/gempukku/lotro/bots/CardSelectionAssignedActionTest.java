package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.semanticaction.CardSelectionAssignedAction;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.NestedAssignment;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class CardSelectionAssignedActionTest {
    private static class Scenario extends VirtualTableScenario {
        Scenario() throws Exception {
            super(new HashMap<>() {{
                put("aragorn", "1_89");
                put("runner1", "1_178");
                put("runner2", "1_178");
            }}, FellowshipSites, FOTRFrodo, RulingRing);
        }
    }

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

    @Test
    public void nestedSkirmishChoiceRecordsItsSinglePendingMinion() throws Exception {
        Scenario scenario = new Scenario();
        var aragorn = scenario.GetFreepsCard("aragorn");
        var runner1 = scenario.GetShadowCard("runner1");
        var runner2 = scenario.GetShadowCard("runner2");
        scenario.MoveCompanionsToTable(aragorn);
        scenario.MoveMinionsToTable(runner1, runner2);
        scenario.StartGame();
        scenario.gameState().getAssignments().add(new NestedAssignment(aragorn, Set.of(runner1, runner2)));

        CardsSelectionDecision decision = new CardsSelectionDecision(1, "Choose next skirmish to resolve",
                List.of(runner1, runner2), 1, 1) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
                throw new UnsupportedOperationException();
            }
        };
        CardSelectionAssignedAction action = new CardSelectionAssignedAction(
                String.valueOf(runner2.getCardId()), decision, scenario.gameState());

        assertEquals(List.of("1_89"), action.getAssignedFellowshipBlueprintIds());
        assertEquals(List.of(0), action.getWoundsOnAssignedFellowship());
        assertEquals(List.of(1), action.getMinionsOnChosen());
        assertEquals(List.of(5), action.getStrengthOfMinionsOnChosen());
        assertEquals(String.valueOf(runner2.getCardId()), action.toDecisionString(decision, scenario.gameState()));

        CardSelectionAssignedAction restored = CardSelectionAssignedAction.fromJson(action.toJson());
        assertEquals(action.getAssignedFellowshipBlueprintIds(), restored.getAssignedFellowshipBlueprintIds());
        assertEquals(action.getWoundsOnAssignedFellowship(), restored.getWoundsOnAssignedFellowship());
        assertEquals(action.getMinionsOnChosen(), restored.getMinionsOnChosen());
    }
}
