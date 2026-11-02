package com.gempukku.lotro.bots.rl.semanticaction;

import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.bots.rl.fotrstarters.SkirmishOrderAssignment;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CardSelectionAssignedAction extends CardSelectionAction {
    private final List<Integer> minionsOnChosen = new ArrayList<>();
    private final List<Integer> strengthOfMinionsOnChosen = new ArrayList<>();
    private final List<String> assignedFellowshipBlueprintIds = new ArrayList<>();
    private final List<Integer> woundsOnAssignedFellowship = new ArrayList<>();
    private final String physicalAnswer;

    public CardSelectionAssignedAction(String answer, AwaitingDecision decision, GameState gameState) {
        super(answer, decision, gameState);
        physicalAnswer = answer;
        if (answer.isEmpty())
            return;

        String[] individualCards = answer.split(",");

        for (String individualCard : individualCards) {
            if (individualCard.isEmpty())
                continue;
            SkirmishOrderAssignment.findForChoice(gameState.getAssignments(), Integer.parseInt(individualCard))
                    .ifPresent(assignment -> {
                        var fellowshipCharacter = assignment.getFellowshipCharacter();
                        assignedFellowshipBlueprintIds.add(fellowshipCharacter.getBlueprintId());
                        woundsOnAssignedFellowship.add(gameState.getWounds(fellowshipCharacter));
                        minionsOnChosen.add(assignment.getShadowCharacters().size());
                        strengthOfMinionsOnChosen.add(assignment.getShadowCharacters().stream()
                                .mapToInt(value -> value.getBlueprint().getStrength()).sum());
                    });
        }
    }

    public CardSelectionAssignedAction(String[] chosenBlueprintIds, String[] notChosenBlueprintIds, Integer[] woundsOnChosen, Integer[] woundsOnNotChosen, String zone, Integer[] minionsOnChosen, Integer[] strengthOfMinionsOnChosen) {
        this(chosenBlueprintIds, notChosenBlueprintIds, woundsOnChosen, woundsOnNotChosen, zone,
                minionsOnChosen, strengthOfMinionsOnChosen, new String[0], new Integer[0]);
    }

    public CardSelectionAssignedAction(String[] chosenBlueprintIds, String[] notChosenBlueprintIds,
                                       Integer[] woundsOnChosen, Integer[] woundsOnNotChosen, String zone,
                                       Integer[] minionsOnChosen, Integer[] strengthOfMinionsOnChosen,
                                       String[] assignedFellowshipBlueprintIds,
                                       Integer[] woundsOnAssignedFellowship) {
        super(chosenBlueprintIds, notChosenBlueprintIds, woundsOnChosen, woundsOnNotChosen, zone);
        physicalAnswer = null;
        this.minionsOnChosen.addAll(Arrays.asList(minionsOnChosen));
        this.strengthOfMinionsOnChosen.addAll(Arrays.asList(strengthOfMinionsOnChosen));
        this.assignedFellowshipBlueprintIds.addAll(Arrays.asList(assignedFellowshipBlueprintIds));
        this.woundsOnAssignedFellowship.addAll(Arrays.asList(woundsOnAssignedFellowship));
        if (this.assignedFellowshipBlueprintIds.size() != this.woundsOnAssignedFellowship.size())
            throw new IllegalArgumentException("Skirmish assignment context has different blueprint and wound counts");
    }

    public List<Integer> getMinionsOnChosen() {
        return minionsOnChosen;
    }

    public List<Integer> getStrengthOfMinionsOnChosen() {
        return strengthOfMinionsOnChosen;
    }

    public List<String> getAssignedFellowshipBlueprintIds() {
        return assignedFellowshipBlueprintIds;
    }

    public List<Integer> getWoundsOnAssignedFellowship() {
        return woundsOnAssignedFellowship;
    }

    @Override
    public String toDecisionString(AwaitingDecision decision, GameState gameState) {
        if (decision.getDecisionType() != AwaitingDecisionType.CARD_SELECTION)
            throw new IllegalArgumentException("Wrong decision type.");
        if (physicalAnswer == null)
            return super.toDecisionString(decision, gameState);

        String[] selectedIds = physicalAnswer.isEmpty() ? new String[0] : physicalAnswer.split(",");
        String[] parameters = decision.getDecisionParameters().get("min");
        int minimum = Integer.parseInt(parameters[0]);
        int maximum = Integer.parseInt(decision.getDecisionParameters().get("max")[0]);
        if (selectedIds.length < minimum || selectedIds.length > maximum)
            throw new IllegalArgumentException("Chosen number out of bounds.");

        List<String> availableIds = Arrays.asList(decision.getDecisionParameters().get("cardId"));
        for (String selectedId : selectedIds) {
            if (!availableIds.contains(selectedId))
                throw new IllegalArgumentException("Selected physical card is not present in the decision.");
        }
        if (Arrays.stream(selectedIds).distinct().count() != selectedIds.length)
            throw new IllegalArgumentException("A physical card cannot be selected more than once.");
        return physicalAnswer;
    }

    @Override
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("type", "CardSelectionAssignedAction");
        obj.put("chosenBlueprints", chosenBlueprintIds.toArray());
        obj.put("woundsOnChosen", woundsOnChosen.toArray());
        obj.put("notChosenBlueprints", notChosenBlueprintIds.toArray());
        obj.put("woundsOnNotChosen", woundsOnNotChosen.toArray());
        obj.put("zone", zoneString);
        obj.put("minionsOnChosen", minionsOnChosen.toArray());
        obj.put("strengthOfMinionsOnChosen", strengthOfMinionsOnChosen.toArray());
        obj.put("assignedFellowshipBlueprintIds", assignedFellowshipBlueprintIds.toArray());
        obj.put("woundsOnAssignedFellowship", woundsOnAssignedFellowship.toArray());
        return obj;
    }

    public static CardSelectionAssignedAction fromJson(JSONObject obj) {
        String[] assignedFellowshipBlueprintIds = obj.getObject("assignedFellowshipBlueprintIds", String[].class);
        Integer[] woundsOnAssignedFellowship = obj.getObject("woundsOnAssignedFellowship", Integer[].class);
        String[] chosenBlueprintIds = obj.getObject("chosenBlueprints", String[].class);
        String[] notChosenBlueprintIds = obj.getObject("notChosenBlueprints", String[].class);
        Integer[] woundsOnChosen = obj.getObject("woundsOnChosen", Integer[].class);
        Integer[] woundsOnNotChosen = obj.getObject("woundsOnNotChosen", Integer[].class);
        Integer[] minionsOnChosen = obj.getObject("minionsOnChosen", Integer[].class);
        Integer[] strengthOfMinionsOnChosen = obj.getObject("strengthOfMinionsOnChosen", Integer[].class);

        if (assignedFellowshipBlueprintIds == null && woundsOnAssignedFellowship == null)
            return new CardSelectionAssignedAction(chosenBlueprintIds, notChosenBlueprintIds, woundsOnChosen,
                    woundsOnNotChosen, obj.getString("zone"), minionsOnChosen, strengthOfMinionsOnChosen);
        if (assignedFellowshipBlueprintIds == null || woundsOnAssignedFellowship == null)
            throw new IllegalArgumentException("Skirmish assignment context is incomplete");

        return new CardSelectionAssignedAction(chosenBlueprintIds, notChosenBlueprintIds, woundsOnChosen,
                woundsOnNotChosen, obj.getString("zone"), minionsOnChosen, strengthOfMinionsOnChosen,
                assignedFellowshipBlueprintIds, woundsOnAssignedFellowship);
    }
}
