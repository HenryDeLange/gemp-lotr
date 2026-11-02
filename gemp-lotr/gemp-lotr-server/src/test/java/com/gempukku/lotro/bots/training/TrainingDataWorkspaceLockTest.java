package com.gempukku.lotro.bots.training;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrainingDataWorkspaceLockTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preventsConcurrentRunsFromWritingTheSameCorpusAndReleasesOnClose() throws Exception {
        Path dataDirectory = temporaryFolder.newFolder("shared-data").toPath();
        try (TrainingDataWorkspaceLock first = TrainingDataWorkspaceLock.acquire(dataDirectory)) {
            assertNotNull(first);
            try {
                TrainingDataWorkspaceLock.acquire(dataDirectory.resolve("."));
                fail("A second training run must not lock the same normalized corpus directory");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("already owns data directory"));
            }
        }

        try (TrainingDataWorkspaceLock afterRelease = TrainingDataWorkspaceLock.acquire(dataDirectory)) {
            assertNotNull(afterRelease);
        }
    }

    @Test
    public void allowsIndependentTrainingDataDirectories() throws Exception {
        Path firstDirectory = temporaryFolder.newFolder("first-data").toPath();
        Path secondDirectory = temporaryFolder.newFolder("second-data").toPath();
        try (TrainingDataWorkspaceLock first = TrainingDataWorkspaceLock.acquire(firstDirectory);
             TrainingDataWorkspaceLock second = TrainingDataWorkspaceLock.acquire(secondDirectory)) {
            assertNotNull(first);
            assertNotNull(second);
        }
    }
}
