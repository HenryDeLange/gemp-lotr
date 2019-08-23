package com.gempukku.lotro.cards.set3.shire;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filter;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.ActivateCardAction;
import com.gempukku.lotro.logic.cardtype.AbstractAttachableFPPossession;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.decisions.ForEachYouSpotDecision;
import com.gempukku.lotro.logic.effects.ChooseAndHealCharactersEffect;
import com.gempukku.lotro.logic.effects.PlayoutDecisionEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndDiscardCardsFromPlayEffect;
import com.gempukku.lotro.logic.timing.PlayConditions;

import java.util.Collections;
import java.util.List;

/**
 * Set: Realms of Elf-lords
 * Side: Free
 * Culture: Shire
 * Twilight Cost: 1
 * Type: Possession • Pipe
 * Game Text: Bearer must be Frodo. Fellowship: Discard a pipeweed possession and spot X pipes to heal a companion
 * with the Frodo signet X times.
 */
public class Card3_107 extends AbstractAttachableFPPossession {
    public Card3_107() {
        super(1, 0, 0, Culture.SHIRE, PossessionClass.PIPE, "Frodo's Pipe", null, true);
    }

    @Override
    public Filter getValidTargetFilter(String playerId, LotroGame game, PhysicalCard self) {
        return Filters.frodo;
    }

    @Override
    public List<? extends ActivateCardAction> getPhaseActionsInPlay(final String playerId, LotroGame game, final PhysicalCard self) {
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.FELLOWSHIP, self)
                && Filters.canSpot(game, Keyword.PIPEWEED, CardType.POSSESSION)) {
            final ActivateCardAction action = new ActivateCardAction(self);
            action.appendCost(
                    new ChooseAndDiscardCardsFromPlayEffect(action, playerId, 1, 1, Keyword.PIPEWEED, CardType.POSSESSION));
            action.appendEffect(
                    new PlayoutDecisionEffect(playerId,
                            new ForEachYouSpotDecision(1, "Choose number of pipes you wish to spot", game, Integer.MAX_VALUE, PossessionClass.PIPE) {
                                @Override
                                public void decisionMade(String result) throws DecisionResultInvalidException {
                                    final int spotCount = getValidatedResult(result);
                                    action.appendEffect(
                                            new ChooseAndHealCharactersEffect(action, playerId, 1, 1, spotCount, CardType.COMPANION, Signet.FRODO));
                                }
                            }));
            return Collections.singletonList(action);
        }
        return null;
    }
}
