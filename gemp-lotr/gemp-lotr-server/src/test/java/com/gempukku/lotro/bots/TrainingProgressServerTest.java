package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.training.TrainingProgress;
import com.gempukku.lotro.bots.training.TrainingProgressServer;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.simulation.BatchTacticalMetrics;
import com.gempukku.lotro.bots.simulation.SimulationStats;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrainingProgressServerTest {
    @Test
    public void recordsIndividualPolicyPromotionAndFinalReleaseStatus() {
        TrainingProgress progress = new TrainingProgress();
        progress.beginFormat("shared-policy", 10);
        progress.modelCompleted("BurdenTrainer", "trained", 100, 60, 40,
                80, 20, 82.0, 75.0, 65.0, 5);
        progress.beginFormat("movie", 10);
        progress.policyModelComparisonCompleted("BurdenTrainer", 1, 75.0, 65.0,
                12, 20, 0, 60.0, 58.0, 52.0, true, true);

        TrainingProgress.ModelProgress model = (TrainingProgress.ModelProgress)
                ((List<?>) progress.snapshot().get("models")).getFirst();
        TrainingProgress.PolicyComparison comparison = (TrainingProgress.PolicyComparison)
                ((List<?>) progress.snapshot().get("policyComparisons")).getFirst();

        assertEquals("selected-for-final-release", model.state());
        assertEquals(65.0, model.incumbentValidationAccuracy(), 0.0);
        assertEquals("BurdenTrainer", comparison.modelName());
        assertEquals("Selected for final release", comparison.selection());
    }

    @Test
    public void aggregatesSuccessfullyRecoveredDecisionCyclesPerBatch() {
        TrainingProgress progress = new TrainingProgress();
        progress.beginFormat("movie", 1);
        for (int i = 0; i < 100; i++) {
            progress.recordEvent(TrainingEventReporter.Severity.WARNING, "repeated-decision-cycle",
                    null, "A safe alternate answer was selected.", null);
        }

        assertEquals(0, progress.eventPage(TrainingEventReporter.Severity.WARNING, 1, 10).total());

        progress.batchCompleted("movie generation 1 self-play", 1, 1);

        TrainingProgress.EventPage successEvents =
                progress.eventPage(TrainingEventReporter.Severity.SUCCESS, 1, 10);
        assertTrue(successEvents.events().stream()
                .anyMatch(event -> event.type().equals("decision-cycle-recoveries")
                        && event.message().contains("100 repeated-decision cycles")));
    }

    @Test
    public void eventPagesShowNewestEventsFirst() {
        TrainingProgress progress = new TrainingProgress();
        for (int i = 1; i <= 12; i++) {
            progress.recordEvent(TrainingEventReporter.Severity.WARNING, "warning-" + i,
                    null, "Warning " + i, null);
        }

        TrainingProgress.EventPage firstPage =
                progress.eventPage(TrainingEventReporter.Severity.WARNING, 1, 10);
        TrainingProgress.EventPage secondPage =
                progress.eventPage(TrainingEventReporter.Severity.WARNING, 2, 10);

        assertEquals(12, firstPage.total());
        assertEquals("warning-12", firstPage.events().get(0).type());
        assertEquals("warning-3", firstPage.events().get(9).type());
        assertEquals("warning-2", secondPage.events().get(0).type());
        assertEquals("warning-1", secondPage.events().get(1).type());
    }

    @Test
    public void servesDashboardAndReadOnlyJsonProgress() throws Exception {
        TrainingProgress progress = new TrainingProgress();
        progress.setPlan(List.of(
                new TrainingProgress.PlannedStage("movie", "deck-builder-evaluation", 0, 1),
                new TrainingProgress.PlannedStage("movie", "decision-model-fitting", 1, 1),
                new TrainingProgress.PlannedStage(
                        "movie", "movie generation 1 evaluation policy-change-1-candidate-first", 1, 2),
                new TrainingProgress.PlannedStage(
                        "movie", "movie generation 1 evaluation policy-change-1-candidate-second", 1, 2),
                new TrainingProgress.PlannedStage("movie", "decision-model-fitting", 2, 1),
                new TrainingProgress.PlannedStage(
                        "movie", "movie generation 2 evaluation policy-change-1-candidate-first", 2, 2),
                new TrainingProgress.PlannedStage(
                        "movie", "movie generation 2 evaluation policy-change-1-candidate-second", 2, 2)));
        progress.beginFormat("movie");
        progress.beginStage("deck-builder-evaluation", 0, 1);
        progress.gameCompleted();
        progress.deckBuilderCompleted(new DeckBuilderTrainer.TrainingResult(
                null, List.of(), 2, 1, 1, 0, 8, 4, 4,
                "Smile 7 logistic", Double.NaN, Double.NaN, false, 0));
        progress.beginStage("decision-model-fitting", 1, 1);
        progress.modelQueued("MoveAgainTrainer");
        progress.modelStarted("MoveAgainTrainer");
        progress.modelCompleted("MoveAgainTrainer", "trained", 20, 12, 8,
                16, 4, 92.5, 75.0, 4);
        progress.beginStage("movie generation 1 evaluation policy-change-1-candidate-first", 1, 2);
        progress.gameCompleted();
        progress.gameCompleted();
        progress.batchCompleted("movie generation 1 evaluation policy-change-1-candidate-first",
                new SimulationStats(1, 1, 2, BatchTacticalMetrics.empty()));
        progress.beginStage("movie generation 1 evaluation policy-change-1-candidate-second", 1, 2);
        progress.gameCompleted();
        progress.gameCompleted();
        progress.batchCompleted("movie generation 1 evaluation policy-change-1-candidate-second",
                new SimulationStats(2, 0, 2, BatchTacticalMetrics.empty()));
        progress.modelComparisonCompleted(25.0, false);
        progress.beginStage("decision-model-fitting", 2, 1);
        progress.modelQueued("MoveAgainTrainer");
        progress.modelStarted("MoveAgainTrainer");
        progress.modelCompleted("MoveAgainTrainer", "trained", 24, 14, 10,
                18, 6, 90.0, 70.0, 5);
        progress.beginStage("movie generation 2 evaluation policy-change-1-candidate-first", 2, 2);
        progress.gameCompleted();
        progress.batchCompleted("movie generation 2 evaluation policy-change-1-candidate-first",
                new SimulationStats(1, 0, 1, BatchTacticalMetrics.empty(), 1));
        progress.recordEvent(TrainingEventReporter.Severity.WARNING, "test-warning",
                "MoveAgainTrainer", "Test warning detail.", new IllegalArgumentException("warning cause"));
        progress.recordEvent(TrainingEventReporter.Severity.ERROR, "test-error",
                "MoveAgainTrainer", "Test error detail.", new IllegalStateException("simulation failure"));

        try (TrainingProgressServer server = new TrainingProgressServer("127.0.0.1", 0, progress)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + server.getPort());

            HttpResponse<String> page = client.send(
                    HttpRequest.newBuilder(base.resolve("/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> favicon = client.send(
                    HttpRequest.newBuilder(base.resolve("/favicon.svg")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> status = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/status")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("<title>GEMP LOTR Bot Training</title>"));
            assertTrue(page.body().contains("href=\"/favicon.svg\""));
            assertTrue(page.body().contains("completion-status"));
            assertTrue(page.body().contains("run-time"));
            assertTrue(page.body().contains("Last updated ${formatTimestamp(data.updatedAt)}"));
            assertEquals(200, favicon.statusCode());
            assertTrue(favicon.headers().firstValue("Content-Type").orElse("").contains("image/svg+xml"));
            assertTrue(favicon.body().contains("<svg"));
            assertTrue(page.body().contains("Training roadmap"));
            assertTrue(page.body().contains("Choose a generation to inspect its progress and results across formats"));
            assertTrue(page.body().contains("id=\"previous-generation\""));
            assertTrue(page.body().contains("id=\"next-generation\""));
            assertTrue(page.body().contains("function generationForView(data)"));
            assertTrue(page.body().contains("Number(stage.generation) === viewGeneration"));
            assertTrue(page.body().contains("Number(model.generation) === viewGeneration"));
            assertTrue(page.body().contains("class=\"generation-controls sticky-generation-controls\""));
            assertTrue(page.body().contains("position: sticky"));
            assertTrue(page.body().contains("id=\"overall-progress\""));
            assertTrue(page.body().contains("data-event-severity=\"warning\""));
            assertTrue(page.body().contains("id=\"event-dialog\""));
            assertTrue(page.body().contains("Deck builder: options and ranking model"));
            assertTrue(page.body().contains("Format win rate"));
            assertTrue(page.body().contains("overall-segment-fill"));
            assertTrue(page.body().contains("name=\"comparison-generation-scope\" value=\"all\""));
            assertTrue(page.body().contains("renderPolicyComparisonTable(data.policyComparisons || [], viewGeneration)"));
            assertTrue(page.body().contains("Number(batch.generation) === viewGeneration"));
            assertTrue(page.body().contains(
                    "generation 1 stages before any generation 2 stage starts"));
            assertTrue(page.body().contains("overall-bar"));
            assertTrue(page.body().contains("Active generation"));
            assertTrue(page.body().contains("Game results"));
            assertTrue(page.body().contains("Individual policy changes: game evaluation and promotion"));
            assertTrue(page.body().contains("selected-for-final-release"));
            assertTrue(page.body().contains("Learning choices matched"));
            assertTrue(page.body().contains("Check choices matched"));
            assertTrue(page.body().contains("These scores measure how well it repeats recorded choices, not whether the bot wins"));
            assertTrue(page.body().contains("data-help="));
            assertTrue(page.body().contains("aria-expanded=\"false\""));
            assertTrue(page.body().contains("popover.className = \"help-popover\""));
            assertTrue(page.body().contains("event.key === \"Escape\""));
            assertTrue(page.body().contains("Examples (total / learning / check)"));
            assertTrue(page.body().contains("pending"));
            assertEquals(200, status.statusCode());
            assertTrue(status.body().contains("MoveAgainTrainer"));
            assertTrue(status.body().contains("\"positiveExamples\":12"));
            assertTrue(status.body().contains("\"trainingExamples\":16"));
            assertTrue(status.body().contains("\"validationExamples\":4"));
            assertTrue(status.body().contains("\"trainingAccuracy\":92.5"));
            assertTrue(status.body().contains("\"validationAccuracy\":75.0"));
            assertTrue(status.body().contains("\"generation\":2"));
            assertTrue(status.body().contains("\"validationAccuracy\":70.0"));
            assertTrue(status.body().contains("\"tacticalMetrics\""));
            assertTrue(status.body().contains("\"skippedGames\":1"));
            assertTrue(status.body().contains("1 wins / 0 losses / 1 skipped"));
            assertTrue(status.body().contains("\"eventCounts\":{\"success\":"));
            assertTrue(status.body().contains("\"warning\":1"));
            assertTrue(status.body().contains("\"error\":1"));

            HttpResponse<String> events = client.send(
                    HttpRequest.newBuilder(base.resolve(
                            "/api/events?severity=error&page=1&pageSize=10")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, events.statusCode());
            assertTrue(events.body().contains("\"type\":\"test-error\""));
            assertTrue(events.body().contains("\"format\":\"movie\""));
            assertTrue(events.body().contains("\"generation\":2"));
            assertTrue(events.body().contains("\"step\":\"movie generation 2 evaluation policy-change-1-candidate-first\""));
            assertTrue(events.body().contains("\"model\":\"MoveAgainTrainer\""));
            assertTrue(events.body().contains("java.lang.IllegalStateException: simulation failure"));
            assertTrue(status.body().contains("Smile 7 logistic"));
            assertFalse(status.body().contains("NaN"));
            assertTrue(status.body().contains("\"overallCompleted\":8"));
            assertTrue(status.body().contains("\"overallTotal\":11"));
            assertTrue(status.body().contains("\"generationCount\":2"));
            assertTrue(status.body().contains("deck-builder-evaluation"));
            assertTrue(status.body().contains("decision-model-fitting"));
            assertTrue(status.body().contains("\"state\":\"running\""));

            progress.gameCompleted();
            progress.batchCompleted("movie generation 2 evaluation policy-change-1-candidate-first",
                    new SimulationStats(1, 1, 2, BatchTacticalMetrics.empty()));
            progress.beginStage("movie generation 2 evaluation policy-change-1-candidate-second", 2, 2);
            progress.gameCompleted();
            progress.gameCompleted();
            progress.batchCompleted("movie generation 2 evaluation policy-change-1-candidate-second",
                    new SimulationStats(1, 1, 2, BatchTacticalMetrics.empty()));
            progress.modelComparisonCompleted(50.0, false);
            progress.finish("completed");
            HttpResponse<String> completedStatus = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/status")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(completedStatus.body().contains("\"status\":\"completed\""));
            assertTrue(completedStatus.body().contains("\"completedAt\":\""));
            assertTrue(completedStatus.body().contains("\"elapsedMillis\":"));
            assertTrue(completedStatus.body().contains("\"overallPercent\":100.0"));
            assertTrue(completedStatus.body().contains("\"candidateWinRate\":50.0"));
        }
    }
}
