package com.gempukku.lotro.bots.training;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TrainingInputFingerprintsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fingerprintsAreStableAndIncludeModelPathsAndContentsOnly() throws Exception {
        Path modelDirectory = temporaryDirectory.resolve("models");
        Files.createDirectories(modelDirectory.resolve("nested"));
        Files.writeString(modelDirectory.resolve("nested/B.model"), "second");
        Files.writeString(modelDirectory.resolve("A.model"), "first");
        Files.writeString(modelDirectory.resolve("README.txt"), "not a model");
        Files.writeString(modelDirectory.resolve(".training.lock"), "ignored lock");

        var original = TrainingInputFingerprints.modelFiles(modelDirectory);
        Files.writeString(modelDirectory.resolve("README.txt"), "edited documentation");
        Files.writeString(modelDirectory.resolve(".training.lock"), "changed lock");
        var unchanged = TrainingInputFingerprints.modelFiles(modelDirectory);

        assertEquals(2, original.fileCount());
        assertEquals(11, original.bytesHashed());
        assertEquals(original.sha256(), unchanged.sha256());

        Files.writeString(modelDirectory.resolve("nested/B.model"), "changed");
        assertNotEquals(original.sha256(), TrainingInputFingerprints.modelFiles(modelDirectory).sha256());
    }

    @Test
    void corpusFingerprintIncludesLegacyAndCompressedJsonlButIgnoresOtherFiles() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("corpus");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("decision.jsonl"), "legacy");
        Files.writeString(dataDirectory.resolve("deck.jsonl.gz"), "compressed");
        Files.writeString(dataDirectory.resolve("notes.txt"), "ignored");

        var original = TrainingInputFingerprints.trainingDataFiles(dataDirectory);
        assertEquals(2, original.fileCount());
        assertEquals(16, original.bytesHashed());

        Files.writeString(dataDirectory.resolve("notes.txt"), "changed notes");
        assertEquals(original.sha256(), TrainingInputFingerprints.trainingDataFiles(dataDirectory).sha256());

        Files.writeString(dataDirectory.resolve("deck.jsonl.gz"), "changed");
        assertNotEquals(original.sha256(), TrainingInputFingerprints.trainingDataFiles(dataDirectory).sha256());
    }
}
