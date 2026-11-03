package com.gempukku.lotro.bots.rl.fotrstarters;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericCardActionChoiceTrainer;
import com.gempukku.lotro.bots.rl.semanticaction.CardActionChoiceAction;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.bots.training.TrainingDataIO;
import com.gempukku.lotro.bots.training.TrainingProgress;
import com.gempukku.lotro.bots.training.TrainingRunMetricsWriter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class FotrStartersLearningStepsPersistenceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadUsesDeterministicBoundedSample() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("fotr_block").toPath();
        Path recordsFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        List<String> records = java.util.stream.IntStream.range(0, 10)
                .mapToObj(FotrStartersLearningStepsPersistenceTest::trainingRecord)
                .map(step -> step.toJson().toJSONString())
                .toList();
        Files.write(recordsFile, records, StandardCharsets.UTF_8);
        FotrStartersLearningStepsPersistence persistence =
                new FotrStartersLearningStepsPersistence(dataDirectory);
        Trainer trainer = new GenericCardActionChoiceTrainer();

        List<LearningStep> firstSample = persistence.load(trainer, 3);
        List<LearningStep> secondSample = persistence.load(trainer, 3);

        assertEquals(3, firstSample.size());
        assertEquals(3, Files.readAllLines(recordsFile).size());
        assertEquals(
                firstSample.stream().map(step -> step.gameId).toList(),
                secondSample.stream().map(step -> step.gameId).toList());
        assertTrue(firstSample.stream().allMatch(step -> records.stream()
                .anyMatch(record -> record.contains("\"gameId\":\"" + step.gameId + "\""))));
    }

    @Test
    public void loadPreservesRareDecisionCategoriesWithinTheCap() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("category-sample").toPath();
        Path recordsFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        List<LearningStep> records = new java.util.ArrayList<>();
        for (int index = 0; index < 20; index++)
            records.add(trainingRecord(index,
                    index % 2 == 0 ? "Common decision" : " COMMON\t decision ", index + 1.0));
        records.add(trainingRecord(20, "Rare decision", 21.0));
        records.add(trainingRecord(21, "Rare decision", 22.0));
        Files.write(recordsFile, records.stream()
                .map(step -> step.toJson().toJSONString()).toList(), StandardCharsets.UTF_8);
        FotrStartersLearningStepsPersistence persistence =
                new FotrStartersLearningStepsPersistence(dataDirectory);
        Trainer trainer = new GenericCardActionChoiceTrainer();

        List<LearningStep> firstSample = persistence.load(trainer, 4);
        List<LearningStep> secondSample = persistence.load(trainer, 4);

        assertEquals(4, firstSample.size());
        assertEquals(firstSample.stream().map(step -> step.gameId).toList(),
                secondSample.stream().map(step -> step.gameId).toList());
        assertEquals(2, firstSample.stream()
                .filter(step -> step.decision.getText().equals("Rare decision")).count());
        assertEquals(2, firstSample.stream()
                .filter(step -> step.decision.getText().strip().replaceAll("\\s+", " ")
                        .equalsIgnoreCase("Common decision")).count());
        Map<String, Double> weightsByGame = records.stream()
                .collect(java.util.stream.Collectors.toMap(step -> step.gameId, step -> step.trainingWeight));
        for (LearningStep step : firstSample)
            assertEquals(weightsByGame.get(step.gameId), step.trainingWeight, 0.0);
    }

    @Test
    public void loadBelowTheCapPreservesAllRecordsInOrder() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("uncapped-sample").toPath();
        Path recordsFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        List<LearningStep> records = List.of(
                trainingRecord(1, "Rare decision", 1.5),
                trainingRecord(2, "Common decision", 2.5),
                trainingRecord(3, "Common decision", 3.5));
        Files.write(recordsFile, records.stream()
                .map(step -> step.toJson().toJSONString()).toList(), StandardCharsets.UTF_8);
        FotrStartersLearningStepsPersistence persistence =
                new FotrStartersLearningStepsPersistence(dataDirectory);

        List<LearningStep> loaded = persistence.load(new GenericCardActionChoiceTrainer(), records.size());

        assertEquals(records.stream().map(step -> step.gameId).toList(),
                loaded.stream().map(step -> step.gameId).toList());
        for (int index = 0; index < records.size(); index++)
            assertEquals(records.get(index).trainingWeight, loaded.get(index).trainingWeight, 0.0);
    }

    @Test
    public void reportsCompactionToProgressAndRunMetrics() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("compacted-data").toPath();
        Path recordsFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        List<String> records = java.util.stream.IntStream.range(0, 10)
                .mapToObj(FotrStartersLearningStepsPersistenceTest::trainingRecord)
                .map(step -> step.toJson().toJSONString())
                .toList();
        Files.write(recordsFile, records, StandardCharsets.UTF_8);

        Path metricsDirectory = temporaryFolder.newFolder("metrics").toPath();
        TrainingProgress progress = new TrainingProgress();
        Path metricsFile;
        try (TrainingRunMetricsWriter writer = new TrainingRunMetricsWriter(metricsDirectory, Map.of())) {
            metricsFile = writer.file();
            progress.setMetricsWriter(writer);
            progress.beginFormat("shared-policy", 2);
            FotrStartersLearningStepsPersistence persistence =
                    new FotrStartersLearningStepsPersistence(dataDirectory, compaction ->
                            progress.trainingDataCompacted("fotr_block", compaction.trainerName(),
                                    compaction.fileName(), compaction.recordsBefore(), compaction.recordsAfter()));

            assertEquals(3, persistence.load(new GenericCardActionChoiceTrainer(), 3).size());
        }

        TrainingProgress.EventPage warnings =
                progress.eventPage(TrainingEventReporter.Severity.WARNING, 1, 10);
        assertEquals(1, warnings.total());
        assertEquals("training-data-compacted", warnings.events().getFirst().type());
        assertTrue(warnings.events().getFirst().message().contains("fotr_block"));
        assertTrue(warnings.events().getFirst().message().contains("from 10 to 3 records"));

        JSONObject compaction = Files.readAllLines(metricsFile).stream()
                .map(JSON::parseObject)
                .filter(record -> record.getString("type").equals("event"))
                .map(record -> record.getJSONObject("data"))
                .filter(event -> event.getString("type").equals("training-data-compacted"))
                .findFirst()
                .orElseThrow();
        assertEquals("warning", compaction.getString("severity"));
        assertEquals(2, compaction.getIntValue("generation"));
        assertTrue(compaction.getString("message").contains("from 10 to 3 records"));
    }

    @Test
    public void samplingWithoutCompactionPreservesHistoricalFile() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("preserved-data").toPath();
        Path recordsFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        Files.write(recordsFile, java.util.stream.IntStream.range(0, 10)
                .mapToObj(FotrStartersLearningStepsPersistenceTest::trainingRecord)
                .map(step -> step.toJson().toJSONString()).toList(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(recordsFile);
        FotrStartersLearningStepsPersistence persistence = new FotrStartersLearningStepsPersistence(
                dataDirectory, compaction -> { throw new AssertionError("Unexpected compaction"); },
                stats -> { }, false);

        assertEquals(3, persistence.load(new GenericCardActionChoiceTrainer(), 3).size());
        org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(recordsFile));
    }

    @Test
    public void countsInputStepsTrainerRecordsAndExactAppendedBytes() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("appended-data").toPath();
        AtomicReference<FotrStartersLearningStepsPersistence.AppendStats> observed = new AtomicReference<>();
        FotrStartersLearningStepsPersistence persistence = new FotrStartersLearningStepsPersistence(
                dataDirectory, compaction -> { }, observed::set, false);
        persistence.save(List.of(trainingRecord(1), trainingRecord(2)));
        long bytes;
        long records;
        try (var files = Files.list(dataDirectory)) {
            List<Path> paths = files.toList();
            bytes = 0;
            records = 0;
            for (Path path : paths) {
                bytes += Files.size(path);
                long[] fileRecords = {0};
                Path legacyFile = path.getFileName().toString().endsWith(".gz")
                        ? path.resolveSibling(path.getFileName().toString().substring(
                        0, path.getFileName().toString().length() - 3))
                        : path;
                Path compressedFile = legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
                TrainingDataIO.forEachLine(legacyFile, compressedFile,
                        (source, lineNumber, line) -> fileRecords[0]++);
                records += fileRecords[0];
            }
        }
        assertEquals(2, observed.get().inputSteps());
        assertEquals(2, persistence.load(new GenericCardActionChoiceTrainer(), 10).size());
        assertEquals(records, observed.get().trainerRecords());
        assertEquals(bytes, observed.get().appendedBytes());
        assertTrue(observed.get().jsonBytes() > observed.get().appendedBytes());
        assertTrue(observed.get().appendedBytes() > 0);
    }

    @Test
    public void loadsLegacyJsonlAndMultipleCompressedAppendsTogether() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("mixed-data").toPath();
        Path legacyFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        Files.write(legacyFile, java.util.stream.IntStream.range(0, 5)
                .mapToObj(FotrStartersLearningStepsPersistenceTest::trainingRecord)
                .map(step -> step.toJson().toJSONString()).toList(), StandardCharsets.UTF_8);
        FotrStartersLearningStepsPersistence persistence = new FotrStartersLearningStepsPersistence(
                dataDirectory, compaction -> { }, stats -> { }, false);

        persistence.save(List.of(trainingRecord(10), trainingRecord(11)));
        persistence.save(List.of(trainingRecord(12), trainingRecord(13)));

        assertEquals(9, persistence.load(new GenericCardActionChoiceTrainer(), 20).size());
        assertEquals(5, Files.readAllLines(legacyFile).size());
        assertTrue(Files.isRegularFile(
                legacyFile.resolveSibling(legacyFile.getFileName() + ".gz")));
    }

    @Test
    public void holdoutFilteringExcludesReservedAndUnattributedRecordsAndReadsLegacyJson() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("holdout-data").toPath();
        Path recordsFile = dataDirectory.resolve("movie-block-generic-card-action-choice.jsonl");
        LearningStep heldOut = trainingRecord(1, List.of("reserved-deck"));
        LearningStep eligible = trainingRecord(2, List.of("training-deck"));
        JSONObject legacyRecord = trainingRecord(3).toJson();
        legacyRecord.remove("deckFingerprints");
        Files.write(recordsFile, List.of(
                heldOut.toJson().toJSONString(), eligible.toJson().toJSONString(),
                legacyRecord.toJSONString()), StandardCharsets.UTF_8);
        FotrStartersLearningStepsPersistence persistence = new FotrStartersLearningStepsPersistence(
                dataDirectory, compaction -> { }, stats -> { }, false, Set.of("reserved-deck"));

        List<LearningStep> loaded = persistence.load(new GenericCardActionChoiceTrainer(), 10);

        assertEquals(List.of("game-2"), loaded.stream().map(step -> step.gameId).toList());
        assertEquals(List.of("training-deck"), loaded.getFirst().deckFingerprints);
        assertEquals(new FotrStartersLearningStepsPersistence.HoldoutFilterSummary(1, 1),
                persistence.holdoutFilterSummary());
        assertEquals(List.of(), LearningStep.fromJson(legacyRecord.toJSONString()).deckFingerprints);
    }

    @Test
    public void sharedRecordsPreserveEveryTrainerMembershipAndMixWithLegacyFiles() throws Exception {
        Path legacyDir = temporaryFolder.newFolder("per-trainer").toPath();
        Path sharedDir = temporaryFolder.newFolder("shared").toPath();
        AtomicReference<FotrStartersLearningStepsPersistence.AppendStats> stats = new AtomicReference<>();
        var oldWriter = new FotrStartersLearningStepsPersistence(
                legacyDir, ignored -> { }, ignored -> { }, false);
        var sharedWriter = new FotrStartersLearningStepsPersistence(
                sharedDir, ignored -> { }, stats::set, false, Set.of(),
                FotrStartersLearningStepsPersistence.DataLayout.SHARED_V1);
        List<LearningStep> steps = List.of(trainingRecord(1, List.of("deck-a", "deck-b")),
                trainingRecord(2, List.of("deck-c", "deck-d")));
        steps.getFirst().trainingWeight = 0.25;
        oldWriter.save(steps);
        sharedWriter.save(steps);
        assertEquals(2, stats.get().storedRecords());
        assertTrue(stats.get().trainerRecords() > stats.get().storedRecords());
        assertEquals(Files.size(sharedDir.resolve("movie-block-shared-v1.jsonl.gz")),
                stats.get().appendedBytes());
        for (Trainer trainer : FotrStarterTrainerCatalog.createAll()) {
            assertEquals(oldWriter.load(trainer, 100).stream().map(step -> step.toJson().toJSONString()).toList(),
                    sharedWriter.load(trainer, 100).stream().map(step -> step.toJson().toJSONString()).toList());
        }

        var mixedWriter = new FotrStartersLearningStepsPersistence(sharedDir, ignored -> { }, ignored -> { }, false);
        mixedWriter.save(List.of(trainingRecord(3, List.of("deck-e"))));
        sharedWriter.save(List.of(trainingRecord(4, List.of("deck-f")), trainingRecord(5)));
        var trainer = new GenericCardActionChoiceTrainer();
        assertEquals(List.of("game-3", "game-1", "game-2", "game-4", "game-5"),
                mixedWriter.load(trainer, 10).stream().map(step -> step.gameId).toList());
        assertEquals(mixedWriter.load(trainer, 2).stream().map(step -> step.gameId).toList(),
                mixedWriter.load(trainer, 2).stream().map(step -> step.gameId).toList());

        var holdoutReader = new FotrStartersLearningStepsPersistence(
                sharedDir, ignored -> { }, ignored -> { }, false, Set.of("deck-b", "deck-f"));
        assertEquals(List.of("game-3", "game-2"),
                holdoutReader.load(trainer, 10).stream().map(step -> step.gameId).toList());
        assertEquals(2, holdoutReader.holdoutFilterSummary().heldOutRecords());
        assertEquals(1, holdoutReader.holdoutFilterSummary().unattributedRecords());
    }

    @Test
    public void sharedLayoutPreservesLegacyFilesAndRefusesCorruptionOrCompaction() throws Exception {
        Path directory = temporaryFolder.newFolder("shared-safety").toPath();
        Path legacy = directory.resolve("movie-block-generic-card-action-choice.jsonl");
        Files.writeString(legacy, trainingRecord(1).toJson().toJSONString() + System.lineSeparator());
        byte[] before = Files.readAllBytes(legacy);
        var shared = new FotrStartersLearningStepsPersistence(directory, ignored -> { }, ignored -> { },
                false, Set.of(), FotrStartersLearningStepsPersistence.DataLayout.SHARED_V1);
        shared.save(List.of(trainingRecord(2)));
        org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(legacy));
        assertThrows(IllegalStateException.class, () -> new FotrStartersLearningStepsPersistence(directory)
                .load(new GenericCardActionChoiceTrainer(), 1));
        assertThrows(IllegalArgumentException.class, () -> new FotrStartersLearningStepsPersistence(
                directory, ignored -> { }, ignored -> { }, true, Set.of(),
                FotrStartersLearningStepsPersistence.DataLayout.SHARED_V1));
        Path malformed = directory.resolve("movie-block-shared-v1.jsonl");
        Files.writeString(malformed, "{\"schema\":2,\"trainers\":[],\"step\":{}}\n");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> shared.load(new GenericCardActionChoiceTrainer(), 20));
        assertTrue(error.getMessage().contains("movie-block-shared-v1.jsonl:1"));
        Files.writeString(malformed, "{\"schema\":1,\"trainers\":[],\"step\":{}}\n");
        assertThrows(IllegalStateException.class, () -> shared.load(new GenericCardActionChoiceTrainer(), 20));
        Files.writeString(malformed, "{\"schema\":1,\"trainers\":[42],\"step\":{}}\n");
        assertThrows(IllegalStateException.class, () -> shared.load(new GenericCardActionChoiceTrainer(), 20));
    }

    @Test
    public void sharedLayoutAvoidsDuplicatingSyntheticTrainerRecords() throws Exception {
        Path oldDir = temporaryFolder.newFolder("old-bytes").toPath();
        Path newDir = temporaryFolder.newFolder("shared-bytes").toPath();
        AtomicReference<FotrStartersLearningStepsPersistence.AppendStats> oldStats = new AtomicReference<>();
        AtomicReference<FotrStartersLearningStepsPersistence.AppendStats> newStats = new AtomicReference<>();
        List<LearningStep> steps = java.util.stream.IntStream.range(0, 500)
                .mapToObj(index -> trainingRecord(index, List.of("deck-" + index))).toList();
        new FotrStartersLearningStepsPersistence(oldDir, ignored -> { }, oldStats::set, false).save(steps);
        new FotrStartersLearningStepsPersistence(newDir, ignored -> { }, newStats::set,
                false, Set.of(), FotrStartersLearningStepsPersistence.DataLayout.SHARED_V1).save(steps);
        assertEquals(oldStats.get().trainerRecords(), newStats.get().trainerRecords());
        assertEquals(500, newStats.get().storedRecords());
        assertTrue(newStats.get().appendedBytes() < oldStats.get().appendedBytes());
    }

    @Test
    public void sharedAndPerTrainerExamplesProduceEquivalentWholeGameModelFits() throws Exception {
        Path oldDir = temporaryFolder.newFolder("old-fit").toPath();
        Path newDir = temporaryFolder.newFolder("shared-fit").toPath();
        var oldWriter = new FotrStartersLearningStepsPersistence(oldDir, ignored -> { }, ignored -> { }, false);
        var sharedWriter = new FotrStartersLearningStepsPersistence(newDir, ignored -> { }, ignored -> { },
                false, Set.of(), FotrStartersLearningStepsPersistence.DataLayout.SHARED_V1);
        List<LearningStep> steps = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> trainingRecord(index, List.of("deck-" + index))).toList();
        oldWriter.save(steps);
        sharedWriter.save(steps);
        var trainer = new GenericCardActionChoiceTrainer();
        var oldFit = trainer.trainIfReadyWithMetrics(oldWriter.load(trainer, 200));
        var sharedFit = trainer.trainIfReadyWithMetrics(sharedWriter.load(trainer, 200));
        assertEquals("trained", oldFit.status());
        assertEquals(oldFit.status(), sharedFit.status());
        assertEquals(oldFit.trainingExamples(), sharedFit.trainingExamples());
        assertEquals(oldFit.validationExamples(), sharedFit.validationExamples());
        assertEquals(oldFit.validationAccuracy(), sharedFit.validationAccuracy());
    }

    private static LearningStep trainingRecord(int index) {
        return trainingRecord(index, List.of());
    }

    private static LearningStep trainingRecord(int index, List<String> deckFingerprints) {
        return trainingRecord(index, deckFingerprints, "Optional responses", 1.0);
    }

    private static LearningStep trainingRecord(int index, String decisionText, double trainingWeight) {
        return trainingRecord(index, List.of(), decisionText, trainingWeight);
    }

    private static LearningStep trainingRecord(
            int index, List<String> deckFingerprints, String decisionText, double trainingWeight) {
        CardActionSelectionDecision decision = new CardActionSelectionDecision(
                index,
                decisionText,
                List.of("42"),
                List.of("1_2"),
                List.of("Play card")) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
            }
        };
        CardActionChoiceAction action = new CardActionChoiceAction("", decision, null);
        LearningStep step = new LearningStep(
                new double[]{index}, action, true, decision, "game-" + index, deckFingerprints);
        step.reward = index % 2;
        step.trainingWeight = trainingWeight;
        return step;
    }
}
