package com.gempukku.lotro.logic.decisions.bot;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;

public interface MakeBotDecision {
    public String getBotChoice(AwaitingDecision awaitingDecision);
}
