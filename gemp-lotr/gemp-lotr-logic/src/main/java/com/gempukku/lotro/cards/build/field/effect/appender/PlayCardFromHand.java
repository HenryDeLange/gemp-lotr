package com.gempukku.lotro.cards.build.field.effect.appender;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.EffectAppender;
import com.gempukku.lotro.cards.build.field.effect.EffectAppenderProducer;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.CardResolver;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.ValueResolver;
import com.gempukku.lotro.common.Filterable;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayUtils;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.effects.StackActionEffect;
import com.gempukku.lotro.logic.modifiers.evaluator.ConstantEvaluator;
import com.gempukku.lotro.logic.timing.Effect;
import com.gempukku.lotro.logic.timing.ExtraFilters;
import org.json.simple.JSONObject;

import java.util.Collection;

public class PlayCardFromHand implements EffectAppenderProducer {
    @Override
    public EffectAppender createEffectAppender(JSONObject effectObject, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(effectObject, "filter", "on", "cost", "ignoreInDeadPile", "memorize");

        final String filter = FieldUtils.getString(effectObject.get("filter"), "filter");
        final String onFilter = FieldUtils.getString(effectObject.get("on"), "on");
        final ValueSource costModifierSource = ValueResolver.resolveEvaluator(effectObject.get("cost"), 0, environment);
        final boolean ignoreInDeadPile = FieldUtils.getBoolean(effectObject.get("ignoreInDeadPile"), "ignoreInDeadPile", false);
        final String memorize = FieldUtils.getString(effectObject.get("memorize"), "memorize", "_temp");

        final FilterableSource onFilterableSource = (onFilter != null) ? environment.getFilterFactory().generateFilter(onFilter, environment) : null;

        MultiEffectAppender result = new MultiEffectAppender();
        result.setPlayabilityCheckedForEffect(true);

        result.addEffectAppender(
                CardResolver.resolveCardsInHand(filter,
                        (actionContext) -> {
                            final LotroGame game = actionContext.getGame();
                            final int costModifier = costModifierSource.getEvaluator(actionContext).evaluateExpression(game, actionContext.getSource());
                            if (onFilterableSource != null) {
                                final Filterable onFilterable = onFilterableSource.getFilterable(actionContext);
                                return Filters.and(Filters.playable(game, costModifier, false, ignoreInDeadPile), ExtraFilters.attachableTo(game, onFilterable));
                            }
                            return Filters.playable(game, costModifier, false, ignoreInDeadPile);
                        },
                        new ConstantEvaluator(1), memorize, "you", "you", "Choose card to play from hand", environment));
        result.addEffectAppender(
                new DelayedAppender() {
                    @Override
                    protected Effect createEffect(boolean cost, CostToEffectAction action, ActionContext actionContext) {
                        final Collection<? extends PhysicalCard> cardsToPlay = actionContext.getCardsFromMemory(memorize);
                        if (cardsToPlay.size() == 1) {
                            final LotroGame game = actionContext.getGame();
                            final int costModifier = costModifierSource.getEvaluator(actionContext).evaluateExpression(game, actionContext.getSource());

                            Filterable onFilterable = (onFilterableSource != null) ? onFilterableSource.getFilterable(actionContext) : Filters.any;

                            final CostToEffectAction playCardAction = PlayUtils.getPlayCardAction(game, cardsToPlay.iterator().next(), costModifier, onFilterable, false);
                            return new StackActionEffect(playCardAction);
                        } else {
                            return null;
                        }
                    }
                });

        return result;
    }
}
