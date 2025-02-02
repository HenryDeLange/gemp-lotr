package com.gempukku.lotro.logic.effects;

import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.timing.AbstractEffect;
import com.gempukku.lotro.logic.timing.Effect;
import com.gempukku.lotro.logic.timing.results.DrawCardOrPutIntoHandResult;

import java.util.Collections;

public class PutCardFromDeckIntoHandOrDiscardEffect extends AbstractEffect {
    private PhysicalCard _physicalCard;

    public PutCardFromDeckIntoHandOrDiscardEffect(PhysicalCard physicalCard) {
        _physicalCard = physicalCard;
    }

    public PhysicalCard getCard() {
        return _physicalCard;
    }

    @Override
    public Effect.Type getType() {
        return null;
    }

    @Override
    public String getText(LotroGame game) {
        return "Put card from deck into hand";
    }

    @Override
    public boolean isPlayableInFull(LotroGame game) {
        return _physicalCard.getZone() == Zone.DECK;
    }

    @Override
    protected FullEffectResult playEffectReturningResult(LotroGame game) {
        if (_physicalCard.getZone() == Zone.DECK) {
            if ((!game.getFormat().hasRuleOfFour() || game.getModifiersQuerying().canDrawCardAndIncrementForRuleOfFour(game, _physicalCard.getOwner()))) {
                game.getGameState().sendMessage(_physicalCard.getOwner() + " puts " + GameUtils.getCardLink(_physicalCard) + " from deck into their hand");
                game.getGameState().removeCardsFromZone(_physicalCard.getOwner(), Collections.singleton(_physicalCard));
                game.getGameState().addCardToZone(game, _physicalCard, Zone.HAND);
                game.getActionsEnvironment().emitEffectResult(new DrawCardOrPutIntoHandResult(_physicalCard.getOwner()));
                return new FullEffectResult(true);
            } else {
                game.getGameState().sendMessage(_physicalCard.getOwner() + " discards " + GameUtils.getCardLink(_physicalCard) + " from deck due to Rule of 4");
                game.getGameState().removeCardsFromZone(_physicalCard.getOwner(), Collections.singleton(_physicalCard));
                game.getGameState().addCardToZone(game, _physicalCard, Zone.DISCARD);
            }
        }
        return new FullEffectResult(false);
    }
}
