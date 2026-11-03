package com.gempukku.lotro.bots.rl.fotrstarters.models.assignment;

import com.gempukku.lotro.game.state.GameState;

import java.util.*;

public class ShadowAssignmentTrainer extends AbstractAssignmentTrainer {
    @Override
    protected boolean isForFp() {
        return false;
    }

    private static final int MAX_ASSIGNMENTS = 10_000;

    @Override
    protected void generateAssignmentsRecursive(Map<String, String> minionsToAssign,
                                                List<String> minionKeys, int index,
                                                Map<String, String> freeChars,
                                                AssignmentInfo current,
                                                List<AssignmentInfo> results,
                                                Map<String, Integer> remainingAssignmentLimits,
                                                GameState gameState) {
        generateAssignmentsRecursive(minionsToAssign, minionKeys, index, freeChars, current, results,
                remainingAssignmentLimits, gameState, MAX_ASSIGNMENTS);
    }

    private void generateAssignmentsRecursive(Map<String, String> minionsToAssign,
                                              List<String> minionKeys, int index,
                                              Map<String, String> freeChars,
                                              AssignmentInfo current,
                                              List<AssignmentInfo> results,
                                              Map<String, Integer> remainingAssignmentLimits,
                                              GameState gameState,
                                              int resultLimit) {
        if (results.size() >= resultLimit)
            return;

        if (index >= minionKeys.size()) {
            // Deep copy the current assignment
            AssignmentInfo copy = new AssignmentInfo(new HashMap<>(), new HashMap<>(), 0, 0);
            for (Map.Entry<String, List<String>> entry : current.blueprintAssignment().entrySet()) {
                copy.blueprintAssignment().put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            for (Map.Entry<String, List<String>> entry : current.physicalAssignment().entrySet()) {
                copy.physicalAssignment().put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            results.add(copy);
            return;
        }

        Map.Entry<String, String> currentMinion = new AbstractMap.SimpleEntry<>(minionKeys.get(index), minionsToAssign.get(minionKeys.get(index)));
        List<String> freeCharacterIds = freeChars.keySet().stream()
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
        if (freeCharacterIds.isEmpty())
            return;

        int branchesRemaining = freeCharacterIds.size();
        for (String freeCharacterId : freeCharacterIds) {
            int branchResultLimit = getBranchResultLimit(results.size(), resultLimit, branchesRemaining--);
            String blueprintId = freeChars.get(freeCharacterId);
            current.physicalAssignment().computeIfAbsent(freeCharacterId, ignored -> new ArrayList<>())
                    .add(currentMinion.getKey());
            current.blueprintAssignment().computeIfAbsent(blueprintId, ignored -> new ArrayList<>())
                    .add(currentMinion.getValue());
            generateAssignmentsRecursive(minionsToAssign, minionKeys, index + 1, freeChars, current, results,
                    remainingAssignmentLimits, gameState, branchResultLimit);
            current.physicalAssignment().get(freeCharacterId)
                    .remove(current.physicalAssignment().get(freeCharacterId).size() - 1);
            current.blueprintAssignment().get(blueprintId)
                    .remove(current.blueprintAssignment().get(blueprintId).size() - 1);
            if (results.size() >= resultLimit)
                return;
        }
    }

    private static int getBranchResultLimit(int currentSize, int resultLimit, int branchesRemaining) {
        int remainingResults = resultLimit - currentSize;
        return currentSize + (remainingResults + branchesRemaining - 1) / branchesRemaining;
    }
}
