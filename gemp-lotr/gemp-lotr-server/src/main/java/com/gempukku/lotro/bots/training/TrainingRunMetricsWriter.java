package com.gempukku.lotro.bots.training;

import com.alibaba.fastjson2.JSON;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TrainingRunMetricsWriter implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final int SCHEMA_VERSION = 1;

    private final String runId;
    private final Path file;
    private final BufferedWriter writer;

    public TrainingRunMetricsWriter(Path directory, Map<String, Object> metadata) throws IOException {
        Files.createDirectories(directory);
        runId = FILE_TIMESTAMP.format(Instant.now()) + "-" + UUID.randomUUID();
        file = directory.resolve("bot-training-" + runId + ".jsonl");
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            append("run_started", metadata);
        } catch (UncheckedIOException e) {
            try {
                writer.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    public String runId() {
        return runId;
    }

    public Path file() {
        return file;
    }

    public synchronized void append(String type, Object data) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", SCHEMA_VERSION);
        record.put("runId", runId);
        record.put("recordedAt", Instant.now().toString());
        record.put("type", type);
        record.put("data", data);
        try {
            writer.write(JSON.toJSONString(record));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write bot training metrics to " + file, e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
