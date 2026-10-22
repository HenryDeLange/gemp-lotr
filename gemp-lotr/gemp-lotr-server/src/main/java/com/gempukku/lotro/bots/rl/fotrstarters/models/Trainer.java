package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.LearningStep;
import smile.classification.Classifier;

import java.util.List;

public interface Trainer {
    boolean isStepRelevant(LearningStep step);
    Classifier<double[]> train(List<LearningStep> steps);

    static boolean equal(Trainer t1, Trainer t2) {
        return t1.getClass().equals(t2.getClass());
    }
}
