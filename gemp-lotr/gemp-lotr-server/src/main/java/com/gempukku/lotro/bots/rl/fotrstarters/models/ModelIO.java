package com.gempukku.lotro.bots.rl.fotrstarters.models;

import smile.classification.Classifier;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class ModelIO {
    public static final String MODEL_DIRECTORY_PROPERTY = "gemp.lotr.bot.modelDir";
    public static final String MODEL_DIRECTORY_ENVIRONMENT_VARIABLE = "GEMP_BOT_MODEL_DIR";
    public static final String OBSERVATION_SCHEMA_DIRECTORY = "public-observation-v6";
    public static final String TRAINING_DATA_SCHEMA_DIRECTORY = "public-observation-v8";
    private static final String DEFAULT_MODEL_DIRECTORY = "bot-models";

    private ModelIO() {
    }

    public static Path getModelDirectory() {
        String configuredDirectory = System.getProperty(MODEL_DIRECTORY_PROPERTY);
        if (configuredDirectory == null || configuredDirectory.isBlank())
            configuredDirectory = System.getenv(MODEL_DIRECTORY_ENVIRONMENT_VARIABLE);
        if (configuredDirectory == null || configuredDirectory.isBlank())
            configuredDirectory = DEFAULT_MODEL_DIRECTORY;

        return Path.of(configuredDirectory).toAbsolutePath().normalize();
    }

    public static Path resolveModelDirectory(Path baseDirectory, String formatCode) {
        Path versionedDirectory = baseDirectory.resolve(OBSERVATION_SCHEMA_DIRECTORY);
        return versionedDirectory.resolve("movie-trilogy");
    }

    public static Path resolveDeckBuilderDirectory(Path baseDirectory, String formatCode) {
        return baseDirectory.resolve(OBSERVATION_SCHEMA_DIRECTORY)
                .resolve("deck-builder")
                .resolve(formatCode);
    }

    public static void saveModel(Class<?> trainerClass, Classifier<double[]> model) {
        saveModel(trainerClass, model, getModelDirectory());
    }

    public static void saveModel(Class<?> trainerClass, Classifier<double[]> model, Path directory) {
        Objects.requireNonNull(model, "model");
        directory = directory.toAbsolutePath().normalize();
        Path file = getModelFile(trainerClass, directory);
        Path temporaryFile = null;
        try {
            Files.createDirectories(directory);
            temporaryFile = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temporaryFile))) {
                out.writeObject(model);
            }
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("Saved model: " + file);
        } catch (IOException e) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
            }
            throw new UncheckedIOException("Failed to save model " + trainerClass.getSimpleName() + " to " + file, e);
        }
    }

    public static Classifier<double[]> loadModel(Class<?> trainerClass) {
        return loadModel(trainerClass, getModelDirectory());
    }

    public static Classifier<double[]> loadModel(Class<?> trainerClass, Path directory) {
        Path file = getModelFile(trainerClass, directory.toAbsolutePath().normalize());
        if (!Files.isRegularFile(file))
            throw new IllegalStateException("Missing bot model for " + trainerClass.getSimpleName() + ": " + file);

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object obj = in.readObject();
            if (!(obj instanceof Classifier<?>))
                throw new IllegalStateException("Unexpected model type in " + file);
            @SuppressWarnings("unchecked")
            Classifier<double[]> model = (Classifier<double[]>) obj;
            return model;
        } catch (IOException | ClassNotFoundException | ClassCastException | LinkageError e) {
            throw new IllegalStateException("Failed to load model " + trainerClass.getSimpleName() + " from " + file, e);
        }
    }

    private static Path getModelFile(Class<?> trainerClass, Path directory) {
        return directory.resolve(trainerClass.getSimpleName() + ".model");
    }
}
