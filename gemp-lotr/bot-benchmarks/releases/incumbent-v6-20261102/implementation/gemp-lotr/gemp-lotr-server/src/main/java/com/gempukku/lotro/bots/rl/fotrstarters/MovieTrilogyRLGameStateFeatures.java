package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.Assignment;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.NestedAssignment;
import com.gempukku.lotro.game.state.Skirmish;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.logic.timing.GameStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class MovieTrilogyRLGameStateFeatures implements RLGameStateFeatures {
    private static final List<CardType> CARD_TYPES = List.of(CardType.values());
    private static final List<Culture> CULTURES = List.of(Culture.values());
    private static final List<Side> SIDES = List.of(Side.values());
    private static final List<Phase> PHASES = List.of(Phase.values());
    private static final List<String> DECISION_TEXT_MARKERS = List.of(
            "starting fellowship", "another move", "mulligan", "reconcile", "archery",
            "assignment", "skirmish", "fellowship", "shadow", "move", "wound",
            "heal", "discard", "play", "pass", "spot", "burden", "threat", "twilight",
            "strength", "damage", "control", "overwhelm", "ring-bearer", "resistance",
            "draw", "site 9", "sanctuary");
    private static final List<String> DECISION_OPTION_KEYS = List.of(
            "cardId", "blueprintId", "actionId", "actionText", "results",
            "freeCharacters", "minions", "selectable");

    public static final int TACTICAL_CONTEXT_FEATURE_COUNT = 48;
    public static final int SELF_BURDEN_PRESSURE = 0;
    public static final int OPPONENT_BURDEN_PRESSURE = 1;
    public static final int SELF_RING_BEARER_HEALTH = 2;
    public static final int OPPONENT_RING_BEARER_HEALTH = 3;
    public static final int SELF_COMPANION_WOUND_RATIO = 4;
    public static final int OPPONENT_COMPANION_WOUND_RATIO = 5;
    public static final int SELF_COMPANION_COUNT = 6;
    public static final int OPPONENT_COMPANION_COUNT = 7;
    public static final int SELF_MINION_COUNT = 8;
    public static final int OPPONENT_MINION_COUNT = 9;
    public static final int SELF_THREAT_COUNT = 10;
    public static final int OPPONENT_THREAT_COUNT = 11;
    public static final int SELF_CONTROLLED_SITES = 12;
    public static final int OPPONENT_CONTROLLED_SITES = 13;
    public static final int SELF_SITE_PROGRESS = 14;
    public static final int OPPONENT_SITE_PROGRESS = 15;
    public static final int TWILIGHT_POOL = 16;
    public static final int REMAINING_MOVES = 17;
    public static final int SELF_HAND_DEFICIT = 18;
    public static final int OPPONENT_HAND_SIZE = 19;
    public static final int SELF_DECK_REMAINING = 20;
    public static final int OPPONENT_DECK_REMAINING = 21;
    public static final int SKIRMISH_PRESSURE = 22;
    public static final int ARCHERY_PRESSURE = 23;
    public static final int SELF_COMPANION_STRENGTH = 24;
    public static final int OPPONENT_COMPANION_STRENGTH = 25;
    public static final int SELF_MINION_STRENGTH = 26;
    public static final int OPPONENT_MINION_STRENGTH = 27;
    public static final int SELF_DEAD_COMPANIONS = 28;
    public static final int OPPONENT_DEAD_COMPANIONS = 29;
    public static final int SELF_HAND_COMBAT_BOOST_EVENTS = 30;
    public static final int PENDING_SKIRMISH_COUNT = 31;
    public static final int PENDING_SELF_LOSS_COUNT = 32;
    public static final int PENDING_SELF_OVERWHELM_COUNT = 33;
    public static final int PENDING_SELF_STRENGTH_DEFICIT = 34;
    public static final int CURRENT_SKIRMISH_SELF_LOSS_RISK = 35;
    public static final int CURRENT_SKIRMISH_SELF_WINNING = 36;
    public static final int PENDING_RING_BEARER_DANGER = 37;
    public static final int ADDITIONAL_MOVE_TWILIGHT_BUDGET = 38;
    public static final int SELF_MINION_WOUND_RATIO = 39;
    public static final int OPPONENT_MINION_WOUND_RATIO = 40;
    public static final int SELF_MINION_RECOVERY_SOURCES = 41;
    public static final int OPPONENT_MINION_RECOVERY_SOURCES = 42;
    public static final int SELF_DISCARD_MINION_COUNT = 43;
    public static final int OPPONENT_DISCARD_VISIBLE = 44;
    public static final int OPPONENT_DISCARD_MINION_COUNT = 45;
    public static final int SELF_MINION_RECOVERY_POTENTIAL = 46;
    public static final int OPPONENT_MINION_RECOVERY_POTENTIAL = 47;

    private final String formatCode;

    public MovieTrilogyRLGameStateFeatures(String formatCode) {
        this.formatCode = formatCode;
    }

    @Override
    public double[] extractFeatures(GameState gameState, AwaitingDecision decision, String playerId) {
        String opponentId = findOpponentId(gameState, playerId);
        List<Double> features = new ArrayList<>();

        appendFormat(features);
        features.add(gameState.getCurrentPlayerId() == null ? -1.0
                : gameState.getCurrentPlayerId().equals(playerId) ? 1.0 : 0.0);
        appendOneHot(features, PHASES, gameState.getCurrentPhase());
        appendOneHot(features, Arrays.asList(AwaitingDecisionType.values()), decision.getDecisionType());
        features.add((double) gameState.getTwilightPool());
        features.add((double) gameState.getPlayerPosition(playerId));
        features.add((double) gameState.getPlayerPosition(opponentId));
        features.add((double) gameState.getPlayerBurdens(playerId));
        features.add((double) gameState.getPlayerBurdens(opponentId));
        List<? extends PhysicalCard> selfHand = gameState.isInit() ? gameState.getHand(playerId) : List.of();
        List<? extends PhysicalCard> opponentHand = gameState.isInit() ? gameState.getHand(opponentId) : List.of();
        boolean opponentHandRevealed = gameState.isHandRevealed(opponentId);
        features.add(selfHand.size() >= 4 ? 1.0 : 0.0);
        features.add(opponentHand.size() >= 4 ? 1.0 : 0.0);
        features.add((double) selfHand.size());
        features.add((double) opponentHand.size());

        appendRingBearerFeatures(features, gameState, playerId);
        appendRingBearerFeatures(features, gameState, opponentId);
        int selfDeckSize = 0;
        int opponentDeckSize = 0;
        List<? extends PhysicalCard> opponentDiscard = List.of();
        if (gameState.isInit()) {
            appendZoneFeatures(features, gameState.getInPlay(), playerId, gameState);
            appendZoneFeatures(features, gameState.getInPlay(), opponentId, gameState);
            appendZoneFeatures(features, selfHand, playerId, gameState);
            if (opponentHandRevealed)
                appendZoneFeatures(features, opponentHand, opponentId, gameState);
            else
                appendPublicCardCountOnly(features, opponentHand.size());
            appendZoneFeatures(features, gameState.getDiscard(playerId), playerId, gameState);
            opponentDiscard = gameState.getDiscard(opponentId);
            if (gameState.isDiscardPilePublic())
                appendZoneFeatures(features, opponentDiscard, opponentId, gameState);
            else
                appendPublicCardCountOnly(features, opponentDiscard.size());
            appendZoneFeatures(features, gameState.getDeadPile(playerId), playerId, gameState);
            appendZoneFeatures(features, gameState.getDeadPile(opponentId), opponentId, gameState);
            appendPublicCardCountOnly(features, gameState.getDeck(playerId).size());
            appendPublicCardCountOnly(features, gameState.getDeck(opponentId).size());
            selfDeckSize = gameState.getDeck(playerId).size();
            opponentDeckSize = gameState.getDeck(opponentId).size();
        } else {
            for (int i = 0; i < 10; i++)
                appendEmptyZoneFeatures(features);
        }

        appendSkirmishFeatures(features, gameState);
        appendDecisionFeatures(features, decision);
        appendTacticalContext(
                features, gameState, playerId, opponentId, selfHand, opponentHand,
                selfDeckSize, opponentDeckSize);
        return features.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static String findOpponentId(GameState gameState, String playerId) {
        return gameState.getPlayerNames().stream()
                .filter(name -> !name.equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown opponent for " + playerId));
    }

    private void appendFormat(List<Double> features) {
        features.add("fotr_block".equals(formatCode) ? 1.0 : 0.0);
        features.add("ttt_block".equals(formatCode) ? 1.0 : 0.0);
        features.add("king_block".equals(formatCode) ? 1.0 : 0.0);
        features.add("movie".equals(formatCode) ? 1.0 : 0.0);
    }

    private static void appendRingBearerFeatures(List<Double> features, GameState gameState, String playerId) {
        PhysicalCard ringBearer = gameState.getRingBearer(playerId);
        if (ringBearer == null) {
            features.addAll(List.of(0.0, 0.0, 0.0, 0.0, 0.0));
            return;
        }

        features.add(1.0);
        features.add((double) gameState.getWounds(ringBearer));
        features.add((double) ringBearer.getBlueprint().getVitality());
        features.add((double) ringBearer.getBlueprint().getResistance());
        features.add((double) gameState.getPlayerBurdens(playerId));
    }

    private static void appendZoneFeatures(
            List<Double> features,
            List<? extends PhysicalCard> zone,
            String owner,
            GameState gameState) {
        int total = 0;
        int totalWounds = 0;
        int[] typeCounts = new int[CARD_TYPES.size()];
        int[] cultureCounts = new int[CULTURES.size()];
        int[] sideCounts = new int[SIDES.size()];
        int[] strategyCounts = new int[CardStrategyFeatures.FEATURE_COUNT];

        for (PhysicalCard card : zone) {
            if (!card.getOwner().equals(owner))
                continue;

            total++;
            totalWounds += gameState.getWounds(card);
            increment(typeCounts, CARD_TYPES, card.getBlueprint().getCardType());
            increment(cultureCounts, CULTURES, card.getBlueprint().getCulture());
            increment(sideCounts, SIDES, card.getBlueprint().getSide());
            double[] strategyFeatures = CardStrategyFeatures.extract(card.getBlueprint());
            for (int i = 0; i < strategyCounts.length; i++)
                strategyCounts[i] += (int) strategyFeatures[i];
        }

        features.add((double) total);
        features.add((double) totalWounds);
        appendCounts(features, typeCounts);
        appendCounts(features, cultureCounts);
        appendCounts(features, sideCounts);
        appendCounts(features, strategyCounts);
    }

    private static <T> void increment(int[] counts, List<T> values, T value) {
        if (value == null)
            return;
        int index = values.indexOf(value);
        if (index >= 0)
            counts[index]++;
    }

    private static void appendCounts(List<Double> features, int[] counts) {
        for (int count : counts)
            features.add((double) count);
    }

    private static void appendEmptyZoneFeatures(List<Double> features) {
        features.add(0.0);
        features.add(0.0);
        for (int i = 0; i < CARD_TYPES.size() + CULTURES.size() + SIDES.size()
                + CardStrategyFeatures.FEATURE_COUNT; i++)
            features.add(0.0);
    }

    private static void appendPublicCardCountOnly(List<Double> features, int count) {
        features.add((double) count);
        features.add(0.0);
        for (int i = 0; i < CARD_TYPES.size() + CULTURES.size() + SIDES.size()
                + CardStrategyFeatures.FEATURE_COUNT; i++)
            features.add(0.0);
    }

    private static void appendTacticalContext(
            List<Double> features,
            GameState gameState,
            String playerId,
            String opponentId,
            List<? extends PhysicalCard> selfHand,
            List<? extends PhysicalCard> opponentHand,
            int selfDeckSize,
            int opponentDeckSize) {
        PhysicalCard selfRingBearer = gameState.getRingBearer(playerId);
        PhysicalCard opponentRingBearer = gameState.getRingBearer(opponentId);
        GameStats stats = gameState.getMostRecentGameStats();

        features.add(burdenPressure(gameState, selfRingBearer, playerId));
        features.add(burdenPressure(gameState, opponentRingBearer, opponentId));
        features.add(ringBearerHealth(gameState, selfRingBearer));
        features.add(ringBearerHealth(gameState, opponentRingBearer));
        features.add(companionWoundRatio(gameState, playerId));
        features.add(companionWoundRatio(gameState, opponentId));
        features.add(normalizedCount(countInPlay(gameState, playerId, CardType.COMPANION), 9));
        features.add(normalizedCount(countInPlay(gameState, opponentId, CardType.COMPANION), 9));
        features.add(normalizedCount(countInPlay(gameState, playerId, CardType.MINION), 12));
        features.add(normalizedCount(countInPlay(gameState, opponentId, CardType.MINION), 12));
        features.add(normalizedCount(gameState.getPlayerThreats(playerId), 9));
        features.add(normalizedCount(gameState.getPlayerThreats(opponentId), 9));
        features.add(normalizedCount(countControlledSites(gameState, playerId), 9));
        features.add(normalizedCount(countControlledSites(gameState, opponentId), 9));
        features.add(normalizedCount(gameState.getPlayerPosition(playerId), 9));
        features.add(normalizedCount(gameState.getPlayerPosition(opponentId), 9));
        features.add(Math.min(2.0, gameState.getTwilightPool() / 20.0));

        int moveLimit = stats == null ? 2 : stats.getMoveLimit();
        int moveCount = stats == null ? gameState.getMoveCount() : stats.getMoveCount();
        features.add(normalizedCount(Math.max(0, moveLimit - moveCount), 2));
        features.add(1.0 - normalizedCount(selfHand.size(), 8));
        features.add(normalizedCount(opponentHand.size(), 8));
        features.add(normalizedCount(selfDeckSize, 30));
        features.add(normalizedCount(opponentDeckSize, 30));
        features.add(stats == null ? 0.0 : Math.min(1.0,
                Math.abs(stats.getFellowshipSkirmishStrength() - stats.getShadowSkirmishStrength()) / 30.0));

        Integer fellowshipArchery = stats == null ? null : stats.getFellowshipArchery();
        Integer shadowArchery = stats == null ? null : stats.getShadowArchery();
        features.add(fellowshipArchery == null || shadowArchery == null ? 0.0
                : Math.min(1.0, Math.abs(fellowshipArchery - shadowArchery) / 10.0));
        features.add(normalizedCount(sumStrength(gameState, playerId, CardType.COMPANION, stats), 100));
        features.add(normalizedCount(sumStrength(gameState, opponentId, CardType.COMPANION, stats), 100));
        features.add(normalizedCount(sumStrength(gameState, playerId, CardType.MINION, stats), 100));
        features.add(normalizedCount(sumStrength(gameState, opponentId, CardType.MINION, stats), 100));
        features.add(normalizedCount(countDeadCompanions(gameState, playerId), 9));
        features.add(normalizedCount(countDeadCompanions(gameState, opponentId), 9));
        appendSkirmishPlanningFeatures(features, gameState, playerId, selfHand, stats);
        appendMinionPressureFeatures(features, gameState, playerId, opponentId, selfRingBearer, opponentRingBearer, stats);
    }

    private static void appendMinionPressureFeatures(
            List<Double> features,
            GameState gameState,
            String playerId,
            String opponentId,
            PhysicalCard selfRingBearer,
            PhysicalCard opponentRingBearer,
            GameStats stats) {
        String fellowshipPlayerId = selfRingBearer != null ? playerId
                : opponentRingBearer != null ? opponentId : null;
        int additionalMoveTwilight = 0;
        if (gameState.getCurrentPhase() == Phase.REGROUP && fellowshipPlayerId != null) {
            additionalMoveTwilight = gameState.getTwilightPool()
                    + countInPlay(gameState, fellowshipPlayerId, CardType.COMPANION);
        }
        features.add(normalizedCount(additionalMoveTwilight, 40));

        double selfMinionWounds = minionWoundRatio(gameState, playerId, stats);
        double opponentMinionWounds = minionWoundRatio(gameState, opponentId, stats);
        features.add(selfMinionWounds);
        features.add(opponentMinionWounds);

        int selfRecoverySources = countInPlayCardsWithStrategy(
                gameState, playerId, CardStrategyFeatures.MINION_FROM_DISCARD);
        int opponentRecoverySources = countInPlayCardsWithStrategy(
                gameState, opponentId, CardStrategyFeatures.MINION_FROM_DISCARD);
        features.add(normalizedCount(selfRecoverySources, 6));
        features.add(normalizedCount(opponentRecoverySources, 6));

        int selfDiscardMinions = gameState.isInit()
                ? countMinions(gameState.getDiscard(playerId), playerId) : 0;
        boolean opponentDiscardVisible = gameState.isDiscardPilePublic();
        int opponentDiscardMinions = opponentDiscardVisible && gameState.isInit()
                ? countMinions(gameState.getDiscard(opponentId), opponentId) : 0;
        features.add(normalizedCount(selfDiscardMinions, 12));
        features.add(opponentDiscardVisible ? 1.0 : 0.0);
        features.add(normalizedCount(opponentDiscardMinions, 12));

        features.add(normalizedCount(selfRecoverySources * selfDiscardMinions, 12));
        features.add(normalizedCount(opponentRecoverySources * opponentDiscardMinions, 12));
    }

    private static int countInPlayCardsWithStrategy(GameState gameState, String owner, int strategyFeature) {
        return (int) gameState.getInPlay().stream()
                .filter(card -> owner.equals(card.getOwner()))
                .filter(card -> CardStrategyFeatures.extract(card.getBlueprint())[strategyFeature] > 0)
                .count();
    }

    private static int countMinions(List<? extends PhysicalCard> cards, String owner) {
        return (int) cards.stream()
                .filter(card -> owner.equals(card.getOwner()))
                .filter(card -> card.getBlueprint().getCardType() == CardType.MINION)
                .count();
    }

    private static double minionWoundRatio(GameState gameState, String owner, GameStats stats) {
        int wounds = 0;
        int vitality = 0;
        for (PhysicalCard card : gameState.getInPlay()) {
            if (!owner.equals(card.getOwner()) || card.getBlueprint().getCardType() != CardType.MINION)
                continue;

            wounds += gameState.getWounds(card);
            int baseVitality = card.getBlueprint().getVitality();
            vitality += stats == null ? baseVitality
                    : stats.getCharVitalities().getOrDefault(card.getCardId(), baseVitality);
        }
        return vitality == 0 ? 0.0 : Math.min(1.0, wounds / (double) vitality);
    }

    private static void appendSkirmishPlanningFeatures(
            List<Double> features,
            GameState gameState,
            String playerId,
            List<? extends PhysicalCard> selfHand,
            GameStats stats) {
        int combatBoostEvents = (int) selfHand.stream()
                .filter(card -> card.getBlueprint().getCardType() == CardType.EVENT)
                .filter(card -> CardStrategyFeatures.extract(card.getBlueprint())[CardStrategyFeatures.COMBAT_BOOST] > 0)
                .count();
        features.add(normalizedCount(combatBoostEvents, 6));

        int pendingCount = 0;
        int pendingSelfLosses = 0;
        int pendingSelfOverwhelms = 0;
        int pendingStrengthDeficit = 0;
        boolean pendingRingBearerDanger = false;

        for (Assignment assignment : getPendingAssignments(gameState)) {
            PhysicalCard fellowshipCharacter = assignment.getFellowshipCharacter();
            if (fellowshipCharacter == null || assignment.getShadowCharacters().isEmpty())
                continue;

            int fellowshipStrength = getCharacterStrength(fellowshipCharacter, stats);
            int shadowStrength = assignment.getShadowCharacters().stream()
                    .mapToInt(character -> getCharacterStrength(character, stats))
                    .sum();
            int selfStrength = playerId.equals(fellowshipCharacter.getOwner())
                    ? fellowshipStrength : shadowStrength;
            int opponentStrength = playerId.equals(fellowshipCharacter.getOwner())
                    ? shadowStrength : fellowshipStrength;
            boolean shadowWins = shadowStrength >= fellowshipStrength;
            boolean selfLoses = playerId.equals(fellowshipCharacter.getOwner())
                    ? shadowWins : !shadowWins;

            pendingCount++;
            if (selfLoses)
                pendingSelfLosses++;
            if (opponentStrength > 0 && (selfStrength == 0 || opponentStrength >= 2 * selfStrength))
                pendingSelfOverwhelms++;
            pendingStrengthDeficit += Math.max(0, opponentStrength - selfStrength);

            PhysicalCard ringBearer = gameState.getRingBearer(fellowshipCharacter.getOwner());
            if (ringBearer != null && ringBearer.getCardId() == fellowshipCharacter.getCardId() && shadowWins)
                pendingRingBearerDanger = true;
        }

        features.add(normalizedCount(pendingCount, 9));
        features.add(normalizedCount(pendingSelfLosses, 9));
        features.add(normalizedCount(pendingSelfOverwhelms, 9));
        features.add(normalizedCount(pendingStrengthDeficit, 45));

        Skirmish currentSkirmish = gameState.getSkirmish();
        if (currentSkirmish == null || currentSkirmish.getFellowshipCharacter() == null
                || currentSkirmish.getShadowCharacters().isEmpty()) {
            features.add(0.0);
            features.add(0.0);
        } else {
            int fellowshipStrength = stats == null
                    ? getCharacterStrength(currentSkirmish.getFellowshipCharacter(), null)
                    : stats.getFellowshipSkirmishStrength();
            int shadowStrength = stats == null
                    ? currentSkirmish.getShadowCharacters().stream()
                    .mapToInt(character -> getCharacterStrength(character, null))
                    .sum()
                    : stats.getShadowSkirmishStrength();
            boolean selfIsFellowship = playerId.equals(currentSkirmish.getFellowshipCharacter().getOwner());
            boolean shadowWins = shadowStrength >= fellowshipStrength;
            boolean selfLoses = selfIsFellowship ? shadowWins : !shadowWins;
            features.add(selfLoses ? 1.0 : 0.0);
            features.add(selfLoses ? 0.0 : 1.0);
        }

        features.add(pendingRingBearerDanger ? 1.0 : 0.0);
    }

    private static List<Assignment> getPendingAssignments(GameState gameState) {
        List<Assignment> assignments = gameState.getAssignments();
        if (assignments == null || assignments.isEmpty())
            return List.of();

        for (Assignment assignment : assignments) {
            if (assignment instanceof NestedAssignment nestedAssignment)
                return List.copyOf(nestedAssignment.getPendingSubskirmishes());
        }
        return assignments;
    }

    private static int getCharacterStrength(PhysicalCard character, GameStats stats) {
        int baseStrength = character.getBlueprint().getStrength();
        return stats == null ? baseStrength
                : stats.getCharStrengths().getOrDefault(character.getCardId(), baseStrength);
    }

    private static double burdenPressure(GameState gameState, PhysicalCard ringBearer, String playerId) {
        if (ringBearer == null)
            return 0.0;
        int resistance = ringBearer.getBlueprint().getResistance();
        if (resistance <= 0)
            return gameState.getPlayerBurdens(playerId) > 0 ? 1.0 : 0.0;
        return Math.min(2.0, gameState.getPlayerBurdens(playerId) / (double) resistance);
    }

    private static double ringBearerHealth(GameState gameState, PhysicalCard ringBearer) {
        if (ringBearer == null)
            return 0.0;
        int vitality = Math.max(1, ringBearer.getBlueprint().getVitality());
        return Math.max(0.0, vitality - gameState.getWounds(ringBearer)) / (double) vitality;
    }

    private static double companionWoundRatio(GameState gameState, String owner) {
        int totalVitality = 0;
        int totalWounds = 0;
        for (PhysicalCard card : gameState.getInPlay()) {
            if (owner.equals(card.getOwner()) && card.getBlueprint().getCardType() == CardType.COMPANION) {
                totalVitality += Math.max(1, card.getBlueprint().getVitality());
                totalWounds += gameState.getWounds(card);
            }
        }
        return totalVitality == 0 ? 0.0 : totalWounds / (double) totalVitality;
    }

    private static int countInPlay(GameState gameState, String owner, CardType type) {
        return (int) gameState.getInPlay().stream()
                .filter(card -> owner.equals(card.getOwner()) && card.getBlueprint().getCardType() == type)
                .count();
    }

    private static int countControlledSites(GameState gameState, String playerId) {
        int controlled = 0;
        for (int siteNumber = 1; siteNumber <= 9; siteNumber++) {
            PhysicalCard site = gameState.getSite(siteNumber);
            if (site != null && playerId.equals(site.getCardController()))
                controlled++;
        }
        return controlled;
    }

    private static int countDeadCompanions(GameState gameState, String playerId) {
        return (int) gameState.getDeadPile(playerId).stream()
                .filter(card -> card.getBlueprint().getCardType() == CardType.COMPANION)
                .count();
    }

    private static int sumStrength(GameState gameState, String owner, CardType type, GameStats stats) {
        return gameState.getInPlay().stream()
                .filter(card -> owner.equals(card.getOwner()) && card.getBlueprint().getCardType() == type)
                .mapToInt(card -> stats == null
                        ? card.getBlueprint().getStrength()
                        : stats.getCharStrengths().getOrDefault(card.getCardId(), card.getBlueprint().getStrength()))
                .sum();
    }

    private static double normalizedCount(int value, int maximum) {
        return Math.min(1.0, Math.max(0.0, value / (double) maximum));
    }

    private static void appendSkirmishFeatures(List<Double> features, GameState gameState) {
        Skirmish skirmish = gameState.getSkirmish();
        GameStats stats = gameState.getMostRecentGameStats();
        features.add(skirmish == null ? 0.0 : 1.0);
        features.add(stats == null ? 0.0
                : (double) (stats.getFellowshipSkirmishStrength() - stats.getShadowSkirmishStrength()));
        features.add(stats == null ? 0.0 : (double) stats.getFellowshipSkirmishDamageBonus());
        features.add(stats != null && stats.isFpOverwhelmed() ? 1.0 : 0.0);
        features.add(skirmish == null || skirmish.getFellowshipCharacter() == null ? 0.0
                : (double) (skirmish.getFellowshipCharacter().getBlueprint().getVitality()
                - gameState.getWounds(skirmish.getFellowshipCharacter())));
        features.add(skirmish == null ? 0.0 : (double) skirmish.getShadowCharacters().size());
    }

    private static void appendDecisionFeatures(List<Double> features, AwaitingDecision decision) {
        Map<String, String[]> parameters = decision.getDecisionParameters();
        appendNumber(features, parameters.get("min"));
        appendNumber(features, parameters.get("max"));
        for (String key : DECISION_OPTION_KEYS) {
            String[] values = parameters.get(key);
            features.add(values == null ? 0.0 : (double) values.length);
        }
        String text = decision.getText().toLowerCase();
        for (String marker : DECISION_TEXT_MARKERS)
            features.add(text.contains(marker) ? 1.0 : 0.0);
    }

    private static void appendNumber(List<Double> features, String[] value) {
        if (value == null || value.length == 0) {
            features.add(0.0);
            return;
        }
        try {
            features.add(Double.parseDouble(value[0]));
        } catch (NumberFormatException e) {
            features.add(0.0);
        }
    }

    private static <T> void appendOneHot(List<Double> features, Iterable<T> values, T chosen) {
        for (T value : values)
            features.add(value == chosen ? 1.0 : 0.0);
    }
}
