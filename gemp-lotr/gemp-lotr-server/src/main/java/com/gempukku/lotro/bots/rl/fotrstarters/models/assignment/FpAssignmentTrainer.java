package com.gempukku.lotro.bots.rl.fotrstarters.models.assignment;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;

import java.util.*;

public class FpAssignmentTrainer extends AbstractAssignmentTrainer {
    @Override
    protected boolean isForFp() {
        return true;
    }

    @Override
    protected void generateAssignmentsRecursive(Map<String, String> minionsToAssign,
                                                List<String> minionKeys, int index,
                                                Map<String, String> freeChars,
                                                AssignmentInfo current,
                                                List<AssignmentInfo> results,
                                                Map<String, Integer> remainingAssignmentLimits,
                                                GameState gameState) {
        generateAssignmentsRecursive(minionsToAssign, minionKeys, index, freeChars, current, results,
                new ArrayList<>(), remainingAssignmentLimits, gameState, MAX_ASSIGNMENTS);
    }

    private static final int MAX_ASSIGNMENTS = 10_000;

    private void generateAssignmentsRecursive(Map<String, String> minionsToAssign,
                                              List<String> minionKeys, int index,
                                              Map<String, String> freeChars,
                                              AssignmentInfo current,
                                              List<AssignmentInfo> results,
                                              List<String> unassignedMinions,
                                              Map<String, Integer> remainingAssignmentLimits,
                                              GameState gameState,
                                              int resultLimit) {
        if (results.size() >= resultLimit)
            return;

        if (index >= minionKeys.size()) {
            // Deep copy the current assignment
            int unassignedStrength = unassignedMinions.stream().mapToInt(minionId -> {
                for (PhysicalCard physicalCard : gameState.getInPlay()) {
                    if (physicalCard.getCardId() == Integer.parseInt(minionId)) {
                        return physicalCard.getBlueprint().getStrength();
                    }
                }
                return 0;
            }).sum();
            AssignmentInfo copy = new AssignmentInfo(new HashMap<>(), new HashMap<>(), unassignedMinions.size(), unassignedStrength); // strength placeholder

            for (Map.Entry<String, List<String>> entry : current.blueprintAssignment().entrySet()) {
                copy.blueprintAssignment().put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            for (Map.Entry<String, List<String>> entry : current.physicalAssignment().entrySet()) {
                copy.physicalAssignment().put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            results.add(copy);
            return;
        }

        String minionPhysId = minionKeys.get(index);
        String minionBlueprint = minionsToAssign.get(minionPhysId);
        Map.Entry<String, String> currentMinion = new AbstractMap.SimpleEntry<>(minionPhysId, minionBlueprint);

        List<String> eligibleFreeCharacters = freeChars.keySet().stream()
                .filter(fpPhysId -> current.physicalAssignment().getOrDefault(fpPhysId, List.of()).size()
                        < remainingAssignmentLimits.getOrDefault(fpPhysId, 1))
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
        int branchesRemaining = eligibleFreeCharacters.size() + 1;
        for (String fpPhysId : eligibleFreeCharacters) {
            int branchResultLimit = getBranchResultLimit(results.size(), resultLimit, branchesRemaining--);
            String fpBlueprintId = freeChars.get(fpPhysId);
            current.physicalAssignment().computeIfAbsent(fpPhysId, ignored -> new ArrayList<>()).add(currentMinion.getKey());
            current.blueprintAssignment().computeIfAbsent(fpBlueprintId, ignored -> new ArrayList<>())
                    .add(currentMinion.getValue());

            generateAssignmentsRecursive(minionsToAssign, minionKeys, index + 1, freeChars, current, results,
                    unassignedMinions, remainingAssignmentLimits, gameState, branchResultLimit);

            current.physicalAssignment().get(fpPhysId).remove(current.physicalAssignment().get(fpPhysId).size() - 1);
            current.blueprintAssignment().get(fpBlueprintId)
                    .remove(current.blueprintAssignment().get(fpBlueprintId).size() - 1);
            if (results.size() >= resultLimit)
                return;
        }

        // Let the current minion remain unassigned
        int branchResultLimit = getBranchResultLimit(results.size(), resultLimit, branchesRemaining);
        unassignedMinions.add(minionPhysId);
        generateAssignmentsRecursive(minionsToAssign, minionKeys, index + 1, freeChars, current, results,
                unassignedMinions, remainingAssignmentLimits, gameState, branchResultLimit);
        unassignedMinions.remove(unassignedMinions.size() - 1); // backtrack
    }

    private static int getBranchResultLimit(int currentSize, int resultLimit, int branchesRemaining) {
        int remainingResults = resultLimit - currentSize;
        return currentSize + (remainingResults + branchesRemaining - 1) / branchesRemaining;
    }
}
