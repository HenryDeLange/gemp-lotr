package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;

import java.util.Arrays;

public final class CardCandidateFeatures {
    public static final int INTERACTION_FEATURE_COUNT = 32;

    private CardCandidateFeatures() {
    }

    public static double[] append(double[] state, double[] cardFeatures) {
        if (state.length < MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT
                || cardFeatures.length < CardStrategyFeatures.FEATURE_COUNT) {
            throw new IllegalArgumentException("Card candidate features require tactical state and card strategy features");
        }

        int contextOffset = state.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;
        int strategyOffset = cardFeatures.length - CardStrategyFeatures.FEATURE_COUNT;
        double[] context = Arrays.copyOfRange(state, contextOffset, state.length);
        double[] strategies = Arrays.copyOfRange(cardFeatures, strategyOffset, cardFeatures.length);
        double[] result = Arrays.copyOf(state, state.length + cardFeatures.length + INTERACTION_FEATURE_COUNT);
        System.arraycopy(cardFeatures, 0, result, state.length, cardFeatures.length);

        int offset = state.length + cardFeatures.length;
        result[offset++] = strategies[CardStrategyFeatures.BURDEN_ADD]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_BURDEN_PRESSURE];
        result[offset++] = strategies[CardStrategyFeatures.BURDEN_ADD]
                * context[MovieTrilogyRLGameStateFeatures.SELF_BURDEN_PRESSURE];
        result[offset++] = strategies[CardStrategyFeatures.BURDEN_REMOVE]
                * context[MovieTrilogyRLGameStateFeatures.SELF_BURDEN_PRESSURE];
        result[offset++] = strategies[CardStrategyFeatures.THREAT_ADD]
                * context[MovieTrilogyRLGameStateFeatures.SELF_THREAT_COUNT];
        result[offset++] = strategies[CardStrategyFeatures.THREAT_ADD]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_THREAT_COUNT];
        result[offset++] = strategies[CardStrategyFeatures.THREAT_REMOVE]
                * context[MovieTrilogyRLGameStateFeatures.SELF_THREAT_COUNT];
        result[offset++] = strategies[CardStrategyFeatures.WOUNDING]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_COMPANION_COUNT];
        result[offset++] = strategies[CardStrategyFeatures.WOUNDING]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_COMPANION_WOUND_RATIO];
        result[offset++] = strategies[CardStrategyFeatures.HEALING]
                * context[MovieTrilogyRLGameStateFeatures.SELF_COMPANION_WOUND_RATIO];
        result[offset++] = strategies[CardStrategyFeatures.WOUND_PREVENTION]
                * (1.0 - context[MovieTrilogyRLGameStateFeatures.SELF_RING_BEARER_HEALTH]);
        result[offset++] = strategies[CardStrategyFeatures.COMBAT_BOOST]
                * context[MovieTrilogyRLGameStateFeatures.SKIRMISH_PRESSURE];
        result[offset++] = strategies[CardStrategyFeatures.ARCHERY]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_COMPANION_COUNT];
        result[offset++] = strategies[CardStrategyFeatures.SITE_CONTROL]
                * (1.0 - context[MovieTrilogyRLGameStateFeatures.OPPONENT_CONTROLLED_SITES]);
        result[offset++] = strategies[CardStrategyFeatures.CARD_DRAW]
                * context[MovieTrilogyRLGameStateFeatures.SELF_HAND_DEFICIT];
        result[offset++] = strategies[CardStrategyFeatures.CARD_DISCARD]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_HAND_SIZE];
        result[offset++] = strategies[CardStrategyFeatures.TWILIGHT_ADD]
                * context[MovieTrilogyRLGameStateFeatures.TWILIGHT_POOL];
        result[offset++] = strategies[CardStrategyFeatures.TWILIGHT_REMOVE]
                * context[MovieTrilogyRLGameStateFeatures.TWILIGHT_POOL];
        result[offset++] = strategies[CardStrategyFeatures.MOVE_AGAIN]
                * context[MovieTrilogyRLGameStateFeatures.SELF_SITE_PROGRESS]
                * context[MovieTrilogyRLGameStateFeatures.SELF_RING_BEARER_HEALTH];
        result[offset++] = strategies[CardStrategyFeatures.RING_BEARER]
                * Math.max(context[MovieTrilogyRLGameStateFeatures.SELF_BURDEN_PRESSURE],
                        context[MovieTrilogyRLGameStateFeatures.OPPONENT_BURDEN_PRESSURE]);
        result[offset++] = strategies[CardStrategyFeatures.DECK_SEARCH]
                * context[MovieTrilogyRLGameStateFeatures.SELF_DECK_REMAINING];
        result[offset++] = strategies[CardStrategyFeatures.CARD_CYCLE]
                * context[MovieTrilogyRLGameStateFeatures.SELF_HAND_DEFICIT];
        result[offset++] = strategies[CardStrategyFeatures.COMBAT_BOOST]
                * context[MovieTrilogyRLGameStateFeatures.SELF_COMPANION_STRENGTH];
        result[offset++] = strategies[CardStrategyFeatures.COMBAT_BOOST]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_COMPANION_STRENGTH];
        result[offset++] = strategies[CardStrategyFeatures.EXERTION]
                * context[MovieTrilogyRLGameStateFeatures.SELF_COMPANION_WOUND_RATIO];
        result[offset++] = strategies[CardStrategyFeatures.EXERTION]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_COMPANION_WOUND_RATIO];
        result[offset++] = strategies[CardStrategyFeatures.HEALING_PREVENTION]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_COMPANION_WOUND_RATIO];

        double combatBoost = strategies[CardStrategyFeatures.COMBAT_BOOST];
        double pendingRisk = Math.min(1.0,
                context[MovieTrilogyRLGameStateFeatures.PENDING_SELF_LOSS_COUNT]
                        + context[MovieTrilogyRLGameStateFeatures.PENDING_SELF_OVERWHELM_COUNT]
                        + context[MovieTrilogyRLGameStateFeatures.PENDING_RING_BEARER_DANGER]);
        boolean skirmishPlayCard = cardFeatures.length
                == CardFeatures.SKIRMISH_PLAY_CARD_BASE_FEATURE_COUNT + CardStrategyFeatures.FEATURE_COUNT;
        double eventCombatBoost = skirmishPlayCard && cardFeatures[4] > 0 ? combatBoost : 0.0;

        result[offset++] = combatBoost
                * context[MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_LOSS_RISK];
        result[offset++] = combatBoost
                * context[MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_WINNING];
        result[offset++] = combatBoost * pendingRisk;

        double heldBoostEvents = context[MovieTrilogyRLGameStateFeatures.SELF_HAND_COMBAT_BOOST_EVENTS] * 6;
        double otherBoostEvents = Math.max(0.0, heldBoostEvents - eventCombatBoost);
        result[offset] = eventCombatBoost * pendingRisk / (1.0 + otherBoostEvents);
        offset++;

        result[offset++] = strategies[CardStrategyFeatures.MINION_FROM_DISCARD]
                * context[MovieTrilogyRLGameStateFeatures.SELF_DISCARD_MINION_COUNT];
        result[offset] = strategies[CardStrategyFeatures.MINION_FROM_DISCARD]
                * context[MovieTrilogyRLGameStateFeatures.OPPONENT_DISCARD_MINION_COUNT];
        return result;
    }
}
