package com.gempukku.lotro.cards.build.field.effect.appender;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.CardGenerationEnvironment;
import com.gempukku.lotro.cards.build.InvalidCardDefinitionException;
import com.gempukku.lotro.cards.build.PlayerSource;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.EffectAppender;
import com.gempukku.lotro.cards.build.field.effect.EffectAppenderProducer;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.decisions.IntegerAwaitingDecision;
import com.gempukku.lotro.logic.effects.PlayoutDecisionEffect;
import com.gempukku.lotro.logic.timing.Effect;
import org.json.simple.JSONObject;

public class ChooseANumber implements EffectAppenderProducer {
    @Override
    public EffectAppender createEffectAppender(JSONObject effectObject, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(effectObject, "player", "text", "from", "to", "memorize");

        final String player = FieldUtils.getString(effectObject.get("player"), "player", "you");
        final String displayText = FieldUtils.getString(effectObject.get("text"), "text", "Choose a number");
        final int from = FieldUtils.getInteger(effectObject.get("from"), "from", 0);
        final int to = FieldUtils.getInteger(effectObject.get("to"), "to", 1);
        final String memorize = FieldUtils.getString(effectObject.get("memorize"), "memorize");

        final PlayerSource playerSource = PlayerResolver.resolvePlayer(player, environment);

        if (memorize == null)
            throw new InvalidCardDefinitionException("ChooseANumber requires a field to memorize the value");

        return new DelayedAppender() {
            @Override
            protected Effect createEffect(boolean cost, CostToEffectAction action, ActionContext actionContext) {
                return new PlayoutDecisionEffect(actionContext.getPerformingPlayer(),
                        new IntegerAwaitingDecision(1, displayText, from, to) {
                    @Override
                    public void decisionMade(String result) throws DecisionResultInvalidException {
                        actionContext.setValueToMemory(memorize, String.valueOf(getValidatedResult(result)));
                    }
                });
            }
        };
    }
}