package com.gempukku.lotro.bots.training;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.simulation.BatchTacticalMetrics;
import com.gempukku.lotro.bots.simulation.SimulationStats;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrainingRunMetricsWriterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesProgressAndDiagnosticEventsAsStructuredJsonLines() throws Exception {
        Path metricsDirectory = temporaryFolder.newFolder().toPath();
        TrainingProgress progress = new TrainingProgress();
        Path metricsFile;
        try (TrainingRunMetricsWriter writer = new TrainingRunMetricsWriter(
                metricsDirectory, Map.of("formats", List.of("movie"), "generations", 1))) {
            metricsFile = writer.file();
            progress.setMetricsWriter(writer);
            progress.setPlan(List.of(
                    new TrainingProgress.PlannedStage("movie", "decision-model-fitting", 1, 1),
                    new TrainingProgress.PlannedStage(
                            "movie", "movie generation 1 evaluation candidate-first", 1, 2),
                    new TrainingProgress.PlannedStage(
                            "movie", "movie generation 1 evaluation candidate-second", 1, 2)));
            progress.beginFormat("movie", 1);
            progress.beginStage("decision-model-fitting", 1, 1);
            progress.modelQueued("MoveAgainTrainer");
            progress.modelStarted("MoveAgainTrainer");
            progress.modelCompleted("MoveAgainTrainer", "trained", 20, 12, 8,
                    16, 4, 92.5, 75.0, 4);
            progress.beginStage("movie generation 1 evaluation candidate-first", 1, 2);
            progress.batchCompleted("movie generation 1 evaluation candidate-first",
                    new SimulationStats(2, 1, 3, BatchTacticalMetrics.empty(), 1));
            progress.beginStage("movie generation 1 evaluation candidate-second", 1, 2);
            progress.batchCompleted("movie generation 1 evaluation candidate-second",
                    new SimulationStats(1, 2, 3, BatchTacticalMetrics.empty()));
            progress.modelComparisonCompleted(66.6667, true, 55.0, 51.0);
            progress.recordEvent(TrainingEventReporter.Severity.WARNING, "test-warning",
                    "MoveAgainTrainer", "Check this result.", new IllegalArgumentException("warning cause"));
            progress.recordEvent(TrainingEventReporter.Severity.ERROR, "test-error",
                    "MoveAgainTrainer", "A simulation failed.", new IllegalStateException("error cause"));
            progress.finish("failed");
        }

        assertTrue(Files.isRegularFile(metricsFile));
        List<String> lines = Files.readAllLines(metricsFile);
        List<JSONObject> records = lines.stream().map(JSON::parseObject).toList();
        assertEquals("run_started", records.getFirst().getString("type"));
        assertEquals(1, records.getFirst().getIntValue("schemaVersion"));
        assertTrue(records.stream().anyMatch(record -> record.getString("type").equals("model")
                && record.getJSONObject("data").getDoubleValue("validationAccuracy") == 75.0));
        assertTrue(records.stream().anyMatch(record -> record.getString("type").equals("batch")));
        assertTrue(records.stream().anyMatch(record -> record.getString("type").equals("policy_comparison")
                && record.getJSONObject("data").getIntValue("candidateWins") == 4
                && record.getJSONObject("data").getIntValue("games") == 6));

        List<JSONObject> events = records.stream()
                .filter(record -> record.getString("type").equals("event"))
                .map(record -> record.getJSONObject("data"))
                .toList();
        assertTrue(events.stream().anyMatch(event -> event.getString("severity").equals("warning")
                && event.getString("stackTrace").contains("warning cause")));
        assertTrue(events.stream().anyMatch(event -> event.getString("severity").equals("error")
                && event.getString("stackTrace").contains("error cause")));

        JSONObject finished = records.stream()
                .filter(record -> record.getString("type").equals("run_finished"))
                .map(record -> record.getJSONObject("data"))
                .findFirst()
                .orElseThrow();
        assertEquals("failed", finished.getString("status"));
        assertEquals(66.6667, finished.getJSONArray("policyComparisons")
                .getJSONObject(0).getDoubleValue("candidateWinRate"), 0.001);
        assertEquals(55.0, finished.getJSONArray("policyComparisons")
                .getJSONObject(0).getDoubleValue("aggregateCandidateWinRate"), 0.001);
        assertEquals(51.0, finished.getJSONArray("policyComparisons")
                .getJSONObject(0).getDoubleValue("aggregateLowerConfidenceBound"), 0.001);
        assertFalse(lines.isEmpty());
    }

    @Test
    public void writesTrainingFailureBeforeMetricsWriterCloses() throws Exception {
        Path metricsDirectory = temporaryFolder.newFolder().toPath();
        TrainingProgress progress = new TrainingProgress();
        Path metricsFile;
        try (TrainingRunMetricsWriter writer = new TrainingRunMetricsWriter(metricsDirectory, Map.of())) {
            metricsFile = writer.file();
            progress.setMetricsWriter(writer);
            BotModelTrainingMain.logTrainingFailure(
                    progress, new IllegalStateException("original training failure"));
            progress.finish("failed");
        }

        List<JSONObject> records = Files.readAllLines(metricsFile).stream()
                .map(JSON::parseObject)
                .toList();
        JSONObject failure = records.stream()
                .filter(record -> record.getString("type").equals("event"))
                .map(record -> record.getJSONObject("data"))
                .filter(event -> event.getString("type").equals("training-failure"))
                .findFirst()
                .orElseThrow();
        assertTrue(failure.getString("stackTrace").contains("original training failure"));
        JSONObject finished = records.stream()
                .filter(record -> record.getString("type").equals("run_finished"))
                .map(record -> record.getJSONObject("data"))
                .findFirst()
                .orElseThrow();
        assertEquals("failed", finished.getString("status"));
    }
}
