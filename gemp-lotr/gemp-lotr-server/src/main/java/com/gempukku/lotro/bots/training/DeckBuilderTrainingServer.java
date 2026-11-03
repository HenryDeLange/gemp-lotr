package com.gempukku.lotro.bots.training;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.training.BotModelTrainingMain.TrainingConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeckBuilderTrainingServer implements AutoCloseable {
    private static final String DASHBOARD_RESOURCE = "/deck-builder-training-dashboard.html";
    private static final String FAVICON_RESOURCE = "/bot-training-favicon.svg";
    private static final int MAX_REQUEST_BYTES = 8_192;
    private static final Set<String> ALLOWED_SETTINGS =
            Set.of("formats", "generations", "candidatesPerGeneration", "gamesPerCandidate", "simulationThreads");
    private static final List<String> SUPPORTED_FORMATS =
            List.of("fotr_block", "ttt_block", "king_block", "movie");

    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ExecutorService trainingExecutor;
    private final TrainingRunner trainingRunner;
    private final Object runLock = new Object();
    private final String dashboardHtml;
    private final String faviconSvg;
    private final TrainingConfig baseConfig;

    private volatile TrainingProgress progress = new TrainingProgress();
    private volatile TrainingConfig activeConfig;
    private volatile String runState = "idle";
    private volatile String lastError = "";

    public DeckBuilderTrainingServer(TrainingConfig config) throws IOException {
        this(config, (runConfig, runProgress) -> BotModelTrainingMain.runTraining(runConfig, runProgress, false));
    }

    DeckBuilderTrainingServer(TrainingConfig config, TrainingRunner trainingRunner) throws IOException {
        if (!config.deckBuilderOnly())
            throw new IllegalArgumentException("The deck-builder training UI requires deck-builder-only mode");
        if (config.dashboardPort() == 0)
            throw new IllegalArgumentException("The deck-builder training UI requires a nonzero dashboard port");
        this.baseConfig = config;
        this.activeConfig = config;
        this.trainingRunner = trainingRunner;
        this.dashboardHtml = loadResource(DASHBOARD_RESOURCE);
        this.faviconSvg = loadResource(FAVICON_RESOURCE);

        InetAddress address = loopbackAddress(config.dashboardHost());
        this.server = HttpServer.create(new InetSocketAddress(address, config.dashboardPort()), 0);
        AtomicInteger httpThreadNumber = new AtomicInteger(1);
        this.httpExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable,
                    "deck-builder-training-ui-" + httpThreadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        this.trainingExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "deck-builder-training-run");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(httpExecutor);
        server.createContext("/", this::handleRequest);
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/start".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            handleStart(exchange);
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }
        switch (path) {
            case "/" -> send(exchange, 200, "text/html; charset=utf-8", dashboardHtml);
            case "/api/config" -> sendJson(exchange, 200, configSnapshot());
            case "/api/status" -> sendJson(exchange, 200, statusSnapshot());
            case "/favicon.svg" -> send(exchange, 200, "image/svg+xml; charset=utf-8", faviconSvg);
            default -> send(exchange, 404, "text/plain; charset=utf-8", "Not found");
        }
    }

    private void handleStart(HttpExchange exchange) throws IOException {
        try {
            JSONObject request = readJson(exchange.getRequestBody());
            Map<String, String> overrides = settingOverrides(request);
            TrainingConfig runConfig = TrainingConfig.load(
                    new String[]{"--config=" + baseConfig.configFile()}, overrides);
            if (!runConfig.deckBuilderOnly())
                throw new IllegalArgumentException("The deck-builder training UI only runs deck-builder-only mode");

            synchronized (runLock) {
                if ("running".equals(runState)) {
                    sendJson(exchange, 409, Map.of("error", "A training run is already in progress."));
                    return;
                }
                TrainingProgress nextProgress = new TrainingProgress();
                progress = nextProgress;
                activeConfig = runConfig;
                lastError = "";
                runState = "running";
                try {
                    trainingExecutor.execute(() -> executeRun(runConfig, nextProgress));
                } catch (RejectedExecutionException e) {
                    runState = "failed";
                    lastError = "The training runner is shutting down.";
                    sendJson(exchange, 503, Map.of("error", lastError));
                    return;
                }
            }
            sendJson(exchange, 202, statusSnapshot());
        } catch (JSONException | IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage() == null ? "Invalid run settings." : e.getMessage()));
        } catch (IOException | UncheckedIOException e) {
            System.err.println("Unable to start deck-builder training: " + e.getMessage());
            sendJson(exchange, 500, Map.of("error", "Unable to load training settings: " + e.getMessage()));
        }
    }

    private JSONObject readJson(InputStream requestBody) throws IOException {
        byte[] bytes;
        try (requestBody) {
            bytes = requestBody.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (bytes.length > MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("Run settings request is too large.");
        JSONObject request = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        if (request == null)
            throw new IllegalArgumentException("Run settings must be a JSON object.");
        return request;
    }

    private Map<String, String> settingOverrides(JSONObject request) {
        List<String> unknownSettings = new ArrayList<>(request.keySet());
        unknownSettings.removeAll(ALLOWED_SETTINGS);
        if (!unknownSettings.isEmpty())
            throw new IllegalArgumentException("Unsupported run settings: " + String.join(", ", unknownSettings));

        Object rawFormats = request.get("formats");
        if (!(rawFormats instanceof List<?> values) || values.isEmpty())
            throw new IllegalArgumentException("Select at least one training format.");
        List<String> formats = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String format) || !SUPPORTED_FORMATS.contains(format))
                throw new IllegalArgumentException("Unsupported training format: " + value);
            if (formats.contains(format))
                throw new IllegalArgumentException("A training format can only be selected once: " + format);
            formats.add(format);
        }

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("training.formats", String.join(",", formats));
        overrides.put("training.generations",
                Integer.toString(positiveInteger(request, "generations")));
        overrides.put("training.deckbuilder.candidates.per.generation",
                Integer.toString(positiveInteger(request, "candidatesPerGeneration")));
        overrides.put("training.deckbuilder.games.per.candidate",
                Integer.toString(positiveInteger(request, "gamesPerCandidate")));
        overrides.put("training.cpu.threads",
                Integer.toString(positiveInteger(request, "simulationThreads")));
        return overrides;
    }

    private static int positiveInteger(JSONObject request, String name) {
        Object value = request.get(name);
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.longValue()
                || number.longValue() < 1
                || number.longValue() > Integer.MAX_VALUE)
            throw new IllegalArgumentException(name + " must be a positive whole number.");
        return number.intValue();
    }

    private void executeRun(TrainingConfig runConfig, TrainingProgress runProgress) {
        try {
            trainingRunner.run(runConfig, runProgress);
            synchronized (runLock) {
                runState = "completed";
            }
        } catch (IOException | RuntimeException e) {
            Map<String, Object> snapshot = runProgress.snapshot();
            if (!"failed".equals(snapshot.get("status"))) {
                BotModelTrainingMain.logTrainingFailure(runProgress, e);
                try {
                    runProgress.finish("failed");
                } catch (UncheckedIOException metricsFailure) {
                    e.addSuppressed(metricsFailure);
                }
            }
            synchronized (runLock) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                runState = "failed";
            }
        }
    }

    private Map<String, Object> configSnapshot() {
        Map<String, Object> result = settingsSnapshot(baseConfig);
        result.put("supportedFormats", SUPPORTED_FORMATS);
        result.put("formatLabels", Map.of(
                "fotr_block", "Fellowship of the Ring",
                "ttt_block", "The Two Towers",
                "king_block", "The Return of the King",
                "movie", "Movie block"));
        return result;
    }

    private Map<String, Object> statusSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>(progress.snapshot());
        result.put("runState", runState);
        result.put("error", lastError);
        result.put("settings", settingsSnapshot(activeConfig));
        return result;
    }

    private static Map<String, Object> settingsSnapshot(TrainingConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configFile", config.configFile().toString());
        result.put("formats", config.formats());
        result.put("generations", config.generations());
        result.put("candidatesPerGeneration", config.deckBuilderCandidatesPerGeneration());
        result.put("gamesPerCandidate", config.deckBuilderGamesPerCandidate());
        result.put("simulationThreads", config.threads());
        result.put("modelDirectory", config.modelDirectory().toString());
        result.put("trainingDataDirectory", config.trainingDataDirectory().toString());
        result.put("referenceManifest", config.deckBuilderReferenceManifest().toString());
        result.put("holdoutManifest", config.holdoutDeckManifest() == null
                ? "Not configured" : config.holdoutDeckManifest().toString());
        result.put("metricsDirectory", config.metricsDirectory().toString());
        return result;
    }

    private static InetAddress loopbackAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!address.isLoopbackAddress())
                throw new IllegalArgumentException("The interactive training UI must bind to a loopback address.");
            return address;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve training UI host " + host, e);
        }
    }

    private static String loadResource(String resource) throws IOException {
        try (InputStream input = DeckBuilderTrainingServer.class.getResourceAsStream(resource)) {
            if (input == null)
                throw new IOException("Missing deck-builder training UI resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", JSON.toJSONString(body));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        httpExecutor.shutdownNow();
        trainingExecutor.shutdownNow();
    }

    @FunctionalInterface
    interface TrainingRunner {
        void run(TrainingConfig config, TrainingProgress progress) throws IOException;
    }
}
