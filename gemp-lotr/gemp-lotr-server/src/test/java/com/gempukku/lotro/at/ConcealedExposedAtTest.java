package com.gempukku.lotro.at;

import com.gempukku.lotro.cards.GenericCardTestHelper;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.modifiers.KeywordModifier;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertTrue;

public class ConcealedExposedAtTest extends AbstractAtTest {
    protected GenericCardTestHelper GetScenario() throws CardNotFoundException, DecisionResultInvalidException {
        return new GenericCardTestHelper(
                new HashMap<String, String>()
                {{
                    put("aragorn", "1_89");
                    put("arwen", "1_30");
                }}
        );
    }


    @Test
    public void ConcealedDoesNothingIfNoTwilight() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl aragorn = scn.GetFreepsCard("aragorn");

        scn.FreepsMoveCharToTable(aragorn);

        scn.StartGame();

        scn.ApplyAdHocModifier(new KeywordModifier(aragorn, aragorn, Keyword.CONCEALED));

        assertEquals(0, scn.GetTwilight());
        scn.FreepsPassCurrentPhaseAction();

        //1 for the ring-bearer, 1 for aragorn, 1 for the site (King's Tent)
        assertEquals(3, scn.GetTwilight());
    }

    @Test
    public void ConcealedRemovesOneIfAvailable() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl aragorn = scn.GetFreepsCard("aragorn");
        PhysicalCardImpl nostranger = scn.GetFreepsCard("nostranger");

        scn.FreepsMoveCardToHand(aragorn);

        scn.StartGame();

        scn.FreepsPlayCard(aragorn);
        scn.ApplyAdHocModifier(new KeywordModifier(aragorn, aragorn, Keyword.CONCEALED));

        //4 from playing aragorn
        assertEquals(4, scn.GetTwilight());
        scn.FreepsPassCurrentPhaseAction();

        //4 from playing aragorn, 1 for the ring-bearer, 1 for aragorn, 1 for the site (King's Tent), -1 for concealed
        assertEquals(6, scn.GetTwilight());
    }

    @Test
    public void TwoConcealedRemovesTwoIfAvailable() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl aragorn = scn.GetFreepsCard("aragorn");
        PhysicalCardImpl arwen = scn.GetFreepsCard("arwen");

        scn.FreepsMoveCardToHand(aragorn);
        scn.FreepsMoveCharToTable(arwen);

        scn.StartGame();

        scn.FreepsPlayCard(aragorn);
        scn.ApplyAdHocModifier(new KeywordModifier(aragorn, Keyword.RANGER, Keyword.CONCEALED));


        //4 from playing aragorn
        assertEquals(4, scn.GetTwilight());
        scn.FreepsPassCurrentPhaseAction();

        //4 from playing aragorn, 1+1+1 for companions, 1 for the site (King's Tent), -2 for concealed
        assertEquals(6, scn.GetTwilight());
    }

    @Test
    public void ConcealedRemovesNothingIfExposed() throws DecisionResultInvalidException, CardNotFoundException {
        //Pre-game setup
        GenericCardTestHelper scn = GetScenario();

        PhysicalCardImpl aragorn = scn.GetFreepsCard("aragorn");

        scn.FreepsMoveCardToHand(aragorn);

        scn.StartGame();

        scn.FreepsPlayCard(aragorn);
        scn.ApplyAdHocModifier(new KeywordModifier(aragorn, Keyword.RANGER, Keyword.CONCEALED));

        scn.ApplyAdHocModifier(new KeywordModifier(null, CardType.SITE, Keyword.EXPOSED));

        //4 from playing aragorn
        assertEquals(4, scn.GetTwilight());
        scn.FreepsPassCurrentPhaseAction();

        //4 from playing aragorn, 1 for the ring-bearer, 1 for aragorn, 1 for the site (King's Tent), 0 for exposed concealed
        assertEquals(7, scn.GetTwilight());
    }
}
