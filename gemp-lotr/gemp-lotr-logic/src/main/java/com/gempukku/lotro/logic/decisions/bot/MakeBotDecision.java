package com.gempukku.lotro.logic.decisions.bot;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;

public abstract class MakeBotDecision {
    public abstract String getBotChoice(AwaitingDecision awaitingDecision);

    protected String getArrayAsString(String[] values) {
        StringBuilder result = new StringBuilder();
        if (values != null && values.length > 0) {
            for (String value : values) {
                if (result.length() == 0) {
                    result.append(value);
                }
                else {
                    result.append(" / ").append(value);
                }
            }
            return result.toString();
        }
        return "<empty>";
    }

}
