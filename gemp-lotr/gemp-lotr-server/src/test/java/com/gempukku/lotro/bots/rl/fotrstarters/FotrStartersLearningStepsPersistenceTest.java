package com.gempukku.lotro.bots.rl.fotrstarters;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.rl.LearningStep;
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

    private static LearningStep trainingRecord(int index) {
        return trainingRecord(index, List.of());
    }

    private static LearningStep trainingRecord(int index, List<String> deckFingerprints) {
        CardActionSelectionDecision decision = new CardActionSelectionDecision(
                index,
                "Optional responses",
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
        return step;
    }
}
