package com.gempukku.lotro.bots.deckbuilding;

import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.common.PossessionClass;
import com.gempukku.lotro.common.Race;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeckFeatureExtractor {
    private static final List<Side> SIDES = List.of(Side.values());
    private static final List<CardType> CARD_TYPES = List.of(CardType.values());
    private static final List<Culture> CULTURES = List.of(Culture.values());
    private static final List<Race> RACES = List.of(Race.values());
    private static final List<Keyword> KEYWORDS = List.of(Keyword.values());
    private static final List<PossessionClass> POSSESSION_CLASSES = List.of(PossessionClass.values());

    private DeckFeatureExtractor() {
    }

    public static double[] extract(LotroDeck deck, LotroCardBlueprintLibrary library) {
        List<Double> features = new ArrayList<>();
        String format = deck.getTargetFormat();
        appendFormat(features, format);

        Map<Side, List<LotroCardBlueprint>> cardsBySide = new EnumMap<>(Side.class);
        for (Side side : SIDES)
            cardsBySide.put(side, new ArrayList<>());
        for (String blueprintId : deck.getDrawDeckCards()) {
            LotroCardBlueprint blueprint = requiredBlueprint(library, blueprintId);
            List<LotroCardBlueprint> cards = cardsBySide.get(blueprint.getSide());
            if (cards != null)
                cards.add(blueprint);
        }
        for (Side side : SIDES)
            appendSideFeatures(features, cardsBySide.get(side));

        appendStartingCardFeatures(features, library, deck.getRingBearer());
        appendStartingCardFeatures(features, library, deck.getRing());
        appendSiteFeatures(features, library, deck.getSites());
        return features.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static void appendFormat(List<Double> features, String format) {
        for (String supported : List.of("fotr_block", "ttt_block", "king_block", "movie"))
            features.add(supported.equals(format) ? 1.0 : 0.0);
    }

    private static void appendSideFeatures(List<Double> features, List<LotroCardBlueprint> cards) {
        int[] types = new int[CARD_TYPES.size()];
        int[] cultures = new int[CULTURES.size()];
        int[] races = new int[RACES.size()];
        int[] keywords = new int[KEYWORDS.size()];
        int[] possessionClasses = new int[POSSESSION_CLASSES.size()];
        int[] strategies = new int[CardStrategyFeatures.FEATURE_COUNT];
        int lowCost = 0;
        int mediumCost = 0;
        int highCost = 0;
        int unique = 0;
        long costSum = 0;
        long strengthSum = 0;
        long vitalitySum = 0;

        for (LotroCardBlueprint card : cards) {
            increment(types, CARD_TYPES, card.getCardType());
            increment(cultures, CULTURES, card.getCulture());
            increment(races, RACES, card.getRace());
            for (int i = 0; i < KEYWORDS.size(); i++)
                keywords[i] += card.getKeywordCount(KEYWORDS.get(i));
            double[] cardStrategies = CardStrategyFeatures.extract(card);
            for (int i = 0; i < strategies.length; i++)
                strategies[i] += (int) cardStrategies[i];
            Set<PossessionClass> cardClasses = card.getPossessionClasses();
            if (cardClasses != null) {
                for (PossessionClass possessionClass : cardClasses)
                    increment(possessionClasses, POSSESSION_CLASSES, possessionClass);
            }

            int cost = Math.max(0, card.getTwilightCost());
            if (cost <= 2)
                lowCost++;
            else if (cost <= 4)
                mediumCost++;
            else
                highCost++;
            costSum += cost;
            strengthSum += card.getStrength();
            vitalitySum += card.getVitality();
            if (card.isUnique())
                unique++;
        }

        features.add((double) cards.size());
        features.add((double) unique);
        features.add((double) lowCost);
        features.add((double) mediumCost);
        features.add((double) highCost);
        features.add(cards.isEmpty() ? 0.0 : costSum / (double) cards.size());
        features.add(cards.isEmpty() ? 0.0 : strengthSum / (double) cards.size());
        features.add(cards.isEmpty() ? 0.0 : vitalitySum / (double) cards.size());
        appendCounts(features, types);
        appendCounts(features, cultures);
        appendCounts(features, races);
        appendCounts(features, keywords);
        appendCounts(features, possessionClasses);
        appendCounts(features, strategies);
        appendStrategySynergies(features, strategies, types, keywords);
    }

    private static void appendStrategySynergies(
            List<Double> features,
            int[] strategies,
            int[] types,
            int[] keywords) {
        int companionCount = types[CARD_TYPES.indexOf(CardType.COMPANION)];
        int minionCount = types[CARD_TYPES.indexOf(CardType.MINION)];
        features.add((double) strategies[CardStrategyFeatures.BURDEN_ADD] * minionCount);
        features.add((double) strategies[CardStrategyFeatures.THREAT_ADD] * strategies[CardStrategyFeatures.WOUNDING]);
        features.add((double) strategies[CardStrategyFeatures.ARCHERY] * strategies[CardStrategyFeatures.WOUNDING]);
        features.add((double) strategies[CardStrategyFeatures.SITE_CONTROL] * strategies[CardStrategyFeatures.COMBAT_BOOST]);
        features.add((double) strategies[CardStrategyFeatures.HEALING] * strategies[CardStrategyFeatures.WOUND_PREVENTION]);
        features.add((double) strategies[CardStrategyFeatures.MOVE_AGAIN] * companionCount);
        features.add((double) strategies[CardStrategyFeatures.CARD_DRAW] * strategies[CardStrategyFeatures.CARD_DISCARD]);
        features.add((double) minionCount * strategies[CardStrategyFeatures.COMBAT_BOOST]);
        features.add((double) companionCount * keywords[KEYWORDS.indexOf(Keyword.DEFENDER)]);
        features.add((double) minionCount * keywords[KEYWORDS.indexOf(Keyword.DAMAGE)]);
        features.add((double) companionCount * strategies[CardStrategyFeatures.HEALING]);
        features.add((double) strategies[CardStrategyFeatures.ARCHERY] * strategies[CardStrategyFeatures.WOUNDING]);
    }

    private static void appendStartingCardFeatures(
            List<Double> features,
            LotroCardBlueprintLibrary library,
            String blueprintId) {
        LotroCardBlueprint card = blueprintId == null ? null : requiredBlueprint(library, blueprintId);
        features.add(card == null ? 0.0 : 1.0);
        appendOneHot(features, SIDES, card == null ? null : card.getSide());
        appendOneHot(features, CULTURES, card == null ? null : card.getCulture());
        appendOneHot(features, RACES, card == null ? null : card.getRace());
        features.add(card == null ? 0.0 : Math.max(0, card.getTwilightCost()));
        features.add(card == null ? 0.0 : card.getStrength());
        features.add(card == null ? 0.0 : card.getVitality());
        features.add(card == null ? 0.0 : card.getResistance());
        features.add(card != null && card.isUnique() ? 1.0 : 0.0);
        if (card == null) {
            for (int i = 0; i < KEYWORDS.size(); i++)
                features.add(0.0);
        } else {
            for (Keyword keyword : KEYWORDS)
                features.add((double) card.getKeywordCount(keyword));
        }
    }

    private static void appendSiteFeatures(
            List<Double> features,
            LotroCardBlueprintLibrary library,
            List<String> siteIds) {
        int[] siteNumbers = new int[9];
        int[] keywords = new int[KEYWORDS.size()];
        for (String siteId : siteIds) {
            LotroCardBlueprint site = requiredBlueprint(library, siteId);
            int siteNumber = site.getSiteNumber();
            if (siteNumber >= 1 && siteNumber <= siteNumbers.length)
                siteNumbers[siteNumber - 1]++;
            for (int i = 0; i < KEYWORDS.size(); i++)
                keywords[i] += site.getKeywordCount(KEYWORDS.get(i));
        }
        features.add((double) siteIds.size());
        appendCounts(features, siteNumbers);
        appendCounts(features, keywords);
    }

    private static LotroCardBlueprint requiredBlueprint(LotroCardBlueprintLibrary library, String blueprintId) {
        LotroCardBlueprint blueprint = library.getBaseCards().get(blueprintId);
        if (blueprint == null)
            throw new IllegalArgumentException("Deck contains unknown blueprint " + blueprintId);
        return blueprint;
    }

    private static <T> void increment(int[] counts, List<T> values, T value) {
        if (value == null)
            return;
        int index = values.indexOf(value);
        if (index >= 0)
            counts[index]++;
    }

    private static void appendCounts(List<Double> features, int[] counts) {
        for (int count : counts)
            features.add((double) count);
    }

    private static <T> void appendOneHot(List<Double> features, List<T> values, T chosen) {
        for (T value : values)
            features.add(value == chosen ? 1.0 : 0.0);
    }
}
