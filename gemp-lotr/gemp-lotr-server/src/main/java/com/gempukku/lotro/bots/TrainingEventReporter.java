package com.gempukku.lotro.bots;

import com.gempukku.lotro.common.Phase;

public interface TrainingEventReporter {
    void recordEvent(Severity severity, String type, String model, String message, Throwable failure);

    default void recordPassGateVeto(Phase phase) {
        recordEvent(Severity.SUCCESS, "generic-action-pass-veto", null,
                "The shared card-action model preferred Pass over a phase-specific action in " + phase + ".", null);
    }

    default void recordDiagnosticTrace(TrainingDiagnosticTrace trace) {
    }

    enum Severity {
        SUCCESS,
        WARNING,
        ERROR
    }
}
