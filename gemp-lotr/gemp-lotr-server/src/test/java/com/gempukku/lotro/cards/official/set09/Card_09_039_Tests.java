package com.gempukku.lotro.cards.official.set09;

import com.gempukku.lotro.cards.GenericCardTestHelper;
import com.gempukku.lotro.common.*;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import junit.framework.Assert;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Card_09_039_Tests
{

    protected GenericCardTestHelper GetScenario() throws CardNotFoundException, DecisionResultInvalidException {
        return new GenericCardTestHelper(
                new HashMap<String, String>()
                {{
                    put("library", "9_39");
                    put("troop1", "1_143");
                    put("axe1", "1_9");
                    put("troop2", "1_143");
                    put("axe2", "1_9");
                    put("troop3", "1_143");
                    put("axe3", "1_9");

                    put("runner", "1_178");
                }}
        );
    }


    @Test
    public void LibraryOfOrthancStatsAndKeywordsAreCorrect() throws DecisionResultInvalidException, CardNotFoundException {

        /**
         * Set: 9
         * Title: *Library of Orthanc
         * Side: Shadow
         * Culture: Isengard
         * Twilight Cost: 2
         * Type: Artifact
         * Subtype: Support Area
         * Game Text: Shadow: Play an [ISENGARD] minion to stack the top card of your draw deck on this card.
         * Skirmish: Remove (1) and discard a Free Peoples card stacked here to make an [ISENGARD] minion strength +1.
         * Regroup: Remove (1) to take an [ISENGARD] card stacked here into hand.
         */

        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl card = scn.GetFreepsCard("library");

        assertTrue(card.getBlueprint().isUnique());
        assertEquals(Side.SHADOW, card.getBlueprint().getSide());
        assertEquals(Culture.ISENGARD, card.getBlueprint().getCulture());
        assertEquals(CardType.ARTIFACT, card.getBlueprint().getCardType());
        assertTrue(card.getBlueprint().isUnique());
        assertTrue(scn.HasKeyword(card, Keyword.SUPPORT_AREA)); // test for keywords as needed
        assertEquals(2, card.getBlueprint().getTwilightCost());
        //assertEquals(, card.getBlueprint().getStrength());
        //assertEquals(, card.getBlueprint().getVitality());
        //assertEquals(, card.getBlueprint().getResistance());
        //assertEquals(Signet., card.getBlueprint().getSignet());
        //assertEquals(, card.getBlueprint().getSiteNumber()); // Change this to getAllyHomeSiteNumbers for allies
    }

    @Test
    public void ShadowAbilityStacksTopCardOfDeckAfterPlayingMinion() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl library = scn.GetShadowCard("library");
        PhysicalCardImpl troop1 = scn.GetShadowCard("troop1");
        PhysicalCardImpl troop2 = scn.GetShadowCard("troop2");

        scn.ShadowMoveCardToHand(library, troop1, troop2);

        scn.StartGame();
        scn.SetTwilight(20);

        scn.FreepsSkipCurrentPhaseAction();

        scn.ShadowPlayCard(library);
        assertTrue(scn.ShadowCardActionAvailable(library));
        assertEquals(5, scn.GetShadowDeckCount());
        assertEquals(0, scn.GetStackedCards(library).size());

        scn.ShadowUseCardAction(library);
        assertTrue(scn.ShadowDecisionAvailable("Choose cards from hand"));
        scn.ShadowChooseCards(troop1);
        assertEquals(4, scn.GetShadowDeckCount());
        assertEquals(1, scn.GetStackedCards(library).size());

        scn.ShadowUseCardAction(library);
        assertFalse(scn.ShadowDecisionAvailable("Choose cards from hand"));
        //scn.ShadowChooseCards(troop2);  //As there's only one, this happens automatically
        assertEquals(3, scn.GetShadowDeckCount());
        assertEquals(2, scn.GetStackedCards(library).size());

    }

    @Test
    public void SkirmishAbilityRemoves1AndDiscardsStackedFreepsCardToAdd1Strength() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl library = scn.GetShadowCard("library");
        PhysicalCardImpl troop1 = scn.GetShadowCard("troop1");
        PhysicalCardImpl axe1 = scn.GetShadowCard("axe1");

        scn.ShadowMoveCardToHand(library, troop1);

        scn.StartGame();
        scn.SetTwilight(20);

        scn.FreepsSkipCurrentPhaseAction();

        scn.ShadowPlayCard(library);
        scn.ShadowUseCardAction(library);
        assertEquals(library, axe1.getStackedOn());

        scn.SkipToPhase(Phase.ASSIGNMENT);
        scn.SkipCurrentPhaseActions();
        scn.FreepsAssignToMinions(scn.GetRingBearer(), troop1);
        scn.FreepsResolveSkirmish(scn.GetRingBearer());
        scn.FreepsSkipCurrentPhaseAction();

        assertTrue(scn.ShadowCardActionAvailable(library));
        // 20 initially, +2 from move, -2 from library, -5 for troop, -2 for roaming
        assertEquals(13, scn.GetTwilight());
        assertEquals(9, scn.GetStrength(troop1));
        assertEquals(1, scn.GetStackedCards(library).size());
        scn.ShadowUseCardAction(library);
        assertEquals(13, scn.GetTwilight());
        assertEquals(9, scn.GetStrength(troop1));
        assertEquals(0, scn.GetStackedCards(library).size());
        assertEquals(Zone.DISCARD, axe1.getZone());
    }




}
