package com.gempukku.lotro.bots.training;

import com.alibaba.fastjson2.JSON;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class TrainingProgressServer implements AutoCloseable {
    private static final String DASHBOARD_RESOURCE = "/bot-training-dashboard.html";
    private static final String FAVICON_RESOURCE = "/bot-training-favicon.svg";

    private final HttpServer server;
    private final ExecutorService executor;
    private final TrainingProgress progress;
    private final String dashboardHtml;
    private final String faviconSvg;

    public TrainingProgressServer(String host, int port, TrainingProgress progress) throws IOException {
        this.progress = progress;
        this.dashboardHtml = loadDashboard();
        this.faviconSvg = loadFavicon();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        AtomicInteger dashboardThreadNumber = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable,
                    "bot-training-dashboard-worker-" + dashboardThreadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handleRequest);
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "GET only");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/api/status".equals(path)) {
            send(exchange, 200, "application/json; charset=utf-8", JSON.toJSONString(progress.snapshot()));
        } else if ("/api/events".equals(path)) {
            sendEventPage(exchange);
        } else if ("/".equals(path)) {
            send(exchange, 200, "text/html; charset=utf-8", dashboardHtml);
        } else if ("/favicon.svg".equals(path)) {
            send(exchange, 200, "image/svg+xml; charset=utf-8", faviconSvg);
        } else {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
        }
    }

    private void sendEventPage(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> parameters = queryParameters(exchange.getRequestURI().getRawQuery());
            Severity severity = Severity.valueOf(parameters.getOrDefault("severity", "error")
                    .toUpperCase(Locale.ROOT));
            int page = Integer.parseInt(parameters.getOrDefault("page", "1"));
            int pageSize = Integer.parseInt(parameters.getOrDefault("pageSize", "10"));
            TrainingProgress.EventPage eventPage = progress.eventPage(severity, page, pageSize);
            send(exchange, 200, "application/json; charset=utf-8", JSON.toJSONString(eventPage));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "text/plain; charset=utf-8",
                    "Invalid event query: " + e.getMessage());
        }
    }

    private static Map<String, String> queryParameters(String query) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (query == null || query.isBlank())
            return parameters;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 1 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            parameters.put(key, value);
        }
        return parameters;
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

    private static String loadDashboard() throws IOException {
        return loadResource(DASHBOARD_RESOURCE);
    }

    private static String loadFavicon() throws IOException {
        return loadResource(FAVICON_RESOURCE);
    }

    private static String loadResource(String resource) throws IOException {
        try (InputStream input = TrainingProgressServer.class.getResourceAsStream(resource)) {
            if (input == null)
                throw new IOException("Missing training UI resource " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
