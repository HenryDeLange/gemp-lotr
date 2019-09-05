package com.gempukku.lotro.cards.build.field.effect.appender.resolver;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.common.Filterable;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.modifiers.evaluator.*;
import org.json.simple.JSONObject;

public class ValueResolver {
    public static ValueSource resolveEvaluator(Object value, int defaultValue, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        if (value == null)
            return new ConstantEvaluator(defaultValue);
        if (value instanceof Number)
            return new ConstantEvaluator(((Number) value).intValue());
        if (value instanceof String) {
            String stringValue = (String) value;
            if (stringValue.contains("-")) {
                final String[] split = stringValue.split("-", 2);
                final int min = Integer.parseInt(split[0]);
                final int max = Integer.parseInt(split[1]);
                if (min > max || min < 0 || max < 1)
                    throw new InvalidCardDefinitionException("Unable to resolve count: " + value);
                return new ValueSource() {
                    @Override
                    public Evaluator getEvaluator(ActionContext actionContext) {
                        throw new RuntimeException("Evaluator has resolved to range");
                    }

                    @Override
                    public int getMinimum(ActionContext actionContext) {
                        return min;
                    }

                    @Override
                    public int getMaximum(ActionContext actionContext) {
                        return max;
                    }
                };
            } else {
                int v = Integer.parseInt(stringValue);
                return new ConstantEvaluator(v);
            }
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            final String type = FieldUtils.getString(object.get("type"), "type");
            if (type.equalsIgnoreCase("condition")) {
                final JSONObject[] conditionArray = FieldUtils.getObjectArray(object.get("condition"), "condition");
                final Requirement[] conditions = environment.getRequirementFactory().getRequirements(conditionArray, environment);
                int trueValue = FieldUtils.getInteger(object.get("true"), "true");
                int falseValue = FieldUtils.getInteger(object.get("false"), "false");
                return (actionContext) -> (Evaluator) (game, cardAffected) -> {
                    for (Requirement condition : conditions) {
                        if (!condition.accepts(actionContext))
                            return falseValue;
                    }
                    return trueValue;
                };
            } else if (type.equalsIgnoreCase("forRegionNumber")) {
                return (actionContext) -> (Evaluator) (game, cardAffected) -> GameUtils.getRegion(actionContext.getGame());
            } else if (type.equalsIgnoreCase("forEachInMemory")) {
                final String memory = FieldUtils.getString(object.get("memory"), "memory");
                return (actionContext) -> {
                    final int count = actionContext.getCardsFromMemory(memory).size();
                    return new ConstantEvaluator(count);
                };
            } else if (type.equalsIgnoreCase("forEachThreat")) {
                return actionContext -> new ForEachThreatEvaluator();
            } else if (type.equalsIgnoreCase("forEachWound")) {
                final String filter = FieldUtils.getString(object.get("filter"), "filter");
                final FilterableSource filterableSource = environment.getFilterFactory().generateFilter(filter, environment);
                return (actionContext) -> (Evaluator) (game, cardAffected) -> {
                    int wounds = 0;
                    for (PhysicalCard physicalCard : Filters.filterActive(actionContext.getGame(), filterableSource.getFilterable(actionContext))) {
                        wounds += actionContext.getGame().getGameState().getWounds(physicalCard);
                    }
                    return wounds;
                };
            } else if (type.equalsIgnoreCase("forEachKeywordOnCardInMemory")) {
                final String memory = FieldUtils.getString(object.get("memory"), "memory");
                final Keyword keyword = FieldUtils.getEnum(Keyword.class, object.get("keyword"), "keyword");
                return (actionContext) -> {
                    final LotroGame game = actionContext.getGame();
                    int count = game.getModifiersQuerying().getKeywordCount(game, actionContext.getCardFromMemory(memory), keyword);
                    return new ConstantEvaluator(count);
                };
            } else if (type.equalsIgnoreCase("limit")) {
                final int limit = FieldUtils.getInteger(object.get("value"), "value");
                ValueSource valueSource = resolveEvaluator(object.get("amount"), 0, environment);
                return (actionContext) -> new LimitEvaluator(valueSource.getEvaluator(actionContext), limit);
            } else if (type.equalsIgnoreCase("countStacked")) {
                final String on = FieldUtils.getString(object.get("on"), "on");
                final String filter = FieldUtils.getString(object.get("filter"), "filter", "any");

                final FilterableSource filterableSource = environment.getFilterFactory().generateFilter(filter, environment);
                final FilterableSource onFilter = environment.getFilterFactory().generateFilter(on, environment);

                return (actionContext) -> {
                    final Filterable on1 = onFilter.getFilterable(actionContext);
                    return new CountStackedEvaluator(on1, filterableSource.getFilterable(actionContext));
                };
            } else if (type.equalsIgnoreCase("forEachYouCanSpot")) {
                final String filter = FieldUtils.getString(object.get("filter"), "filter");
                final int over = FieldUtils.getInteger(object.get("over"), "over", 0);
                final int limit = FieldUtils.getInteger(object.get("limit"), "limit", Integer.MAX_VALUE);
                final FilterableSource filterableSource = environment.getFilterFactory().generateFilter(filter, environment);
                return actionContext -> new CountSpottableEvaluator(over, limit, filterableSource.getFilterable(actionContext));
            } else if (type.equalsIgnoreCase("forEachInHand")) {
                final String filter = FieldUtils.getString(object.get("filter"), "filter");
                final FilterableSource filterableSource = environment.getFilterFactory().generateFilter(filter, environment);
                return actionContext ->
                        (Evaluator) (game, cardAffected) -> Filters.filter(game.getGameState().getHand(actionContext.getPerformingPlayer()),
                                game, filterableSource.getFilterable(actionContext)).size();
            } else if (type.equalsIgnoreCase("fromMemory")) {
                String memory = FieldUtils.getString(object.get("memory"), "memory");
                return (actionContext) -> {
                    int value1 = Integer.parseInt(actionContext.getValueFromMemory(memory));
                    return new ConstantEvaluator(value1);
                };
            } else if (type.equalsIgnoreCase("multiply")) {
                final int multiplier = FieldUtils.getInteger(object.get("multiplier"), "multiplier");
                final ValueSource valueSource = ValueResolver.resolveEvaluator(object.get("source"), 0, environment);
                return (actionContext) -> new MultiplyEvaluator(multiplier, valueSource.getEvaluator(actionContext));
            } else if (type.equalsIgnoreCase("forEachVitality")) {
                final int over = FieldUtils.getInteger(object.get("over"), "over", 0);
                return (actionContext) -> (game, cardAffected) -> Math.max(0, game.getModifiersQuerying().getVitality(game, cardAffected) - over);
            } else if (type.equalsIgnoreCase("subtract")) {
                final ValueSource firstNumber = ValueResolver.resolveEvaluator(object.get("firstNumber"), 0, environment);
                final ValueSource secondNumber = ValueResolver.resolveEvaluator(object.get("secondNumber"), 0, environment);
                return actionContext -> (Evaluator) (game, cardAffected) -> {
                    final int first = firstNumber.getEvaluator(actionContext).evaluateExpression(actionContext.getGame(), null);
                    final int second = secondNumber.getEvaluator(actionContext).evaluateExpression(actionContext.getGame(), null);
                    return first - second;
                };
            } else if (type.equals("twilightCostInMemory")) {
                final String memory = FieldUtils.getString(object.get("memory"), "memory");
                return actionContext -> (Evaluator) (game, cardAffected) -> {
                    int total = 0;
                    for (PhysicalCard physicalCard : actionContext.getCardsFromMemory(memory)) {
                        total += physicalCard.getBlueprint().getTwilightCost();
                    }
                    return total;
                };
            }
            throw new InvalidCardDefinitionException("Unrecognized type of an evaluator " + type);
        }
        throw new InvalidCardDefinitionException("Unable to resolve an evaluator");
    }
}
