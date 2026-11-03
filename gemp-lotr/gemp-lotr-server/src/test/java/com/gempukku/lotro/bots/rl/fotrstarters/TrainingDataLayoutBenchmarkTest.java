package com.gempukku.lotro.bots.rl.fotrstarters;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStartersLearningStepsPersistence.DataLayout;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import com.gempukku.lotro.bots.training.TrainingDataIO;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrainingDataLayoutBenchmarkTest {
    private static final List<String> SUPPORTED_FORMATS =
            List.of("fotr_block", "king_block", "movie", "ttt_block");
    private static final String SOURCE_PROPERTY = "training.data.layout.benchmark.source";
    private static final String GAMES_PROPERTY = "training.data.layout.benchmark.games.per.format";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void comparesLayoutsUsingIdenticalRealGameObservations() throws Exception {
        String sourceValue = System.getProperty(SOURCE_PROPERTY);
        Assume.assumeTrue("Set " + SOURCE_PROPERTY + " to run the real-corpus benchmark.",
                sourceValue != null && !sourceValue.isBlank());
        Path sourceRoot = Path.of(sourceValue).toAbsolutePath().normalize();
        assertTrue("Training-data source does not exist: " + sourceRoot, Files.isDirectory(sourceRoot));

        int gamesPerFormat = Integer.parseInt(System.getProperty(GAMES_PROPERTY, "100"));
        if (gamesPerFormat < 1)
            throw new IllegalArgumentException(GAMES_PROPERTY + " must be positive");

        Map<String, List<Path>> filesByFormat = compressedTrainerFiles(sourceRoot);
        assertTrue("No per-trainer compressed decision files found under " + sourceRoot,
                filesByFormat.values().stream().anyMatch(files -> !files.isEmpty()));
        Map<Path, SourceFileStamp> sourceStamps = stampFiles(filesByFormat);
        Map<String, GameReservoir> gameSamples = selectGames(filesByFormat, gamesPerFormat);
        List<FormatCorpus> samples = readSelectedGames(filesByFormat, gameSamples);
        assertSourceFilesUnchanged(sourceStamps);

        Path outputRoot = temporaryFolder.newFolder("layout-benchmark").toPath();
        Path oldRoot = outputRoot.resolve("per-trainer");
        Path newRoot = outputRoot.resolve("shared-v1");
        Path mixedRoot = outputRoot.resolve("mixed");
        LayoutWriteStats oldWrites = savePerTrainerLayout(oldRoot, samples);
        LayoutWriteStats newWrites = saveSharedLayout(newRoot, samples);
        LayoutWriteStats mixedWrites = saveMixedLayout(mixedRoot, samples);

        Coverage oldCoverage = coverage(oldRoot);
        Coverage newCoverage = coverage(newRoot);
        Coverage mixedCoverage = coverage(mixedRoot);
        assertEquals("Shared-v1 changed trainer record or decision-category coverage.",
                oldCoverage, newCoverage);
        assertEquals("The mixed layout changed trainer record or decision-category coverage.",
                oldCoverage, mixedCoverage);
        assertEquals("The per-trainer layout lost selected source memberships.",
                oldWrites.trainerRecords(), loadedRecordCount(oldCoverage));
        assertEquals("The shared-v1 layout lost selected source memberships.",
                oldWrites.trainerRecords(), loadedRecordCount(newCoverage));
        assertEquals("The mixed layout lost selected source memberships.",
                oldWrites.trainerRecords(), loadedRecordCount(mixedCoverage));

        String fitTrainerName = oldCoverage.recordCounts().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1))
                .orElseThrow();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("source", sourceRoot.toString());
        report.put("sourceCompressedFiles", sourceStamps.size());
        report.put("sourceCompressedBytes", sourceStamps.entrySet().stream()
                .mapToLong(entry -> entry.getValue().size()).sum());
        report.put("sampleGamesPerFormat", gamesPerFormat);
        report.put("sampleGames", samples.stream().map(sample ->
                Map.of("format", sample.format(), "games", sample.gameIds().size(),
                        "inputSteps", sample.rows().size())).toList());
        report.put("coveragePreserved", true);
        report.put("oldPerTrainer", corpusStats(oldRoot, oldWrites, oldCoverage));
        report.put("newSharedV1", corpusStats(newRoot, newWrites, newCoverage));
        report.put("mixedLayouts", corpusStats(mixedRoot, mixedWrites, mixedCoverage));
        report.put("sharedByteReductionPercent", byteReduction(
                diskBytes(oldRoot), diskBytes(newRoot)));
        report.put("targetAtLeast50Percent", byteReduction(
                diskBytes(oldRoot), diskBytes(newRoot)) >= 50.0);
        report.put("fitTrainer", fitTrainerName);
        report.put("fitProfiles", List.of(
                fitProfile("per-trainer", oldRoot, fitTrainerName),
                fitProfile("shared-v1", newRoot, fitTrainerName),
                fitProfile("mixed", mixedRoot, fitTrainerName)));
        System.out.println("TRAINING_DATA_LAYOUT_BENCHMARK " + JSON.toJSONString(report));
    }

    private static Map<String, List<Path>> compressedTrainerFiles(Path sourceRoot) throws IOException {
        Map<String, List<Path>> result = new LinkedHashMap<>();
        Set<String> knownTrainerFiles = trainerByFileName().keySet();
        for (String format : SUPPORTED_FORMATS) {
            Path formatDirectory = sourceRoot.resolve(format);
            if (!Files.isDirectory(formatDirectory))
                continue;
            try (Stream<Path> files = Files.walk(formatDirectory)) {
                List<Path> trainerFiles = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith("movie-block-"))
                        .filter(path -> path.getFileName().toString().endsWith(".jsonl.gz"))
                        .filter(path -> !path.getFileName().toString().contains("shared-v1"))
                        .filter(path -> knownTrainerFiles.contains(
                                path.getFileName().toString().replaceFirst("\\.gz$", "")))
                        .sorted(Comparator.naturalOrder())
                        .toList();
                if (!trainerFiles.isEmpty())
                    result.put(format, trainerFiles);
            }
        }
        return result;
    }

    private static Map<Path, SourceFileStamp> stampFiles(Map<String, List<Path>> filesByFormat)
            throws IOException {
        Map<Path, SourceFileStamp> result = new LinkedHashMap<>();
        for (List<Path> files : filesByFormat.values()) {
            for (Path file : files)
                result.put(file, new SourceFileStamp(Files.size(file), Files.getLastModifiedTime(file)));
        }
        return result;
    }

    private static Map<String, GameReservoir> selectGames(
            Map<String, List<Path>> filesByFormat, int gamesPerFormat) throws IOException {
        Map<String, GameReservoir> result = new LinkedHashMap<>();
        filesByFormat.forEach((format, ignored) ->
                result.put(format, new GameReservoir(gamesPerFormat, format.hashCode())));
        for (Map.Entry<String, List<Path>> entry : filesByFormat.entrySet()) {
            GameReservoir reservoir = result.get(entry.getKey());
            for (Path file : entry.getValue()) {
                readCompressedFile(file, record -> reservoir.observe(record.getString("gameId")));
            }
        }
        return result;
    }

    private static List<FormatCorpus> readSelectedGames(
            Map<String, List<Path>> filesByFormat,
            Map<String, GameReservoir> gameSamples) throws IOException {
        Map<String, LinkedHashMap<String, StepOccurrences>> rowsByFormat = new LinkedHashMap<>();
        filesByFormat.keySet().forEach(format -> rowsByFormat.put(format, new LinkedHashMap<>()));
        Map<String, Trainer> trainers = trainerByFileName();
        for (Map.Entry<String, List<Path>> entry : filesByFormat.entrySet()) {
            Set<String> selectedGames = gameSamples.get(entry.getKey()).selectedGames();
            LinkedHashMap<String, StepOccurrences> rows = rowsByFormat.get(entry.getKey());
            for (Path file : entry.getValue()) {
                String trainerFile = file.getFileName().toString().replaceFirst("\\.gz$", "");
                String trainerName = trainers.get(trainerFile).getClass().getName();
                readCompressedFile(file, record -> {
                    if (!selectedGames.contains(record.getString("gameId")))
                        return;
                    LearningStep step = LearningStep.fromJson(record);
                    String key = step.toJson().toJSONString();
                    rows.computeIfAbsent(key, ignored -> new StepOccurrences(step))
                            .add(trainerName);
                });
            }
        }
        return rowsByFormat.entrySet().stream()
                .map(entry -> new FormatCorpus(entry.getKey(),
                        gameSamples.get(entry.getKey()).selectedGames(),
                        List.copyOf(entry.getValue().values())))
                .toList();
    }

    private static void readCompressedFile(Path file, RecordConsumer consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank())
                    consumer.accept(JSON.parseObject(line));
            }
        }
    }

    private static void assertSourceFilesUnchanged(Map<Path, SourceFileStamp> stamps) throws IOException {
        for (Map.Entry<Path, SourceFileStamp> entry : stamps.entrySet()) {
            Path file = entry.getKey();
            SourceFileStamp original = entry.getValue();
            assertEquals("Source size changed during benchmark: " + file, original.size(), Files.size(file));
            assertEquals("Source timestamp changed during benchmark: " + file,
                    original.modified(), Files.getLastModifiedTime(file));
        }
    }

    private static LayoutWriteStats savePerTrainerLayout(Path root, List<FormatCorpus> samples)
            throws IOException {
        LayoutWriteStats stats = LayoutWriteStats.empty();
        for (FormatCorpus sample : samples) {
            stats = stats.plus(savePerTrainerFormat(root.resolve(sample.format()), sample.rows()));
        }
        return stats;
    }

    private static LayoutWriteStats saveSharedLayout(Path root, List<FormatCorpus> samples)
            throws IOException {
        LayoutWriteStats stats = LayoutWriteStats.empty();
        for (FormatCorpus sample : samples) {
            stats = stats.plus(saveSharedFormat(root.resolve(sample.format()), sample.rows()));
        }
        return stats;
    }

    private static LayoutWriteStats saveMixedLayout(Path root, List<FormatCorpus> samples)
            throws IOException {
        LayoutWriteStats stats = LayoutWriteStats.empty();
        for (FormatCorpus sample : samples) {
            List<StepOccurrences> perTrainerRows = new ArrayList<>();
            List<StepOccurrences> sharedRows = new ArrayList<>();
            for (StepOccurrences row : sample.rows()) {
                if (Math.floorMod(row.step().gameId.hashCode(), 2) == 0)
                    perTrainerRows.add(row);
                else
                    sharedRows.add(row);
            }
            Path formatDirectory = root.resolve(sample.format());
            stats = stats.plus(savePerTrainerFormat(formatDirectory, perTrainerRows));
            stats = stats.plus(saveSharedFormat(formatDirectory, sharedRows));
        }
        return stats;
    }

    private static LayoutWriteStats savePerTrainerFormat(Path formatDirectory, List<StepOccurrences> rows)
            throws IOException {
        Map<String, Map<String, List<String>>> linesByTrainerAndGame = new LinkedHashMap<>();
        long trainerRecords = 0;
        for (StepOccurrences row : rows) {
            String json = row.step().toJson().toJSONString();
            for (Map.Entry<String, Integer> membership : row.trainerCounts().entrySet()) {
                String fileName = trainerFileName(membership.getKey()) + ".gz";
                List<String> lines = linesByTrainerAndGame
                        .computeIfAbsent(fileName, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(row.step().gameId, ignored -> new ArrayList<>());
                for (int i = 0; i < membership.getValue(); i++)
                    lines.add(json);
                trainerRecords += membership.getValue();
            }
        }
        long jsonBytes = 0;
        long storedRecords = 0;
        for (Map.Entry<String, Map<String, List<String>>> trainerEntry : linesByTrainerAndGame.entrySet()) {
            Path file = formatDirectory.resolve(trainerEntry.getKey());
            for (List<String> lines : trainerEntry.getValue().values()) {
                TrainingDataIO.AppendResult result = TrainingDataIO.appendCompressedLines(file, lines);
                jsonBytes += result.jsonBytes();
                storedRecords += lines.size();
            }
        }
        return new LayoutWriteStats(rows.size(), storedRecords, trainerRecords, jsonBytes);
    }

    private static LayoutWriteStats saveSharedFormat(Path formatDirectory, List<StepOccurrences> rows)
            throws IOException {
        Map<String, List<String>> linesByGame = new LinkedHashMap<>();
        long trainerRecords = 0;
        for (StepOccurrences row : rows) {
            List<String> lines = linesByGame.computeIfAbsent(row.step().gameId, ignored -> new ArrayList<>());
            int maximumOccurrences = row.trainerCounts().values().stream()
                    .mapToInt(Integer::intValue).max().orElse(0);
            trainerRecords += row.trainerCounts().values().stream().mapToLong(Integer::longValue).sum();
            for (int occurrence = 0; occurrence < maximumOccurrences; occurrence++) {
                int occurrenceIndex = occurrence;
                JSONObject sharedRecord = new JSONObject();
                sharedRecord.put("schema", 1);
                sharedRecord.put("trainers", row.trainerCounts().entrySet().stream()
                        .filter(entry -> entry.getValue() > occurrenceIndex)
                        .map(Map.Entry::getKey)
                        .toList());
                sharedRecord.put("step", row.step().toJson());
                lines.add(sharedRecord.toJSONString());
            }
        }
        Path file = formatDirectory.resolve("movie-block-shared-v1.jsonl.gz");
        long storedRecords = 0;
        long jsonBytes = 0;
        for (List<String> lines : linesByGame.values()) {
            TrainingDataIO.AppendResult result = TrainingDataIO.appendCompressedLines(file, lines);
            storedRecords += lines.size();
            jsonBytes += result.jsonBytes();
        }
        return new LayoutWriteStats(rows.size(), storedRecords, trainerRecords, jsonBytes);
    }

    private static Map<String, Trainer> trainerByFileName() {
        Map<String, Trainer> result = new LinkedHashMap<>();
        for (Trainer trainer : FotrStarterTrainerCatalog.createAll())
            result.put(trainerFileName(trainer.getClass().getName()), trainer);
        return result;
    }

    private static String trainerFileName(String className) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String base = simpleName.replaceAll("Trainer$", "")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
        return "movie-block-" + base + ".jsonl";
    }

    private static Coverage coverage(Path root) {
        Map<String, Long> recordCounts = new LinkedHashMap<>();
        Map<String, Set<String>> categoriesByTrainer = new LinkedHashMap<>();
        for (Trainer trainer : FotrStarterTrainerCatalog.createAll()) {
            for (String format : SUPPORTED_FORMATS) {
                Path formatDirectory = root.resolve(format);
                if (!Files.isDirectory(formatDirectory))
                    continue;
                FotrStartersLearningStepsPersistence source =
                        new FotrStartersLearningStepsPersistence(formatDirectory, ignored -> { }, ignored -> { },
                                false, Set.of(), DataLayout.PER_TRAINER);
                List<LearningStep> steps = source.load(trainer, Integer.MAX_VALUE);
                String key = format + "/" + trainer.getClass().getSimpleName();
                recordCounts.put(key, (long) steps.size());
                Set<String> categories = new LinkedHashSet<>();
                for (LearningStep step : steps) {
                    String prompt = step.decision.getText() == null
                            ? "" : step.decision.getText().trim().replaceAll("\\s+", " ")
                                    .toLowerCase(Locale.ROOT);
                    categories.add(step.decision.getDecisionType().name() + "/" + prompt);
                }
                categoriesByTrainer.put(key, categories);
            }
        }
        return new Coverage(recordCounts, categoriesByTrainer);
    }

    private static Map<String, Object> corpusStats(
            Path root, LayoutWriteStats writes, Coverage coverage) throws IOException {
        return Map.of(
                "diskBytes", diskBytes(root),
                "inputSteps", writes.inputSteps(),
                "storedRecords", writes.storedRecords(),
                "trainerRecords", writes.trainerRecords(),
                "uncompressedJsonBytes", writes.jsonBytes(),
                "loadedRecords", coverage.recordCounts().values().stream().mapToLong(Long::longValue).sum(),
                "trainerDecisionCategories", coverage.categoriesByTrainer().values().stream()
                        .mapToLong(Set::size).sum());
    }

    private static long loadedRecordCount(Coverage coverage) {
        return coverage.recordCounts().values().stream().mapToLong(Long::longValue).sum();
    }

    private static Map<String, Object> fitProfile(String layout, Path root, String trainerName) {
        Trainer trainer = FotrStarterTrainerCatalog.createAll().stream()
                .filter(candidate -> candidate.getClass().getSimpleName().equals(trainerName))
                .findFirst()
                .orElseThrow();
        List<LearningStep> steps = new ArrayList<>();
        long loadStartedNanos = System.nanoTime();
        for (String format : SUPPORTED_FORMATS) {
            Path formatDirectory = root.resolve(format);
            if (Files.isDirectory(formatDirectory)) {
                steps.addAll(new FotrStartersLearningStepsPersistence(formatDirectory,
                        ignored -> { }, ignored -> { }, false, Set.of(), DataLayout.PER_TRAINER)
                        .load(trainer, Integer.MAX_VALUE));
            }
        }
        long loadMillis = elapsedMillis(loadStartedNanos);
        long fitStartedNanos = System.nanoTime();
        AbstractTrainer.TrainingAttempt attempt = ((AbstractTrainer) trainer)
                .trainIfReadyWithMetrics(steps, null, false);
        long modelWallMillis = elapsedMillis(fitStartedNanos);
        return Map.of(
                "layout", layout,
                "trainer", trainerName,
                "recordsLoaded", steps.size(),
                "dataLoadMillis", loadMillis,
                "modelWallMillis", modelWallMillis,
                "fitMillis", attempt.trainingMillis(),
                "status", attempt.status());
    }

    private static long diskBytes(Path root) throws IOException {
        if (!Files.exists(root))
            return 0;
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).sum();
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static double byteReduction(long oldBytes, long newBytes) {
        return oldBytes == 0 ? 0 : (oldBytes - newBytes) * 100.0 / oldBytes;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    @FunctionalInterface
    private interface RecordConsumer {
        void accept(JSONObject record) throws IOException;
    }

    private record SourceFileStamp(long size, FileTime modified) {
    }

    private record FormatCorpus(String format, Set<String> gameIds, List<StepOccurrences> rows) {
    }

    private static final class StepOccurrences {
        private final LearningStep step;
        private final Map<String, Integer> trainerCounts = new LinkedHashMap<>();

        private StepOccurrences(LearningStep step) {
            this.step = step;
        }

        private void add(String trainerName) {
            trainerCounts.merge(trainerName, 1, Integer::sum);
        }

        private LearningStep step() {
            return step;
        }

        private Map<String, Integer> trainerCounts() {
            return trainerCounts;
        }
    }

    private record LayoutWriteStats(
            long inputSteps, long storedRecords, long trainerRecords, long jsonBytes) {
        private static LayoutWriteStats empty() {
            return new LayoutWriteStats(0, 0, 0, 0);
        }

        private LayoutWriteStats plus(LayoutWriteStats other) {
            return new LayoutWriteStats(
                    inputSteps + other.inputSteps,
                    storedRecords + other.storedRecords,
                    trainerRecords + other.trainerRecords,
                    jsonBytes + other.jsonBytes);
        }
    }

    private record Coverage(
            Map<String, Long> recordCounts,
            Map<String, Set<String>> categoriesByTrainer) {
    }

    private static final class GameReservoir {
        private final int limit;
        private final Random random;
        private final Set<String> seen = new HashSet<>();
        private final List<String> selected = new ArrayList<>();
        private long gameCount;

        private GameReservoir(int limit, int seed) {
            this.limit = limit;
            random = new Random(seed);
        }

        private void observe(String gameId) {
            if (gameId == null || gameId.isBlank() || !seen.add(gameId))
                return;
            gameCount++;
            if (selected.size() < limit) {
                selected.add(gameId);
            } else {
                long index = random.nextLong(gameCount);
                if (index < limit)
                    selected.set((int) index, gameId);
            }
        }

        private Set<String> selectedGames() {
            return Set.copyOf(selected);
        }
    }
}
