package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.game.LotroCardBlueprint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CardStrategyFeaturesTest {
    @Test
    public void extractsRulesRelevantCardTags() {
        LotroCardBlueprint card = mock(LotroCardBlueprint.class);
        when(card.getId()).thenReturn("strategy-tag-test-card");
        when(card.getGameText()).thenReturn(
                "Add 2 burdens. Add a threat. Wound a companion. Heal a companion. "
                        + "Exert a companion. Shadow cards cannot heal companions. "
                        + "Control a site. Draw a card, then discard a card. Move again.");
        when(card.hasKeyword(Keyword.ARCHER)).thenReturn(true);

        double[] features = CardStrategyFeatures.extract(card);

        assertEquals(1.0, features[CardStrategyFeatures.BURDEN_ADD], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.THREAT_ADD], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.WOUNDING], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.HEALING], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.EXERTION], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.HEALING_PREVENTION], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.ARCHERY], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.SITE_CONTROL], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.CARD_DRAW], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.CARD_DISCARD], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.MOVE_AGAIN], 0.0);
        assertEquals(1.0, features[CardStrategyFeatures.CARD_CYCLE], 0.0);
    }

    @Test
    public void identifiesMinionRecoveryFromDiscardPile() {
        LotroCardBlueprint card = mock(LotroCardBlueprint.class);
        when(card.getId()).thenReturn("minion-discard-recovery-test-card");
        when(card.getGameText()).thenReturn("Play a minion from your discard pile.");

        double[] features = CardStrategyFeatures.extract(card);

        assertEquals(1.0, features[CardStrategyFeatures.MINION_FROM_DISCARD], 0.0);

        LotroCardBlueprint replacementCard = mock(LotroCardBlueprint.class);
        when(replacementCard.getId()).thenReturn("minion-discard-replacement-test-card");
        when(replacementCard.getGameText()).thenReturn(
                "Replace it with another minion from your discard pile.");

        assertEquals(1.0, CardStrategyFeatures.extract(replacementCard)
                [CardStrategyFeatures.MINION_FROM_DISCARD], 0.0);
    }

    @Test
    public void cardEffectsInteractWithCurrentTacticalContext() {
        double[] state = new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];
        state[MovieTrilogyRLGameStateFeatures.OPPONENT_BURDEN_PRESSURE] = 0.75;
        double[] card = new double[CardStrategyFeatures.FEATURE_COUNT];
        card[CardStrategyFeatures.BURDEN_ADD] = 1.0;

        double[] combined = CardCandidateFeatures.append(state, card);

        assertEquals(0.75, combined[state.length + card.length], 0.0);
    }

    @Test
    public void combatBoostCandidatesAccountForCurrentAndPendingSkirmishRisk() {
        double[] state = new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];
        state[MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_LOSS_RISK] = 1.0;
        state[MovieTrilogyRLGameStateFeatures.PENDING_SELF_LOSS_COUNT] = 0.1;
        state[MovieTrilogyRLGameStateFeatures.PENDING_SELF_OVERWHELM_COUNT] = 0.1;
        state[MovieTrilogyRLGameStateFeatures.SELF_HAND_COMBAT_BOOST_EVENTS] = 2.0 / 6;

        double[] event = new double[CardFeatures.SKIRMISH_PLAY_CARD_BASE_FEATURE_COUNT
                + CardStrategyFeatures.FEATURE_COUNT];
        event[4] = 1.0;
        event[CardFeatures.SKIRMISH_PLAY_CARD_BASE_FEATURE_COUNT + CardStrategyFeatures.COMBAT_BOOST] = 1.0;

        double[] combined = CardCandidateFeatures.append(state, event);
        int interactionOffset = state.length + event.length + 26;

        assertEquals(1.0, combined[interactionOffset], 0.0);
        assertEquals(0.0, combined[interactionOffset + 1], 0.0);
        assertEquals(0.2, combined[interactionOffset + 2], 0.0);
        assertEquals(0.1, combined[interactionOffset + 3], 0.0);
    }

    @Test
    public void minionRecoveryCandidatesInteractWithVisibleDiscardInventory() {
        double[] state = new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];
        state[MovieTrilogyRLGameStateFeatures.SELF_DISCARD_MINION_COUNT] = 0.5;
        state[MovieTrilogyRLGameStateFeatures.OPPONENT_DISCARD_MINION_COUNT] = 0.25;
        double[] card = new double[CardStrategyFeatures.FEATURE_COUNT];
        card[CardStrategyFeatures.MINION_FROM_DISCARD] = 1.0;

        double[] combined = CardCandidateFeatures.append(state, card);
        int interactionOffset = state.length + card.length + 30;

        assertEquals(0.5, combined[interactionOffset], 0.0);
        assertEquals(0.25, combined[interactionOffset + 1], 0.0);
    }
}
