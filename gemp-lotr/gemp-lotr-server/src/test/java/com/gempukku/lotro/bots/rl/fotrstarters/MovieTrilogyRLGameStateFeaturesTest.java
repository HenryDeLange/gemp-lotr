package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.Assignment;
import com.gempukku.lotro.game.state.GameState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MovieTrilogyRLGameStateFeaturesTest {
    @Test
    public void pendingRingBearerRiskDistinguishesShadowTieDamageAndOverwhelm() {
        assertEquals(0.5, projectedRingBearerRisk(3, 3, 2, 0), 0.000001);
        assertEquals(1.0, projectedRingBearerRisk(3, 3, 2, 1), 0.000001);
        assertEquals(1.0, projectedRingBearerRisk(3, 6, 2, 0), 0.000001);
    }

    private static double projectedRingBearerRisk(
            int ringBearerStrength, int minionStrength, int vitality, int damageBonus) {
        GameState gameState = mock(GameState.class);
        Assignment assignment = mock(Assignment.class);
        PhysicalCard ringBearer = mock(PhysicalCard.class);
        PhysicalCard minion = mock(PhysicalCard.class);
        LotroCardBlueprint ringBearerBlueprint = mock(LotroCardBlueprint.class);
        LotroCardBlueprint minionBlueprint = mock(LotroCardBlueprint.class);

        when(gameState.getAssignments()).thenReturn(List.of(assignment));
        when(gameState.getSkirmish()).thenReturn(null);
        when(gameState.getMostRecentGameStats()).thenReturn(null);
        when(assignment.getFellowshipCharacter()).thenReturn(ringBearer);
        when(assignment.getShadowCharacters()).thenReturn(Set.of(minion));
        when(gameState.getRingBearer("free-peoples")).thenReturn(ringBearer);
        when(gameState.getWounds(ringBearer)).thenReturn(0);
        when(ringBearer.getOwner()).thenReturn("free-peoples");
        when(ringBearer.getCardId()).thenReturn(1);
        when(ringBearer.getBlueprint()).thenReturn(ringBearerBlueprint);
        when(ringBearerBlueprint.getStrength()).thenReturn(ringBearerStrength);
        when(ringBearerBlueprint.getVitality()).thenReturn(vitality);
        when(minion.getBlueprint()).thenReturn(minionBlueprint);
        when(minionBlueprint.getStrength()).thenReturn(minionStrength);
        when(minionBlueprint.getKeywordCount(Keyword.DAMAGE)).thenReturn(damageBonus);

        List<Double> features = new ArrayList<>();
        MovieTrilogyRLGameStateFeatures.appendSkirmishPlanningFeatures(
                features, gameState, "shadow", List.of(), null);

        return features.get(7);
    }
}
