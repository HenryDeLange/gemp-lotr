package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.LearningStepsPersistence;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.Consumer;

public class FotrStartersLearningStepsPersistence implements LearningStepsPersistence {
    public static final String DATA_DIRECTORY_PROPERTY = "gemp.lotr.bot.trainingDataDir";
    public static final String DATA_DIRECTORY_ENVIRONMENT_VARIABLE = "GEMP_BOT_TRAINING_DATA_DIR";

    private static final Map<Class<? extends Trainer>, String> trainerFileMap = createTrainerFileMap();
    private final Path dataDirectory;
    private final Consumer<Compaction> compactionListener;

    public FotrStartersLearningStepsPersistence() {
        this(getConfiguredDataDirectory());
    }

    public FotrStartersLearningStepsPersistence(Path dataDirectory) {
        this(dataDirectory, compaction -> {
        });
    }

    public FotrStartersLearningStepsPersistence(
            Path dataDirectory,
            Consumer<Compaction> compactionListener) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.compactionListener = Objects.requireNonNull(compactionListener);
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    private static Path getConfiguredDataDirectory() {
        String directory = System.getProperty(DATA_DIRECTORY_PROPERTY);
        if (directory == null || directory.isBlank())
            directory = System.getenv(DATA_DIRECTORY_ENVIRONMENT_VARIABLE);
        if (directory == null || directory.isBlank())
            directory = "bot-training-data";
        return Path.of(directory);
    }

    private static Map<Class<? extends Trainer>, String> createTrainerFileMap() {
        Map<Class<? extends Trainer>, String> files = new LinkedHashMap<>();
        for (Trainer trainer : FotrStarterTrainerCatalog.createAll())
            files.put(trainer.getClass(), generateFileName(trainer.getClass()));
        return Collections.unmodifiableMap(files);
    }

    private static String generateFileName(Class<? extends Trainer> cls) {
        String base = cls.getSimpleName()
                .replaceAll("Trainer$", "") // Remove "Trainer" suffix
                .replaceAll("([a-z])([A-Z])", "$1-$2") // CamelCase to kebab-case
                .toLowerCase();
        return "movie-block-" + base + ".jsonl";
    }

    @Override
    public synchronized void save(List<LearningStep> steps) {
        if (steps.isEmpty())
            return;
        if (steps.stream().anyMatch(step -> step.gameId == null || step.gameId.isBlank()))
            throw new IllegalArgumentException("Cannot persist bot training steps without a game ID");

        try {
            Files.createDirectories(dataDirectory);
            for (Trainer trainer : FotrStarterTrainerCatalog.createAll()) {
                String filename = trainerFileMap.get(trainer.getClass());
                saveFilteredSteps(dataDirectory.resolve(filename), trainer, steps);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to save bot training data to " + dataDirectory, e);
        }
    }

    private void saveFilteredSteps(Path file, Trainer trainer, List<LearningStep> steps) throws IOException {
        List<LearningStep> relevantSteps = steps.stream().filter(trainer::isStepRelevant).toList();
        if (relevantSteps.isEmpty())
            return;

        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (LearningStep step : relevantSteps) {
                writer.write(step.toJson().toJSONString());
                writer.newLine();
            }
        }
    }

    public List<LearningStep> load(Trainer trainer) {
        return load(trainer, Integer.MAX_VALUE);
    }

    public List<LearningStep> load(Trainer trainer, int maxRecords) {
        if (maxRecords < 1)
            throw new IllegalArgumentException("Maximum training record count must be positive");

        String fileName = trainerFileMap.get(trainer.getClass());
        if (fileName == null)
            return List.of();

        Path file = dataDirectory.resolve(fileName);
        if (!Files.isRegularFile(file))
            return List.of();

        List<LearningStep> steps = new ArrayList<>(Math.min(maxRecords, 16_384));
        SplittableRandom random = new SplittableRandom(
                31L * dataDirectory.hashCode() + trainer.getClass().getName().hashCode());
        long recordsSeen = 0;
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (!line.isBlank()) {
                    try {
                        LearningStep step = LearningStep.fromJson(line);
                        recordsSeen++;
                        if (steps.size() < maxRecords) {
                            steps.add(step);
                        } else {
                            long replacementIndex = random.nextLong(recordsSeen);
                            if (replacementIndex < maxRecords)
                                steps.set((int) replacementIndex, step);
                        }
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("Invalid training record at " + file + ":" + lineNumber, e);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load bot training data from " + file, e);
        }
        if (recordsSeen > maxRecords) {
            compact(file, steps, recordsSeen);
            compactionListener.accept(new Compaction(
                    trainer.getClass().getSimpleName(), file.getFileName().toString(),
                    recordsSeen, steps.size()));
        }
        return steps;
    }

    private static void compact(Path file, List<LearningStep> steps, long recordsSeen) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".compact");
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (LearningStep step : steps) {
                    writer.write(step.toJson().toJSONString());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.printf("Compacted bot training data %s from %d to %d records.%n",
                    file, recordsSeen, steps.size());
        } catch (IOException e) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw new UncheckedIOException("Unable to compact bot training data " + file, e);
        }
    }

    public record Compaction(
            String trainerName,
            String fileName,
            long recordsBefore,
            int recordsAfter) {
    }
}
