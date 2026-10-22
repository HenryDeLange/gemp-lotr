package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    private static List<LabeledPoint> trainingPoints() {
        List<LabeledPoint> points = new ArrayList<>();
        for (int game = 0; game < 10; game++) {
            String gameId = "game-" + game;
            points.add(new LabeledPoint(0, new double[]{-2.0}, gameId));
            points.add(new LabeledPoint(1, new double[]{2.0}, gameId));
        }
        return points;
    }

    private static final class TestTrainer extends AbstractTrainer {
        private final List<LabeledPoint> points;

        private TestTrainer(List<LabeledPoint> points) {
            this.points = points;
        }

        @Override
        protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
            return points;
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
