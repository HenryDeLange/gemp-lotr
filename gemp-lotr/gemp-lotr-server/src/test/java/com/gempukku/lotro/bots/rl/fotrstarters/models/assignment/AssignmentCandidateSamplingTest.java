package com.gempukku.lotro.bots.rl.fotrstarters.models.assignment;

import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AssignmentCandidateSamplingTest {
    @Test
    public void freePeoplesSearchDistributesItsCandidateLimitAcrossLegalBranches() {
        Map<String, String> minions = cards(5, 1, "minion");
        Map<String, String> freeCharacters = cards(8, 100, "character");
        GameState gameState = mockGameState(5);
        FpAssignmentTrainer trainer = new FpAssignmentTrainer();
        List<AbstractAssignmentTrainer.AssignmentInfo> candidates = new ArrayList<>();

        trainer.generateAssignmentsRecursive(minions, sortedKeys(minions), 0, freeCharacters,
                emptyAssignment(), candidates, Map.of(), gameState);

        assertEquals(10_000, candidates.size());
        for (String freeCharacterId : freeCharacters.keySet()) {
            assertTrue("first minion must be considered for character " + freeCharacterId,
                    candidates.stream().anyMatch(candidate -> candidate.physicalAssignment()
                            .getOrDefault(freeCharacterId, List.of()).contains("1")));
        }
    }

    @Test
    public void shadowSearchDistributesItsCandidateLimitAcrossLegalBranches() {
        Map<String, String> minions = cards(5, 1, "minion");
        Map<String, String> freeCharacters = cards(8, 100, "character");
        ShadowAssignmentTrainer trainer = new ShadowAssignmentTrainer();
        List<AbstractAssignmentTrainer.AssignmentInfo> candidates = new ArrayList<>();

        trainer.generateAssignmentsRecursive(minions, sortedKeys(minions), 0, freeCharacters,
                emptyAssignment(), candidates, Map.of(), null);

        assertEquals(10_000, candidates.size());
        for (String freeCharacterId : freeCharacters.keySet()) {
            assertTrue("first minion must be considered for character " + freeCharacterId,
                    candidates.stream().anyMatch(candidate -> candidate.physicalAssignment()
                            .getOrDefault(freeCharacterId, List.of()).contains("1")));
        }
    }

    private static Map<String, String> cards(int count, int firstId, String prefix) {
        Map<String, String> cards = new HashMap<>();
        for (int i = 0; i < count; i++)
            cards.put(String.valueOf(firstId + i), prefix + (i + 1));
        return cards;
    }

    private static List<String> sortedKeys(Map<String, String> cards) {
        return cards.keySet().stream().sorted(Comparator.comparingInt(Integer::parseInt)).toList();
    }

    private static GameState mockGameState(int minionCount) {
        GameState gameState = mock(GameState.class);
        List<PhysicalCard> inPlay = new ArrayList<>();
        for (int i = 1; i <= minionCount; i++) {
            PhysicalCard card = mock(PhysicalCard.class);
            LotroCardBlueprint blueprint = mock(LotroCardBlueprint.class);
            when(card.getCardId()).thenReturn(i);
            when(card.getBlueprint()).thenReturn(blueprint);
            when(blueprint.getStrength()).thenReturn(5);
            inPlay.add(card);
        }
        doReturn(inPlay).when(gameState).getInPlay();
        return gameState;
    }

    private static AbstractAssignmentTrainer.AssignmentInfo emptyAssignment() {
        return new AbstractAssignmentTrainer.AssignmentInfo(new HashMap<>(), new HashMap<>(), 0, 0);
    }
}
