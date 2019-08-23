package com.gempukku.lotro.cards.set20.gandalf;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.cardtype.AbstractEvent;
import com.gempukku.lotro.logic.effects.ChooseAndHealCharactersEffect;
import com.gempukku.lotro.logic.effects.RevealRandomCardsFromHandEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndExertCharactersEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseOpponentEffect;
import com.gempukku.lotro.logic.timing.PlayConditions;

import java.util.List;

/**
 * 1
 * Illuminate
 * Gandalf	Event Manuever
 * Spell.
 * Spot Gandalf to reveal a card at random from your opponent's hand. If it is a Free Peoples card, heal a companion.
 * If it is a Shadow card, exert a minion
 */
public class Card20_470 extends AbstractEvent {
    public Card20_470() {
        super(Side.FREE_PEOPLE, 1, Culture.GANDALF, "Illuminate", Phase.MANEUVER);
        addKeyword(Keyword.SPELL);
    }

    @Override
    public boolean checkPlayRequirements(LotroGame game, PhysicalCard self) {
        return PlayConditions.canSpot(game, Filters.gandalf);
    }

    @Override
    public PlayEventAction getPlayEventCardAction(final String playerId, LotroGame game, final PhysicalCard self) {
        final PlayEventAction action = new PlayEventAction(self);
        action.appendEffect(
                new ChooseOpponentEffect(playerId) {
                    @Override
                    protected void opponentChosen(String opponentId) {
                        action.appendEffect(
                                new RevealRandomCardsFromHandEffect(playerId, opponentId, self, 1) {
                                    @Override
                                    protected void cardsRevealed(List<PhysicalCard> revealedCards) {
                                        if (revealedCards.size()>0) {
                                            final PhysicalCard revealedCard = revealedCards.get(0);
                                            if (revealedCard.getBlueprint().getSide() == Side.FREE_PEOPLE)
                                                action.appendEffect(
                                                        new ChooseAndHealCharactersEffect(action, playerId, CardType.COMPANION));
                                            else
                                                action.appendEffect(
                                                        new ChooseAndExertCharactersEffect(action, playerId, 1, 1, CardType.MINION));
                                        }
                                    }
                                });
                    }
                });
        return action;
    }
}
