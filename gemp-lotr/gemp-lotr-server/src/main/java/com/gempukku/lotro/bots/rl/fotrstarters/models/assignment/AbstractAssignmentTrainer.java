package com.gempukku.lotro.bots.rl.fotrstarters.models.assignment;

import com.gempukku.lotro.bots.AssignmentLegality;
import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.AssignMinionsAction;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.Assignment;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractAssignmentTrainer extends AbstractTrainer {
    protected abstract boolean isForFp();

    protected abstract void generateAssignmentsRecursive(Map<String, String> minionsToAssign,
                                                         List<String> minionKeys, int index,
                                                         Map<String, String> freeChars,
                                                         AssignmentInfo current,
                                                         List<AssignmentInfo> results,
                                                         Map<String, Integer> remainingAssignmentLimits,
                                                         GameState gameState);


    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        boolean correctPlayer = (isForFp() && gameState.getCurrentPlayerId().equals(playerName)) ||
                (!isForFp() && !gameState.getCurrentPlayerId().equals(playerName));

        return decision.getDecisionType().equals(AwaitingDecisionType.ASSIGN_MINIONS) &&
                correctPlayer;
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        return step.decision.getDecisionType().equals(AwaitingDecisionType.ASSIGN_MINIONS) &&
                step.action instanceof AssignMinionsAction ama &&
                ama.isFreePeoplesAssignment() == isForFp();
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        return getAnswer(gameState, decision, playerName, features, modelRegistry, AssignmentLegality.UNCHECKED);
    }

    /**
     * Scores every candidate assignment with the model and answers with the best one the game will accept. If the
     * rules reject every candidate (only possible for Shadow, whose candidates assign every minion), the best-scored
     * one is trimmed down to its legal pairings instead.
     */
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features,
                            ModelRegistry modelRegistry, AssignmentLegality legality) {
        Map<String, String[]> params = decision.getDecisionParameters();
        String[] freeCharIds = params.get("freeCharacters");
        String[] minionIds = params.get("minions");

        if (freeCharIds == null || minionIds == null || freeCharIds.length == 0 || minionIds.length == 0)
            return "";


        Map<String, String> freeChars = Stream.of(freeCharIds)
                .collect(Collectors.toMap(
                        Function.identity(),
                        s -> gameState.getBlueprintId(Integer.parseInt(s))
                ));
        Map<String, String> minions = Stream.of(minionIds)
                .collect(Collectors.toMap(
                        Function.identity(),
                        s -> gameState.getBlueprintId(Integer.parseInt(s))
                ));
        HashMap<String, List<String>> alreadyAssignedMap = new HashMap<>();
        HashMap<String, Integer> woundsOnFp = new HashMap<>();
        gameState.getAssignments().forEach(assignment -> {
            String fpBlueprint = assignment.getFellowshipCharacter().getBlueprintId();
            alreadyAssignedMap.put(fpBlueprint, new ArrayList<>());
            assignment.getShadowCharacters().forEach(shadow -> alreadyAssignedMap.get(fpBlueprint).add(shadow.getBlueprintId()));
        });
        gameState.getInPlay().forEach(fpCharacter -> {
            if ((fpCharacter.getBlueprint().getCardType().equals(CardType.COMPANION) ||
                    fpCharacter.getBlueprint().getCardType().equals(CardType.ALLY)) &&
                    fpCharacter.getOwner().equals(gameState.getCurrentPlayerId())) {
                woundsOnFp.put(fpCharacter.getBlueprintId(), gameState.getWounds(fpCharacter));
                if (!alreadyAssignedMap.containsKey(fpCharacter.getBlueprintId())) {
                    alreadyAssignedMap.put(fpCharacter.getBlueprintId(), new ArrayList<>());
                }
            }
        });

        Map<String, Integer> remainingAssignmentLimits =
                getRemainingAssignmentLimits(gameState, freeChars, legality);
        List<AssignmentInfo> allAssignments = generateAllAssignments(
                alreadyAssignedMap, freeChars, minions, remainingAssignmentLimits, gameState);
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        Classifier<double[]> model = modelRegistry.getModel(getClass());

        Side side = isForFp() ? Side.FREE_PEOPLE : Side.SHADOW;
        String ringBearerBlueprintId = findRingBearerBlueprintId(gameState);
        double bestScore = Double.NEGATIVE_INFINITY;
        Map<String, List<String>> bestAssignment = null;
        double bestIllegalScore = Double.NEGATIVE_INFINITY;
        Map<String, List<String>> bestIllegalAssignment = null;

        for (AssignmentInfo assignment : allAssignments) {
            try {
                double[] assignmentVector = CardFeatures.getAssignmentFeatures(assignment.blueprintAssignment(), woundsOnFp,
                        assignment.numberOfUnassignedMinions(), assignment.strengthOfUnassignedMinions(),
                        ringBearerBlueprintId);
                double[] extended = Arrays.copyOf(stateVector, stateVector.length + assignmentVector.length);
                System.arraycopy(assignmentVector, 0, extended, stateVector.length, assignmentVector.length);

                double[] probs = new double[2];
                model.predict(getModelInputVector(model, extended), probs);
                if (!legality.isLegal(side, freeCharIds, minionIds, assignment.physicalAssignment())) {
                    if (probs[1] > bestIllegalScore) {
                        bestIllegalScore = probs[1];
                        bestIllegalAssignment = assignment.physicalAssignment();
                    }
                } else if (probs[1] > bestScore) {
                    bestScore = probs[1];
                    bestAssignment = assignment.physicalAssignment();
                }
            } catch (CardNotFoundException ignore) {
            }
        }

        if (bestAssignment == null && bestIllegalAssignment != null)
            bestAssignment = legality.keepLegal(side, freeCharIds, minionIds, bestIllegalAssignment);

        if (bestAssignment == null || bestAssignment.isEmpty())
            return "";

        return AssignmentLegality.format(bestAssignment);
    }

    @Override
    protected double[] getModelInputVector(Classifier<double[]> model, double[] assignmentVector) {
        return ModelIO.adaptModelInputVector(model, assignmentVector,
                assignmentVector.length - CardFeatures.ASSIGNMENT_OUTCOME_FEATURE_COUNT);
    }


    private List<AssignmentInfo> generateAllAssignments(HashMap<String, List<String>> alreadyAssignedMap,
                                                        Map<String, String> freeChars,
                                                        Map<String, String> minions,
                                                        Map<String, Integer> remainingAssignmentLimits,
                                                        GameState gameState) {
        List<AssignmentInfo> results = new ArrayList<>();

        // Ensure every freeChar is initialized in the assignment map
        freeChars.forEach((physicalId, blueprintId) -> {
            if (!alreadyAssignedMap.containsKey(blueprintId)) {
                throw new IllegalArgumentException("Unknown fp card: " + blueprintId);
            }

        });

        // Deep copy to avoid mutating input map
        AssignmentInfo current = new AssignmentInfo(new HashMap<>(), new HashMap<>(), 0 , 0);
        for (String fp : alreadyAssignedMap.keySet()) {
            current.blueprintAssignment().put(fp, new ArrayList<>(alreadyAssignedMap.get(fp)));
        }

        List<String> minionKeys = minions.keySet().stream()
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
        generateAssignmentsRecursive(minions, minionKeys, 0, freeChars, current, results,
                remainingAssignmentLimits, gameState);
        return results;
    }

    private Map<String, Integer> getRemainingAssignmentLimits(
            GameState gameState, Map<String, String> freeChars, AssignmentLegality legality) {
        if (!isForFp())
            return Map.of();

        Map<String, Integer> limits = new HashMap<>();
        for (String freeCharacterId : freeChars.keySet()) {
            PhysicalCard freeCharacter = gameState.findCardById(Integer.parseInt(freeCharacterId));
            int defender = freeCharacter == null ? 0
                    : Math.max(0, freeCharacter.getBlueprint().getKeywordCount(Keyword.DEFENDER));
            int alreadyAssigned = 0;
            for (Assignment assignment : gameState.getAssignments()) {
                if (assignment.getFellowshipCharacter() != null
                        && assignment.getFellowshipCharacter().getCardId() == Integer.parseInt(freeCharacterId))
                    alreadyAssigned += assignment.getShadowCharacters().size();
            }
            int fallback = Math.max(0, 1 + defender - alreadyAssigned);
            limits.put(freeCharacterId,
                    legality.getRemainingFreePeopleAssignments(freeCharacterId, fallback));
        }
        return limits;
    }

    private static String findRingBearerBlueprintId(GameState gameState) {
        for (String playerId : gameState.getPlayerNames()) {
            PhysicalCard ringBearer = gameState.getRingBearer(playerId);
            if (ringBearer != null)
                return ringBearer.getBlueprintId();
        }
        return null;
    }

    @Override
    protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>();

        for (LearningStep step : steps) {
            if (!isStepRelevant(step)) continue;

            AssignMinionsAction action = (AssignMinionsAction) step.action;
            if (action.getRingBearerBlueprintId() == null)
                continue;
            Map<String, List<String>> assignmentMap = action.getAssignmentMap();
            Map<String, List<String>> alreadyAssignedMap = action.getAlreadyAssignedMap();
            int numberOfUnassignedMinions = action.getNumberOfUnassignedMinions();
            int strengthOfUnassignedMinions = action.getStrengthOfUnassignedMinions();


            alreadyAssignedMap.forEach((fp, shadow) -> {
                if (!assignmentMap.containsKey(fp)) {
                    assignmentMap.put(fp, new ArrayList<>());
                }
                assignmentMap.get(fp).addAll(shadow);
            });


            if (step.reward > 0) {
                // Chosen: good
                addLabeledPoints(data, assignmentMap, numberOfUnassignedMinions, strengthOfUnassignedMinions,
                        action.getWoundsOnFp(), step.state, step.gameId, step.trainingWeight, 1,
                        action.getRingBearerBlueprintId());
            } else {
                // Chosen: bad
                addLabeledPoints(data, assignmentMap, numberOfUnassignedMinions, strengthOfUnassignedMinions,
                        action.getWoundsOnFp(), step.state, step.gameId, step.trainingWeight, 0,
                        action.getRingBearerBlueprintId());
            }
        }

        return data;
    }

    private void addLabeledPoints(List<LabeledPoint> data, Map<String, List<String>> assignmentMap,
                                  int numberOfUnassignedMinions, int strengthOfUnassignedMinions,
                                  Map<String, Integer> woundsMap, double[] state, String gameId,
                                  double trainingWeight, int label, String ringBearerBlueprintId) {
        try {
            double[] assignmentVector = CardFeatures.getAssignmentFeatures(assignmentMap, woundsMap,
                    numberOfUnassignedMinions, strengthOfUnassignedMinions, ringBearerBlueprintId);
            double[] extended = Arrays.copyOf(state, state.length + assignmentVector.length);
            System.arraycopy(assignmentVector, 0, extended, state.length, assignmentVector.length);
            data.add(new LabeledPoint(label, extended, gameId, trainingWeight));
        } catch (CardNotFoundException ignore) {
        }
    }

    protected record AssignmentInfo(Map<String, List<String>> blueprintAssignment,
                                    Map<String, List<String>> physicalAssignment,
                                    int numberOfUnassignedMinions,
                                    int strengthOfUnassignedMinions) {
    }
}
