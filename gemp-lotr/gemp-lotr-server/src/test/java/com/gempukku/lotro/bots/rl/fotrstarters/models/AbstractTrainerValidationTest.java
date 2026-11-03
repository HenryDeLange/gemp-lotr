package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AbstractTrainerValidationTest {
    @Test
    public void validationSplitKeepsEveryGameInExactlyOnePartition() {
        List<LabeledPoint> points = trainingPoints();

        AbstractTrainer.DatasetSplit split = AbstractTrainer.splitByGame(points);

        Set<String> trainingGames = split.training().stream()
                .map(LabeledPoint::gameId)
                .collect(Collectors.toSet());
        Set<String> validationGames = split.validation().stream()
                .map(LabeledPoint::gameId)
                .collect(Collectors.toSet());

        assertFalse(trainingGames.isEmpty());
        assertFalse(validationGames.isEmpty());
        assertTrue(trainingGames.stream().noneMatch(validationGames::contains));
        assertEquals(points.size(), split.training().size() + split.validation().size());
    }

    @Test
    public void reportsAccuracyOnWholeHeldOutGames() {
        List<LabeledPoint> points = trainingPoints();
        TestTrainer trainer = new TestTrainer(points);
        var incumbent = trainer.trainWithPoints(points);

        AbstractTrainer.TrainingAttempt attempt = trainer.trainIfReadyWithMetrics(List.of(), incumbent);

        assertTrue(attempt.result().isPresent());
        AbstractTrainer.TrainingResult result = attempt.result().orElseThrow();
        assertEquals(20, attempt.totalExamples());
        assertEquals(16, result.trainingExamples());
        assertEquals(4, result.validationExamples());
        assertEquals(100.0, result.trainingAccuracy(), 0.01);
        assertNotNull(attempt.validationAccuracy());
        assertEquals(100.0, result.validationAccuracy(), 0.01);
        assertEquals(100.0, result.incumbentValidationAccuracy(), 0.01);
    }

    @Test
    public void evaluatesLegacyIncumbentUsingTheCompatibleFeaturePrefix() {
        Classifier<double[]> incumbent = new TestTrainer(trainingPoints()).trainWithPoints(trainingPoints());
        TestTrainer trainer = new TestTrainer(extendedTrainingPoints(), 1);

        AbstractTrainer.TrainingAttempt attempt = trainer.trainIfReadyWithMetrics(List.of(), incumbent);

        assertTrue(attempt.result().isPresent());
        assertEquals(100.0, attempt.result().orElseThrow().incumbentValidationAccuracy(), 0.01);
    }

    @Test
    public void weightedTrainingSamplingIsDeterministicAndFavorsRecentExamples() {
        List<LabeledPoint> points = new ArrayList<>();
        for (int i = 0; i < 999; i++)
            points.add(new LabeledPoint(0, new double[]{0.0}, "game-" + i, 1.0));
        points.add(new LabeledPoint(1, new double[]{1.0}, "game-heavy", 100.0));

        List<LabeledPoint> firstSample = AbstractTrainer.weightedTrainingSample(points);
        List<LabeledPoint> secondSample = AbstractTrainer.weightedTrainingSample(points);

        assertEquals(firstSample, secondSample);
        assertTrue(firstSample.stream().filter(point -> point.y() == 1).count() > 60);
    }

    @Test
    public void gameBalancedSamplingBalancesGamesAndLabelsDeterministically() {
        List<LabeledPoint> points = new ArrayList<>();
        for (int index = 0; index < 900; index++)
            points.add(new LabeledPoint(0, new double[]{0.0}, "long-game"));
        for (int index = 0; index < 100; index++)
            points.add(new LabeledPoint(1, new double[]{1.0}, "long-game"));
        for (int index = 0; index < 10; index++) {
            points.add(new LabeledPoint(0, new double[]{0.0}, "short-game"));
            points.add(new LabeledPoint(1, new double[]{1.0}, "short-game"));
        }

        List<LabeledPoint> sample = AbstractTrainer.gameAndLabelBalancedTrainingSample(points);

        assertEquals(points.size(), sample.size());
        assertEquals(sample, AbstractTrainer.gameAndLabelBalancedTrainingSample(points));
        Map<String, Map<Integer, Integer>> countsByGameAndLabel = new HashMap<>();
        for (LabeledPoint point : sample) {
            countsByGameAndLabel.computeIfAbsent(point.gameId(), ignored -> new HashMap<>())
                    .merge(point.y(), 1, Integer::sum);
        }

        int longGameCount = countsByGameAndLabel.get("long-game").values().stream()
                .mapToInt(Integer::intValue).sum();
        int shortGameCount = countsByGameAndLabel.get("short-game").values().stream()
                .mapToInt(Integer::intValue).sum();
        assertTrue(Math.abs(longGameCount - shortGameCount) < 100);
        for (Map<Integer, Integer> labelCounts : countsByGameAndLabel.values())
            assertTrue(Math.abs(labelCounts.getOrDefault(0, 0) - labelCounts.getOrDefault(1, 0)) < 100);
    }

    @Test
    public void gameBalancedSamplingRequiresEpisodeIds() {
        List<LabeledPoint> points = List.of(new LabeledPoint(0, new double[]{0.0}));

        assertThrows(IllegalArgumentException.class,
                () -> AbstractTrainer.gameAndLabelBalancedTrainingSample(points));
    }

    @Test
    public void gameBalancedModePreservesWholeGameValidationSplit() {
        TestTrainer trainer = new TestTrainer(trainingPoints());

        AbstractTrainer.TrainingAttempt attempt =
                trainer.trainIfReadyWithMetrics(List.of(), null, true);

        assertEquals("trained", attempt.status());
        assertEquals(16, attempt.trainingExamples());
        assertEquals(4, attempt.validationExamples());
    }

    @Test
    public void gameBalancedFitScoresTheOriginalValidationExamplesAndWeights() {
        List<LabeledPoint> points = new ArrayList<>();
        for (int game = 0; game < 10; game++) {
            String gameId = "weighted-game-" + game;
            points.add(new LabeledPoint(0, new double[]{-2.0}, gameId, 1.0));
            points.add(new LabeledPoint(1, new double[]{2.0}, gameId, 1.0));
        }
        String heldOutGame = AbstractTrainer.splitByGame(points).validation().getFirst().gameId();
        List<LabeledPoint> weightedPoints = new ArrayList<>();
        for (LabeledPoint point : points) {
            boolean misclassifiedValidationExample =
                    point.gameId().equals(heldOutGame) && point.y() == 1;
            weightedPoints.add(new LabeledPoint(point.y(),
                    misclassifiedValidationExample ? new double[]{-2.0} : point.x(),
                    point.gameId(), misclassifiedValidationExample ? 100.0 : point.weight()));
        }
        List<LabeledPoint> expectedValidation = AbstractTrainer.splitByGame(weightedPoints).validation();
        TestTrainer trainer = new TestTrainer(weightedPoints);

        AbstractTrainer.TrainingAttempt attempt =
                trainer.trainIfReadyWithMetrics(List.of(), null, true);

        var model = attempt.result().orElseThrow().model();
        double correctWeight = 0.0;
        double totalWeight = 0.0;
        int correctExamples = 0;
        for (LabeledPoint point : expectedValidation) {
            totalWeight += point.weight();
            if (model.predict(point.x()) == point.y()) {
                correctWeight += point.weight();
                correctExamples++;
            }
        }
        assertEquals(expectedValidation.size(), attempt.validationExamples());
        assertEquals(correctWeight * 100.0 / totalWeight,
                attempt.result().orElseThrow().validationAccuracy(), 0.0001);
        assertTrue(Math.abs(correctExamples * 100.0 / expectedValidation.size()
                - correctWeight * 100.0 / totalWeight) > 1.0);
    }

    private static List<LabeledPoint> trainingPoints() {
        List<LabeledPoint> points = new ArrayList<>();
        for (int game = 0; game < 10; game++) {
            String gameId = "game-" + game;
            points.add(new LabeledPoint(0, new double[]{-2.0}, gameId));
            points.add(new LabeledPoint(1, new double[]{2.0}, gameId));
        }
        return points;
    }

    private static List<LabeledPoint> extendedTrainingPoints() {
        List<LabeledPoint> points = new ArrayList<>();
        for (int game = 0; game < 10; game++) {
            String gameId = "game-" + game;
            points.add(new LabeledPoint(0, new double[]{-2.0, 1.0}, gameId));
            points.add(new LabeledPoint(1, new double[]{2.0, 1.0}, gameId));
        }
        return points;
    }

    private static final class TestTrainer extends AbstractTrainer {
        private final List<LabeledPoint> points;
        private final int legacyFeatureCount;

        private TestTrainer(List<LabeledPoint> points) {
            this(points, -1);
        }

        private TestTrainer(List<LabeledPoint> points, int legacyFeatureCount) {
            this.points = points;
            this.legacyFeatureCount = legacyFeatureCount;
        }

        @Override
        protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
            return points;
        }

        @Override
        protected double[] getModelInputVector(Classifier<double[]> model, double[] features) {
            return legacyFeatureCount < 0
                    ? features
                    : ModelIO.adaptModelInputVector(model, features, legacyFeatureCount);
        }

        @Override
        public boolean isStepRelevant(LearningStep step) {
            return false;
        }

        @Override
        public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
            return false;
        }

        @Override
        public String getAnswer(
                GameState gameState,
                AwaitingDecision decision,
                String playerName,
                RLGameStateFeatures features,
                ModelRegistry modelRegistry) {
            return "";
        }
    }
}
