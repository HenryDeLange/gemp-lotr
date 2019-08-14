package com.gempukku.lotro.logic;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Filterable;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.filters.Filter;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.AttachPermanentAction;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.actions.PlayPermanentAction;
import com.gempukku.lotro.logic.cardtype.AbstractEvent;
import com.gempukku.lotro.logic.timing.PlayConditions;
import com.gempukku.lotro.logic.timing.RuleUtils;

public class PlayUtils {
    private static Zone getPlayToZone(PhysicalCard card) {
        final CardType cardType = card.getBlueprint().getCardType();
        switch (cardType) {
            case COMPANION:
                return Zone.FREE_CHARACTERS;
            case MINION:
                return Zone.SHADOW_CHARACTERS;
            default:
                return Zone.SUPPORT;
        }
    }


    private static Filter getFullAttachValidTargetFilter(final LotroGame game, final PhysicalCard card) {
        return Filters.and(RuleUtils.getFullValidTargetFilter(card.getOwner(), game, card),
                new Filter() {
                    @Override
                    public boolean accepts(LotroGame game, PhysicalCard physicalCard) {
                        return game.getModifiersQuerying().canHavePlayedOn(game, card, physicalCard);
                    }
                });
    }


    public static CostToEffectAction getPlayCardAction(LotroGame game, PhysicalCard card, int twilightModifier, Filterable additionalAttachmentFilter, boolean ignoreRoamingPenalty) {
        final LotroCardBlueprint blueprint = card.getBlueprint();

        if (blueprint.getCardType() != CardType.EVENT) {
            final Filterable validTargetFilter = blueprint.getValidTargetFilter(card.getOwner(), game, card);
            if (validTargetFilter == null) {
                PlayPermanentAction action = new PlayPermanentAction(card, getPlayToZone(card), twilightModifier, ignoreRoamingPenalty);

                game.getModifiersQuerying().appendExtraCosts(game, action, card);
                game.getModifiersQuerying().appendPotentialDiscounts(game, action, card);

                return action;
            } else {
                final AttachPermanentAction action = new AttachPermanentAction(game, card, Filters.and(getFullAttachValidTargetFilter(game, card), additionalAttachmentFilter), blueprint.getTargetCostModifiers(card.getOwner(), game, card), twilightModifier);
                game.getModifiersQuerying().appendPotentialDiscounts(game, action, card);
                game.getModifiersQuerying().appendExtraCosts(game, action, card);
                return action;
            }
        } else {
            AbstractEvent eventBlueprint = (AbstractEvent) blueprint;
            final PlayEventAction action = eventBlueprint.getPlayCardAction(card.getOwner(), game, card, 0, false);

            game.getModifiersQuerying().appendPotentialDiscounts(game, action, card);
            game.getModifiersQuerying().appendExtraCosts(game, action, card);

            return action;
        }
    }

    public static boolean checkPlayRequirements(LotroGame game, PhysicalCard card, Filterable additionalAttachmentFilter, int withTwilightRemoved, int twilightModifier, boolean ignoreRoamingPenalty, boolean ignoreCheckingDeadPile) {
        final LotroCardBlueprint blueprint = card.getBlueprint();

        // Check if card's own play requirements are met
        if (!card.getBlueprint().checkPlayRequirements(game, card))
            return false;

        // Check if there exists a legal target (if needed)
        final Filterable validTargetFilter = blueprint.getValidTargetFilter(card.getOwner(), game, card);
        if (validTargetFilter != null && Filters.countActive(game, getFullAttachValidTargetFilter(game, card), additionalAttachmentFilter) == 0)
            return false;

        // Check if can play extra costs
        if (!game.getModifiersQuerying().canPayExtraCostsToPlay(game, card))
            return false;

        // Check uniqueness
        if (!blueprint.skipUniquenessCheck() && !PlayConditions.checkUniqueness(game, card, ignoreCheckingDeadPile))
            return false;

        if (blueprint.getCardType() == CardType.COMPANION
                && !(PlayConditions.checkRuleOfNine(game, card) && PlayConditions.checkPlayRingBearer(game, card)))
            return false;

        twilightModifier -= game.getModifiersQuerying().getPotentialDiscount(game, card);

        return (blueprint.getSide() != Side.SHADOW || PlayConditions.canPayForShadowCard(game, card, withTwilightRemoved, twilightModifier, ignoreRoamingPenalty));
    }
}
