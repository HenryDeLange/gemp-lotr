package com.gempukku.lotro.bots.rl.semanticaction;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AssignMinionsActionTest {
    @Test
    public void ringBearerContextSurvivesActionSerialization() {
        GameState gameState = mock(GameState.class);
        PhysicalCard ringBearer = mock(PhysicalCard.class);
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(gameState.getPlayerNames()).thenReturn(List.of("free-peoples", "shadow"));
        when(gameState.getRingBearer("free-peoples")).thenReturn(ringBearer);
        when(gameState.getAssignments()).thenReturn(List.of());
        when(gameState.getInPlay()).thenReturn(List.of());
        when(ringBearer.getBlueprintId()).thenReturn("1_89");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "freeCharacters", new String[0],
                "minions", new String[0]));

        AssignMinionsAction action = new AssignMinionsAction("", decision, gameState, true);

        assertEquals("1_89", action.getRingBearerBlueprintId());
        assertEquals("1_89", AssignMinionsAction.fromJson(action.toJson()).getRingBearerBlueprintId());
    }

    @Test
    public void legacyActionWithoutRingBearerContextRemainsReadable() {
        GameState gameState = mock(GameState.class);
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(gameState.getPlayerNames()).thenReturn(List.of());
        when(gameState.getAssignments()).thenReturn(List.of());
        when(gameState.getInPlay()).thenReturn(List.of());
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "freeCharacters", new String[0],
                "minions", new String[0]));
        var legacyActionJson = new AssignMinionsAction("", decision, gameState, true).toJson();
        legacyActionJson.remove("ringBearerBlueprintId");

        assertNull(AssignMinionsAction.fromJson(legacyActionJson).getRingBearerBlueprintId());
    }
}
