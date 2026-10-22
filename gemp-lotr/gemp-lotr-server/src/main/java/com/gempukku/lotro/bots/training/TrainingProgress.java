package com.gempukku.lotro.bots.training;

import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.simulation.BatchTacticalMetrics;
import com.gempukku.lotro.bots.simulation.SimulationStats;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrainingProgress implements TrainingEventReporter {
    private final List<MutableStage> stages = new ArrayList<>();
    private final Map<String, ModelProgress> models = new LinkedHashMap<>();
    private final Map<String, BatchResult> batchResults = new LinkedHashMap<>();
    private final Map<String, DeckBuilderProgress> deckBuilders = new LinkedHashMap<>();
    private final List<TrainingEvent> events = new ArrayList<>();
    private final List<PolicyComparison> policyComparisons = new ArrayList<>();
    private final EnumMap<Severity, Long> eventCounts = new EnumMap<>(Severity.class);
    private final Map<CycleRecoveryKey, Long> cycleRecoveries = new LinkedHashMap<>();
    private long nextEventId = 1;
    private String status = "starting";
    private String format = "";
    private String stage = "initializing";
    private int generation;
    private int generationCount;
    private int completed;
    private int total;
    private int candidateModelsReady;
    private int previousModelsKept;
    private MutableStage currentStage;
    private Instant startedAt;
    private Instant completedAt;
    private long startedNanos;
    private long completedNanos;
    private TrainingRunMetricsWriter metricsWriter;

    public TrainingProgress() {
        for (Severity severity : Severity.values())
            eventCounts.put(severity, 0L);
    }

    public synchronized void setPlan(List<PlannedStage> plan) {
        stages.clear();
        for (PlannedStage planned : plan)
            stages.add(new MutableStage(planned.format(), planned.name(), planned.generation(), planned.total()));
        generationCount = plan.stream().mapToInt(PlannedStage::generation).max().orElse(0);
        appendMetrics("run_plan", stages.stream().map(MutableStage::toStatus).toList());
    }

    public synchronized void setMetricsWriter(TrainingRunMetricsWriter metricsWriter) {
        if (this.metricsWriter != null)
            throw new IllegalStateException("Training metrics writer has already been set");
        this.metricsWriter = metricsWriter;
    }

    public synchronized void beginFormat(String formatCode) {
        beginFormat(formatCode, 0);
    }

    public synchronized void beginFormat(String formatCode, int generation) {
        closeCurrentStage(null);
        start();
        status = "running";
        format = formatCode;
        stage = "preparing";
        this.generation = generation;
        completed = 0;
        total = 0;
    }

    public synchronized void beginStage(String stageName, int generation, int total) {
        closeCurrentStage(null);
        MutableStage next = stages.stream()
                .filter(candidate -> candidate.state.equals("pending")
                        && candidate.format.equals(format)
                        && candidate.name.equals(stageName)
                        && candidate.generation == generation)
                .findFirst()
                .orElseGet(() -> {
                    MutableStage unplanned = new MutableStage(format, stageName, generation, total);
                    stages.add(unplanned);
                    return unplanned;
                });
        next.state = "running";
        next.completed = 0;
        next.total = total;
        next.result = "";
        currentStage = next;
        stage = stageName;
        this.generation = generation;
        completed = 0;
        this.total = total;
        candidateModelsReady = 0;
        previousModelsKept = 0;
    }

    public synchronized void trainingDataCompacted(
            String sourceFormat,
            String trainerName,
            String fileName,
            long recordsBefore,
            int recordsAfter) {
        String message = String.format(Locale.ROOT,
                    "%s training data for %s exceeded the %,d-record per-format cap; "
                        + "compacted %s from %,d to %,d records.",
                    sourceFormat, trainerName, recordsAfter,
                fileName, recordsBefore, recordsAfter);
        recordEvent(Severity.WARNING, "training-data-compacted", trainerName, message, null);
    }

    public synchronized void gameCompleted() {
        if (currentStage != null && currentStage.completed < currentStage.total)
            currentStage.completed++;
        completed = currentStage == null ? completed : currentStage.completed;
    }

    public synchronized void modelCompleted(
            String name,
            String state,
            long totalExamples,
            long positiveExamples,
            long negativeExamples,
            int trainingExamples,
            int validationExamples,
            Double trainingAccuracy,
            Double validationAccuracy,
            long trainingMillis) {
        modelCompleted(name, state, totalExamples, positiveExamples, negativeExamples,
                trainingExamples, validationExamples, trainingAccuracy, validationAccuracy,
                null, trainingMillis);
    }

    public synchronized void modelCompleted(
            String name,
            String state,
            long totalExamples,
            long positiveExamples,
            long negativeExamples,
            int trainingExamples,
            int validationExamples,
            Double trainingAccuracy,
            Double validationAccuracy,
            Double incumbentValidationAccuracy,
            long trainingMillis) {
        String key = format + "/" + generation + "/" + name;
        models.put(key, new ModelProgress(format, generation, name, state, totalExamples,
                positiveExamples, negativeExamples, trainingExamples, validationExamples,
                finiteOrNull(trainingAccuracy), finiteOrNull(validationAccuracy),
                finiteOrNull(incumbentValidationAccuracy), trainingMillis));
        appendMetrics("model", models.get(key));
        if ("trained".equals(state)) {
            recordEvent(Severity.SUCCESS, "decision-model-fitted", name,
                    "Candidate trained on " + trainingExamples + " learning examples and checked against "
                            + validationExamples + " examples from separate games.", null);
        } else if ("retained-below-accuracy-threshold".equals(state)) {
            recordEvent(Severity.WARNING, "decision-model-below-threshold", name,
                    "The previous model was kept because the candidate missed the configured check-example match target.",
                    null);
        }
        if (currentStage != null && currentStage.name.equals("decision-model-fitting")) {
            if ("trained".equals(state))
                candidateModelsReady++;
            else
                previousModelsKept++;
            currentStage.result = candidateModelsReady + " candidate models ready; "
                    + previousModelsKept + " previous models kept";
        }
        if (currentStage != null && currentStage.completed < currentStage.total)
            currentStage.completed++;
        completed = currentStage == null ? completed : currentStage.completed;
    }

    public synchronized void modelQueued(String name) {
        String key = format + "/" + generation + "/" + name;
        ModelProgress progress = new ModelProgress(format, generation, name, "queued",
                0, 0, 0, 0, 0, null, null, 0);
        models.put(key, progress);
        appendMetrics("model", progress);
    }

    public synchronized void modelStarted(String name) {
        String key = format + "/" + generation + "/" + name;
        ModelProgress progress = new ModelProgress(format, generation, name, "training",
                0, 0, 0, 0, 0, null, null, 0);
        models.put(key, progress);
        appendMetrics("model", progress);
    }

    public synchronized void batchCompleted(String label, int wins, int losses) {
        batchCompleted(label, wins, losses, 0, BatchTacticalMetrics.empty());
    }

    public synchronized void batchCompleted(String label, SimulationStats stats) {
        batchCompleted(label, stats.getBot1Wins(), stats.getBot2Wins(),
                stats.getSkippedGames(), stats.getTacticalMetrics());
    }

    private void batchCompleted(
            String label,
            int wins,
            int losses,
            int skippedGames,
            BatchTacticalMetrics tacticalMetrics) {
        flushCycleRecoveries(format, generation);
        String key = format + "/" + label;
        BatchResult result = new BatchResult(format, generation, label, wins, losses,
                skippedGames, "", tacticalMetrics);
        batchResults.put(key, result);
        appendMetrics("batch", result);
        recordEvent(Severity.SUCCESS, "simulation-batch-completed", null,
                label + " completed with " + wins + " wins, " + losses + " losses, and "
                        + skippedGames + " skipped games.", null);
        if (currentStage != null)
            currentStage.result = wins + " wins / " + losses + " losses"
                    + (skippedGames == 0 ? "" : " / " + skippedGames + " skipped");
    }

    public synchronized void modelComparisonCompleted(double candidateWinRate, boolean candidateSelected) {
        modelComparisonCompleted(candidateWinRate, candidateSelected, candidateWinRate, Double.NaN);
    }

    public synchronized void modelComparisonCompleted(
            double candidateWinRate,
            boolean candidateSelected,
            double aggregateCandidateWinRate,
            double aggregateLowerConfidenceBound) {
        String selection = candidateSelected ? "New decision model selected" : "Previous decision model kept";
        int candidateWins = 0;
        int completedGames = 0;
        int skippedGames = 0;
        for (Map.Entry<String, BatchResult> entry : new ArrayList<>(batchResults.entrySet())) {
            BatchResult batch = entry.getValue();
            if (batch.format().equals(format) && batch.generation() == generation
                    && (batch.label().endsWith("candidate-first")
                    || batch.label().endsWith("candidate-second"))) {
                candidateWins += batch.label().endsWith("candidate-first")
                        ? batch.wins() : batch.losses();
                completedGames += batch.wins() + batch.losses();
                skippedGames += batch.skippedGames();
                batchResults.put(entry.getKey(), new BatchResult(
                        batch.format(), batch.generation(), batch.label(), batch.wins(), batch.losses(),
                        batch.skippedGames(), selection, batch.tacticalMetrics()));
            }
        }
        PolicyComparison comparison = new PolicyComparison(format, generation, candidateWins,
                completedGames, skippedGames, finiteOrNull(candidateWinRate), candidateSelected,
                finiteOrNull(aggregateCandidateWinRate), finiteOrNull(aggregateLowerConfidenceBound));
        policyComparisons.add(comparison);
        appendMetrics("policy_comparison", comparison);
        String message = Double.isFinite(aggregateLowerConfidenceBound)
                ? String.format(Locale.ROOT,
                        "%s after a %.1f%% format win rate; aggregate %.1f%% (95%% lower bound %.1f%%).",
                        selection, candidateWinRate, aggregateCandidateWinRate, aggregateLowerConfidenceBound)
                : String.format(Locale.ROOT, "%s after a %.1f%% candidate win rate.",
                        selection, candidateWinRate);
        recordEvent(Severity.SUCCESS, "decision-model-selection", null, message, null);
    }

    public synchronized void policyModelComparisonCompleted(
            String modelName,
            int evaluationSlot,
            double validationAccuracy,
            Double incumbentValidationAccuracy,
            int candidateWins,
            int games,
            int skippedGames,
            double formatWinRate,
            double aggregateWinRate,
            double aggregateLowerConfidenceBound,
            boolean selected,
            boolean finalGeneration) {
        String selection = selected
                ? finalGeneration ? "Selected for final release" : "Selected for next generation"
                : "Current policy kept";
        String batchSuffix = "policy-change-" + evaluationSlot + "-";
        for (Map.Entry<String, BatchResult> entry : new ArrayList<>(batchResults.entrySet())) {
            BatchResult batch = entry.getValue();
            if (batch.format().equals(format) && batch.generation() == generation
                    && batch.label().contains(batchSuffix)
                    && (batch.label().endsWith("-candidate-first")
                    || batch.label().endsWith("-candidate-second"))) {
                batchResults.put(entry.getKey(), new BatchResult(
                        batch.format(), batch.generation(), batch.label(), batch.wins(), batch.losses(),
                        batch.skippedGames(), selection, batch.tacticalMetrics()));
            }
        }

        PolicyComparison comparison = new PolicyComparison(format, generation, modelName, evaluationSlot,
                candidateWins, games, skippedGames, finiteOrNull(formatWinRate), selected,
                finiteOrNull(aggregateWinRate), finiteOrNull(aggregateLowerConfidenceBound),
                finiteOrNull(validationAccuracy), finiteOrNull(incumbentValidationAccuracy), selection);
        policyComparisons.add(comparison);
        appendMetrics("policy_model_comparison", comparison);

        String key = "shared-policy/" + generation + "/" + modelName;
        ModelProgress current = models.get(key);
        if (current == null)
            throw new IllegalStateException("No fitted model progress exists for " + modelName
                    + " in generation " + generation);
        String state = selected
                ? finalGeneration ? "selected-for-final-release" : "selected-for-next-generation"
                : "retained-after-individual-evaluation";
        ModelProgress updated = new ModelProgress(
                current.format(), current.generation(), current.name(), state, current.examples(),
                current.positiveExamples(), current.negativeExamples(), current.trainingExamples(),
                current.validationExamples(), current.trainingAccuracy(), current.validationAccuracy(),
                current.incumbentValidationAccuracy(), current.trainingMillis());
        models.put(key, updated);
        appendMetrics("model", updated);

        recordEvent(Severity.SUCCESS, "individual-policy-evaluation", modelName,
                String.format(Locale.ROOT, "%s: %.1f%% paired win rate in %s; validation %.1f%% vs %s.",
                        selection, formatWinRate, format,
                        validationAccuracy,
                        incumbentValidationAccuracy == null
                                ? "no incumbent model"
                                : String.format(Locale.ROOT, "%.1f%% incumbent", incumbentValidationAccuracy)),
                null);
    }

    public synchronized void deckBuilderCompleted(DeckBuilderTrainer.TrainingResult result) {
        deckBuilders.put(format + "/" + generation, new DeckBuilderProgress(
                format, generation, result.candidates(), result.games(), result.wins(), result.losses(),
                result.skippedGames(),
                result.accumulatedExamples(), result.trainingExamples(), result.validationExamples(),
                result.algorithm(), finiteOrNull(result.validationAccuracy()),
                finiteOrNull(result.incumbentAccuracy()), result.promoted()));
        appendMetrics("deck_builder", deckBuilders.get(format + "/" + generation));
        String deckBuilderSelection = "not-ready".equals(result.algorithm())
                ? "no ranking was ready; the available ranking or random sampling was retained."
                : result.promoted() ? "new ranking selected." : "previous ranking retained.";
        recordEvent(Severity.SUCCESS, "deck-builder-generation-completed", "DeckBuilderTrainer",
                "Deck builder completed " + result.games() + " games with " + result.skippedGames()
                        + " skipped; " + deckBuilderSelection, null);
        if (currentStage != null) {
            String modelOutcome = "not-ready".equals(result.algorithm())
                    ? "ranking model not ready"
                    : result.promoted() ? "new ranking selected" : "previous ranking kept";
            currentStage.result = result.candidates() + " deck options; "
                    + result.wins() + " wins / " + result.losses() + " losses; " + modelOutcome;
        }
    }

    public synchronized void skipStage(String stageName, int generation, String reason) {
        for (MutableStage candidate : stages) {
            if (candidate.state.equals("pending")
                    && candidate.format.equals(format)
                    && candidate.name.equals(stageName)
                    && candidate.generation == generation) {
                candidate.state = "skipped";
                candidate.completed = candidate.total;
                candidate.result = reason;
                return;
            }
        }
    }

    public synchronized void finish(String finalStatus) {
        start();
        if (completedAt == null) {
            completedAt = Instant.now();
            completedNanos = System.nanoTime();
        }
        String lastStage = stage;
        closeCurrentStage(finalStatus);
        if ("completed".equals(finalStatus)) {
            for (MutableStage planned : stages) {
                if (planned.state.equals("pending")) {
                    planned.state = "skipped";
                    planned.completed = planned.total;
                    planned.result = "Not required";
                }
            }
        }
        status = finalStatus;
        stage = "completed".equals(finalStatus) ? "complete" : lastStage;
        currentStage = null;
        if ("completed".equals(finalStatus))
            completed = total;
        flushAllCycleRecoveries();
        if ("completed".equals(finalStatus))
            recordEvent(Severity.SUCCESS, "training-completed", null,
                    "Training completed and the final bot model release was published successfully.", null);
        appendMetrics("run_finished", snapshot());
    }

    @Override
    public synchronized void recordEvent(
            Severity severity,
            String type,
            String model,
            String message,
            Throwable failure) {
        if ("repeated-decision-cycle".equals(type) && severity == Severity.WARNING) {
            cycleRecoveries.merge(new CycleRecoveryKey(format, generation), 1L, Long::sum);
            return;
        }
        StringWriter stackTrace = new StringWriter();
        if (failure != null)
            failure.printStackTrace(new PrintWriter(stackTrace));
        TrainingEvent event = new TrainingEvent(nextEventId++, severity.name().toLowerCase(Locale.ROOT),
                Instant.now().toString(), format, generation, stage, model, type, message, stackTrace.toString());
        events.add(event);
        eventCounts.put(severity, eventCounts.get(severity) + 1);
        appendMetrics("event", event);
    }

    public synchronized EventPage eventPage(Severity severity, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50)
            throw new IllegalArgumentException("Event page must be positive and page size must be 1-50");
        List<TrainingEvent> matching = events.stream()
                .filter(event -> event.severity().equals(severity.name().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingLong(TrainingEvent::id).reversed())
                .toList();
        long offset = (long) (page - 1) * pageSize;
        int start = (int) Math.min(matching.size(), offset);
        int end = Math.min(matching.size(), start + pageSize);
        int pageCount = Math.max(1, (matching.size() + pageSize - 1) / pageSize);
        return new EventPage(matching.size(), page, pageSize, pageCount, matching.subList(start, end));
    }

    public synchronized Map<String, Object> snapshot() {
        List<StageStatus> stageStatuses = stages.stream()
                .map(MutableStage::toStatus)
                .toList();
        long overallTotal = stages.stream().mapToLong(item -> item.total).sum();
        long overallCompleted = stages.stream().mapToLong(item ->
                item.state.equals("completed") || item.state.equals("skipped")
                        ? item.total
                        : item.completed).sum();
        long completeStageCount = stages.stream()
                .filter(item -> item.state.equals("completed") || item.state.equals("skipped"))
                .count();
        double overallPercent = overallTotal == 0 ? 0.0
                : Math.min(100.0, overallCompleted * 100.0 / overallTotal);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("format", format);
        result.put("stage", stage);
        result.put("generation", generation);
        result.put("generationCount", generationCount);
        result.put("completed", completed);
        result.put("total", total);
        result.put("overallCompleted", overallCompleted);
        result.put("overallTotal", overallTotal);
        result.put("overallPercent", overallPercent);
        result.put("completedStages", completeStageCount);
        result.put("totalStages", stages.size());
        result.put("updatedAt", Instant.now().toString());
        result.put("startedAt", startedAt == null ? null : startedAt.toString());
        result.put("completedAt", completedAt == null ? null : completedAt.toString());
        long elapsedEnd = completedNanos == 0 ? System.nanoTime() : completedNanos;
        result.put("elapsedMillis", startedNanos == 0 ? 0
                : Math.max(0, (elapsedEnd - startedNanos) / 1_000_000));
        result.put("stages", stageStatuses);
        result.put("models", new ArrayList<>(models.values()));
        result.put("policyComparisons", new ArrayList<>(policyComparisons));
        result.put("deckBuilders", new ArrayList<>(deckBuilders.values()));
        result.put("batches", new ArrayList<>(batchResults.values()));
        Map<String, Long> eventCountSnapshot = new LinkedHashMap<>();
        for (Severity severity : Severity.values())
            eventCountSnapshot.put(severity.name().toLowerCase(Locale.ROOT), eventCounts.get(severity));
        result.put("eventCounts", eventCountSnapshot);
        result.put("runId", metricsWriter == null ? null : metricsWriter.runId());
        return result;
    }

    public synchronized void start() {
        if (startedAt == null) {
            startedAt = Instant.now();
            startedNanos = System.nanoTime();
        }
    }

    private void closeCurrentStage(String finalStatus) {
        if (currentStage == null)
            return;
        if (currentStage.state.equals("running")) {
            if (currentStage.completed >= currentStage.total)
                currentStage.state = "completed";
            else if ("failed".equals(finalStatus))
                currentStage.state = "failed";
            else
                currentStage.state = "partial";
        }
        appendMetrics("stage", currentStage.toStatus());
        currentStage = null;
    }

    private void appendMetrics(String type, Object data) {
        if (metricsWriter == null)
            return;
        try {
            metricsWriter.append(type, data);
        } catch (UncheckedIOException e) {
            metricsWriter = null;
            throw e;
        }
    }

    private void flushCycleRecoveries(String formatCode, int generationNumber) {
        CycleRecoveryKey key = new CycleRecoveryKey(formatCode, generationNumber);
        Long count = cycleRecoveries.remove(key);
        if (count != null)
            recordEvent(Severity.SUCCESS, "decision-cycle-recoveries", null,
                    count + " repeated-decision cycles were safely escaped during this simulation batch.",
                    null);
    }

    private void flushAllCycleRecoveries() {
        String previousFormat = format;
        int previousGeneration = generation;
        for (Map.Entry<CycleRecoveryKey, Long> entry : new ArrayList<>(cycleRecoveries.entrySet())) {
            CycleRecoveryKey key = entry.getKey();
            cycleRecoveries.remove(key);
            format = key.format();
            generation = key.generation();
            recordEvent(Severity.SUCCESS, "decision-cycle-recoveries", null,
                    entry.getValue() + " repeated-decision cycles were safely escaped.",
                    null);
        }
        format = previousFormat;
        generation = previousGeneration;
    }

    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static Double finiteOrNull(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }

    public record PlannedStage(String format, String name, int generation, int total) {
    }

    public record StageStatus(
            String format,
            String name,
            int generation,
            String state,
            int completed,
            int total,
            String result) {
    }

    public record ModelProgress(
            String format,
            int generation,
            String name,
            String state,
            long examples,
            long positiveExamples,
            long negativeExamples,
            int trainingExamples,
            int validationExamples,
            Double trainingAccuracy,
            Double validationAccuracy,
            Double incumbentValidationAccuracy,
            long trainingMillis) {
        public ModelProgress(
                String format,
                int generation,
                String name,
                String state,
                long examples,
                long positiveExamples,
                long negativeExamples,
                int trainingExamples,
                int validationExamples,
                Double trainingAccuracy,
                Double validationAccuracy,
                long trainingMillis) {
            this(format, generation, name, state, examples, positiveExamples, negativeExamples,
                    trainingExamples, validationExamples, trainingAccuracy, validationAccuracy, null, trainingMillis);
        }
    }

    public record BatchResult(
            String format,
            int generation,
            String label,
            int wins,
            int losses,
            int skippedGames,
            String selection,
            BatchTacticalMetrics tacticalMetrics) {
    }

    public record PolicyComparison(
            String format,
            int generation,
            String modelName,
            int evaluationSlot,
            int candidateWins,
            int games,
            int skippedGames,
            Double candidateWinRate,
            boolean candidateSelected,
            Double aggregateCandidateWinRate,
            Double aggregateLowerConfidenceBound,
            Double validationAccuracy,
            Double incumbentValidationAccuracy,
            String selection) {
        public PolicyComparison(
                String format,
                int generation,
                int candidateWins,
                int games,
                int skippedGames,
                Double candidateWinRate,
                boolean candidateSelected,
                Double aggregateCandidateWinRate,
                Double aggregateLowerConfidenceBound) {
            this(format, generation, "All decision models", 0, candidateWins, games, skippedGames,
                    candidateWinRate, candidateSelected, aggregateCandidateWinRate,
                    aggregateLowerConfidenceBound, null, null,
                    candidateSelected ? "Selected" : "Current policy kept");
        }
    }

    private record CycleRecoveryKey(String format, int generation) {
    }

    public record DeckBuilderProgress(
            String format,
            int generation,
            int candidates,
            int games,
            int wins,
            int losses,
            int skippedGames,
            int accumulatedExamples,
            int trainingExamples,
            int validationExamples,
            String algorithm,
            Double validationAccuracy,
            Double incumbentAccuracy,
            boolean promoted) {
    }

    public record TrainingEvent(
            long id,
            String severity,
            String timestamp,
            String format,
            int generation,
            String step,
            String model,
            String type,
            String message,
            String stackTrace) {
    }

    public record EventPage(long total, int page, int pageSize, int pageCount, List<TrainingEvent> events) {
        public EventPage {
            events = List.copyOf(events);
        }
    }

    private static final class MutableStage {
        private final String format;
        private final String name;
        private final int generation;
        private int completed;
        private int total;
        private String state = "pending";
        private String result = "";

        private MutableStage(String format, String name, int generation, int total) {
            this.format = format;
            this.name = name;
            this.generation = generation;
            this.total = total;
        }

        private StageStatus toStatus() {
            return new StageStatus(format, name, generation, state, completed, total, result);
        }
    }
}
