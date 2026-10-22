package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.DecisionAnswerer;
import com.gempukku.lotro.bots.rl.LearningStep;
import smile.classification.LogisticRegression;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

public abstract class AbstractTrainer implements Trainer, DecisionAnswerer {
    private static final int MINIMUM_EXAMPLES_PER_CLASS = 2;
    private static final int VALIDATION_GAME_RATIO = 5;

    abstract protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps);

    public Classifier<double[]> trainWithPoints(List<LabeledPoint> points) {
        List<LabeledPoint> sampledPoints = weightedTrainingSample(points);
        double[][] x = sampledPoints.stream().map(LabeledPoint::x).toArray(double[][]::new);
        int[] y = sampledPoints.stream().mapToInt(LabeledPoint::y).toArray();
        return LogisticRegression.fit(x, y);
    }

    @Override
    public Classifier<double[]> train(List<LearningStep> steps) {
        return trainWithPoints(extractTrainingData(steps));
    }

    public Optional<TrainingResult> trainIfReady(List<LearningStep> steps) {
        return trainIfReadyWithMetrics(steps).result();
    }

    public TrainingAttempt trainIfReadyWithMetrics(List<LearningStep> steps) {
        return trainIfReadyWithMetrics(steps, null);
    }

    public TrainingAttempt trainIfReadyWithMetrics(
            List<LearningStep> steps,
            Classifier<double[]> incumbentModel) {
        List<LabeledPoint> points = extractTrainingData(steps);
        long positiveCount = points.stream().filter(point -> point.y() > 0).count();
        long negativeCount = points.stream().filter(point -> point.y() == 0).count();
        if (points.isEmpty())
            return TrainingAttempt.notReady(points.size(), positiveCount, negativeCount, "no-examples");

        DatasetSplit split = splitByGame(points);
        if (split == null)
            return TrainingAttempt.notReady(
                    points.size(), positiveCount, negativeCount, "insufficient-independent-games");

        Map<Integer, Long> trainingExamplesByClass = new HashMap<>();
        for (LabeledPoint point : split.training())
            trainingExamplesByClass.merge(point.y(), 1L, Long::sum);
        if (trainingExamplesByClass.size() < 2
                || trainingExamplesByClass.values().stream()
                .anyMatch(count -> count < MINIMUM_EXAMPLES_PER_CLASS)) {
            return new TrainingAttempt(Optional.empty(), points.size(), positiveCount, negativeCount,
                    split.training().size(), split.validation().size(), null, null, 0,
                    "insufficient-training-class-examples");
        }

        int featureCount = points.getFirst().x().length;
        if (featureCount == 0)
            throw new IllegalArgumentException(getClass().getSimpleName() + " produced empty feature vectors");
        for (LabeledPoint point : points) {
            if (point.x().length != featureCount)
                throw new IllegalArgumentException(getClass().getSimpleName()
                        + " produced inconsistent feature vector lengths: expected " + featureCount
                        + " but got " + point.x().length);
            for (double feature : point.x()) {
                if (!Double.isFinite(feature))
                    throw new IllegalArgumentException(getClass().getSimpleName() + " produced a non-finite feature value");
            }
        }

        long trainingStart = System.nanoTime();
        Classifier<double[]> model = trainWithPoints(split.training());
        long trainingMillis = (System.nanoTime() - trainingStart) / 1_000_000;
        double trainingAccuracy = weightedAccuracy(model, split.training());
        double validationAccuracy = weightedAccuracy(model, split.validation());
        Double incumbentValidationAccuracy = incumbentModel == null
                ? null
                : weightedAccuracy(incumbentModel, split.validation());
        TrainingResult result = new TrainingResult(model, positiveCount, negativeCount,
                split.training().size(), split.validation().size(), trainingAccuracy, validationAccuracy,
                incumbentValidationAccuracy);
        return new TrainingAttempt(Optional.of(result), points.size(), positiveCount, negativeCount,
                split.training().size(), split.validation().size(), trainingAccuracy,
                validationAccuracy, trainingMillis, "trained");
    }

    static DatasetSplit splitByGame(List<LabeledPoint> points) {
        Map<String, List<LabeledPoint>> examplesByGame = new HashMap<>();
        for (LabeledPoint point : points) {
            String gameId = point.gameId();
            if (gameId == null || gameId.isBlank())
                throw new IllegalArgumentException(
                        "Training data is missing a game ID; whole-game validation requires episode IDs");
            examplesByGame.computeIfAbsent(gameId, ignored -> new ArrayList<>()).add(point);
        }
        if (examplesByGame.size() < 2)
            return null;

        List<LabeledPoint> training = new ArrayList<>();
        List<LabeledPoint> validation = new ArrayList<>();
        for (Map.Entry<String, List<LabeledPoint>> entry : examplesByGame.entrySet()) {
            boolean heldOut = Math.floorMod(entry.getKey().hashCode(), VALIDATION_GAME_RATIO) == 0;
            (heldOut ? validation : training).addAll(entry.getValue());
        }
        if (training.isEmpty() || validation.isEmpty())
            return null;
        return new DatasetSplit(List.copyOf(training), List.copyOf(validation));
    }

    static List<LabeledPoint> weightedTrainingSample(List<LabeledPoint> points) {
        if (points.isEmpty())
            return List.of();

        double firstWeight = points.getFirst().weight();
        if (points.stream().allMatch(point -> Double.compare(point.weight(), firstWeight) == 0))
            return points;

        double[] cumulativeWeights = new double[points.size()];
        double totalWeight = 0.0;
        for (int index = 0; index < points.size(); index++) {
            totalWeight += points.get(index).weight();
            cumulativeWeights[index] = totalWeight;
        }

        SplittableRandom random = new SplittableRandom(0x5EEDL);
        List<LabeledPoint> sample = new ArrayList<>(points.size());
        for (int index = 0; index < points.size(); index++) {
            double selectedWeight = random.nextDouble(totalWeight);
            int selectedIndex = Arrays.binarySearch(cumulativeWeights, selectedWeight);
            if (selectedIndex < 0)
                selectedIndex = -selectedIndex - 1;
            sample.add(points.get(selectedIndex));
        }
        return sample;
    }

    private static double weightedAccuracy(Classifier<double[]> model, List<LabeledPoint> examples) {
        if (examples.isEmpty())
            throw new IllegalArgumentException("Validation set must contain at least one complete game");
        double correctWeight = 0.0;
        double totalWeight = 0.0;
        for (LabeledPoint point : examples) {
            totalWeight += point.weight();
            if (model.predict(point.x()) == point.y())
                correctWeight += point.weight();
        }
        return correctWeight * 100.0 / totalWeight;
    }

    public record TrainingResult(
            Classifier<double[]> model,
            long positiveExamples,
            long negativeExamples,
            int trainingExamples,
            int validationExamples,
            double trainingAccuracy,
            double validationAccuracy,
            Double incumbentValidationAccuracy) {
        public TrainingResult(
                Classifier<double[]> model,
                long positiveExamples,
                long negativeExamples,
                int trainingExamples,
                int validationExamples,
                double trainingAccuracy,
                double validationAccuracy) {
            this(model, positiveExamples, negativeExamples, trainingExamples, validationExamples,
                    trainingAccuracy, validationAccuracy, null);
        }
    }

    public record TrainingAttempt(
            Optional<TrainingResult> result,
            long totalExamples,
            long positiveExamples,
            long negativeExamples,
            int trainingExamples,
            int validationExamples,
            Double trainingAccuracy,
            Double validationAccuracy,
            long trainingMillis,
            String status) {
        private static TrainingAttempt notReady(
                long totalExamples,
                long positiveExamples,
                long negativeExamples,
                String status) {
            return new TrainingAttempt(Optional.empty(), totalExamples, positiveExamples, negativeExamples,
                    0, 0, null, null, 0, status);
        }
    }

    record DatasetSplit(List<LabeledPoint> training, List<LabeledPoint> validation) {
    }
}
