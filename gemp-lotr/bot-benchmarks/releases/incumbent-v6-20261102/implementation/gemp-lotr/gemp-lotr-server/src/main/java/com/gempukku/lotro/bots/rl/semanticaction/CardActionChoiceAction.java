package com.gempukku.lotro.bots.rl.semanticaction;

import com.alibaba.fastjson2.JSONObject;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CardActionChoiceAction implements SemanticAction {
    private static final String IN_PLAY_BLUEPRINT = "inPlay";

    private final String actionText;
    private final int selectedActionIndex;
    private final String sourceBlueprint;
    private int woundsOnSource;
    private String holderBlueprint;
    private final List<String> notChosenActions = new ArrayList<>();
    private final List<String> notChosenBlueprints = new ArrayList<>();
    private final String skirmishingFpBlueprintId;
    private final int remainingVitalityOnSkirmishFp;
    private final List<String> skirmishingMinionBlueprintIds = new ArrayList<>();
    private final List<Integer> remainingVitalityOnSkirmishingMinions = new ArrayList<>();
    private final boolean sourceInSkirmish;

    public CardActionChoiceAction(String answer, AwaitingDecision decision, GameState gameState) {
        if (answer == null || answer.isEmpty()) {
            actionText = null;
            selectedActionIndex = -1;
            sourceBlueprint = null;
            woundsOnSource = 0;
            holderBlueprint = null;
            skirmishingFpBlueprintId = null;
            remainingVitalityOnSkirmishFp = 0;
            sourceInSkirmish = false;
            String[] actionTexts = decision.getDecisionParameters().get("actionText");
            for (int i = 0; i < actionTexts.length; i++) {
                notChosenActions.add(actionTexts[i]);
                notChosenBlueprints.add(resolveSourceBlueprint(gameState, decision, i));
            }
        } else {
            String[] actionTexts = decision.getDecisionParameters().get("actionText");
            int selectedIndex = Integer.parseInt(answer);
            selectedActionIndex = selectedIndex;
            actionText = actionTexts[selectedIndex];
            sourceBlueprint = resolveSourceBlueprint(gameState, decision, selectedIndex);
            woundsOnSource = 0;
            holderBlueprint = null;
            for (PhysicalCard physicalCard : gameState.getInPlay()) {
                if (physicalCard.getCardId() == Integer.parseInt(decision.getDecisionParameters().get("cardId")[selectedIndex])) {
                    woundsOnSource = gameState.getWounds(physicalCard);
                    if (physicalCard.getAttachedTo() != null) {
                        holderBlueprint = physicalCard.getAttachedTo().getBlueprintId();
                        woundsOnSource = gameState.getWounds(physicalCard.getAttachedTo());
                    }
                    break;
                }
            }
            for (int i = 0; i < actionTexts.length; i++) {
                if (i != selectedIndex) {
                    notChosenActions.add(actionTexts[i]);
                    notChosenBlueprints.add(resolveSourceBlueprint(gameState, decision, i));
                }
            }

            if (gameState.getSkirmish() != null && gameState.getSkirmish().getFellowshipCharacter() != null && !gameState.getSkirmish().getShadowCharacters().isEmpty()) {
                int sourceId = Integer.parseInt(decision.getDecisionParameters().get("cardId")[Integer.parseInt(answer)]);
                boolean tmpSourceInSkirmish = gameState.getSkirmish().getFellowshipCharacter().getCardId() == sourceId;

                skirmishingFpBlueprintId = gameState.getSkirmish().getFellowshipCharacter().getBlueprintId();
                remainingVitalityOnSkirmishFp = gameState.getSkirmish().getFellowshipCharacter().getBlueprint().getVitality() - gameState.getWounds(gameState.getSkirmish().getFellowshipCharacter());
                for (PhysicalCard shadowCharacter : gameState.getSkirmish().getShadowCharacters()) {
                    skirmishingMinionBlueprintIds.add(shadowCharacter.getBlueprintId());
                    remainingVitalityOnSkirmishingMinions.add(shadowCharacter.getBlueprint().getVitality() - gameState.getWounds(shadowCharacter));
                    if (shadowCharacter.getCardId() == sourceId) {
                        tmpSourceInSkirmish = true;
                    }
                }

                sourceInSkirmish = tmpSourceInSkirmish;
            } else {
                skirmishingFpBlueprintId = null;
                remainingVitalityOnSkirmishFp = 0;
                sourceInSkirmish = false;
            }
        }
    }

    public static String resolveSourceBlueprint(GameState gameState, AwaitingDecision decision, int actionIndex) {
        String[] blueprintIds = decision.getDecisionParameters().get("blueprintId");
        if (blueprintIds != null && actionIndex >= 0 && actionIndex < blueprintIds.length) {
            String blueprintId = blueprintIds[actionIndex];
            if (blueprintId != null && !IN_PLAY_BLUEPRINT.equals(blueprintId))
                return blueprintId;
        }

        String[] cardIds = decision.getDecisionParameters().get("cardId");
        if (cardIds == null || actionIndex < 0 || actionIndex >= cardIds.length) {
            throw new IllegalArgumentException("Missing card ID for card action at index " + actionIndex);
        }
        PhysicalCard sourceCard = gameState.findCardById(Integer.parseInt(cardIds[actionIndex]));
        return sourceCard == null ? null : sourceCard.getBlueprintId();
    }

    public CardActionChoiceAction(String actionText, String sourceBlueprint, int woundsOnSource, String holderBlueprint,
                                  String[] notChosenActions, String[] notChosenBlueprintIds,
                                  String skirmishingFpBlueprintId, String[] skirmishingMinionBlueprintIds,
                                  boolean sourceInSkirmish, int remainingVitalityOnSkirmishFp, Integer[] remainingVitalityOnSkirmishingMinions,
                                  int selectedActionIndex) {
        this.actionText = actionText;
        this.selectedActionIndex = selectedActionIndex;
        this.sourceBlueprint = sourceBlueprint;
        this.woundsOnSource = woundsOnSource;
        this.holderBlueprint = holderBlueprint;
        this.notChosenActions.addAll(Arrays.asList(notChosenActions));
        this.notChosenBlueprints.addAll(Arrays.asList(notChosenBlueprintIds));
        this.skirmishingFpBlueprintId = skirmishingFpBlueprintId;
        this.skirmishingMinionBlueprintIds.addAll(Arrays.asList(skirmishingMinionBlueprintIds));
        this.sourceInSkirmish = sourceInSkirmish;
        this.remainingVitalityOnSkirmishFp = remainingVitalityOnSkirmishFp;
        this.remainingVitalityOnSkirmishingMinions.addAll(Arrays.asList(remainingVitalityOnSkirmishingMinions));
    }

    public String getActionText() {
        return actionText;
    }

    public int getSelectedActionIndex() {
        return selectedActionIndex;
    }

    public String getSourceBlueprint() {
        return sourceBlueprint;
    }

    public List<String> getNotChosenActions() {
        return notChosenActions;
    }

    public List<String> getNotChosenBlueprints() {
        return notChosenBlueprints;
    }

    public int getWoundsOnSource() {
        return woundsOnSource;
    }

    public String getHolderBlueprint() {
        return holderBlueprint;
    }

    public String getSkirmishingFpBlueprintId() {
        return skirmishingFpBlueprintId;
    }

    public List<String> getSkirmishingMinionBlueprintIds() {
        return skirmishingMinionBlueprintIds;
    }

    public boolean isSourceInSkirmish() {
        return sourceInSkirmish;
    }

    public int getRemainingVitalityOnSkirmishFp() {
        return remainingVitalityOnSkirmishFp;
    }

    public List<Integer> getRemainingVitalityOnSkirmishingMinions() {
        return remainingVitalityOnSkirmishingMinions;
    }

    @Override
    public String toDecisionString(AwaitingDecision decision, GameState gameState) {
        if (decision.getDecisionType() != AwaitingDecisionType.CARD_ACTION_CHOICE) {
            throw new IllegalArgumentException("Wrong decision type.");
        }

        if (actionText == null) {
            return "";
        }

        String[] actionTexts = decision.getDecisionParameters().get("actionText");
        if (selectedActionIndex >= 0) {
            if (selectedActionIndex >= actionTexts.length || !actionText.equals(actionTexts[selectedActionIndex])) {
                throw new IllegalArgumentException("Selected option not found.");
            }
            return String.valueOf(selectedActionIndex);
        }

        for (int i = 0; i < actionTexts.length; i++) {
            if (actionTexts[i].equals(actionText)) {
                return String.valueOf(i);
            }
        }
        throw new IllegalArgumentException("Option not found.");
    }

    @Override
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("type", "CardActionChoiceAction");
        obj.put("actionText", actionText);
        obj.put("selectedActionIndex", selectedActionIndex);
        obj.put("sourceBlueprint", sourceBlueprint);
        obj.put("woundsOnSource", woundsOnSource);
        obj.put("holderBlueprint", holderBlueprint);
        obj.put("notChosenActions", notChosenActions.toArray(new String[0]));
        obj.put("notChosenBlueprints", notChosenBlueprints.toArray(new String[0]));
        obj.put("skirmishingFpBlueprintId", skirmishingFpBlueprintId);
        obj.put("skirmishingMinionBlueprintIds", skirmishingMinionBlueprintIds.toArray(new String[0]));
        obj.put("sourceInSkirmish", sourceInSkirmish);
        obj.put("remainingVitalityOnSkirmishFp", remainingVitalityOnSkirmishFp);
        obj.put("remainingVitalityOnSkirmishingMinions", remainingVitalityOnSkirmishingMinions.toArray(new Integer[0]));
        return obj;
    }

    public static CardActionChoiceAction fromJson(JSONObject obj) {
        String actionText = obj.getString("actionText");
        String sourceBlueprint = obj.getString("sourceBlueprint");
        int woundsOnSource = obj.getIntValue("woundsOnSource");
        String holderBlueprint = obj.getString("holderBlueprint");
        String[] notChosenActions = obj.getObject("notChosenActions", String[].class);
        String[] notChosenBlueprints = obj.getObject("notChosenBlueprints", String[].class);
        String skirmishingFpBlueprintId = obj.getString("skirmishingFpBlueprintId");
        String[] skirmishingMinionBlueprintIds = obj.getObject("skirmishingMinionBlueprintIds", String[].class);
        int remainingVitalityOnSkirmishFp = obj.getIntValue("remainingVitalityOnSkirmishFp");
        Integer[] remainingVitalityOnSkirmishingMinions = obj.getObject("remainingVitalityOnSkirmishingMinions", Integer[].class);
        boolean sourceInSkirmish = obj.getBoolean("sourceInSkirmish");
        Integer selectedActionIndex = obj.getInteger("selectedActionIndex");
        return new CardActionChoiceAction(actionText, sourceBlueprint, woundsOnSource, holderBlueprint, notChosenActions,
                notChosenBlueprints, skirmishingFpBlueprintId, skirmishingMinionBlueprintIds, sourceInSkirmish,
                remainingVitalityOnSkirmishFp, remainingVitalityOnSkirmishingMinions,
                selectedActionIndex == null ? -1 : selectedActionIndex);
    }
}
