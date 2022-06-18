package com.gempukku.lotro.logic.decisions.bot;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;

public abstract class MakeBotDecision {
    
    public abstract String getBotChoice(LotroGame _game, AwaitingDecision awaitingDecision);

    protected String getArrayAsString(String[] values) {
        if (values != null && values.length > 0) {
            return String.join(" | ", values);
        }
        return "<empty>";
    }

}
