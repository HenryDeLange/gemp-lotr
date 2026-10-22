package com.gempukku.lotro.bots;

import com.alibaba.fastjson2.JSON;
import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DeckBuilderTrainerTest extends AbstractAtTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void trainsFromSeatSwappedDeckOutcomesAndPersistsExamples() throws Exception {
        var format = _formatLibrary.getFormat("fotr_block");
        var referenceDecks = MovieBlockDeckFactory.createStarterDecks(
                "fotr_block", format, _cardLibrary, _productLibrary);
        var modelDirectory = temporaryFolder.newFolder("models").toPath();
        var dataDirectory = temporaryFolder.newFolder("data").toPath();

        DeckBuilderTrainer.TrainingResult result = DeckBuilderTrainer.train(
                format, _cardLibrary, referenceDecks, null,
                new ModelRegistry(), new MovieTrilogyRLGameStateFeatures("fotr_block"),
                modelDirectory, dataDirectory, 2, 2, 2, 2, null, 1, 65432L);

        assertEquals(2, result.candidates());
        assertEquals(4, result.games());
        assertEquals(4, result.wins() + result.losses());
        assertTrue(Double.isNaN(result.incumbentAccuracy()));
        assertEquals(4, result.accumulatedExamples());
        assertTrue(result.generatedDecks().size() > 0);
        var dataFile = dataDirectory.resolve("deck-builder-outcomes-v4.jsonl");
        assertTrue(Files.isRegularFile(dataFile));
        List<String> records = Files.readAllLines(dataFile);
        assertEquals(4, records.size());
        assertTrue(records.stream().map(JSON::parseObject)
                .allMatch(record -> record.getIntValue("schema") == 4
                        && record.getString("deckKey") != null
                        && record.getJSONArray("features") != null));
        assertNotNull(DeckBuilderTrainer.createDeck(format, _cardLibrary, result.model(), 2, 123L));
    }
}
