package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.LearningStepsPersistence;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import com.gempukku.lotro.bots.training.TrainingDataIO;

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
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public class FotrStartersLearningStepsPersistence implements LearningStepsPersistence {
    public static final String DATA_DIRECTORY_PROPERTY = "gemp.lotr.bot.trainingDataDir";
    public static final String DATA_DIRECTORY_ENVIRONMENT_VARIABLE = "GEMP_BOT_TRAINING_DATA_DIR";

    private static final Map<Class<? extends Trainer>, String> trainerFileMap = createTrainerFileMap();
    private final Path dataDirectory;
    private final Consumer<Compaction> compactionListener;
    private final Consumer<AppendStats> appendListener;
    private final boolean compactOnLoad;
    private final Set<String> heldOutDeckFingerprints;
    private final LongAdder heldOutRecordsExcluded = new LongAdder();
    private final LongAdder unattributedRecordsExcluded = new LongAdder();

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
        this(dataDirectory, compactionListener, stats -> { }, true);
    }

    public FotrStartersLearningStepsPersistence(
            Path dataDirectory,
            Consumer<Compaction> compactionListener,
            Consumer<AppendStats> appendListener,
            boolean compactOnLoad) {
        this(dataDirectory, compactionListener, appendListener, compactOnLoad, Set.of());
    }

    public FotrStartersLearningStepsPersistence(
            Path dataDirectory,
            Consumer<Compaction> compactionListener,
            Consumer<AppendStats> appendListener,
            boolean compactOnLoad,
            Set<String> heldOutDeckFingerprints) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.compactionListener = Objects.requireNonNull(compactionListener);
        this.appendListener = Objects.requireNonNull(appendListener);
        this.compactOnLoad = compactOnLoad;
        this.heldOutDeckFingerprints = Set.copyOf(heldOutDeckFingerprints);
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
            long records = 0;
            long jsonBytes = 0;
            long appendedBytes = 0;
            for (Trainer trainer : FotrStarterTrainerCatalog.createAll()) {
                String filename = trainerFileMap.get(trainer.getClass());
                AppendStats stats = saveFilteredSteps(
                        dataDirectory.resolve(filename), trainer, steps, compactOnLoad);
                records += stats.trainerRecords();
                jsonBytes += stats.jsonBytes();
                appendedBytes += stats.appendedBytes();
            }
            appendListener.accept(new AppendStats(steps.size(), records, jsonBytes, appendedBytes));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to save bot training data to " + dataDirectory, e);
        }
    }

    private AppendStats saveFilteredSteps(
            Path file, Trainer trainer, List<LearningStep> steps, boolean compactOnLoad) throws IOException {
        List<LearningStep> relevantSteps = steps.stream().filter(trainer::isStepRelevant).toList();
        if (relevantSteps.isEmpty())
            return new AppendStats(0, 0, 0, 0);

        List<String> lines = relevantSteps.stream().map(step -> step.toJson().toJSONString()).toList();
        TrainingDataIO.AppendResult result = compactOnLoad
                ? TrainingDataIO.appendPlainLines(file, lines)
                : TrainingDataIO.appendCompressedLines(compressedFile(file), lines);
        return new AppendStats(0, relevantSteps.size(), result.jsonBytes(), result.appendedBytes());
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
        Path compressedFile = compressedFile(file);
        if (!Files.isRegularFile(file) && !Files.isRegularFile(compressedFile))
            return List.of();

        List<LearningStep> steps = new ArrayList<>(Math.min(maxRecords, 16_384));
        SplittableRandom random = new SplittableRandom(
                31L * dataDirectory.hashCode() + trainer.getClass().getName().hashCode());
        long recordsSeen = 0;
        try {
            long[] seen = {0};
            TrainingDataIO.forEachLine(file, compressedFile, (source, lineNumber, line) -> {
                if (!line.isBlank()) {
                    try {
                        LearningStep step = LearningStep.fromJson(line);
                        if (!isEligibleForHoldout(step))
                            return;
                        seen[0]++;
                        if (steps.size() < maxRecords) {
                            steps.add(step);
                        } else {
                            long replacementIndex = random.nextLong(seen[0]);
                            if (replacementIndex < maxRecords)
                                steps.set((int) replacementIndex, step);
                        }
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("Invalid training record at "
                                + source + ":" + lineNumber, e);
                    }
                }
            });
            recordsSeen = seen[0];
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load bot training data from " + file
                    + " and " + compressedFile, e);
        }
        if (compactOnLoad && recordsSeen > maxRecords) {
            if (!heldOutDeckFingerprints.isEmpty())
                throw new IllegalStateException("Cannot compact decision data while a deck holdout is active; "
                        + "historical provenance must remain unchanged: " + file);
            if (Files.isRegularFile(compressedFile))
                throw new IllegalStateException("Cannot compact compressed training records without replacing "
                        + "historical data: " + compressedFile);
            compact(file, steps, recordsSeen);
            compactionListener.accept(new Compaction(
                    trainer.getClass().getSimpleName(), file.getFileName().toString(),
                    recordsSeen, steps.size()));
        }

        return steps;
    }

    private boolean isEligibleForHoldout(LearningStep step) {
        if (heldOutDeckFingerprints.isEmpty())
            return true;
        if (step.deckFingerprints.isEmpty()) {
            unattributedRecordsExcluded.increment();
            return false;
        }
        if (step.deckFingerprints.stream().anyMatch(heldOutDeckFingerprints::contains)) {
            heldOutRecordsExcluded.increment();
            return false;
        }
        return true;
    }

    public HoldoutFilterSummary holdoutFilterSummary() {
        return new HoldoutFilterSummary(heldOutRecordsExcluded.sum(), unattributedRecordsExcluded.sum());
    }

    private static Path compressedFile(Path legacyFile) {
        return legacyFile.resolveSibling(legacyFile.getFileName() + ".gz");
    }

    public record AppendStats(long inputSteps, long trainerRecords, long jsonBytes, long appendedBytes) { }

    public record HoldoutFilterSummary(long heldOutRecords, long unattributedRecords) {
        public HoldoutFilterSummary plus(HoldoutFilterSummary other) {
            return new HoldoutFilterSummary(
                    heldOutRecords + other.heldOutRecords, unattributedRecords + other.unattributedRecords);
        }
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
