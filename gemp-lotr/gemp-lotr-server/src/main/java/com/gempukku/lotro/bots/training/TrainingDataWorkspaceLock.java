package com.gempukku.lotro.bots.training;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class TrainingDataWorkspaceLock implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    private TrainingDataWorkspaceLock(Path directory, FileChannel channel, FileLock lock) {
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
    }

    public static TrainingDataWorkspaceLock acquire(Path directory) throws IOException {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (normalizedDirectory.getParent() == null)
            throw new IllegalArgumentException("training.data.directory must not be a filesystem root");
        Files.createDirectories(normalizedDirectory);
        FileChannel channel = FileChannel.open(
                normalizedDirectory.resolve(".training.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);

        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            closeAfterFailure(channel, e);
            throw new IOException("Another training run already owns data directory "
                    + normalizedDirectory, e);
        } catch (IOException e) {
            closeAfterFailure(channel, e);
            throw e;
        }
        if (lock == null) {
            channel.close();
            throw new IOException("Another training run already owns data directory " + normalizedDirectory);
        }
        return new TrainingDataWorkspaceLock(normalizedDirectory, channel, lock);
    }

    private static void closeAfterFailure(FileChannel channel, Exception failure) {
        try {
            channel.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            lock.release();
        } catch (IOException e) {
            failure = e;
        }
        try {
            channel.close();
        } catch (IOException e) {
            if (failure == null)
                failure = e;
            else
                failure.addSuppressed(e);
        }
        if (failure != null)
            throw new IOException("Unable to release training data lock for " + directory, failure);
    }
}
