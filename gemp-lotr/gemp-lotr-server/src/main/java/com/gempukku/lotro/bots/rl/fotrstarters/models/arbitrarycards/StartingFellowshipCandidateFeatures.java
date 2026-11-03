package com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards;

import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.game.CardNotFoundException;

import java.util.Arrays;
import java.util.List;

final class StartingFellowshipCandidateFeatures {
    private static final List<Culture> CULTURES = List.of(Culture.values());
    static final int CULTURE_ONE_HOT_OFFSET = 0;
    static final int EXISTING_CULTURE_SYNERGY_OFFSET = CULTURES.size();
    static final int REMAINING_DECK_CULTURE_OFFSET = 2 * CULTURES.size();
    static final int EXISTING_STRATEGY_SYNERGY_OFFSET = 3 * CULTURES.size();
    static final int COST_PRESSURE_OFFSET =
            EXISTING_STRATEGY_SYNERGY_OFFSET + CardStrategyFeatures.FEATURE_COUNT;
    static final int FEATURE_COUNT = COST_PRESSURE_OFFSET + 1;
    private static final int PRINTED_TWILIGHT_COST_INDEX = 17;

    private StartingFellowshipCandidateFeatures() {
    }

    static double[] append(double[] state, double[] cardFeatures, String blueprintId)
            throws CardNotFoundException {
        double[] base = CardCandidateFeatures.append(state, cardFeatures);
        double[] result = Arrays.copyOf(base, base.length + FEATURE_COUNT);
        int offset = base.length;

        Culture candidateCulture = CardFeatures.getCardCulture(blueprintId);
        if (candidateCulture != null) {
            int cultureIndex = CULTURES.indexOf(candidateCulture);
            if (cultureIndex >= 0) {
                result[offset + CULTURE_ONE_HOT_OFFSET + cultureIndex] = 1.0;
                double currentCultureCount =
                        MovieTrilogyRLGameStateFeatures.getSelfInPlayCultureCount(state, candidateCulture);
                result[offset + EXISTING_CULTURE_SYNERGY_OFFSET + cultureIndex] = currentCultureCount;
                result[offset + REMAINING_DECK_CULTURE_OFFSET + cultureIndex] = Math.max(0.0,
                        MovieTrilogyRLGameStateFeatures.getSelfDeckCultureCount(state, candidateCulture) - 1.0);
            }
        }

        int strategyOffset = cardFeatures.length - CardStrategyFeatures.FEATURE_COUNT;
        for (int strategyIndex = 0; strategyIndex < CardStrategyFeatures.FEATURE_COUNT; strategyIndex++) {
            result[offset + EXISTING_STRATEGY_SYNERGY_OFFSET + strategyIndex] =
                    cardFeatures[strategyOffset + strategyIndex]
                            * MovieTrilogyRLGameStateFeatures.getSelfInPlayStrategyCount(state, strategyIndex);
        }

        double candidateCost = cardFeatures.length > PRINTED_TWILIGHT_COST_INDEX
                ? Math.max(0.0, cardFeatures[PRINTED_TWILIGHT_COST_INDEX]) : 0.0;
        double costAlreadySpent = MovieTrilogyRLGameStateFeatures.getStartingCostSpent(state);
        result[offset + COST_PRESSURE_OFFSET] =
                Math.min(1.0, candidateCost * costAlreadySpent / 16.0);

        return result;
    }
}
