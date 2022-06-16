package com.gempukku.lotro.communication;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;

public interface UserFeedback {
    public void sendAwaitingDecision(String playerId, AwaitingDecision awaitingDecision);

    public boolean hasPendingDecisions();

    /**
     * Don't use, only added for the bot to use
     */
    @Deprecated
    public void resetBotActionHistory();
}
