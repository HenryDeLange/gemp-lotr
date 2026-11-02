package com.gempukku.lotro.bots.training;

import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class TrainingModelWorkspace implements AutoCloseable {
    private final Path modelDirectory;
    private final Path stagingDirectory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean published;

    private TrainingModelWorkspace(
            Path modelDirectory,
            Path stagingDirectory,
            FileChannel lockChannel,
            FileLock lock) {
        this.modelDirectory = modelDirectory;
        this.stagingDirectory = stagingDirectory;
        this.lockChannel = lockChannel;
        this.lock = lock;
    }

    public static TrainingModelWorkspace create(Path modelDirectory) throws IOException {
        Path normalizedDirectory = modelDirectory.toAbsolutePath().normalize();
        if (normalizedDirectory.getParent() == null)
            throw new IllegalArgumentException("models.directory must not be a filesystem root");
        Files.createDirectories(normalizedDirectory);

        FileChannel lockChannel = FileChannel.open(
                normalizedDirectory.resolve(".training.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("Another training run already owns " + normalizedDirectory, e);
        } catch (IOException e) {
            try {
                lockChannel.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        if (lock == null) {
            lockChannel.close();
            throw new IOException("Another training run already owns " + normalizedDirectory);
        }

        Path stagingDirectory = null;
        try {
            removeStaleTrainingDirectories(normalizedDirectory);
            stagingDirectory = Files.createTempDirectory(normalizedDirectory, ".training-");
            copyActiveRelease(normalizedDirectory, stagingDirectory);
            return new TrainingModelWorkspace(normalizedDirectory, stagingDirectory, lockChannel, lock);
        } catch (IOException | RuntimeException e) {
            if (stagingDirectory != null) {
                try {
                    deleteTree(stagingDirectory);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            try {
                lock.release();
            } catch (IOException releaseFailure) {
                e.addSuppressed(releaseFailure);
            }
            try {
                lockChannel.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static void copyActiveRelease(Path modelDirectory, Path stagingDirectory) throws IOException {
        Path activeRelease = modelDirectory.resolve(ModelIO.OBSERVATION_SCHEMA_DIRECTORY);
        if (!Files.exists(activeRelease, LinkOption.NOFOLLOW_LINKS))
            return;
        if (Files.isSymbolicLink(activeRelease))
            throw new IOException("Active model release must not be a symbolic link: " + activeRelease);

        Path stagedRelease = stagingDirectory.resolve(ModelIO.OBSERVATION_SCHEMA_DIRECTORY);
        Files.walkFileTree(activeRelease, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (attributes.isSymbolicLink())
                    throw new IOException("Active model release contains a symbolic link: " + directory);
                Files.createDirectories(stagedRelease.resolve(activeRelease.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink())
                    throw new IOException("Active model release contains a symbolic link: " + file);
                Files.copy(file, stagedRelease.resolve(activeRelease.relativize(file)),
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public Path getStagingDirectory() {
        return stagingDirectory;
    }

    public Path getStagedReleaseDirectory() {
        return stagingDirectory.resolve(ModelIO.OBSERVATION_SCHEMA_DIRECTORY);
    }

    public PublicationResult publish() throws IOException {
        if (published)
            throw new IllegalStateException("Training workspace has already been published");

        Path stagedRelease = getStagedReleaseDirectory();
        Path stagedPolicyDirectory = ModelIO.resolveModelDirectory(stagingDirectory, "fotr_block");
        int policyModelCount = countModelFiles(stagedPolicyDirectory);
        if (policyModelCount == 0)
            throw new IOException("Training produced no shared movie-trilogy policy models");

        Path activeRelease = modelDirectory.resolve(ModelIO.OBSERVATION_SCHEMA_DIRECTORY);
        Path backupRelease = modelDirectory.resolve(".previous-" + UUID.randomUUID());
        boolean backedUp = false;
        if (Files.exists(activeRelease, LinkOption.NOFOLLOW_LINKS)) {
            move(activeRelease, backupRelease);
            backedUp = true;
        }

        try {
            move(stagedRelease, activeRelease);
        } catch (IOException publishFailure) {
            if (backedUp) {
                try {
                    move(backupRelease, activeRelease);
                } catch (IOException restoreFailure) {
                    publishFailure.addSuppressed(restoreFailure);
                }
            }
            throw new IOException("Unable to activate trained bot models from " + stagedRelease, publishFailure);
        }

        try {
            int removedLegacyModels = removeLegacyModelFiles(modelDirectory, activeRelease);
            if (backedUp)
                deleteTree(backupRelease);
            deleteTree(stagingDirectory);
            published = true;
            return new PublicationResult(policyModelCount, removedLegacyModels);
        } catch (IOException cleanupFailure) {
            throw new IOException("Trained models are active, but legacy model cleanup failed in "
                    + modelDirectory, cleanupFailure);
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            if (Files.exists(stagingDirectory, LinkOption.NOFOLLOW_LINKS))
                deleteTree(stagingDirectory);
        } catch (IOException e) {
            failure = e;
        }
        try {
            lock.release();
        } catch (IOException e) {
            if (failure == null)
                failure = e;
            else
                failure.addSuppressed(e);
        }
        try {
            lockChannel.close();
        } catch (IOException e) {
            if (failure == null)
                failure = e;
            else
                failure.addSuppressed(e);
        }
        if (failure != null)
            throw failure;
    }

    private static int countModelFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            return 0;
        try (Stream<Path> paths = Files.walk(directory)) {
            return (int) paths.filter(Files::isRegularFile)
                    .filter(TrainingModelWorkspace::isModelFile)
                    .count();
        }
    }

    private static int removeLegacyModelFiles(Path baseDirectory, Path activeRelease) throws IOException {
        List<Path> legacyModelFiles;
        try (Stream<Path> paths = Files.walk(baseDirectory)) {
            legacyModelFiles = paths
                    .filter(path -> !path.startsWith(activeRelease))
                    .filter(Files::isRegularFile)
                    .filter(TrainingModelWorkspace::isModelFile)
                    .toList();
        }

        for (Path legacyModelFile : legacyModelFiles)
            Files.deleteIfExists(legacyModelFile);

        List<Path> emptyDirectories;
        try (Stream<Path> paths = Files.walk(baseDirectory)) {
            emptyDirectories = paths
                    .filter(path -> !path.equals(baseDirectory) && !path.startsWith(activeRelease))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
        for (Path directory : emptyDirectories) {
            if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && isEmptyDirectory(directory))
                Files.deleteIfExists(directory);
        }
        return legacyModelFiles.size();
    }

    private static void removeStaleTrainingDirectories(Path modelDirectory) throws IOException {
        List<Path> staleWorkspaces;
        try (Stream<Path> children = Files.list(modelDirectory)) {
            staleWorkspaces = children
                    .filter(path -> path.getFileName().toString().startsWith(".training-"))
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .toList();
        }
        for (Path staleWorkspace : staleWorkspaces)
            deleteTree(staleWorkspace);
    }

    private static boolean isModelFile(Path path) {
        return path.getFileName().toString().endsWith(".model");
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS))
            return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null)
                    throw exception;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public record PublicationResult(int sharedPolicyModels, int removedLegacyModels) {
    }
}
