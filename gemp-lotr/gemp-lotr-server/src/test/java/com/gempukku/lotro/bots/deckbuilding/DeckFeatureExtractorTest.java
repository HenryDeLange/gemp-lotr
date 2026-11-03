package com.gempukku.lotro.bots.deckbuilding;

import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Keyword;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DeckFeatureExtractorTest {
    @Test
    public void strategySynergiesRequireBothCompatibleCardCountsAndStrategies() {
        int[] strategies = new int[CardStrategyFeatures.FEATURE_COUNT];
        int[] types = new int[CardType.values().length];
        int[] keywords = new int[Keyword.values().length];
        strategies[CardStrategyFeatures.BURDEN_ADD] = 3;
        strategies[CardStrategyFeatures.THREAT_ADD] = 2;
        strategies[CardStrategyFeatures.WOUNDING] = 4;
        strategies[CardStrategyFeatures.ARCHERY] = 3;
        strategies[CardStrategyFeatures.COMBAT_BOOST] = 2;
        types[CardType.MINION.ordinal()] = 5;
        types[CardType.COMPANION.ordinal()] = 6;
        keywords[Keyword.DEFENDER.ordinal()] = 2;

        List<Double> features = new ArrayList<>();
        DeckFeatureExtractor.appendStrategySynergies(features, strategies, types, keywords);

        assertEquals(12, features.size());
        assertEquals(15.0, features.get(0), 0.0);
        assertEquals(8.0, features.get(1), 0.0);
        assertEquals(12.0, features.get(2), 0.0);
        assertEquals(10.0, features.get(7), 0.0);
        assertEquals(12.0, features.get(8), 0.0);
        assertEquals(12.0, features.get(11), 0.0);

        types[CardType.MINION.ordinal()] = 0;
        features.clear();
        DeckFeatureExtractor.appendStrategySynergies(features, strategies, types, keywords);
        assertEquals(0.0, features.get(0), 0.0);
        assertEquals(0.0, features.get(7), 0.0);
    }
}
