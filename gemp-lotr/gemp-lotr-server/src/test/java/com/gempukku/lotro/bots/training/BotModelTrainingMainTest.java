package com.gempukku.lotro.bots.training;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BotModelTrainingMainTest {
    @Test
    public void trainingFailurePrintsStageAndCompleteStackTrace() {
        TrainingProgress progress = new TrainingProgress();
        progress.beginFormat("king_block");
        progress.beginStage("king_block generation 1 self-play", 1, 800);
        PrintStream previousError = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
            BotModelTrainingMain.logTrainingFailure(progress, new NullPointerException("simulation failure"));
        } finally {
            System.setErr(previousError);
        }

        String loggedFailure = output.toString(StandardCharsets.UTF_8);
        assertTrue(loggedFailure.contains("king_block, generation 1, stage king_block generation 1 self-play"));
        assertTrue(loggedFailure.contains("java.lang.NullPointerException: simulation failure"));
        assertTrue(loggedFailure.contains("BotModelTrainingMainTest.trainingFailurePrintsStageAndCompleteStackTrace"));
    }

    @Test
    public void trainingThreadsIncludeStageAndWorkerNumber() {
        TrainingThreadFactory threadFactory = new TrainingThreadFactory("king_block generation 1 vs-random");

        assertEquals("bot-training-king_block-generation-1-vs-random-worker-1",
                threadFactory.newThread(() -> { }).getName());
        assertEquals("bot-training-king_block-generation-1-vs-random-worker-2",
                threadFactory.newThread(() -> { }).getName());
    }
}
