package com.gempukku.lotro.bots;

public interface TrainingEventReporter {
    void recordEvent(Severity severity, String type, String model, String message, Throwable failure);

    enum Severity {
        SUCCESS,
        WARNING,
        ERROR
    }
}
