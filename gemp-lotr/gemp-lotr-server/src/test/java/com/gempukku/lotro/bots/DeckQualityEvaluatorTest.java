package com.gempukku.lotro.bots;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.deckbuilding.DeckQualityEvaluator;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.logic.vo.LotroDeck;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DeckQualityEvaluatorTest extends AbstractAtTest {
    @Test
    public void prefersFocusedFactionsButStillScoresMultifactionDecks() {
        List<LotroCardBlueprint> companions = distinctCultureCards(Side.FREE_PEOPLE, CardType.COMPANION, 2);
        List<LotroCardBlueprint> minions = distinctCultureCards(Side.SHADOW, CardType.MINION, 2);
        var focused = createDeck("focused",
                repeatedCards(companions.getFirst().getId(), 15),
                repeatedCards(minions.getFirst().getId(), 15));
        focused.setRingBearer(companions.getFirst().getId());
        var multifaction = createDeck("multifaction",
                mixedCards(companions.get(0).getId(), companions.get(1).getId(), 8),
                mixedCards(minions.get(0).getId(), minions.get(1).getId(), 8));
        multifaction.setRingBearer(companions.getFirst().getId());

        var focusedQuality = DeckQualityEvaluator.evaluate(focused, _cardLibrary);
        var multifactionQuality = DeckQualityEvaluator.evaluate(multifaction, _cardLibrary);

        assertEquals(1.0, focusedQuality.freePeopleFactionFocus(), 0.0);
        assertEquals(1.0, focusedQuality.shadowFactionFocus(), 0.0);
        assertTrue(multifactionQuality.freePeopleFactionFocus() < focusedQuality.freePeopleFactionFocus());
        assertTrue(multifactionQuality.shadowFactionFocus() < focusedQuality.shadowFactionFocus());
        assertTrue(multifactionQuality.score() > 0.0);
    }

    @Test
    public void rewardsBalancedCharacterAndSupportCardTypes() {
        String companion = findCard(Side.FREE_PEOPLE, CardType.COMPANION).getId();
        String fpSupport = findCard(Side.FREE_PEOPLE, CardType.POSSESSION).getId();
        String minion = findCard(Side.SHADOW, CardType.MINION).getId();
        String shadowSupport = findCard(Side.SHADOW, CardType.CONDITION).getId();

        var balanced = createDeck("balanced",
                repeatedCards(companion, 6, fpSupport, 9),
                repeatedCards(minion, 6, shadowSupport, 9));
        balanced.setRingBearer(companion);
        var characterLight = createDeck("character-light",
                repeatedCards(fpSupport, 15),
                repeatedCards(minion, 6, shadowSupport, 9));
        characterLight.setRingBearer(companion);

        var balancedQuality = DeckQualityEvaluator.evaluate(balanced, _cardLibrary);
        var characterLightQuality = DeckQualityEvaluator.evaluate(characterLight, _cardLibrary);

        assertTrue(balancedQuality.freePeopleTypeBalance()
                > characterLightQuality.freePeopleTypeBalance());
    }

    @Test
    public void rewardsAFocusedVictoryPlan() {
        List<LotroCardBlueprint> burdenCards = cardsMatching(Side.SHADOW, card -> {
            double[] strategies = CardStrategyFeatures.extract(card);
            return strategies[CardStrategyFeatures.BURDEN_ADD] > 0
                    && strategies[CardStrategyFeatures.WOUNDING] == 0
                    && strategies[CardStrategyFeatures.ARCHERY] == 0
                    && strategies[CardStrategyFeatures.SITE_CONTROL] == 0;
        }, 3);
        LotroCardBlueprint woundCard = findCard(Side.SHADOW, card -> {
            double[] strategies = CardStrategyFeatures.extract(card);
            return (strategies[CardStrategyFeatures.WOUNDING] > 0
                    || strategies[CardStrategyFeatures.ARCHERY] > 0
                    || card.getKeywordCount(Keyword.DAMAGE) > 0)
                    && strategies[CardStrategyFeatures.BURDEN_ADD] == 0
                    && strategies[CardStrategyFeatures.SITE_CONTROL] == 0;
        });
        LotroCardBlueprint siteControlCard = findCard(Side.SHADOW, card -> {
            double[] strategies = CardStrategyFeatures.extract(card);
            return strategies[CardStrategyFeatures.SITE_CONTROL] > 0
                    && strategies[CardStrategyFeatures.BURDEN_ADD] == 0
                    && strategies[CardStrategyFeatures.WOUNDING] == 0
                    && strategies[CardStrategyFeatures.ARCHERY] == 0;
        });
        String neutralMinion = findCard(Side.SHADOW, CardType.MINION, card -> {
            double[] strategies = CardStrategyFeatures.extract(card);
            return card.getTwilightCost() > 3
                    && strategies[CardStrategyFeatures.BURDEN_ADD] == 0
                    && strategies[CardStrategyFeatures.WOUNDING] == 0
                    && strategies[CardStrategyFeatures.ARCHERY] == 0
                    && strategies[CardStrategyFeatures.SITE_CONTROL] == 0
                    && card.getKeywordCount(Keyword.DAMAGE) == 0;
        }).getId();

        List<String> focusedShadow = new ArrayList<>();
        burdenCards.forEach(card -> focusedShadow.add(card.getId()));
        focusedShadow.addAll(repeatedCards(neutralMinion, 12));
        List<String> mixedShadow = new ArrayList<>(List.of(
                burdenCards.getFirst().getId(), woundCard.getId(), siteControlCard.getId()));
        mixedShadow.addAll(repeatedCards(neutralMinion, 12));
        var focused = createDeck("burden-plan", List.of(), focusedShadow);
        var mixed = createDeck("mixed-plan", List.of(), mixedShadow);

        assertTrue(DeckQualityEvaluator.evaluate(focused, _cardLibrary).strategyFocus()
                > DeckQualityEvaluator.evaluate(mixed, _cardLibrary).strategyFocus());
    }

    @Test
    public void penalizesNamedBearerCardsWhenTheRequiredCharacterIsAbsent() {
        LotroCardBlueprint namedBearerCard = _cardLibrary.getBaseCards().get("9_23");
        LotroCardBlueprint elrond = findCard(Side.FREE_PEOPLE, CardType.COMPANION,
                card -> "elrond".equalsIgnoreCase(card.getTitle()));
        LotroCardBlueprint otherRingBearer = findCard(Side.FREE_PEOPLE, CardType.COMPANION,
                card -> card.canStartWithRing() && !"elrond".equalsIgnoreCase(card.getTitle()));
        assertNotNull("Expected a named-bearer card in the card library", namedBearerCard);

        var missingBearer = createDeck("missing-bearer", List.of(namedBearerCard.getId()), List.of());
        missingBearer.setRingBearer(otherRingBearer.getId());
        var availableBearer = createDeck("available-bearer",
                List.of(namedBearerCard.getId(), elrond.getId()), List.of());
        availableBearer.setRingBearer(otherRingBearer.getId());

        assertTrue(DeckQualityEvaluator.evaluate(missingBearer, _cardLibrary).unplayableCardCount() > 0);
        assertEquals(0, DeckQualityEvaluator.evaluate(availableBearer, _cardLibrary).unplayableCardCount());
    }

    private List<LotroCardBlueprint> distinctCultureCards(Side side, CardType cardType, int count) {
        Map<Culture, LotroCardBlueprint> cardsByCulture = new EnumMap<>(Culture.class);
        _cardLibrary.getBaseCards().values().stream()
                .filter(card -> card.getSide() == side && card.getCardType() == cardType)
                .filter(card -> card.getCulture() != null)
                .forEach(card -> cardsByCulture.putIfAbsent(card.getCulture(), card));
        List<LotroCardBlueprint> result = cardsByCulture.values().stream().limit(count).toList();
        assertEquals("Expected cards from multiple cultures for " + side + " " + cardType, count, result.size());
        return result;
    }

    private LotroCardBlueprint findCard(Side side, CardType cardType) {
        return findCard(side, cardType, ignored -> true);
    }

    private LotroCardBlueprint findCard(Side side, CardType cardType,
                                        java.util.function.Predicate<LotroCardBlueprint> predicate) {
        LotroCardBlueprint card = _cardLibrary.getBaseCards().values().stream()
                .filter(candidate -> candidate.getSide() == side && candidate.getCardType() == cardType)
                .filter(predicate)
                .findFirst()
                .orElse(null);
        assertNotNull("Expected a card for " + side + " " + cardType, card);
        return card;
    }

    private LotroCardBlueprint findCard(Side side,
                                        java.util.function.Predicate<LotroCardBlueprint> predicate) {
        LotroCardBlueprint card = _cardLibrary.getBaseCards().values().stream()
                .filter(candidate -> candidate.getSide() == side)
                .filter(predicate)
                .findFirst()
                .orElse(null);
        assertNotNull("Expected a card for " + side, card);
        return card;
    }

    private List<LotroCardBlueprint> cardsMatching(
            Side side,
            java.util.function.Predicate<LotroCardBlueprint> predicate,
            int count) {
        List<LotroCardBlueprint> cards = _cardLibrary.getBaseCards().values().stream()
                .filter(card -> card.getSide() == side)
                .filter(predicate)
                .distinct()
                .limit(count)
                .toList();
        assertEquals("Expected enough strategy cards for " + side, count, cards.size());
        return cards;
    }

    private LotroDeck createDeck(String name, List<String> freePeople, List<String> shadow) {
        LotroDeck deck = new LotroDeck(name);
        deck.setTargetFormat("movie");
        freePeople.forEach(deck::addCard);
        shadow.forEach(deck::addCard);
        return deck;
    }

    private List<String> repeatedCards(String blueprintId, int count) {
        return java.util.Collections.nCopies(count, blueprintId);
    }

    private List<String> repeatedCards(
            String firstId, int firstCount, String secondId, int secondCount) {
        List<String> result = new ArrayList<>(firstCount + secondCount);
        result.addAll(repeatedCards(firstId, firstCount));
        result.addAll(repeatedCards(secondId, secondCount));
        return result;
    }

    private List<String> mixedCards(String firstId, String secondId, int firstCount) {
        List<String> result = new ArrayList<>(15);
        result.addAll(repeatedCards(firstId, firstCount));
        result.addAll(repeatedCards(secondId, 15 - firstCount));
        return result;
    }
}
