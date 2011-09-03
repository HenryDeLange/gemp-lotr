package com.gempukku.lotro.cards.set1.site;

import com.gempukku.lotro.cards.AbstractSite;
import com.gempukku.lotro.cards.PlayConditions;
import com.gempukku.lotro.cards.effects.AddUntilEndOfPhaseModifierEffect;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.filters.Filter;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.effects.ChooseArbitraryCardsEffect;
import com.gempukku.lotro.logic.modifiers.AbstractModifier;
import com.gempukku.lotro.logic.modifiers.ModifierEffect;
import com.gempukku.lotro.logic.modifiers.ModifiersQuerying;
import com.gempukku.lotro.logic.timing.Action;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Set: The Fellowship of the Ring
 * Twilight Cost: 9
 * Type: Site
 * Site: 9
 * Game Text: River. Shadow: Play up to 3 trackers from your discard pile; end your Shadow phase.
 */
public class Card1_363 extends AbstractSite {
    public Card1_363() {
        super("Tol Brandir", 9, 9, Direction.LEFT);
        addKeyword(Keyword.RIVER);
    }

    @Override
    public List<? extends Action> getPhaseActions(final String playerId, final LotroGame game, PhysicalCard self) {
        if (PlayConditions.canUseSiteDuringPhase(game.getGameState(), Phase.SHADOW, self)) {
            final CostToEffectAction action = new CostToEffectAction(self, Keyword.SHADOW, "Play up to 3 trackers from your discard pile; end your Shadow phase.");
            action.addEffect(
                    new ChooseTrackerToPlay(action, game, 1, playerId, "Choose tracker to play",
                            new LinkedList<PhysicalCard>(game.getGameState().getDiscard(playerId)),
                            Filters.and(
                                    Filters.keyword(Keyword.TRACKER),
                                    new Filter() {
                                        @Override
                                        public boolean accepts(GameState gameState, ModifiersQuerying modifiersQuerying, PhysicalCard physicalCard) {
                                            List<? extends Action> playableActions = physicalCard.getBlueprint().getPhaseActions(playerId, game, physicalCard);
                                            return (playableActions != null && playableActions.size() > 0);
                                        }
                                    }), 0, 1));
            return Collections.singletonList(action);
        }
        return null;
    }

    private class ChooseTrackerToPlay extends ChooseArbitraryCardsEffect {
        private CostToEffectAction _action;
        private LotroGame _game;
        private int _count;
        private String _playerId;
        private String _choiceText;
        private Filter _filter;

        private ChooseTrackerToPlay(CostToEffectAction action, LotroGame game, int count, String playerId, String choiceText, List<PhysicalCard> cards, Filter filter, int minimum, int maximum) {
            super(playerId, choiceText, cards, filter, minimum, maximum);
            _action = action;
            _game = game;
            _count = count;
            _playerId = playerId;
            _choiceText = choiceText;
            _filter = filter;
        }

        @Override
        protected void cardsSelected(List<PhysicalCard> selectedCards) {
            if (selectedCards.size() > 0) {
                PhysicalCard selectedCard = selectedCards.get(0);
                _game.getActionsEnvironment().addActionToStack(selectedCard.getBlueprint().getPhaseActions(_playerId, _game, selectedCard).get(0));

                LinkedList<PhysicalCard> remainingCards = new LinkedList<PhysicalCard>(_game.getGameState().getDiscard(_playerId));
                remainingCards.remove(selectedCard);
                if (_count < 3)
                    _action.addEffect(new ChooseTrackerToPlay(_action, _game, _count + 1, _playerId, _choiceText, remainingCards, _filter, 0, 1));
                else
                    _action.addEffect(
                            new AddUntilEndOfPhaseModifierEffect(
                                    new AbstractModifier(null, "End Shadow Phase", null, new ModifierEffect[]{ModifierEffect.ACTION_MODIFIER}) {
                                        @Override
                                        public boolean canPlayAction(GameState gameState, ModifiersQuerying modifiersQuerying, Action action, boolean result) {
                                            return false;
                                        }
                                    }, Phase.SHADOW
                            ));
            } else {
                _action.addEffect(
                        new AddUntilEndOfPhaseModifierEffect(
                                new AbstractModifier(null, "End Shadow Phase", null, new ModifierEffect[]{ModifierEffect.ACTION_MODIFIER}) {
                                    @Override
                                    public boolean canPlayAction(GameState gameState, ModifiersQuerying modifiersQuerying, Action action, boolean result) {
                                        return false;
                                    }
                                }, Phase.SHADOW
                        ));
            }
        }
    }
}
