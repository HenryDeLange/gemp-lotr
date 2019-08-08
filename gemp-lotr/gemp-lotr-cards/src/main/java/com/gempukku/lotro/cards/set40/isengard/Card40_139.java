package com.gempukku.lotro.cards.set40.isengard;

import com.gempukku.lotro.cards.AbstractAttachable;
import com.gempukku.lotro.cards.modifiers.conditions.NotCondition;
import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.modifiers.CantTakeWoundsModifier;
import com.gempukku.lotro.logic.modifiers.Modifier;
import com.gempukku.lotro.logic.modifiers.condition.PhaseCondition;

import java.util.Collections;
import java.util.List;

/**
 * Title: Sword of Orthanc
 * Set: Second Edition
 * Side: Shadow
 * Culture: Isengard
 * Twilight Cost: 1
 * Type: Possession - Hand Weapon
 * Strength: +2
 * Card Number: 1C139
 * Game Text: Bearer must be an Uruk-hai. While at a battleground, bearer may only take wounds during skirmishes.
 */
public class Card40_139 extends AbstractAttachable {
    public Card40_139() {
        super(Side.SHADOW, CardType.POSSESSION, 1, Culture.ISENGARD, PossessionClass.HAND_WEAPON, "Sword of Orthanc");
    }

    @Override
    protected Filterable getValidTargetFilter(String playerId, LotroGame game, PhysicalCard self) {
        return Race.URUK_HAI;
    }

    @Override
    public List<? extends Modifier> getAlwaysOnModifiers(LotroGame game, PhysicalCard self) {
        CantTakeWoundsModifier modifier = new CantTakeWoundsModifier(self,
                new NotCondition(new PhaseCondition(Phase.SKIRMISH)), Filters.hasAttached(self));
        return Collections.singletonList(modifier);
    }
}