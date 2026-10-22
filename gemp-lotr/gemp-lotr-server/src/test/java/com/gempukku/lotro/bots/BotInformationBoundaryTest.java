package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.Assignment;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.Skirmish;
import com.gempukku.lotro.logic.PlayerOrder;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BotInformationBoundaryTest {
    private static final String BOT = "bot";
    private static final String OPPONENT = "opponent";

    @Test
    public void allMovieFormatsUseTheSameFeatureSchema() {
        GameState state = movieBlockState(
                List.of(hiddenCard(CardType.EVENT, Culture.values()[0], Side.FREE_PEOPLE)),
                List.of(hiddenCard(CardType.MINION, Culture.values()[0], Side.SHADOW)),
                List.of());
        int expectedLength = -1;

        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            int actualLength = new MovieTrilogyRLGameStateFeatures(formatCode)
                    .extractFeatures(state, cardSelectionDecision(), BOT).length;
            if (expectedLength < 0)
                expectedLength = actualLength;
            else
                assertEquals(formatCode + " feature length", expectedLength, actualLength);
        }
    }

    @Test
    public void gameStateExposesPlayersBeforePregameSetup() {
        GameState gameState = new GameState(List.of(BOT, OPPONENT));

        assertEquals(List.of(BOT, OPPONENT), gameState.getPlayerNames());
        double[] features = new MovieTrilogyRLGameStateFeatures("king_block")
                .extractFeatures(gameState, cardSelectionDecision(), BOT);
        assertEquals(1.0, features[2], 0.0);
    }

    @Test
    public void everyFormatIgnoresOpponentHiddenHandDeckAndDiscardComposition() {
        GameState first = movieBlockState(
                List.of(hiddenCard(CardType.COMPANION, Culture.values()[0], Side.FREE_PEOPLE)),
                List.of(hiddenCard(CardType.EVENT, Culture.values()[0], Side.FREE_PEOPLE)),
                List.of(hiddenCard(CardType.EVENT, Culture.values()[0], Side.FREE_PEOPLE)));
        GameState second = movieBlockState(
                List.of(hiddenCard(CardType.MINION, Culture.values()[Culture.values().length - 1], Side.SHADOW)),
                List.of(hiddenCard(CardType.POSSESSION, Culture.values()[Culture.values().length - 1], Side.SHADOW)),
                List.of(hiddenCard(CardType.MINION, Culture.values()[Culture.values().length - 1], Side.SHADOW)));
        doReturn(List.of(cardForOwner(BOT, CardType.COMPANION, Culture.values()[0], Side.FREE_PEOPLE)))
                .when(first).getDeck(BOT);
        doReturn(List.of(cardForOwner(BOT, CardType.MINION, Culture.values()[Culture.values().length - 1], Side.SHADOW)))
                .when(second).getDeck(BOT);

        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            double[] expected = new MovieTrilogyRLGameStateFeatures(formatCode)
                    .extractFeatures(first, cardSelectionDecision(), BOT);
            double[] actual = new MovieTrilogyRLGameStateFeatures(formatCode)
                    .extractFeatures(second, cardSelectionDecision(), BOT);
            assertArrayEquals(formatCode, expected, actual, 0.0);
        }
    }

    @Test
    public void revealedOpponentHandCompositionIsIncluded() {
        GameState companionInHand = movieBlockState(
                List.of(hiddenCard(CardType.COMPANION, Culture.values()[0], Side.FREE_PEOPLE)),
                List.of(), List.of());
        GameState minionInHand = movieBlockState(
                List.of(hiddenCard(CardType.MINION, Culture.values()[0], Side.SHADOW)),
                List.of(), List.of());
        when(companionInHand.isHandRevealed(OPPONENT)).thenReturn(true);
        when(minionInHand.isHandRevealed(OPPONENT)).thenReturn(true);

        double[] companionFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(companionInHand, cardSelectionDecision(), BOT);
        double[] minionFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(minionInHand, cardSelectionDecision(), BOT);

        assertFalse(java.util.Arrays.equals(companionFeatures, minionFeatures));
    }

    @Test
    public void publicOpponentDiscardCompositionIsIncluded() {
        GameState eventInDiscard = movieBlockState(
                List.of(), List.of(),
                List.of(hiddenCard(CardType.EVENT, Culture.values()[0], Side.FREE_PEOPLE)));
        GameState minionInDiscard = movieBlockState(
                List.of(), List.of(),
                List.of(hiddenCard(CardType.MINION, Culture.values()[0], Side.SHADOW)));
        when(eventInDiscard.isDiscardPilePublic()).thenReturn(true);
        when(minionInDiscard.isDiscardPilePublic()).thenReturn(true);

        double[] eventFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(eventInDiscard, cardSelectionDecision(), BOT);
        double[] minionFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(minionInDiscard, cardSelectionDecision(), BOT);

        assertFalse(java.util.Arrays.equals(eventFeatures, minionFeatures));
    }

    @Test
    public void pendingSkirmishFeaturesExposeLossAndRingBearerRisk() {
        GameState risky = movieBlockState(List.of(), List.of(), List.of());
        PhysicalCard ringBearer = characterCard(BOT, CardType.COMPANION, 3, 3);
        PhysicalCard minion = characterCard(OPPONENT, CardType.MINION, 8, 3);
        doReturn(List.of(new Assignment(ringBearer, Set.of(minion)))).when(risky).getAssignments();
        when(risky.getRingBearer(BOT)).thenReturn(ringBearer);

        double[] riskyFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(risky, cardSelectionDecision(), BOT);
        int tacticalOffset = riskyFeatures.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;

        assertEquals(1.0 / 9, riskyFeatures[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.PENDING_SELF_LOSS_COUNT], 0.0);
        assertEquals(1.0 / 9, riskyFeatures[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.PENDING_SELF_OVERWHELM_COUNT], 0.0);
        assertEquals(1.0, riskyFeatures[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.PENDING_RING_BEARER_DANGER], 0.0);

        GameState safe = movieBlockState(List.of(), List.of(), List.of());
        PhysicalCard safeRingBearer = characterCard(BOT, CardType.COMPANION, 8, 3);
        PhysicalCard weakMinion = characterCard(OPPONENT, CardType.MINION, 3, 3);
        doReturn(List.of(new Assignment(safeRingBearer, Set.of(weakMinion)))).when(safe).getAssignments();
        when(safe.getRingBearer(BOT)).thenReturn(safeRingBearer);

        double[] safeFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(safe, cardSelectionDecision(), BOT);
        int safeTacticalOffset = safeFeatures.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;

        assertEquals(0.0, safeFeatures[safeTacticalOffset
                + MovieTrilogyRLGameStateFeatures.PENDING_SELF_LOSS_COUNT], 0.0);
        assertEquals(0.0, safeFeatures[safeTacticalOffset
                + MovieTrilogyRLGameStateFeatures.PENDING_SELF_OVERWHELM_COUNT], 0.0);
        assertEquals(0.0, safeFeatures[safeTacticalOffset
                + MovieTrilogyRLGameStateFeatures.PENDING_RING_BEARER_DANGER], 0.0);
    }

    @Test
    public void currentSkirmishRiskReflectsWhichSideTheBotControls() {
        GameState fellowshipGame = movieBlockState(List.of(), List.of(), List.of());
        PhysicalCard fellowshipCharacter = characterCard(BOT, CardType.COMPANION, 3, 3);
        PhysicalCard minion = characterCard(OPPONENT, CardType.MINION, 8, 3);
        when(fellowshipGame.getSkirmish()).thenReturn(new Skirmish(fellowshipCharacter, Set.of(minion)));

        double[] fellowshipFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(fellowshipGame, cardSelectionDecision(), BOT);
        int fellowshipOffset = fellowshipFeatures.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;
        assertEquals(1.0, fellowshipFeatures[fellowshipOffset
                + MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_LOSS_RISK], 0.0);
        assertEquals(0.0, fellowshipFeatures[fellowshipOffset
                + MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_WINNING], 0.0);

        GameState shadowGame = movieBlockState(List.of(), List.of(), List.of());
        PhysicalCard opposingFellowshipCharacter = characterCard(OPPONENT, CardType.COMPANION, 3, 3);
        PhysicalCard ownMinion = characterCard(BOT, CardType.MINION, 8, 3);
        when(shadowGame.getSkirmish()).thenReturn(
                new Skirmish(opposingFellowshipCharacter, Set.of(ownMinion)));

        double[] shadowFeatures = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(shadowGame, cardSelectionDecision(), BOT);
        int shadowOffset = shadowFeatures.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;
        assertEquals(0.0, shadowFeatures[shadowOffset
                + MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_LOSS_RISK], 0.0);
        assertEquals(1.0, shadowFeatures[shadowOffset
                + MovieTrilogyRLGameStateFeatures.CURRENT_SKIRMISH_SELF_WINNING], 0.0);
    }

    @Test
    public void stateCountsCombatBoostEventsInItsOwnHand() {
        GameState gameState = movieBlockState(List.of(), List.of(), List.of());
        PhysicalCard boostEvent = mock(PhysicalCard.class);
        LotroCardBlueprint blueprint = mock(LotroCardBlueprint.class);
        when(boostEvent.getOwner()).thenReturn(BOT);
        when(boostEvent.getBlueprint()).thenReturn(blueprint);
        when(blueprint.getCardType()).thenReturn(CardType.EVENT);
        when(blueprint.getGameText()).thenReturn("During a skirmish, this character is strength +2.");
        doReturn(List.of(boostEvent)).when(gameState).getHand(BOT);

        double[] features = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(gameState, cardSelectionDecision(), BOT);
        int tacticalOffset = features.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;

        assertEquals(1.0 / 6, features[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.SELF_HAND_COMBAT_BOOST_EVENTS], 0.0);
    }

    @Test
    public void regroupMoveBudgetIncludesKnownTwilightAndFellowshipSize() {
        GameState gameState = movieBlockState(List.of(), List.of(), List.of());
        PhysicalCard ringBearer = characterCard(BOT, CardType.COMPANION, 6, 4);
        PhysicalCard companion = characterCard(BOT, CardType.COMPANION, 5, 3);
        when(gameState.getRingBearer(BOT)).thenReturn(ringBearer);
        when(gameState.getCurrentPhase()).thenReturn(Phase.REGROUP);
        when(gameState.getTwilightPool()).thenReturn(5);
        doReturn(List.of(ringBearer, companion)).when(gameState).getInPlay();

        double[] features = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(gameState, cardSelectionDecision(), BOT);
        int tacticalOffset = features.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;

        assertEquals(7.0 / 40, features[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.ADDITIONAL_MOVE_TWILIGHT_BUDGET], 0.0);
    }

    @Test
    public void visibleDiscardMinionsAndRecoverySourcesContributeToPressure() {
        PhysicalCard discardedMinion = characterCard(OPPONENT, CardType.MINION, 6, 3);
        GameState gameState = movieBlockState(List.of(), List.of(), List.of(discardedMinion));
        when(gameState.isDiscardPilePublic()).thenReturn(true);

        PhysicalCard recoverySource = mock(PhysicalCard.class);
        LotroCardBlueprint recoveryBlueprint = mock(LotroCardBlueprint.class);
        when(recoverySource.getOwner()).thenReturn(OPPONENT);
        when(recoverySource.getBlueprint()).thenReturn(recoveryBlueprint);
        when(recoveryBlueprint.getCardType()).thenReturn(CardType.CONDITION);
        when(recoveryBlueprint.getGameText()).thenReturn("Play a minion from your discard pile.");
        doReturn(List.of(recoverySource)).when(gameState).getInPlay();

        double[] features = new MovieTrilogyRLGameStateFeatures("movie")
                .extractFeatures(gameState, cardSelectionDecision(), BOT);
        int tacticalOffset = features.length - MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT;

        assertEquals(1.0 / 6, features[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.OPPONENT_MINION_RECOVERY_SOURCES], 0.0);
        assertEquals(1.0 / 12, features[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.OPPONENT_DISCARD_MINION_COUNT], 0.0);
        assertEquals(1.0 / 12, features[tacticalOffset
                + MovieTrilogyRLGameStateFeatures.OPPONENT_MINION_RECOVERY_POTENTIAL], 0.0);
    }

    private static GameState movieBlockState(
            List<? extends PhysicalCard> opponentHand,
            List<? extends PhysicalCard> opponentDeck,
            List<? extends PhysicalCard> opponentDiscard) {
        GameState gameState = baseGameState();
        when(gameState.isInit()).thenReturn(true);
        when(gameState.getHand(BOT)).thenReturn(List.of());
        doReturn(opponentHand).when(gameState).getHand(OPPONENT);
        when(gameState.getInPlay()).thenReturn(List.of());
        when(gameState.getDiscard(BOT)).thenReturn(List.of());
        doReturn(opponentDiscard).when(gameState).getDiscard(OPPONENT);
        when(gameState.getDeadPile(BOT)).thenReturn(List.of());
        when(gameState.getDeadPile(OPPONENT)).thenReturn(List.of());
        when(gameState.getDeck(BOT)).thenReturn(List.of());
        doReturn(opponentDeck).when(gameState).getDeck(OPPONENT);
        return gameState;
    }

    private static GameState baseGameState() {
        GameState gameState = mock(GameState.class);
        when(gameState.getPlayerNames()).thenReturn(List.of(BOT, OPPONENT));
        when(gameState.getPlayerOrder()).thenReturn(new PlayerOrder(List.of(BOT, OPPONENT)));
        when(gameState.getCurrentPlayerId()).thenReturn(BOT);
        when(gameState.getCurrentPhase()).thenReturn(Phase.FELLOWSHIP);
        when(gameState.getPlayerPosition(BOT)).thenReturn(1);
        when(gameState.getPlayerPosition(OPPONENT)).thenReturn(1);
        when(gameState.getPlayerBurdens(BOT)).thenReturn(0);
        when(gameState.getPlayerBurdens(OPPONENT)).thenReturn(0);
        when(gameState.getRingBearer(BOT)).thenReturn(null);
        when(gameState.getRingBearer(OPPONENT)).thenReturn(null);
        return gameState;
    }

    private static AwaitingDecision cardSelectionDecision() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_SELECTION);
        when(decision.getText()).thenReturn("Choose a card");
        when(decision.getDecisionParameters()).thenReturn(Map.of());
        return decision;
    }

    private static PhysicalCard hiddenCard(CardType type, Culture culture, Side side) {
        return cardForOwner(OPPONENT, type, culture, side);
    }

    private static PhysicalCard cardForOwner(String owner, CardType type, Culture culture, Side side) {
        PhysicalCard card = mock(PhysicalCard.class);
        LotroCardBlueprint blueprint = mock(LotroCardBlueprint.class);
        when(card.getOwner()).thenReturn(owner);
        when(card.getBlueprint()).thenReturn(blueprint);
        when(blueprint.getCardType()).thenReturn(type);
        when(blueprint.getCulture()).thenReturn(culture);
        when(blueprint.getSide()).thenReturn(side);
        return card;
    }

    private static PhysicalCard characterCard(String owner, CardType type, int strength, int vitality) {
        PhysicalCard card = mock(PhysicalCard.class);
        LotroCardBlueprint blueprint = mock(LotroCardBlueprint.class);
        when(card.getOwner()).thenReturn(owner);
        when(card.getBlueprint()).thenReturn(blueprint);
        when(blueprint.getCardType()).thenReturn(type);
        when(blueprint.getStrength()).thenReturn(strength);
        when(blueprint.getVitality()).thenReturn(vitality);
        return card;
    }
}
