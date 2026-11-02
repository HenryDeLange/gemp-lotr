package com.gempukku.lotro.bots.simulation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GameDecisionMetricsTest {
    @Test
    public void recordsAcceptedChoicesByPhaseAndActionType() {
        GameDecisionMetricsAccumulator accumulator = new GameDecisionMetricsAccumulator();
        AwaitingDecision action = mock(AwaitingDecision.class);
        when(action.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_ACTION_CHOICE);
        when(action.getDecisionParameters()).thenReturn(Map.of(
                "actionText", new String[]{"Play a companion", "Transfer a possession"}));
        AwaitingDecision selection = mock(AwaitingDecision.class);
        when(selection.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_SELECTION);

        accumulator.recordDecision(Phase.FELLOWSHIP, action, "");
        accumulator.recordDecision(Phase.FELLOWSHIP, action, "1");
        accumulator.recordDecision(Phase.SHADOW, action, "0");
        accumulator.recordDecision(Phase.SKIRMISH, selection, "17");

        GameDecisionMetrics metrics = accumulator.finish();
        assertEquals(4, metrics.decisions());
        assertEquals(3, metrics.cardActionChoices());
        assertEquals(1, metrics.cardActionPasses());
        assertEquals(1, metrics.transferActions());
        assertEquals(Map.of(Phase.FELLOWSHIP, 2, Phase.SHADOW, 1, Phase.SKIRMISH, 1),
                metrics.decisionsByPhase());
        assertEquals(Map.of(AwaitingDecisionType.CARD_ACTION_CHOICE, 3, AwaitingDecisionType.CARD_SELECTION, 1),
                metrics.decisionsByType());
    }

    @Test
    public void countsOnlyObservedSameTurnFellowshipAttachmentReversals() {
        GameDecisionMetricsAccumulator accumulator = new GameDecisionMetricsAccumulator();
        GameState state = mock(GameState.class);
        PhysicalCard item = mock(PhysicalCard.class);
        PhysicalCard bearerA = mock(PhysicalCard.class);
        PhysicalCard bearerB = mock(PhysicalCard.class);
        LotroCardBlueprint blueprint = mock(LotroCardBlueprint.class);
        when(state.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
        when(state.getTurnNumber()).thenReturn(1);
        doReturn(List.of(item)).when(state).getInPlay();
        when(item.getBlueprint()).thenReturn(blueprint);
        when(blueprint.getCardType()).thenReturn(CardType.POSSESSION);
        when(item.getCardId()).thenReturn(3);
        when(bearerA.getCardId()).thenReturn(10);
        when(bearerB.getCardId()).thenReturn(20);

        when(item.getAttachedTo()).thenReturn(bearerA);
        accumulator.capture(state);
        when(item.getAttachedTo()).thenReturn(bearerB);
        accumulator.capture(state);
        when(item.getAttachedTo()).thenReturn(bearerA);
        accumulator.capture(state);
        assertEquals(1, accumulator.finish().attachmentReversals());

        when(item.getAttachedTo()).thenReturn(bearerB);
        accumulator.capture(state);
        when(state.getTurnNumber()).thenReturn(2);
        when(item.getAttachedTo()).thenReturn(bearerA);
        accumulator.capture(state);
        when(state.getCurrentPhase()).thenReturn(Phase.SHADOW);
        accumulator.capture(state);
        when(state.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
        when(item.getAttachedTo()).thenReturn(bearerB);
        accumulator.capture(state);
        assertEquals(2, accumulator.finish().attachmentReversals());
    }

    @Test
    public void aggregatesHistogramsAndSerializesNearestRankPercentiles() {
        BatchDecisionMetrics first = BatchDecisionMetrics.empty()
                .plus(game(10, Phase.FELLOWSHIP))
                .plus(game(20, Phase.SHADOW))
                .plus(game(20, Phase.FELLOWSHIP));
        BatchDecisionMetrics second = BatchDecisionMetrics.empty()
                .plus(game(40, Phase.SKIRMISH))
                .plus(game(100, Phase.FELLOWSHIP));
        BatchDecisionMetrics batch = first.plus(second);

        assertEquals(5, batch.gamesMeasured());
        assertEquals(190, batch.decisionsTotal());
        assertEquals(20, batch.getMedianDecisions());
        assertEquals(100, batch.getP95Decisions());
        assertEquals(Map.of("10", 1, "20", 2, "40", 1, "100", 1), batch.decisionsPerGameHistogram());
        assertEquals(Long.valueOf(130), batch.decisionsByPhase().get(Phase.FELLOWSHIP));

        JSONObject json = JSON.parseObject(JSON.toJSONString(batch));
        assertEquals(20, json.getIntValue("medianDecisions"));
        assertEquals(100, json.getIntValue("p95Decisions"));
        assertEquals(json.toJSONString(), 2, json.getJSONObject("decisionsPerGameHistogram").getIntValue("20"));
    }

    @Test
    public void percentileUsesTheNineteenthValueForTwentyGames() {
        BatchDecisionMetrics batch = BatchDecisionMetrics.empty();
        for (int i = 1; i <= 20; i++)
            batch = batch.plus(game(i, Phase.FELLOWSHIP));
        assertEquals(10, batch.getMedianDecisions());
        assertEquals(19, batch.getP95Decisions());
    }

    private static GameDecisionMetrics game(int decisions, Phase phase) {
        return new GameDecisionMetrics(decisions, 0, 0, 0, 0, Map.of(phase, decisions),
                Map.of(AwaitingDecisionType.INTEGER, decisions));
    }
}
