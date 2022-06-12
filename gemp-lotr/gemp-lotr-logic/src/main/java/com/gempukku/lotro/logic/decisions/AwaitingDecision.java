package com.gempukku.lotro.logic.decisions;

import java.util.Map;

public abstract class AwaitingDecision {
    public abstract int getAwaitingDecisionId();

    public abstract String getText();

    public abstract AwaitingDecisionType getDecisionType();

    public abstract Map<String, String[]> getDecisionParameters();

    public abstract void decisionMade(String result) throws DecisionResultInvalidException;
}
