package com.gempukku.lotro.bots;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.db.PlayerDAO;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class BotDeckSelectionTest extends AbstractAtTest {
    @Test
    public void listsOfficialStarterAndGeneratedDeckChoicesForAllFourModes() {
        BotService botService = new BotService(
                _cardLibrary, _formatLibrary, mock(PlayerDAO.class), _productLibrary);

        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            List<BotService.BotDeckOption> options = botService.getBotDeckOptions(formatCode);

            assertEquals(formatCode, "movie".equals(formatCode) ? 20 : 8, options.size());
            assertEquals("", options.getFirst().id());
            assertEquals(BotService.GENERATED_BOT_DECK_SELECTION, options.get(1).id());
            assertTrue(BotService.isBotDeckSelection(options.get(1).id()));
            assertFalse(BotService.isBotDeckSelection(options.get(2).label()));
        }
    }

    @Test
    public void generatedSelectionReturnsAFormatLegalDeck() {
        BotService botService = new BotService(
                _cardLibrary, _formatLibrary, mock(PlayerDAO.class), _productLibrary);
        var marker = BotService.createBotDeckSelectionMarker("movie", BotService.GENERATED_BOT_DECK_SELECTION);

        BotService.BotWithDeck bot = botService.getBotForDeck(marker);

        assertEquals("~bot", bot.getBotPlayer().getName());
        assertEquals("movie", bot.getLotroDeck().getTargetFormat());
        assertTrue(_formatLibrary.getFormat("movie").validateDeck(bot.getLotroDeck()).isEmpty());
    }

    @Test
    public void fixedSelectionCopiesTheRequestedBuiltInDeck() {
        BotService botService = new BotService(
                _cardLibrary, _formatLibrary, mock(PlayerDAO.class), _productLibrary);
        BotService.BotDeckOption fixed = botService.getBotDeckOptions("fotr_block").get(2);
        var marker = BotService.createBotDeckSelectionMarker("fotr_block", fixed.id());

        BotService.BotWithDeck bot = botService.getBotForDeck(marker);

        assertEquals(fixed.label(), bot.getLotroDeck().getDeckName());
        assertTrue(_formatLibrary.getFormat("fotr_block").validateDeck(bot.getLotroDeck()).isEmpty());
    }
}
