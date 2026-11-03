package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.game.LotroCardBlueprint;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CardFeaturesAssignmentOutcomeTest extends AbstractAtTest {
    private static final String RING_BEARER = "1_89";
    private static final String DAMAGE_MINION = "1_151";

    @Test
    public void assignmentFeaturesDistinguishFatalDamageLossAndRingBearerExposure() throws Exception {
        CardFeatures.init(_cardLibrary);
        LotroCardBlueprint ringBearer = _cardLibrary.getLotroCardBlueprint(RING_BEARER);
        LotroCardBlueprint minion = _cardLibrary.getLotroCardBlueprint(DAMAGE_MINION);
        int wounds = Math.max(0, ringBearer.getVitality() - 2);
        List<String> assignedMinions = List.of(DAMAGE_MINION, DAMAGE_MINION);
        assertTrue(2 * minion.getStrength() >= ringBearer.getStrength());
        assertTrue(2 * minion.getStrength() < 2 * ringBearer.getStrength());

        double[] features = CardFeatures.getAssignmentFeatures(
                Map.of(RING_BEARER, assignedMinions), Map.of(RING_BEARER, wounds),
                0, 0, RING_BEARER);
        int outcomeOffset = features.length - CardFeatures.ASSIGNMENT_OUTCOME_FEATURE_COUNT;

        assertEquals(1.0 / 13, features[outcomeOffset + CardFeatures.ASSIGNMENT_MATCHUP_COUNT], 0.0);
        assertEquals(1.0 / 13, features[outcomeOffset + CardFeatures.ASSIGNMENT_SHADOW_WIN_COUNT], 0.0);
        assertEquals(0.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_OVERWHELM_COUNT], 0.0);
        assertEquals(1.0 / 13, features[outcomeOffset + CardFeatures.ASSIGNMENT_PROJECTED_DEATH_COUNT], 0.0);
        assertEquals(2.0 / 24, features[outcomeOffset + CardFeatures.ASSIGNMENT_DAMAGE_BONUS], 0.0);
        assertEquals(1.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_ASSIGNED], 0.0);
        assertEquals(1.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_LOSES], 0.0);
        assertEquals(0.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_OVERWHELMED], 0.0);
        assertEquals(1.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_FATAL], 0.0);
    }

    @Test
    public void aShadowTieWinsAndAppliesItsDamageBonus() throws Exception {
        CardFeatures.init(_cardLibrary);

        double[] features = CardFeatures.getAssignmentFeatures(
                Map.of(RING_BEARER, List.of("1_146")), Map.of(RING_BEARER, 0),
                0, 0, RING_BEARER);
        int outcomeOffset = features.length - CardFeatures.ASSIGNMENT_OUTCOME_FEATURE_COUNT;

        assertEquals(1.0 / 13, features[outcomeOffset + CardFeatures.ASSIGNMENT_SHADOW_WIN_COUNT], 0.0);
        assertEquals(0.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_OVERWHELM_COUNT], 0.0);
        assertEquals(1.0 / 24, features[outcomeOffset + CardFeatures.ASSIGNMENT_DAMAGE_BONUS], 0.0);
        assertEquals(1.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_LOSES], 0.0);
    }

    @Test
    public void damageBonusIsNotCountedWhenShadowLosesTheSkirmish() throws Exception {
        CardFeatures.init(_cardLibrary);

        double[] features = CardFeatures.getAssignmentFeatures(
                Map.of(RING_BEARER, List.of(DAMAGE_MINION)), Map.of(RING_BEARER, 0),
                0, 0, RING_BEARER);
        int outcomeOffset = features.length - CardFeatures.ASSIGNMENT_OUTCOME_FEATURE_COUNT;

        assertEquals(0.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_SHADOW_WIN_COUNT], 0.0);
        assertEquals(0.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_DAMAGE_BONUS], 0.0);
        assertEquals(0.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_FATAL], 0.0);
    }

    @Test
    public void assignmentFeaturesIdentifyRingBearerOverwhelm() throws Exception {
        CardFeatures.init(_cardLibrary);
        LotroCardBlueprint ringBearer = _cardLibrary.getLotroCardBlueprint(RING_BEARER);
        LotroCardBlueprint minion = _cardLibrary.getLotroCardBlueprint(DAMAGE_MINION);
        int minionCount = (2 * ringBearer.getStrength() + minion.getStrength() - 1) / minion.getStrength();
        List<String> assignedMinions = java.util.Collections.nCopies(minionCount, DAMAGE_MINION);

        double[] features = CardFeatures.getAssignmentFeatures(
                Map.of(RING_BEARER, assignedMinions), Map.of(RING_BEARER, 0),
                0, 0, RING_BEARER);
        int outcomeOffset = features.length - CardFeatures.ASSIGNMENT_OUTCOME_FEATURE_COUNT;

        assertEquals(1.0, features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_OVERWHELMED], 0.0);
        assertEquals(ringBearer.getKeywordCount(com.gempukku.lotro.common.Keyword.ENDURING) > 0 ? 0.0 : 1.0,
                features[outcomeOffset + CardFeatures.ASSIGNMENT_RING_BEARER_FATAL], 0.0);
    }
}
