package com.gempukku.lotro.bots;

import com.alibaba.fastjson2.JSON;
import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.training.TrainingDataIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

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
        assertEquals(4, result.recentExamples());
        assertTrue(result.generatedDecks().size() > 0);
        assertEquals(4, result.pairings().size());
        assertTrue(result.pairings().stream().allMatch(pairing -> pairing.candidateSeatIndex() == null
                || pairing.candidateSeatIndex() == 0 || pairing.candidateSeatIndex() == 1));
        assertTrue(result.pairings().stream().allMatch(pairing -> pairing.candidateStartsFirst() != null
                || pairing.skipReason() != null));
        var dataFile = dataDirectory.resolve("deck-builder-outcomes-v5.jsonl");
        var compressedDataFile = dataFile.resolveSibling(dataFile.getFileName() + ".gz");
        assertTrue(Files.isRegularFile(compressedDataFile));
        java.util.List<String> records = new java.util.ArrayList<>();
        TrainingDataIO.forEachLine(dataFile, compressedDataFile,
                (source, lineNumber, line) -> records.add(line));
        assertEquals(4, records.size());
        assertTrue(records.stream().map(JSON::parseObject)
                .allMatch(record -> record.getIntValue("schema") == 5
                        && record.getString("deckKey") != null
                        && record.getJSONArray("features") != null
                        && record.getString("playingPolicySha256") != null
                        && record.getString("referencePoolSha256") != null
                        && record.getString("referenceDeckKey") != null
                        && record.getInteger("candidateSeatIndex") != null
                        && record.getBoolean("candidateStartsFirst") != null));
        assertTrue(!Files.exists(dataFile));
        assertNotNull(DeckBuilderTrainer.createDeck(format, _cardLibrary, result.model(), 2, 123L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToUseOnlyHeldOutReferenceDecks() throws Exception {
        var format = _formatLibrary.getFormat("fotr_block");
        var referenceDecks = MovieBlockDeckFactory.createStarterDecks(
                "fotr_block", format, _cardLibrary, _productLibrary);
        Set<String> heldOut = referenceDecks.stream()
                .map(deck -> DeckBuilderTrainer.fingerprint("fotr_block", deck))
                .collect(java.util.stream.Collectors.toSet());

        DeckBuilderTrainer.train(
                format, _cardLibrary, referenceDecks, null,
                new ModelRegistry(), new MovieTrilogyRLGameStateFeatures("fotr_block"),
                temporaryFolder.newFolder("holdout-models").toPath(),
                temporaryFolder.newFolder("holdout-data").toPath(),
                2, 2, 1, 1, null, 1, 76543L,
                DeckBuilderTrainer.DEFAULT_MAX_HISTORY_RECORDS, heldOut);
    }
}
