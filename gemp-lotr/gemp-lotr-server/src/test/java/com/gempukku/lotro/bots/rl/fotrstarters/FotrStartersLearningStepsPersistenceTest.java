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

    private static LearningStep trainingRecord(int index) {
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
                new double[]{index}, action, true, decision, "game-" + index);
        step.reward = index % 2;
        return step;
    }
}
