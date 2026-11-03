package com.gempukku.lotro.bots.training;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class TrainingInputFingerprints {
    private static final byte[] FINGERPRINT_FORMAT =
            "gemp-training-input-fingerprint-v1".getBytes(StandardCharsets.UTF_8);
    private static final int BUFFER_SIZE = 64 * 1024;

    private TrainingInputFingerprints() {
    }

    static Fingerprint modelFiles(Path directory) throws IOException {
        return fingerprint(directory, path -> path.getFileName().toString().endsWith(".model"));
    }

    static Fingerprint trainingDataFiles(Path directory) throws IOException {
        return fingerprint(directory, path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".jsonl") || name.endsWith(".jsonl.gz");
        });
    }

    private static Fingerprint fingerprint(Path directory, Predicate<Path> included) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        List<Path> files;
        if (!Files.exists(root)) {
            files = List.of();
        } else {
            if (!Files.isDirectory(root))
                throw new IOException("Training fingerprint root is not a directory: " + root);
            try (Stream<Path> paths = Files.walk(root)) {
                files = paths.filter(Files::isRegularFile)
                        .filter(included)
                        .sorted(Comparator.comparing(path -> relativePath(root, path)))
                        .toList();
            }
        }

        MessageDigest digest = sha256();
        digest.update(FINGERPRINT_FORMAT);
        updateLong(digest, files.size());
        long bytesHashed = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        for (Path file : files) {
            String relativePath = relativePath(root, file);
            byte[] pathBytes = relativePath.getBytes(StandardCharsets.UTF_8);
            updateLong(digest, pathBytes.length);
            digest.update(pathBytes);

            long expectedSize = Files.size(file);
            updateLong(digest, expectedSize);
            long fileBytesHashed = 0;
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    fileBytesHashed += read;
                }
            }
            if (fileBytesHashed != expectedSize || Files.size(file) != expectedSize)
                throw new IOException("Training input changed while fingerprinting: " + file);
            bytesHashed += fileBytesHashed;
        }
        return new Fingerprint(HexFormat.of().formatHex(digest.digest()), files.size(), bytesHashed);
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString()
                .replace(file.getFileSystem().getSeparator(), "/");
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE)
            digest.update((byte) (value >>> shift));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    record Fingerprint(String sha256, int fileCount, long bytesHashed) {
    }
}
