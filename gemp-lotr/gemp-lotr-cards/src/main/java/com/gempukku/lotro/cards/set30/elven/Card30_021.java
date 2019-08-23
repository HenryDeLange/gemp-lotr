package com.gempukku.lotro.cards.set30.elven;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.ActivateCardAction;
import com.gempukku.lotro.logic.actions.OptionalTriggerAction;
import com.gempukku.lotro.logic.cardtype.AbstractAlly;
import com.gempukku.lotro.logic.effects.ChoiceEffect;
import com.gempukku.lotro.logic.effects.ChooseAndHealCharactersEffect;
import com.gempukku.lotro.logic.effects.SelfExertEffect;
import com.gempukku.lotro.logic.timing.Effect;
import com.gempukku.lotro.logic.timing.EffectResult;
import com.gempukku.lotro.logic.timing.PlayConditions;
import com.gempukku.lotro.logic.timing.TriggerConditions;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Set: Main Deck
 * Side: Free
 * Culture: Elven
 * Twilight Cost: 4
 * Type: Ally • Home 3 • Elf
 * Strength: 8
 * Vitality: 4
 * Site: 3
 * Game Text: Wise. At the start of each of your turns, you may heal a Wise ally (or heal Elrond twice).
 * Regroup: Exert Elrond twice to heal a companion.
 */
public class Card30_021 extends AbstractAlly {
    public Card30_021() {
        super(4, SitesBlock.HOBBIT, 3, 8, 4, Race.ELF, Culture.ELVEN, "Elrond", "Elven Lord", true);
		addKeyword(Keyword.WISE);
    }

    @Override
    public List<OptionalTriggerAction> getOptionalAfterTriggers(final String playerId, LotroGame game, EffectResult effectResult, final PhysicalCard self) {
        if (TriggerConditions.startOfTurn(game, effectResult)) {
            final OptionalTriggerAction action = new OptionalTriggerAction(self);
            List<Effect> possibleEffects = new LinkedList<Effect>();
            possibleEffects.add(
                    new ChooseAndHealCharactersEffect(action, playerId, CardType.ALLY, Keyword.WISE) {
                        @Override
                        public String getText(LotroGame game) {
                            return "Heal a Wise ally";
                        }
                    });
            possibleEffects.add(
                    new ChooseAndHealCharactersEffect(action, playerId, 1, 1, 2, self) {
                        @Override
                        public String getText(LotroGame game) {
                            return "Heal Elrond twice";
                        }
                    });
            action.appendEffect(new ChoiceEffect(action, playerId, possibleEffects));

            return Collections.singletonList(action);
        }
        return null;
    }

    @Override
    public List<? extends ActivateCardAction> getPhaseActionsInPlay(String playerId, LotroGame game, PhysicalCard self) {
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.REGROUP, self)
                && PlayConditions.canExert(self, game, 2, self)) {
            ActivateCardAction action = new ActivateCardAction(self);
            action.appendCost(
                    new SelfExertEffect(action, self));
            action.appendCost(
                    new SelfExertEffect(action, self));
            action.appendEffect(
                    new ChooseAndHealCharactersEffect(action, playerId, CardType.COMPANION));
            return Collections.singletonList(action);
        }
        return null;
    }
}
