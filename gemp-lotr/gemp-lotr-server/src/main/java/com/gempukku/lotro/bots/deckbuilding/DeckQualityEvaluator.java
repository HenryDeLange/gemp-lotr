package com.gempukku.lotro.bots.deckbuilding;

import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeckQualityEvaluator {
    private static final double CHARACTER_RATIO_TARGET = 0.4;
    private static final Pattern NAMED_PLAY_REQUIREMENT = Pattern.compile(
            "(?im)(?:^|\\n)\\s*(?:\\*{2,}\\s*be\\s+|bearer\\s+must\\s+be\\s+|play\\s+on\\s+)([^.,;\\n]+)");

    private DeckQualityEvaluator() {
    }

    public static Evaluation evaluate(LotroDeck deck, LotroCardBlueprintLibrary library) {
        Map<Side, List<LotroCardBlueprint>> cardsBySide = new EnumMap<>(Side.class);
        Map<Side, Set<String>> charactersBySide = new EnumMap<>(Side.class);
        for (Side side : Side.values()) {
            cardsBySide.put(side, new ArrayList<>());
            charactersBySide.put(side, new HashSet<>());
        }

        List<LotroCardBlueprint> cardsWithPlayRequirements = new ArrayList<>();
        for (String blueprintId : deck.getDrawDeckCards()) {
            LotroCardBlueprint card = requiredBlueprint(library, blueprintId);
            addCard(card, cardsBySide, charactersBySide);
            cardsWithPlayRequirements.add(card);
        }

        LotroCardBlueprint ringBearer = optionalBlueprint(library, deck.getRingBearer());
        if (ringBearer != null)
            addCard(ringBearer, cardsBySide, charactersBySide);
        LotroCardBlueprint ring = optionalBlueprint(library, deck.getRing());
        if (ring != null)
            cardsWithPlayRequirements.add(ring);

        Set<String> allCharacterTitles = new HashSet<>();
        library.getBaseCards().values().stream()
                .filter(DeckQualityEvaluator::isCharacter)
                .map(card -> normalizeName(card.getTitle()))
                .filter(title -> !title.isEmpty())
                .forEach(allCharacterTitles::add);

        int unplayableCardCount = (int) cardsWithPlayRequirements.stream()
                .filter(card -> hasUnmetNamedRequirement(
                        card, charactersBySide, allCharacterTitles))
                .count();
        double freePeopleFactionFocus = factionFocus(cardsBySide.get(Side.FREE_PEOPLE));
        double shadowFactionFocus = factionFocus(cardsBySide.get(Side.SHADOW));
        double freePeopleTypeBalance = typeBalance(Side.FREE_PEOPLE, cardsBySide.get(Side.FREE_PEOPLE));
        double shadowTypeBalance = typeBalance(Side.SHADOW, cardsBySide.get(Side.SHADOW));
        double strategyFocus = strategyFocus(
                cardsBySide.get(Side.FREE_PEOPLE), cardsBySide.get(Side.SHADOW));
        double factionFocus = (freePeopleFactionFocus + shadowFactionFocus) / 2.0;
        double typeBalance = (freePeopleTypeBalance + shadowTypeBalance) / 2.0;
        double playability = Math.max(0.0, 1.0 - 0.35 * unplayableCardCount);
        double score = 0.30 * factionFocus
                + 0.25 * typeBalance
                + 0.25 * strategyFocus
                + 0.20 * playability;

        return new Evaluation(score, factionFocus, freePeopleFactionFocus, shadowFactionFocus,
                typeBalance, freePeopleTypeBalance, shadowTypeBalance, strategyFocus, unplayableCardCount);
    }

    private static void addCard(
            LotroCardBlueprint card,
            Map<Side, List<LotroCardBlueprint>> cardsBySide,
            Map<Side, Set<String>> charactersBySide) {
        Side side = card.getSide();
        if (side == null)
            return;
        List<LotroCardBlueprint> sideCards = cardsBySide.get(side);
        if (sideCards != null)
            sideCards.add(card);
        if (isCharacter(card)) {
            String title = normalizeName(card.getTitle());
            if (!title.isEmpty())
                charactersBySide.get(side).add(title);
        }
    }

    private static boolean hasUnmetNamedRequirement(
            LotroCardBlueprint card,
            Map<Side, Set<String>> charactersBySide,
            Set<String> allCharacterTitles) {
        String gameText = card.getGameText();
        if (gameText == null || gameText.isBlank())
            return false;
        String plainText = gameText
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]*>", " ");
        Matcher matcher = NAMED_PLAY_REQUIREMENT.matcher(plainText);
        while (matcher.find()) {
            String[] alternatives = matcher.group(1).split("(?i)\\s+or\\s+");
            List<String> namedAlternatives = new ArrayList<>(alternatives.length);
            boolean allAreNamedCharacters = true;
            for (String alternative : alternatives) {
                String title = normalizeName(alternative);
                if (!allCharacterTitles.contains(title)) {
                    allAreNamedCharacters = false;
                    break;
                }
                namedAlternatives.add(title);
            }
            if (!allAreNamedCharacters)
                continue;

            Side requiredSide = card.getSide() == null && card.getCardType() == CardType.THE_ONE_RING
                    ? Side.FREE_PEOPLE : card.getSide();
            Set<String> availableCharacters = charactersBySide.get(requiredSide);
            if (availableCharacters != null
                    && namedAlternatives.stream().noneMatch(availableCharacters::contains))
                return true;
        }
        return false;
    }

    private static double factionFocus(List<LotroCardBlueprint> cards) {
        Map<Culture, Integer> cultureCounts = new EnumMap<>(Culture.class);
        int cardsWithCulture = 0;
        for (LotroCardBlueprint card : cards) {
            Culture culture = card.getCulture();
            if (culture != null) {
                cultureCounts.merge(culture, 1, Integer::sum);
                cardsWithCulture++;
            }
        }
        if (cardsWithCulture == 0)
            return 0.5;
        int largestFaction = cultureCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return 0.5 + 0.5 * largestFaction / cardsWithCulture;
    }

    private static double typeBalance(Side side, List<LotroCardBlueprint> cards) {
        if (cards.isEmpty())
            return 0.0;

        int characters = 0;
        int[] supportTypes = new int[4];
        int supportCount = 0;
        for (LotroCardBlueprint card : cards) {
            if (isPrimaryCharacter(side, card.getCardType())) {
                characters++;
                continue;
            }
            supportTypes[supportTypeIndex(card.getCardType())]++;
            supportCount++;
        }
        double characterRatio = characters / (double) cards.size();
        double characterBalance = Math.max(0.0,
                1.0 - Math.abs(characterRatio - CHARACTER_RATIO_TARGET) / CHARACTER_RATIO_TARGET);
        double supportDiversity = supportCount == 0 ? 0.0 : supportDiversity(supportTypes, supportCount);
        return 0.75 * characterBalance + 0.25 * supportDiversity;
    }

    private static double supportDiversity(int[] counts, int total) {
        double concentration = 0.0;
        for (int count : counts) {
            double share = count / (double) total;
            concentration += share * share;
        }
        return (1.0 - concentration) / (1.0 - 1.0 / counts.length);
    }

    private static int supportTypeIndex(CardType cardType) {
        return switch (cardType) {
            case POSSESSION, ARTIFACT -> 0;
            case CONDITION -> 1;
            case EVENT -> 2;
            default -> 3;
        };
    }

    private static double strategyFocus(
            List<LotroCardBlueprint> freePeopleCards,
            List<LotroCardBlueprint> shadowCards) {
        int moveCards = 0;
        int survivalCards = 0;
        for (LotroCardBlueprint card : freePeopleCards) {
            double[] strategies = CardStrategyFeatures.extract(card);
            if (strategies[CardStrategyFeatures.MOVE_AGAIN] > 0)
                moveCards++;
            if (strategies[CardStrategyFeatures.HEALING] > 0
                    || strategies[CardStrategyFeatures.WOUND_PREVENTION] > 0
                    || strategies[CardStrategyFeatures.BURDEN_REMOVE] > 0)
                survivalCards++;
        }

        int burdenCards = 0;
        int woundCards = 0;
        int siteControlCards = 0;
        int minionCount = 0;
        int lowCostMinions = 0;
        for (LotroCardBlueprint card : shadowCards) {
            double[] strategies = CardStrategyFeatures.extract(card);
            if (strategies[CardStrategyFeatures.BURDEN_ADD] > 0)
                burdenCards++;
            if (strategies[CardStrategyFeatures.WOUNDING] > 0
                    || strategies[CardStrategyFeatures.ARCHERY] > 0
                    || card.getKeywordCount(Keyword.DAMAGE) > 0)
                woundCards++;
            if (strategies[CardStrategyFeatures.SITE_CONTROL] > 0)
                siteControlCards++;
            if (card.getCardType() == CardType.MINION) {
                minionCount++;
                if (card.getTwilightCost() <= 3)
                    lowCostMinions++;
            }
        }

        double[] plans = {
                Math.min(1.0, (moveCards + 0.25 * survivalCards) / 3.0),
                Math.min(1.0, burdenCards / 3.0),
                Math.min(1.0, woundCards / 4.0),
                Math.min(1.0, siteControlCards / 3.0),
                minionCount == 0 ? 0.0 : Math.min(1.0, minionCount / 6.0)
                        * lowCostMinions / minionCount
        };
        double total = 0.0;
        double strongestPlan = 0.0;
        for (double plan : plans) {
            total += plan;
            strongestPlan = Math.max(strongestPlan, plan);
        }
        if (total == 0.0)
            return 0.5;
        return 0.5 + 0.5 * strongestPlan / total * Math.min(1.0, total);
    }

    private static boolean isPrimaryCharacter(Side side, CardType cardType) {
        return side == Side.FREE_PEOPLE && cardType == CardType.COMPANION
                || side == Side.SHADOW && cardType == CardType.MINION;
    }

    private static boolean isCharacter(LotroCardBlueprint card) {
        return card.getCardType() == CardType.COMPANION
                || card.getCardType() == CardType.ALLY
                || card.getCardType() == CardType.MINION;
    }

    private static String normalizeName(String name) {
        if (name == null)
            return "";
        return name.replaceAll("<[^>]*>", " ")
                .replaceAll("\\[[^]]*]", " ")
        .replaceAll("[^\\p{L}\\p{N}'-]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static LotroCardBlueprint optionalBlueprint(
            LotroCardBlueprintLibrary library,
            String blueprintId) {
        return blueprintId == null ? null : requiredBlueprint(library, blueprintId);
    }

    private static LotroCardBlueprint requiredBlueprint(
            LotroCardBlueprintLibrary library,
            String blueprintId) {
        LotroCardBlueprint blueprint = library.getBaseCards().get(blueprintId);
        if (blueprint == null)
            throw new IllegalArgumentException("Deck contains unknown blueprint " + blueprintId);
        return blueprint;
    }

    public record Evaluation(
            double score,
            double factionFocus,
            double freePeopleFactionFocus,
            double shadowFactionFocus,
            double typeBalance,
            double freePeopleTypeBalance,
            double shadowTypeBalance,
            double strategyFocus,
            int unplayableCardCount) {
    }
}
