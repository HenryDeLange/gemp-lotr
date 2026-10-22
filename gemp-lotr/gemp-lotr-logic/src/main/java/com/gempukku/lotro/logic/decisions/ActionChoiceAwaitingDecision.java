package com.gempukku.lotro.logic.decisions;

import com.alibaba.fastjson2.JSONObject;

public class ActionChoiceAwaitingDecision extends AbstractAwaitingDecision {
    public ActionChoiceAwaitingDecision(
            int id,
            String text,
            String[] actionIds,
            String[] blueprintIds,
            String[] actionTexts) {
        super(id, text, AwaitingDecisionType.ACTION_CHOICE);
        setParam("actionId", actionIds);
        setParam("blueprintId", blueprintIds);
        setParam("actionText", actionTexts);
    }

    public static ActionChoiceAwaitingDecision fromJson(JSONObject obj) {
        return new ActionChoiceAwaitingDecision(
                obj.getInteger("id"),
                obj.getString("text"),
                obj.getObject("actionId", String[].class),
                obj.getObject("blueprintId", String[].class),
                obj.getObject("actionText", String[].class));
    }

    @Override
    public void decisionMade(String result) {
        throw new UnsupportedOperationException("Not implemented in training context");
    }
}
