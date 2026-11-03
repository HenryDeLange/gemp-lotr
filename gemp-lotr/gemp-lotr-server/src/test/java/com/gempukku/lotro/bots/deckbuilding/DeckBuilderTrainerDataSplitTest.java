package com.gempukku.lotro.bots.deckbuilding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckBuilderTrainerDataSplitTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void referenceSchedulePairsTheSameDeckInBothArgumentOrders() {
        for (int candidate = 0; candidate < 8; candidate++) {
            Set<Integer> references = new HashSet<>();
            for (int pair = 0; pair < 5; pair++) {
                int first = DeckBuilderTrainer.referenceIndex(candidate, pair * 2, 5);
                int second = DeckBuilderTrainer.referenceIndex(candidate, pair * 2 + 1, 5);
                assertEquals(first, second);
                references.add(first);
            }
            assertEquals(Set.of(0, 1, 2, 3, 4), references);
        }
    }

    @Test
    void readsLegacyJsonlAndMultipleCompressedAppendsWithoutChangingLegacyRecords() throws Exception {
        Path legacyFile = temporaryDirectory.resolve("deck-builder-outcomes-v4.jsonl");
        Path compressedFile = legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
        double[] features = new double[512];
        Arrays.fill(features, 1.0);
        JSONObject legacy = outcome("legacy-deck", features, true);
        Files.writeString(legacyFile, JSON.toJSONString(legacy) + System.lineSeparator());
        byte[] legacyBefore = Files.readAllBytes(legacyFile);

        var firstAppend = DeckBuilderTrainer.appendExamples(compressedFile, List.of(
                new DeckBuilderTrainer.DeckTrainingExample("compressed-one", features, false)));
        var secondAppend = DeckBuilderTrainer.appendExamples(compressedFile, List.of(
                new DeckBuilderTrainer.DeckTrainingExample("compressed-two", features, true)));

        List<DeckBuilderTrainer.DeckTrainingExample> examples =
                DeckBuilderTrainer.loadExamples(legacyFile, compressedFile, features.length);
        assertEquals(3, examples.size());
        assertEquals(Set.of("legacy-deck", "compressed-one", "compressed-two"),
                fingerprints(examples));
        assertEquals(firstAppend.appendedBytes() + secondAppend.appendedBytes(),
                Files.size(compressedFile));
        assertTrue(firstAppend.jsonBytes() > firstAppend.appendedBytes());
        assertTrue(Arrays.equals(legacyBefore, Files.readAllBytes(legacyFile)));
    }

    @Test
    void filtersMixedHistoryByPolicyAndReferenceProvenanceWithoutRewritingLegacyFiles() throws Exception {
        Path legacyFile = temporaryDirectory.resolve("deck-builder-outcomes-v4.jsonl");
        Path legacyCompressedFile = legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
        Path currentFile = temporaryDirectory.resolve("deck-builder-outcomes-v5.jsonl");
        Path currentCompressedFile = currentFile.resolveSibling(currentFile.getFileName() + ".gz");
        double[] features = new double[]{1, 2, 3};
        Files.writeString(legacyFile, JSON.toJSONString(outcome("legacy", features, true))
                + System.lineSeparator());
        byte[] legacyBefore = Files.readAllBytes(legacyFile);

        DeckBuilderProvenance expected = new DeckBuilderProvenance("policy-a", "pool-a", "manifest-a");
        DeckBuilderProvenance incompatiblePolicy = new DeckBuilderProvenance(
                "policy-b", "pool-a", "manifest-a");
        DeckBuilderProvenance incompatibleReferences = new DeckBuilderProvenance(
                "policy-a", "pool-b", "manifest-a");
        DeckBuilderProvenance noMatchingProvenance = new DeckBuilderProvenance(
                "policy-c", "pool-a", "manifest-a");
        DeckBuilderTrainer.appendProvenancedExamples(currentCompressedFile, List.of(
                example("matching", features, true, expected),
                example("wrong-policy", features, false, incompatiblePolicy),
                example("wrong-reference", features, false, incompatibleReferences)));

        List<DeckBuilderTrainer.DeckTrainingExample> compatible =
                DeckBuilderTrainer.loadCompatibleExamples(
                        currentFile, currentCompressedFile, features.length, 20, Set.of(), expected);
        assertEquals(List.of("matching"),
                compatible.stream().map(DeckBuilderTrainer.DeckTrainingExample::deckKey).toList());
        List<DeckBuilderTrainer.DeckTrainingExample> mixed = new ArrayList<>(
                DeckBuilderTrainer.loadExamples(legacyFile, legacyCompressedFile, features.length));
        mixed.addAll(DeckBuilderTrainer.loadExamples(currentFile, currentCompressedFile, features.length));
        assertEquals(4, mixed.size());
        assertTrue(Arrays.equals(legacyBefore, Files.readAllBytes(legacyFile)));
        assertTrue(DeckBuilderTrainer.loadCompatibleExamples(
                currentFile, currentCompressedFile, features.length, 20, Set.of(),
                noMatchingProvenance).isEmpty());
    }

    @Test
    void balancedRatesRequireBothStrataToContainCompletedGames() {
        assertEquals(0.75, DeckBuilderTrainer.balancedWinRate(
                new DeckBuilderTrainer.StratumStats(2, 2),
                new DeckBuilderTrainer.StratumStats(2, 1)), 0.0001);
        assertNull(DeckBuilderTrainer.balancedWinRate(
                new DeckBuilderTrainer.StratumStats(2, 2),
                new DeckBuilderTrainer.StratumStats(0, 0)));
    }

    @Test
    void reservoirSamplesHistoricalOutcomesDeterministicallyAndBoundsMemorySample() throws Exception {
        Path legacyFile = temporaryDirectory.resolve("historical-outcomes.jsonl");
        Path compressedFile = legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
        double[] features = new double[8];
        List<String> historical = new ArrayList<>();
        for (int index = 0; index < 40; index++)
            historical.add(JSON.toJSONString(outcome("old-deck-" + index, features, index % 2 == 0)));
        Files.write(legacyFile, historical);
        byte[] before = Files.readAllBytes(legacyFile);

        List<DeckBuilderTrainer.DeckTrainingExample> first =
                DeckBuilderTrainer.loadExamples(legacyFile, compressedFile, features.length, 7);
        List<DeckBuilderTrainer.DeckTrainingExample> second =
                DeckBuilderTrainer.loadExamples(legacyFile, compressedFile, features.length, 7);

        assertEquals(7, first.size());
        assertEquals(first.stream().map(DeckBuilderTrainer.DeckTrainingExample::deckKey).toList(),
                second.stream().map(DeckBuilderTrainer.DeckTrainingExample::deckKey).toList());
        assertTrue(Arrays.equals(before, Files.readAllBytes(legacyFile)));
    }

    @Test
    void excludesHeldOutDeckOutcomesBeforeReservoirSampling() throws Exception {
        Path legacyFile = temporaryDirectory.resolve("held-out-outcomes.jsonl");
        Path compressedFile = legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
        double[] features = new double[8];
        Files.write(legacyFile, List.of(
                JSON.toJSONString(outcome("reserved-one", features, true)),
                JSON.toJSONString(outcome("reserved-two", features, false)),
                JSON.toJSONString(outcome("eligible-one", features, true)),
                JSON.toJSONString(outcome("eligible-two", features, false))));

        List<DeckBuilderTrainer.DeckTrainingExample> examples =
                DeckBuilderTrainer.loadExamples(legacyFile, compressedFile, features.length, 2,
                        Set.of("reserved-one", "reserved-two"));

        assertEquals(Set.of("eligible-one", "eligible-two"), fingerprints(examples));
    }

    @Test
    void keepsCurrentGenerationOutcomesInAdditionToCappedHistory() throws Exception {
        Path legacyFile = temporaryDirectory.resolve("prior-generation-outcomes.jsonl");
        Path compressedFile = legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
        double[] features = new double[8];
        List<String> historical = new ArrayList<>();
        for (int index = 0; index < 40; index++)
            historical.add(JSON.toJSONString(outcome("old-deck-" + index, features, index % 2 == 0)));
        Files.write(legacyFile, historical);

        List<DeckBuilderTrainer.DeckTrainingExample> historicalSample =
                DeckBuilderTrainer.loadExamples(legacyFile, compressedFile, features.length, 7);
        List<DeckBuilderTrainer.DeckTrainingExample> recent = List.of(
                new DeckBuilderTrainer.DeckTrainingExample("new-deck-one", features, true),
                new DeckBuilderTrainer.DeckTrainingExample("new-deck-two", features, false));
        List<DeckBuilderTrainer.DeckTrainingExample> fitExamples = new ArrayList<>(historicalSample);
        fitExamples.addAll(recent);

        assertEquals(9, fitExamples.size());
        assertTrue(fingerprints(fitExamples).containsAll(Set.of("new-deck-one", "new-deck-two")));
        assertTrue(java.util.Collections.disjoint(
                fingerprints(DeckBuilderTrainer.splitByDeckFingerprint(fitExamples).training()),
                fingerprints(DeckBuilderTrainer.splitByDeckFingerprint(fitExamples).validation())));
    }

    @Test
    void keepsEveryDeckFingerprintOnOnlyOneSideOfTheHoldout() {
        String validationDeck = null;
        String trainingDeck = null;
        for (int index = 0; index < 1000 && (validationDeck == null || trainingDeck == null); index++) {
            String fingerprint = "deck-" + index;
            if (Math.floorMod(fingerprint.hashCode(), 5) == 0)
                validationDeck = fingerprint;
            else
                trainingDeck = fingerprint;
        }

        List<DeckBuilderTrainer.DeckTrainingExample> examples = new ArrayList<>();
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(validationDeck, new double[]{1}, true));
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(validationDeck, new double[]{2}, false));
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(trainingDeck, new double[]{3}, true));
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(trainingDeck, new double[]{4}, false));

        DeckBuilderTrainer.TrainingSplit split = DeckBuilderTrainer.splitByDeckFingerprint(examples);
        Set<String> trainingFingerprints = fingerprints(split.training());
        Set<String> validationFingerprints = fingerprints(split.validation());

        assertEquals(Set.of(trainingDeck), trainingFingerprints);
        assertEquals(Set.of(validationDeck), validationFingerprints);
        assertTrue(java.util.Collections.disjoint(trainingFingerprints, validationFingerprints));
    }

    private static Set<String> fingerprints(List<DeckBuilderTrainer.DeckTrainingExample> examples) {
        Set<String> fingerprints = new HashSet<>();
        examples.forEach(example -> fingerprints.add(example.deckKey()));
        return fingerprints;
    }

    private static JSONObject outcome(String deckKey, double[] features, boolean won) {
        JSONObject object = new JSONObject();
        object.put("schema", 4);
        object.put("deckKey", deckKey);
        object.put("features", features);
        object.put("won", won);
        return object;
    }

    private static DeckBuilderTrainer.DeckTrainingExample example(
            String deckKey,
            double[] features,
            boolean won,
            DeckBuilderProvenance provenance) {
        return new DeckBuilderTrainer.DeckTrainingExample(
                deckKey, features, won, provenance, "reference-" + deckKey,
                "P1", 0, true);
    }
}
