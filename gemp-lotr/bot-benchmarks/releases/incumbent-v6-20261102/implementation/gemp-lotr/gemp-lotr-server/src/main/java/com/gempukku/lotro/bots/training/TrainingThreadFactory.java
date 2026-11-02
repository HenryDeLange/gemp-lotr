package com.gempukku.lotro.bots.training;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class TrainingThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger nextThreadNumber = new AtomicInteger(1);

    public TrainingThreadFactory(String stageName) {
        String normalizedStageName = Objects.requireNonNull(stageName, "stageName")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalizedStageName.isEmpty())
            throw new IllegalArgumentException("Training thread stage name must not be blank");
        namePrefix = "bot-training-" + normalizedStageName + "-worker-";
    }

    @Override
    public Thread newThread(Runnable runnable) {
        return new Thread(runnable, namePrefix + nextThreadNumber.getAndIncrement());
    }
}
