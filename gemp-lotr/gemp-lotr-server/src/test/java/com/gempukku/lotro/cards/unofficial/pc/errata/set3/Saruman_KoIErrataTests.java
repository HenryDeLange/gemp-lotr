package com.gempukku.lotro.cards.unofficial.pc.errata.set3;

import com.gempukku.lotro.cards.GenericCardTestHelper;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Saruman_KoIErrataTests
{
    protected GenericCardTestHelper GetScenario() throws CardNotFoundException, DecisionResultInvalidException {
        return new GenericCardTestHelper(
                new HashMap<String, String>()
                {{
                    put("legolas", "1_50");
                    put("tale", "1_66");
                    put("doubleshot", "1_38");

                    put("saruman", "53_68");
                    put("uruk1", "1_151");
                    put("uruk2", "1_151");
                }}
        );
    }

    @Test
    public void SarumanImmunityAndWoundBlock() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl legolas = scn.GetFreepsCard("legolas");

        PhysicalCardImpl saruman = scn.GetShadowCard("saruman");
        PhysicalCardImpl uruk1 = scn.GetShadowCard("uruk1");
        PhysicalCardImpl uruk2 = scn.GetShadowCard("uruk2");


        scn.FreepsMoveCharToTable(legolas);

        scn.ShadowMoveCharToTable(saruman);
        scn.ShadowMoveCharToTable(uruk1);
        scn.ShadowMoveCharToTable(uruk2);

        scn.StartGame();

        scn.SkipToPhase(Phase.ARCHERY);

        //can't hit saruman
        scn.FreepsUseCardAction(legolas);
        assertFalse(scn.FreepsCanChooseCharacter(saruman));

        scn.FreepsChooseCard(uruk1);
        assertTrue(scn.ShadowCardActionAvailable(saruman));

        //saruman blocks the uruk hit
        scn.ShadowUseCardAction(saruman);

        assertEquals(0, scn.GetWoundsOn(uruk1));
        assertEquals(1, scn.GetWoundsOn(saruman));
        //Old version made them fierce, ensure that was removed
        assertFalse(scn.HasKeyword(uruk1, Keyword.FIERCE));

        //shadow has to skip archery actions
        scn.ShadowSkipCurrentPhaseAction();


        assertEquals(0, scn.GetWoundsOn(uruk1));
        assertEquals(1, scn.GetWoundsOn(saruman));


        scn.SkipToPhase(Phase.ASSIGNMENT);
        scn.SkipCurrentPhaseActions();

        //saruman is not on the list of assignable minions
        assertEquals(2, scn.FreepsGetADParamAsList("minions").size());

    }


    @Test
    public void SarumanAbilityGrantsFierce() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl saruman = scn.GetShadowCard("saruman");
        PhysicalCardImpl uruk1 = scn.GetShadowCard("uruk1");

        scn.ShadowMoveCharToTable(saruman);
        scn.ShadowMoveCharToTable(uruk1);

        scn.StartGame();

        scn.SkipToPhase(Phase.MANEUVER);
        scn.FreepsSkipCurrentPhaseAction();

        assertTrue(scn.ShadowCardActionAvailable(saruman));

        scn.ShadowUseCardAction(saruman);

        assertTrue(scn.HasKeyword(uruk1, Keyword.FIERCE));

        assertEquals(0, scn.GetWoundsOn(uruk1));
        assertEquals(1, scn.GetWoundsOn(saruman));

    }
}
