package com.gempukku.lotro.bots;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public record TrainingDiagnosticTrace(
        int schemaVersion,
        String kind,
        String format,
        String phase,
        String decisionType,
        String decisionFingerprint,
        String observerStateFingerprint,
        Map<String, Integer> parameterCounts,
        AnswerOutcome proposedAnswer,
        AnswerOutcome acceptedAnswer,
        int cycleLength,
        List<CycleDecision> cyclePattern) {
    private static final byte[] FINGERPRINT_KEY = createFingerprintKey();

    public TrainingDiagnosticTrace {
        parameterCounts = Map.copyOf(parameterCounts);
        cyclePattern = List.copyOf(cyclePattern);
    }

    public static TrainingDiagnosticTrace create(
            String kind,
            String format,
            String phase,
            AwaitingDecision decision,
            String observerStateSignature,
            String proposedAnswer,
            String acceptedAnswer,
            int cycleLength,
            List<CycleDecision> cyclePattern) {
        Map<String, String[]> parameters = decision.getDecisionParameters();
        Map<String, Integer> counts = new TreeMap<>();
        parameters.forEach((name, values) -> counts.put(name, values == null ? 0 : values.length));
        String decisionMaterial = decision.getText() + "\n" + parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Arrays.toString(entry.getValue()))
                .reduce("", (left, right) -> left + right + "\n");

        return new TrainingDiagnosticTrace(
                1,
                kind,
                format,
                phase,
                decision.getDecisionType().name(),
                fingerprint(decisionMaterial),
                fingerprint(observerStateSignature),
                counts,
                answerOutcome(proposedAnswer, decision.getDecisionType()),
                answerOutcome(acceptedAnswer, decision.getDecisionType()),
                cycleLength,
                cyclePattern);
    }

    public static String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(FINGERPRINT_KEY, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to create a diagnostic fingerprint", e);
        }
    }

    private static byte[] createFingerprintKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static AnswerOutcome answerOutcome(String answer, AwaitingDecisionType decisionType) {
        if (answer == null)
            return new AnswerOutcome("unavailable", null);
        if (answer.isEmpty())
            return new AnswerOutcome("empty", null);
        if ((decisionType == AwaitingDecisionType.CARD_ACTION_CHOICE
                || decisionType == AwaitingDecisionType.MULTIPLE_CHOICE)
                && answer.matches("[0-9]+") && answer.length() <= 9)
            return new AnswerOutcome("choice-index", Integer.parseInt(answer));
        return new AnswerOutcome("opaque", null);
    }

    public record AnswerOutcome(String kind, Integer index) {
    }

    public record CycleDecision(
            String decisionType,
            String decisionFingerprint,
            AnswerOutcome answer) {
    }
}
