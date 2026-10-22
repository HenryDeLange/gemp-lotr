package com.gempukku.lotro.bots;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.deckbuilding.DeckFeatureExtractor;
import com.gempukku.lotro.common.CardType;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MovieBlockDeckFactoryTest extends AbstractAtTest {
    @Test
    public void generatesLegalBotDecksForAllMovieBlocks() {
        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var decks = MovieBlockDeckFactory.createBotDecks(formatCode, format, _cardLibrary, 2, 1234L);

            assertEquals(formatCode, 2, decks.size());
            for (var deck : decks) {
                assertEquals(formatCode, deck.getTargetFormat());
                assertTrue(formatCode + ": " + format.validateDeck(deck), format.validateDeck(deck).isEmpty());
            }
        }
    }

    @Test
    public void loadsOfficialStarterDecksForEachMovieBlock() {
        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var decks = MovieBlockDeckFactory.createStarterDecks(
                    formatCode, format, _cardLibrary, _productLibrary);

            assertEquals(formatCode, "movie".equals(formatCode) ? 18 : 6, decks.size());
            for (var deck : decks) {
                assertEquals(formatCode, deck.getTargetFormat());
                assertTrue(formatCode + " / " + deck.getDeckName() + ": " + format.validateDeck(deck),
                        format.validateDeck(deck).isEmpty());
            }
        }
    }

    @Test
    public void coverageDecksIncludeEveryFormatLegalDeckableCard() {
        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var decks = MovieBlockDeckFactory.createCardCoverageDecks(
                    formatCode, format, _cardLibrary, formatCode.hashCode());
            Set<String> coveredCardIds = new HashSet<>();
            for (var deck : decks) {
                assertTrue(formatCode + ": " + format.validateDeck(deck), format.validateDeck(deck).isEmpty());
                coveredCardIds.add(deck.getRingBearer());
                coveredCardIds.add(deck.getRing());
                coveredCardIds.addAll(deck.getSites());
                coveredCardIds.addAll(deck.getDrawDeckCards());
            }

            Set<String> legalCardIds = new HashSet<>();
            _cardLibrary.getBaseCards().forEach((blueprintId, blueprint) -> {
                if (blueprint.getCardType() == CardType.SITE) {
                    if (blueprint.getSiteBlock() == format.getSiteBlock()
                            && format.validateSite(blueprintId) == null)
                        legalCardIds.add(blueprintId);
                } else if (blueprint.getCardType() == CardType.THE_ONE_RING) {
                    if (format.validateCard(blueprintId) == null)
                        legalCardIds.add(blueprintId);
                } else if (blueprint.getSide() != null && format.validateCard(blueprintId) == null) {
                    legalCardIds.add(blueprintId);
                }
            });
            assertEquals(formatCode, legalCardIds, coveredCardIds);
        }
    }

    @Test
    public void generatesLegalRandomDecksForAllMovieFormats() {
        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var deck = MovieBlockDeckFactory.createRandomLegalDeck(formatCode, format, _cardLibrary, 789L);

            assertEquals(formatCode, deck.getTargetFormat());
            assertTrue(formatCode + ": " + format.validateDeck(deck), format.validateDeck(deck).isEmpty());
        }
    }

    @Test
    public void extractsFiniteStructuredDeckFeaturesAcrossMovieFormats() {
        int expectedFeatureCount = -1;
        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var deck = MovieBlockDeckFactory.createRandomLegalDeck(formatCode, format, _cardLibrary, 9876L);
            double[] features = DeckFeatureExtractor.extract(deck, _cardLibrary);

            assertTrue(formatCode, features.length > 0);
            for (double feature : features)
                assertTrue(formatCode + " has finite features", Double.isFinite(feature));
            if (expectedFeatureCount < 0)
                expectedFeatureCount = features.length;
            else
                assertEquals(formatCode, expectedFeatureCount, features.length);
        }
    }
}
