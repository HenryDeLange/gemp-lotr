package com.gempukku.lotro.cards.build.field.effect.modifier;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.EffectProcessor;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.ValueResolver;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.logic.modifiers.evaluator.Evaluator;
import org.json.simple.JSONObject;

public class ModifyOwnCost implements EffectProcessor {
    @Override
    public void processEffect(JSONObject value, BuiltLotroCardBlueprint blueprint, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(value, "amount", "condition", "on");

        final String onFilter = FieldUtils.getString(value.get("on"), "on", "any");
        final ValueSource amountSource = ValueResolver.resolveEvaluator(value.get("amount"), 0, environment);
        final JSONObject[] conditionArray = FieldUtils.getObjectArray(value.get("condition"), "condition");

        final FilterableSource onFilterableSource = environment.getFilterFactory().generateFilter(onFilter, environment);
        final Requirement[] requirements = environment.getRequirementFactory().getRequirements(conditionArray, environment);

        blueprint.appendTwilightCostModifier(
                (actionContext, target) -> {
                    for (Requirement requirement : requirements) {
                        if (!requirement.accepts(actionContext))
                            return 0;
                    }

                    if (target != null) {
                        if (!Filters.and(onFilterableSource.getFilterable(actionContext)).accepts(actionContext.getGame(), target))
                            return 0;
                    }

                    final Evaluator evaluator = amountSource.getEvaluator(actionContext);
                    return evaluator.evaluateExpression(actionContext.getGame(), actionContext.getSource());
                });
    }
}