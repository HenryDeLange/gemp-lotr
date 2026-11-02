package com.gempukku.lotro.bots.training;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class TrainingDataIO {
    private static final byte[] NEWLINE = System.lineSeparator().getBytes(StandardCharsets.UTF_8);

    private TrainingDataIO() {
    }

    public static AppendResult appendCompressedLines(Path file, List<String> lines) throws IOException {
        return appendLines(file, lines, true);
    }

    public static AppendResult appendPlainLines(Path file, List<String> lines) throws IOException {
        return appendLines(file, lines, false);
    }

    private static AppendResult appendLines(Path file, List<String> lines, boolean compressed) throws IOException {
        if (lines.isEmpty())
            return new AppendResult(0, 0);

        Files.createDirectories(file.getParent());
        long previousSize = Files.exists(file) ? Files.size(file) : 0;
        long jsonBytes = 0;
        if (compressed) {
            try (var output = Files.newOutputStream(
                    file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 var gzip = fastGzip(output);
                 var writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                    jsonBytes += line.getBytes(StandardCharsets.UTF_8).length + NEWLINE.length;
                }
            }
        } else {
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                    jsonBytes += line.getBytes(StandardCharsets.UTF_8).length + NEWLINE.length;
                }
            }
        }
        return new AppendResult(jsonBytes, Files.size(file) - previousSize);
    }

    private static GZIPOutputStream fastGzip(java.io.OutputStream output) throws IOException {
        return new GZIPOutputStream(output) {
            {
                def.setLevel(Deflater.BEST_SPEED);
            }
        };
    }

    public static void forEachLine(Path legacyFile, Path compressedFile, LineConsumer consumer)
            throws IOException {
        if (Files.isRegularFile(legacyFile)) {
            try (BufferedReader reader = Files.newBufferedReader(legacyFile, StandardCharsets.UTF_8)) {
                readLines(reader, legacyFile, consumer);
            }
        }
        if (Files.isRegularFile(compressedFile)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(compressedFile)), StandardCharsets.UTF_8))) {
                readLines(reader, compressedFile, consumer);
            }
        }
    }

    private static void readLines(BufferedReader reader, Path file, LineConsumer consumer) throws IOException {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null)
            consumer.accept(file, ++lineNumber, line);
    }

    public record AppendResult(long jsonBytes, long appendedBytes) {
    }

    @FunctionalInterface
    public interface LineConsumer {
        void accept(Path file, int lineNumber, String line) throws IOException;
    }
}
