package com.gempukku.lotro.bots.training;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.training.BotModelTrainingMain.TrainingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckBuilderTrainingServerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void servesDefaultsStartsOneRunWithOverridesAndShowsItsFinalStatus() throws Exception {
        TrainingConfig config = createConfig();
        CountDownLatch runnerStarted = new CountDownLatch(1);
        CountDownLatch finishRun = new CountDownLatch(1);
        AtomicReference<TrainingConfig> receivedConfig = new AtomicReference<>();
        DeckBuilderTrainingServer.TrainingRunner runner = (runConfig, progress) -> {
            receivedConfig.set(runConfig);
            runnerStarted.countDown();
            try {
                if (!finishRun.await(5, TimeUnit.SECONDS))
                    throw new IOException("Timed out waiting for test release");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Test runner interrupted", e);
            }
            progress.start();
            progress.setPlan(List.of(new TrainingProgress.PlannedStage(
                    "movie", "movie generation 1 deck-builder-evaluation", 1, 1)));
            progress.beginFormat("movie", 1);
            progress.beginStage("movie generation 1 deck-builder-evaluation", 1, 1);
            progress.gameCompleted();
            progress.deckBuilderCompleted(new DeckBuilderTrainer.TrainingResult(
                    null, List.of(), 1, 1, 1, 0,
                    0, 0, 0, 0, "not-ready", 0, 0, false, 0));
            progress.finish("completed");
        };
        HttpClient client = HttpClient.newHttpClient();

        try (DeckBuilderTrainingServer server = new DeckBuilderTrainingServer(config, runner)) {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + server.getPort());

            JSONObject defaults = getJson(client, base.resolve("/api/config"));
            assertEquals(2, defaults.getIntValue("generations"));
            assertEquals(List.of("fotr_block", "movie"), defaults.getJSONArray("formats").toJavaList(String.class));

            HttpResponse<String> started = postJson(client, base.resolve("/api/start"), Map.of(
                    "formats", List.of("movie"),
                    "generations", 3,
                    "candidatesPerGeneration", 10,
                    "gamesPerCandidate", 4,
                    "simulationThreads", 2));
            assertEquals(202, started.statusCode());
            assertTrue(runnerStarted.await(2, TimeUnit.SECONDS));

            TrainingConfig runConfig = receivedConfig.get();
            assertEquals(List.of("movie"), runConfig.formats());
            assertEquals(3, runConfig.generations());
            assertEquals(10, runConfig.deckBuilderCandidatesPerGeneration());
            assertEquals(4, runConfig.deckBuilderGamesPerCandidate());
            assertEquals(2, runConfig.threads());
            assertEquals(2, config.generations());

            HttpResponse<String> duplicate = postJson(client, base.resolve("/api/start"), Map.of(
                    "formats", List.of("movie"),
                    "generations", 1,
                    "candidatesPerGeneration", 2,
                    "gamesPerCandidate", 2,
                    "simulationThreads", 1));
            assertEquals(409, duplicate.statusCode());

            finishRun.countDown();
            JSONObject result = awaitState(client, base.resolve("/api/status"), "completed");
            assertEquals(1, result.getJSONArray("deckBuilders").size());
            assertEquals("completed", result.getString("status"));
        } finally {
            finishRun.countDown();
        }
    }

    @Test
    void rejectsInvalidSettingsWithoutStartingTraining() throws Exception {
        TrainingConfig config = createConfig();
        AtomicReference<Boolean> runnerInvoked = new AtomicReference<>(false);
        try (DeckBuilderTrainingServer server = new DeckBuilderTrainingServer(config,
                (runConfig, progress) -> runnerInvoked.set(true))) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/start");
            HttpResponse<String> response = postJson(HttpClient.newHttpClient(), endpoint, Map.of(
                    "formats", List.of("movie"),
                    "generations", 1,
                    "candidatesPerGeneration", 2,
                    "gamesPerCandidate", 3,
                    "simulationThreads", 1));

            assertEquals(400, response.statusCode());
            assertFalse(runnerInvoked.get());
            assertEquals("idle", getJson(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getPort() + "/api/status")).getString("runState"));
        }
    }

    private TrainingConfig createConfig() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Files.writeString(temporaryDirectory.resolve("references.json"), "{}");
        Path configFile = temporaryDirectory.resolve("deck-only.properties");
        Files.writeString(configFile, "training.mode=deck-builder-only\n"
                + "training.deckbuilder.reference.manifest=references.json\n"
                + "training.formats=fotr_block,movie\n"
                + "training.generations=2\n"
                + "training.deckbuilder.candidates.per.generation=4\n"
                + "training.deckbuilder.games.per.candidate=4\n"
                + "training.cpu.threads=1\n"
                + "training.dashboard.host=127.0.0.1\n"
                + "training.dashboard.port=" + port + "\n");
        return TrainingConfig.load(new String[]{"--config=" + configFile});
    }

    private static HttpResponse<String> postJson(
            HttpClient client, URI endpoint, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JSONObject getJson(HttpClient client, URI endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint).GET().build();
        return JSON.parseObject(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private static JSONObject awaitState(HttpClient client, URI endpoint, String expectedState) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            JSONObject status = getJson(client, endpoint);
            if (expectedState.equals(status.getString("runState")))
                return status;
            Thread.sleep(10);
        }
        throw new AssertionError("Training run did not reach " + expectedState);
    }
}
