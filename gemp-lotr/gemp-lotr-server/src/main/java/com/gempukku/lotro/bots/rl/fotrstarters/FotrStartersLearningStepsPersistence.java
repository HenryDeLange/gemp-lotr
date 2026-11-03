package com.gempukku.lotro.bots.rl.fotrstarters;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public class FotrStartersLearningStepsPersistence implements LearningStepsPersistence {
    public static final String DATA_DIRECTORY_PROPERTY = "gemp.lotr.bot.trainingDataDir";
    public static final String DATA_DIRECTORY_ENVIRONMENT_VARIABLE = "GEMP_BOT_TRAINING_DATA_DIR";

    private static final Map<Class<? extends Trainer>, String> trainerFileMap = createTrainerFileMap();
    private static final String SHARED_FILE = "movie-block-shared-v1.jsonl";
    private final Path dataDirectory;
    private final Consumer<Compaction> compactionListener;
    private final Consumer<AppendStats> appendListener;
    private final boolean compactOnLoad;
    private final Set<String> heldOutDeckFingerprints;
    private final DataLayout dataLayout;
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
        this(dataDirectory, compactionListener, appendListener, compactOnLoad, heldOutDeckFingerprints,
                DataLayout.PER_TRAINER);
    }

    public FotrStartersLearningStepsPersistence(
            Path dataDirectory,
            Consumer<Compaction> compactionListener,
            Consumer<AppendStats> appendListener,
            boolean compactOnLoad,
            Set<String> heldOutDeckFingerprints,
            DataLayout dataLayout) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.compactionListener = Objects.requireNonNull(compactionListener);
        this.appendListener = Objects.requireNonNull(appendListener);
        this.compactOnLoad = compactOnLoad;
        this.heldOutDeckFingerprints = Set.copyOf(heldOutDeckFingerprints);
        this.dataLayout = Objects.requireNonNull(dataLayout);
        if (compactOnLoad && dataLayout == DataLayout.SHARED_V1)
            throw new IllegalArgumentException("Shared decision data cannot be written with compaction enabled");
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

        long writeStartedNanos = System.nanoTime();
        try {
            Files.createDirectories(dataDirectory);
            if (dataLayout == DataLayout.SHARED_V1) {
                AppendStats stats = saveSharedSteps(steps);
                appendListener.accept(new AppendStats(stats.inputSteps(), stats.trainerRecords(),
                        stats.storedRecords(), stats.jsonBytes(), stats.appendedBytes(),
                        elapsedMillis(writeStartedNanos)));
                return;
            }
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
            appendListener.accept(new AppendStats(steps.size(), records, records, jsonBytes,
                    appendedBytes, elapsedMillis(writeStartedNanos)));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to save bot training data to " + dataDirectory, e);
        }
    }

    private AppendStats saveSharedSteps(List<LearningStep> steps) throws IOException {
        List<Trainer> trainers = FotrStarterTrainerCatalog.createAll();
        List<String> lines = new ArrayList<>();
        long memberships = 0;
        for (LearningStep step : steps) {
            List<String> relevant = trainers.stream()
                    .filter(trainer -> trainer.isStepRelevant(step))
                    .map(trainer -> trainer.getClass().getName())
                    .toList();
            if (relevant.isEmpty())
                continue;
            JSONObject row = new JSONObject();
            row.put("schema", 1);
            row.put("trainers", relevant);
            row.put("step", step.toJson());
            lines.add(row.toJSONString());
            memberships += relevant.size();
        }
        TrainingDataIO.AppendResult result = TrainingDataIO.appendCompressedLines(
                compressedFile(dataDirectory.resolve(SHARED_FILE)), lines);
        return new AppendStats(steps.size(), memberships, lines.size(), result.jsonBytes(), result.appendedBytes());
    }

    private AppendStats saveFilteredSteps(
            Path file, Trainer trainer, List<LearningStep> steps, boolean compactOnLoad) throws IOException {
        List<LearningStep> relevantSteps = steps.stream().filter(trainer::isStepRelevant).toList();
        if (relevantSteps.isEmpty())
            return new AppendStats(0, 0, 0, 0, 0);

        List<String> lines = relevantSteps.stream().map(step -> step.toJson().toJSONString()).toList();
        TrainingDataIO.AppendResult result = compactOnLoad
                ? TrainingDataIO.appendPlainLines(file, lines)
                : TrainingDataIO.appendCompressedLines(compressedFile(file), lines);
        return new AppendStats(0, relevantSteps.size(), relevantSteps.size(),
                result.jsonBytes(), result.appendedBytes());
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
        Path sharedFile = dataDirectory.resolve(SHARED_FILE);
        Path compressedSharedFile = compressedFile(sharedFile);
        boolean hasShared = Files.isRegularFile(sharedFile) || Files.isRegularFile(compressedSharedFile);
        if (compactOnLoad && hasShared)
            throw new IllegalStateException("Cannot compact decision data while shared records exist: " + sharedFile);
        if (!Files.isRegularFile(file) && !Files.isRegularFile(compressedFile) && !hasShared)
            return List.of();

        List<LearningStep> steps = new ArrayList<>(Math.min(maxRecords, 16_384));
        Map<DecisionCategory, Long> categoryCounts = new TreeMap<>();
        Set<DecisionCategory> trackedCategories = new HashSet<>();
        // Bound both category count and prompt-key size, including malformed or unusually variable data.
        int maxTrackedCategories = Math.max(0, maxRecords - 1);
        long[] recordsSeen = {0};
        boolean[] withinLimit = {true};
        List<LearningStep> selectedSteps;
        try {
            forEachEligibleStep(trainer, file, compressedFile, sharedFile, compressedSharedFile,
                    true, step -> {
                        recordsSeen[0]++;
                        DecisionCategory category = trackDecisionCategory(
                                decisionCategory(step), trackedCategories, maxTrackedCategories);
                        categoryCounts.merge(category, 1L, Long::sum);
                        if (withinLimit[0]) {
                            if (steps.size() < maxRecords) {
                                steps.add(step);
                            } else {
                                steps.clear();
                                withinLimit[0] = false;
                            }
                        }
                    });
            selectedSteps = withinLimit[0]
                    ? steps
                    : sampleByDecisionCategory(trainer, file, compressedFile, sharedFile,
                            compressedSharedFile, categoryCounts, trackedCategories, maxRecords);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load bot training data from " + file
                    + " and " + compressedFile, e);
        }
        if (compactOnLoad && recordsSeen[0] > maxRecords) {
            if (!heldOutDeckFingerprints.isEmpty())
                throw new IllegalStateException("Cannot compact decision data while a deck holdout is active; "
                        + "historical provenance must remain unchanged: " + file);
            if (Files.isRegularFile(compressedFile))
                throw new IllegalStateException("Cannot compact compressed training records without replacing "
                        + "historical data: " + compressedFile);
            long compactionStartedNanos = System.nanoTime();
            compact(file, selectedSteps, recordsSeen[0]);
            compactionListener.accept(new Compaction(
                    trainer.getClass().getSimpleName(), file.getFileName().toString(),
                    recordsSeen[0], selectedSteps.size(), elapsedMillis(compactionStartedNanos)));
        }

        return selectedSteps;
    }

    private List<LearningStep> sampleByDecisionCategory(
            Trainer trainer,
            Path file,
            Path compressedFile,
            Path sharedFile,
            Path compressedSharedFile,
            Map<DecisionCategory, Long> categoryCounts,
            Set<DecisionCategory> trackedCategories,
            int maxRecords) throws IOException {
        Map<DecisionCategory, Integer> quotas = allocateCategoryQuotas(categoryCounts, maxRecords);
        Map<DecisionCategory, List<LearningStep>> samples = new TreeMap<>();
        Map<DecisionCategory, Long> recordsSeenByCategory = new LinkedHashMap<>();
        Map<DecisionCategory, SplittableRandom> randomByCategory = new LinkedHashMap<>();
        long seed = 31L * dataDirectory.hashCode() + trainer.getClass().getName().hashCode();

        forEachEligibleStep(trainer, file, compressedFile, sharedFile, compressedSharedFile,
                false, step -> {
                    DecisionCategory category = samplingCategory(step, trackedCategories);
                    Integer quota = quotas.get(category);
                    if (quota == null)
                        throw new IllegalStateException("Training data changed while sampling decision categories");
                    long seen = recordsSeenByCategory.merge(category, 1L, Long::sum);
                    if (quota == 0)
                        return;

                    List<LearningStep> categorySample =
                            samples.computeIfAbsent(category, ignored -> new ArrayList<>(quota));
                    SplittableRandom random = randomByCategory.computeIfAbsent(category,
                            ignored -> new SplittableRandom(seed ^ category.hashCode()));
                    if (categorySample.size() < quota) {
                        categorySample.add(step);
                    } else {
                        long replacementIndex = random.nextLong(seen);
                        if (replacementIndex < quota)
                            categorySample.set((int) replacementIndex, step);
                    }
                });

        if (!recordsSeenByCategory.equals(categoryCounts))
            throw new IllegalStateException("Training data changed while sampling decision categories");

        List<LearningStep> sampledSteps = new ArrayList<>(Math.min(maxRecords, 16_384));
        samples.values().forEach(sampledSteps::addAll);
        return sampledSteps;
    }

    private static Map<DecisionCategory, Integer> allocateCategoryQuotas(
            Map<DecisionCategory, Long> categoryCounts, int maxRecords) {
        Map<DecisionCategory, Integer> quotas = new TreeMap<>();
        categoryCounts.keySet().forEach(category -> quotas.put(category, 0));
        List<DecisionCategory> activeCategories = new ArrayList<>(categoryCounts.keySet());
        int remaining = maxRecords;

        while (remaining > 0 && !activeCategories.isEmpty()) {
            int perCategory = Math.max(1, remaining / activeCategories.size());
            int allocated = 0;
            for (DecisionCategory category : activeCategories) {
                int currentQuota = quotas.get(category);
                long available = categoryCounts.get(category) - currentQuota;
                int additional = (int) Math.min(Math.min(available, perCategory), remaining - allocated);
                quotas.put(category, currentQuota + additional);
                allocated += additional;
                if (allocated == remaining)
                    break;
            }
            if (allocated == 0)
                break;
            remaining -= allocated;
            activeCategories.removeIf(category ->
                    quotas.get(category) >= categoryCounts.get(category));
        }
        return quotas;
    }

    private static DecisionCategory decisionCategory(LearningStep step) {
        if (step.decision == null || step.decision.getDecisionType() == null)
            throw new IllegalArgumentException("Training record is missing its decision type");
        String prompt = step.decision.getText();
        String normalizedPrompt = prompt == null
                ? ""
                : prompt.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        // Prompts are category labels, not arbitrary-sized training features.
        if (normalizedPrompt.length() > 256)
            normalizedPrompt = normalizedPrompt.substring(0, 256);
        return new DecisionCategory(step.decision.getDecisionType().name(), normalizedPrompt, false);
    }

    private static DecisionCategory trackDecisionCategory(
            DecisionCategory category,
            Set<DecisionCategory> trackedCategories,
            int maxTrackedCategories) {
        if (trackedCategories.contains(category) || trackedCategories.size() < maxTrackedCategories) {
            trackedCategories.add(category);
            return category;
        }
        return category.overflowBucket();
    }

    private static DecisionCategory samplingCategory(
            LearningStep step, Set<DecisionCategory> trackedCategories) {
        DecisionCategory category = decisionCategory(step);
        return trackedCategories.contains(category) ? category : category.overflowBucket();
    }

    private void forEachEligibleStep(
            Trainer trainer,
            Path file,
            Path compressedFile,
            Path sharedFile,
            Path compressedSharedFile,
            boolean countHoldoutExclusions,
            Consumer<LearningStep> consumer) throws IOException {
        TrainingDataIO.forEachLine(file, compressedFile, (source, lineNumber, line) -> {
            if (line.isBlank())
                return;
            try {
                LearningStep step = LearningStep.fromJson(line);
                if (isEligibleForHoldout(step, countHoldoutExclusions))
                    consumer.accept(step);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid training record at " + source + ":" + lineNumber, e);
            }
        });
        TrainingDataIO.forEachLine(sharedFile, compressedSharedFile, (source, lineNumber, line) -> {
            if (line.isBlank())
                return;
            try {
                JSONObject row = JSON.parseObject(line);
                if (row == null || !row.containsKey("schema") || row.getIntValue("schema") != 1
                        || row.getJSONArray("trainers") == null || row.getJSONObject("step") == null)
                    throw new IllegalArgumentException("Invalid shared-v1 record");
                var memberships = row.getJSONArray("trainers");
                if (memberships.isEmpty()
                        || memberships.stream().anyMatch(value -> !(value instanceof String name)
                                || name.isBlank()))
                    throw new IllegalArgumentException("Invalid shared-v1 trainer memberships");
                if (memberships.contains(trainer.getClass().getName())) {
                    LearningStep step = LearningStep.fromJson(row.getJSONObject("step"));
                    if (isEligibleForHoldout(step, countHoldoutExclusions))
                        consumer.accept(step);
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid training record at " + source + ":" + lineNumber, e);
            }
        });
    }

    private boolean isEligibleForHoldout(LearningStep step, boolean countExclusions) {
        if (heldOutDeckFingerprints.isEmpty())
            return true;
        if (step.deckFingerprints.isEmpty()) {
            if (countExclusions)
                unattributedRecordsExcluded.increment();
            return false;
        }
        if (step.deckFingerprints.stream().anyMatch(heldOutDeckFingerprints::contains)) {
            if (countExclusions)
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

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    public record AppendStats(
            long inputSteps,
            long trainerRecords,
            long storedRecords,
            long jsonBytes,
            long appendedBytes,
            long writeMillis) {
        public AppendStats(
                long inputSteps, long trainerRecords, long storedRecords, long jsonBytes, long appendedBytes) {
            this(inputSteps, trainerRecords, storedRecords, jsonBytes, appendedBytes, 0);
        }
    }

    public enum DataLayout {
        PER_TRAINER("per-trainer"), SHARED_V1("shared-v1");

        private final String setting;

        DataLayout(String setting) {
            this.setting = setting;
        }

        public String setting() {
            return setting;
        }

        public static DataLayout parse(String value) {
            for (DataLayout layout : values())
                if (layout.setting.equals(value))
                    return layout;
            throw new IllegalArgumentException("training.data.layout must be per-trainer or shared-v1");
        }
    }

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
            int recordsAfter,
            long durationMillis) {
    }

    private record DecisionCategory(String type, String prompt, boolean overflow)
            implements Comparable<DecisionCategory> {
        private DecisionCategory overflowBucket() {
            return new DecisionCategory(type, "", true);
        }

        @Override
        public int compareTo(DecisionCategory other) {
            int typeComparison = type.compareTo(other.type);
            if (typeComparison != 0)
                return typeComparison;
            int overflowComparison = Boolean.compare(overflow, other.overflow);
            return overflowComparison != 0 ? overflowComparison : prompt.compareTo(other.prompt);
        }
    }
}
