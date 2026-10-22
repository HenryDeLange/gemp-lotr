package com.gempukku.lotro.bots.rl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.rl.semanticaction.SemanticAction;
import com.gempukku.lotro.bots.rl.semanticaction.SemanticActionFactory;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionFactory;
import com.gempukku.util.JsonSerializable;

public class LearningStep implements JsonSerializable {
    public final double[] state;
    public final boolean fpPlayer;
    public final SemanticAction action;
    public final AwaitingDecision decision;
    public final String gameId;
    public double reward;
    public double trainingWeight = 1.0;

    public LearningStep(double[] state, SemanticAction action, boolean fpPlayer, AwaitingDecision decision) {
        this(state, action, fpPlayer, decision, null);
    }

    public LearningStep(
            double[] state,
            SemanticAction action,
            boolean fpPlayer,
            AwaitingDecision decision,
            String gameId) {
        this.state = state;
        this.action = action;
        this.fpPlayer = fpPlayer;
        this.decision = decision;
        this.gameId = gameId;
    }

    @Override
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("state", state);
        obj.put("fpPlayer", fpPlayer);
        obj.put("reward", reward);
        obj.put("gameId", gameId);
        obj.put("action", action.toJson());
        obj.put("decision", decision.toJson());
        obj.put("trainingWeight", trainingWeight);
        return obj;
    }

    public static LearningStep fromJson(String json) {
        JSONObject obj = JSON.parseObject(json);
        double[] state = obj.getObject("state", double[].class);
        boolean fpPlayer = obj.getBoolean("fpPlayer");
        double reward = obj.getDoubleValue("reward");
        String gameId = obj.getString("gameId");

        JSONObject actionObj = obj.getJSONObject("action");
        SemanticAction action = SemanticActionFactory.fromJson(actionObj);

        JSONObject decisionObj = obj.getJSONObject("decision");
        AwaitingDecision decision = AwaitingDecisionFactory.fromJson(decisionObj);

        LearningStep step = new LearningStep(state, action, fpPlayer, decision, gameId);
        step.reward = reward;
        step.trainingWeight = obj.containsKey("trainingWeight")
                ? obj.getDoubleValue("trainingWeight")
                : 1.0;
        return step;
    }
}
