package com.gempukku.lotro.bots.rl.fotrstarters.models;

import smile.classification.Classifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ModelRegistry {
    private final Map<Class<? extends Trainer>, Classifier<double[]>> modelMap = new HashMap<>();

    public <T extends Trainer> void registerModel(Class<T> trainerClass, Classifier<double[]> model) {
        modelMap.put(Objects.requireNonNull(trainerClass, "trainerClass"), Objects.requireNonNull(model, "model"));
    }

    public <T extends Trainer> Classifier<double[]> getModel(Class<T> trainerClass) {
        return modelMap.get(trainerClass);
    }

    public boolean hasModel(Class<? extends Trainer> trainerClass) {
        return modelMap.get(trainerClass) != null;
    }

    public Set<Class<? extends Trainer>> getRegisteredTrainerTypes() {
        return Collections.unmodifiableSet(modelMap.keySet());
    }

    public ModelRegistry copy() {
        ModelRegistry copy = new ModelRegistry();
        copy.modelMap.putAll(modelMap);
        return copy;
    }
}
