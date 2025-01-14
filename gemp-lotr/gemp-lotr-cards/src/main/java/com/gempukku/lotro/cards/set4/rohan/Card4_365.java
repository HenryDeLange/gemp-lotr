package com.gempukku.lotro.cards.set4.rohan;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filter;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayUtils;
import com.gempukku.lotro.logic.actions.ActivateCardAction;
import com.gempukku.lotro.logic.actions.AttachPermanentAction;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.cardtype.AbstractCompanion;
import com.gempukku.lotro.logic.effects.ChooseArbitraryCardsEffect;
import com.gempukku.lotro.logic.effects.HealCharactersEffect;
import com.gempukku.lotro.logic.effects.IncrementTurnLimitEffect;
import com.gempukku.lotro.logic.timing.ExtraFilters;
import com.gempukku.lotro.logic.timing.PlayConditions;
import com.gempukku.lotro.logic.timing.UnrespondableEffect;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Set: The Two Towers
 * Side: Free
 * Culture: Rohan
 * Twilight Cost: 2
 * Type: Companion • Man
 * Strength: 6
 * Vitality: 2
 * Resistance: 6
 * Signet: Theoden
 * Game Text: Fellowship: Play a [ROHAN] possession on a [ROHAN] companion to heal that companion (limit once per turn).
 */
public class Card4_365 extends AbstractCompanion {
    public Card4_365() {
        super(2, 6, 2, 6, Culture.ROHAN, Race.MAN, Signet.THEODEN, Names.theoden, "Lord of the Mark", true);
    }

    @Override
    public List<? extends ActivateCardAction> getPhaseActionsInPlay(final String playerId, final LotroGame game, final PhysicalCard self) {
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.FELLOWSHIP, self)
                && PlayConditions.canPlayFromHand(playerId, game, Culture.ROHAN, CardType.POSSESSION, ExtraFilters.attachableTo(game, Culture.ROHAN, CardType.COMPANION))
        && PlayConditions.checkTurnLimit(game, self, 1)) {
            final Filter additionalAttachmentFilter = Filters.and(Culture.ROHAN, CardType.COMPANION);
            final ActivateCardAction action = new ActivateCardAction(self);
            action.appendCost(
                    new IncrementTurnLimitEffect(self, 1));
            action.appendCost(
                    new ChooseArbitraryCardsEffect(playerId, "Choose card to play", game.getGameState().getHand(playerId),
                            Filters.and(
                                    Culture.ROHAN,
                                    CardType.POSSESSION,
                                    ExtraFilters.attachableTo(game, additionalAttachmentFilter)), 1, 1) {
                        @Override
                        protected void cardsSelected(LotroGame game, Collection<PhysicalCard> selectedCards) {
                            if (selectedCards.size() > 0) {
                                PhysicalCard selectedCard = selectedCards.iterator().next();
                                AttachPermanentAction attachPermanentAction = (AttachPermanentAction) PlayUtils.getPlayCardAction(game, selectedCard, 0, additionalAttachmentFilter, false);
                                game.getActionsEnvironment().addActionToStack(attachPermanentAction);
                                action.appendEffect(
                                                new AppendHealTargetEffect(action, attachPermanentAction));
                            }
                        }
                    });
            return Collections.singletonList(action);
        }
        return null;
    }

    private static class AppendHealTargetEffect extends UnrespondableEffect {
        private CostToEffectAction _action;
        private AttachPermanentAction _attachPermanentAction;

        private AppendHealTargetEffect(CostToEffectAction action, AttachPermanentAction attachPermanentAction) {
            _action = action;
            _attachPermanentAction = attachPermanentAction;
        }

        @Override
        protected void doPlayEffect(LotroGame game) {
            _action.appendEffect(
                    new HealCharactersEffect(_action.getActionSource(), _action.getActionSource().getOwner(), _attachPermanentAction.getTarget()));
        }
    }
}

