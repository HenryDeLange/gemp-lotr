package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.framework.TestConstants;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.GameUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class FellowshipTransferGuardEngineTest {
    @Test
    public void limitsSecondVoluntaryTransferWhileEngineStillOffersIt() throws Exception {
        HashMap<String, String> cards = new HashMap<>();
        cards.put("sam", "1_311");
        cards.put("sword", "1_299");
        VirtualTableScenario scn = new VirtualTableScenario(cards);
        var sam = scn.GetFreepsCard("sam");
        var sword = scn.GetFreepsCard("sword");
        var frodo = scn.GetRingBearer();
        scn.MoveCompanionsToTable(sam);
        scn.AttachCardsTo(frodo, sword);
        scn.StartGame();

        assertTrue(scn.FreepsTransferAvailable(sword));
        var guard = new FellowshipTransferGuard(TestConstants.P1);
        var decision = scn.FreepsGetAwaitingDecision();
        guard.observe(scn.gameState(), decision);
        String transfer = scn.GetCardActionId(TestConstants.P1, sword, "Transfer");
        guard.selected(scn.gameState(), decision, transfer);
        scn.FreepsDecided(transfer);
        assertEquals(sam.getCardId(), sword.getAttachedTo().getCardId());
        assertTrue("The engine remains free to offer the legal transfer", scn.FreepsTransferAvailable(sword));

        var next = scn.FreepsGetAwaitingDecision();
        guard.observe(scn.gameState(), next);
        var prepared = guard.prepare(scn.gameState(), next);
        assertTrue(prepared.filtered());
        assertFalse(Arrays.asList(prepared.decision().getDecisionParameters().get("actionText"))
                .contains("Transfer " + GameUtils.getFullName(sword)));
    }
}
