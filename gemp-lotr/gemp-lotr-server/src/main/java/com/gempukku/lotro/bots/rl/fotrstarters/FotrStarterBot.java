package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.AssignmentLegality;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.bots.rl.*;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.CardFromDiscardTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.GenericArbitraryCardsTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.StartingFellowshipTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.assignment.AbstractAssignmentTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.assignment.FpAssignmentTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.assignment.ShadowAssignmentTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.*;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.*;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.BurdenTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.GenericIntegerTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.AnotherMoveTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.GenericMultipleChoiceTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.GoFirstTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.MulliganTrainer;
import com.gempukku.lotro.bots.rl.semanticaction.*;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.Skirmish;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FotrStarterBot extends RandomDecisionBot implements LearningBotPlayer {
    private static final Logger LOG = LogManager.getLogger(FotrStarterBot.class);
    private static final double TERMINAL_REWARD_DISCOUNT = 0.995;
    private final List<DecisionAnswerer> cardSelectionTrainers = List.of(
            new ReconcileTrainer(),
            new SanctuaryTrainer(),
            new ArcheryWoundTrainer(),
            new AttachItemTrainer(),
            new SkirmishOrderTrainer(),
            new HealTrainer(),
            new DiscardFromHandTrainer(),
            new ExertTrainer(),
            new DiscardFromPlayTrainer(),
            new PlayFromHandTrainer()
    );

    private final List<DecisionAnswerer> fallBackTrainers = List.of(
            new FallBackCardSelectionTrainer() // card selection
    );

    private final List<DecisionAnswerer> integerTrainers = List.of(
            new BurdenTrainer(),
            new GenericIntegerTrainer()
    );

    private final List<DecisionAnswerer> multipleChoiceTrainers = List.of(
            new GoFirstTrainer(),
            new MulliganTrainer(),
            new AnotherMoveTrainer(),
            new GenericMultipleChoiceTrainer()
    );

    private final List<DecisionAnswerer> actionChoiceTrainers = List.of(
            new GenericActionChoiceTrainer()
    );

    private final List<DecisionAnswerer> arbitraryCardsTrainers = List.of(
            new CardFromDiscardTrainer(),
            new StartingFellowshipTrainer(),
            new GenericArbitraryCardsTrainer()
    );

    private final List<DecisionAnswerer> cardActionTrainers = List.of(
            new FellowshipCardActionAnswerer(),
            new ShadowCardActionAnswerer(),
            new ManeuverCardActionAnswerer(),
            new ArcheryCardActionAnswerer(),
            new AssignmentCardActionAnswerer(),
            new SkirmishFpCardActionAnswerer(),
            new SkirmishShadowCardActionAnswerer(),
            new RegroupCardActionAnswerer(),
            new OptionalResponsesCardActionTrainer(),
            new GenericCardActionChoiceTrainer()
    );

    private final List<AbstractAssignmentTrainer> assignmentTrainers = List.of(
            new ShadowAssignmentTrainer(),
            new FpAssignmentTrainer()
    );

    private final RLGameStateFeatures features;
    private final ModelRegistry modelRegistry;
    private final ReplayBuffer replayBuffer;
    private final TrainingEventReporter eventReporter;
    private final double explorationRate;
    private final double explorationExampleWeight;
    private final double episodeExampleWeight;
    private final Set<Class<?>> trainerFailuresLogged = ConcurrentHashMap.newKeySet();

    private CardActionSelectionDecision lastDecision = null;
    private int decisionRepeat = 0;
    private String lastAction = null;
    private int answerRepeat = 0;
    private String gameId;

    private final List<LearningStep> episodeSteps = new ArrayList<>();

    public FotrStarterBot(RLGameStateFeatures features, String playerId, ModelRegistry modelRegistry, ReplayBuffer replayBuffer) {
        this(features, playerId, modelRegistry, replayBuffer, null);
    }

    public FotrStarterBot(
            RLGameStateFeatures features,
            String playerId,
            ModelRegistry modelRegistry,
            ReplayBuffer replayBuffer,
            TrainingEventReporter eventReporter) {
        this(features, playerId, modelRegistry, replayBuffer, eventReporter, 0.0, 0.25, 1.0);
    }

    public FotrStarterBot(
            RLGameStateFeatures features,
            String playerId,
            ModelRegistry modelRegistry,
            ReplayBuffer replayBuffer,
            TrainingEventReporter eventReporter,
            double explorationRate,
            double explorationExampleWeight,
            double episodeExampleWeight) {
        super(playerId);
        if (!Double.isFinite(explorationRate) || explorationRate < 0.0 || explorationRate > 1.0)
            throw new IllegalArgumentException("Exploration rate must be between 0 and 1");
        if (!Double.isFinite(explorationExampleWeight)
                || explorationExampleWeight <= 0.0 || explorationExampleWeight > 1.0)
            throw new IllegalArgumentException("Exploration example weight must be greater than 0 and at most 1");
        if (!Double.isFinite(episodeExampleWeight)
                || episodeExampleWeight < 0.0 || episodeExampleWeight > 1.0
                || replayBuffer != null && episodeExampleWeight == 0.0)
            throw new IllegalArgumentException("Episode example weight must be between 0 and 1");
        this.features = features;
        this.modelRegistry = modelRegistry;
        this.replayBuffer = replayBuffer;
        this.eventReporter = eventReporter;
        this.explorationRate = explorationRate;
        this.explorationExampleWeight = explorationExampleWeight;
        this.episodeExampleWeight = episodeExampleWeight;
    }

    @Override
    public String chooseAction(GameState gameState, AwaitingDecision decision) {
        return chooseAction(gameState, decision, AssignmentLegality.UNCHECKED);
    }

    @Override
    public String chooseAction(GameState gameState, AwaitingDecision decision, AssignmentLegality legality) {
        double[] stateVector = features.extractFeatures(gameState, decision, getName());

        boolean exploratory = replayBuffer != null
                && ThreadLocalRandom.current().nextDouble() < explorationRate;
        SemanticAction action = chooseSemanticAction(gameState, decision, legality, exploratory);

        if (replayBuffer != null) {
            LearningStep step = new LearningStep(
                    stateVector, action, getName().equals(gameState.getCurrentPlayerId()), decision, gameId);
            step.trainingWeight = episodeExampleWeight
                    * (exploratory ? explorationExampleWeight : 1.0);
            episodeSteps.add(step);
        }

        return action.toDecisionString(decision, gameState);
    }

    private SemanticAction chooseSemanticAction(
            GameState gameState,
            AwaitingDecision decision,
            AssignmentLegality legality,
            boolean exploratory) {
        String action = exploratory
                ? super.chooseAction(gameState, decision, legality)
                : switch (decision.getDecisionType()) {
                    case INTEGER -> chooseIntegerAction(gameState, decision);
                    case MULTIPLE_CHOICE -> chooseMultipleChoiceAction(gameState, decision);
                    case ARBITRARY_CARDS -> chooseArbitraryCardsAction(gameState, decision);
                    case CARD_ACTION_CHOICE -> chooseCardActionChoice(gameState, decision);
                    case ACTION_CHOICE -> chooseActionChoice(gameState, decision);
                    case CARD_SELECTION -> chooseCardSelectionAction(gameState, decision);
                    case ASSIGN_MINIONS -> chooseAssignmentAction(gameState, decision, legality);
                };

        return switch (decision.getDecisionType()) {
            case INTEGER -> new IntegerChoiceAction(Integer.parseInt(action));
            case MULTIPLE_CHOICE -> new MultipleChoiceAction(action, decision);
            case ARBITRARY_CARDS -> new ChooseFromArbitraryCardsAction(action, decision);
            case CARD_ACTION_CHOICE -> new CardActionChoiceAction(action, decision, gameState);
            case ACTION_CHOICE -> new ActionChoiceAction(action, decision);
            case CARD_SELECTION -> {
                if (gameState.getCurrentPhase().equals(Phase.SKIRMISH)) {
                    yield new CardSelectionAssignedAction(action, decision, gameState);
                }
                yield new CardSelectionAction(action, decision, gameState);
            }
            case ASSIGN_MINIONS -> new AssignMinionsAction(action, decision, gameState, gameState.getCurrentPlayerId().equals(getName()));
        };
    }

    private String chooseAssignmentAction(GameState gameState, AwaitingDecision decision, AssignmentLegality legality) {
        for (AbstractAssignmentTrainer trainer : assignmentTrainers) {
            if (trainer.appliesTo(gameState, decision, getName())) {
                return answerAssignmentTrainer(trainer, gameState, decision, legality);
            }
        }

        System.out.println("Unknown assign minions decision: "
                + gameState.getCurrentPhase() + " - "
                + decision.getText()
                + "; freeCharacters=" + Arrays.toString(decision.getDecisionParameters().get("freeCharacters"))
                + "; minions=" + Arrays.toString(decision.getDecisionParameters().get("minions")));
        return super.chooseAction(gameState, decision, legality);
    }

    private String chooseActionChoice(GameState gameState, AwaitingDecision decision) {
        for (DecisionAnswerer trainer : actionChoiceTrainers) {
            if (trainer.appliesTo(gameState, decision, getName()))
                return answerTrainer(trainer, gameState, decision);
        }
        return super.chooseAction(gameState, decision);
    }

    private String chooseCardActionChoice(GameState gameState, AwaitingDecision decision) {
        String[] actionIds = decision.getDecisionParameters().get("actionId");
        if (actionIds == null || actionIds.length == 0) {
            // No actions available: must pass
            return "";
        }

        String chosenAnswer = null;
        for (DecisionAnswerer trainer : cardActionTrainers) {
            if (!trainer.appliesTo(gameState, decision, getName()))
                continue;
            if (trainer instanceof AbstractCardActionAnswerer composite
                    && !composite.hasAvailableModel(gameState, decision, getName(), modelRegistry))
                continue;
            if (trainer instanceof Trainer modelTrainer
                    && !(trainer instanceof GenericCardActionChoiceTrainer)
                    && !modelRegistry.hasModel(modelTrainer.getClass()))
                continue;
            chosenAnswer = answerTrainer(trainer, gameState, decision);
            break;
        }
        // Loop prevention
        if (chosenAnswer != null) {
            if (decision.equals(lastDecision) && chosenAnswer.equals(lastAction)) {
                answerRepeat++;
                decisionRepeat++;
                if (answerRepeat >= 3) {
                    answerRepeat = 0;
                    lastAction = null;
                    return "";
                }
            } else if (decision.equals(lastDecision)) {
                decisionRepeat++;
                if (decisionRepeat >= 6) {
                    answerRepeat = 0;
                    lastAction = null;
                    return "";
                }
            } else {
                lastDecision = (CardActionSelectionDecision) decision;
                decisionRepeat = 0;
                answerRepeat = 0;
                lastAction = chosenAnswer;
            }
            return chosenAnswer;
        }

        System.out.println("Unknown card action decision: "
                + gameState.getCurrentPhase() + " - "
                + decision.getText()
                + "; actionId=" + Arrays.toString(decision.getDecisionParameters().get("actionId"))
                + "; cardId=" + Arrays.toString(decision.getDecisionParameters().get("cardId"))
                + "; blueprintId=" + Arrays.toString(decision.getDecisionParameters().get("blueprintId"))
                + "; actionText=" + Arrays.toString(decision.getDecisionParameters().get("actionText")));
        return super.chooseAction(gameState, decision);
    }

    private String chooseArbitraryCardsAction(GameState gameState, AwaitingDecision decision) {
        Map<String, String[]> params = decision.getDecisionParameters();
        String[] cardIds = params.get("cardId");
        String[] selectable = params.get("selectable");
        if (cardIds == null || selectable == null || cardIds.length != selectable.length)
            throw new IllegalArgumentException("Invalid card options for arbitrary-card decision: " + decision.getText());

        int min = params.containsKey("min") ? Integer.parseInt(params.get("min")[0]) : 0;
        int max = params.containsKey("max") ? Integer.parseInt(params.get("max")[0]) : cardIds.length;
        if (min < 0 || max < min)
            throw new IllegalArgumentException("Invalid selection range for arbitrary-card decision: "
                    + min + ".." + max);

        // Collect all selectable indices
        List<Integer> selectableIndices = new ArrayList<>();
        for (int i = 0; i < selectable.length; i++) {
            if (Boolean.parseBoolean(selectable[i])) {
                selectableIndices.add(i);
            }
        }
        if (selectableIndices.isEmpty() || max == 0) {
            if (min > 0)
                throw new IllegalStateException("No selectable cards satisfy the minimum for: " + decision.getText());
            return "";
        }

        for (DecisionAnswerer trainer : arbitraryCardsTrainers) {
            if (!trainer.appliesTo(gameState, decision, getName()))
                continue;
            if (trainer instanceof Trainer modelTrainer
                    && !(trainer instanceof GenericArbitraryCardsTrainer)
                    && !modelRegistry.hasModel(modelTrainer.getClass()))
                continue;
            return answerTrainer(trainer, gameState, decision);
        }

        // Fallback to random decision
        System.out.println("Unknown arbitrary card selection action: "
                + gameState.getCurrentPhase() + " - "
                + decision.getText()
                + " (" + decision.getDecisionParameters().get("min")[0]
                + ";" + decision.getDecisionParameters().get("max")[0] + ") "
                + "cards=" + Arrays.toString(decision.getDecisionParameters().get("blueprintId"))
                + ", selectable=" + Arrays.toString(decision.getDecisionParameters().get("selectable")));
        return super.chooseAction(gameState, decision);
    }

    private String chooseCardSelectionAction(GameState gameState, AwaitingDecision decision) {
        int min = Integer.parseInt(decision.getDecisionParameters().get("min")[0]);
        int max = Integer.parseInt(decision.getDecisionParameters().get("max")[0]);
        List<String> cardIds = Arrays.stream(decision.getDecisionParameters().get("cardId")).toList();

        if (min == max && min == cardIds.size()) {
            // Need to choose all
            return String.join(",", cardIds);
        }

        // Choose the one skirmishing
        if (gameState.getSkirmish() != null &&
                gameState.getSkirmish().getFellowshipCharacter() != null &&
                gameState.getSkirmish().getShadowCharacters() != null &&
                !gameState.getSkirmish().getShadowCharacters().isEmpty()
                && min == 1 && max == 1) {
            Skirmish skirmish = gameState.getSkirmish();
            for (String cardId : cardIds) {
                if (skirmish.getFellowshipCharacter().getCardId() == Integer.parseInt(cardId)) {
                    return cardId;
                }
                for (PhysicalCard shadowCharacter : skirmish.getShadowCharacters()) {
                    if (shadowCharacter.getCardId() == Integer.parseInt(cardId)) {
                        return cardId;
                    }
                }
            }
            // Skirmishing character cannot be chosen, choose whatever
            return super.chooseAction(gameState, decision);
        }

        for (DecisionAnswerer trainer : cardSelectionTrainers) {
            if (trainer.appliesTo(gameState, decision, getName())) {
                return answerTrainer(trainer, gameState, decision);
            }
        }

        // Last fallback to trainer
        for (DecisionAnswerer trainer : fallBackTrainers) {
            if (trainer.appliesTo(gameState, decision, getName())) {
                return answerTrainer(trainer, gameState, decision);
            }
        }

        // Fallback to random decision
        System.out.println("Unknown card selection action: " + gameState.getCurrentPhase() + " - " + decision.getText() + " (" + decision.getDecisionParameters().get("min")[0] + ";" + decision.getDecisionParameters().get("max")[0] + ") " + Arrays.toString(decision.getDecisionParameters().get("cardId")));
        return super.chooseAction(gameState, decision);
    }

    private String chooseIntegerAction(GameState gameState, AwaitingDecision decision) {
        for (DecisionAnswerer trainer : integerTrainers) {
            if (trainer.appliesTo(gameState, decision, getName())) {
                return answerTrainer(trainer, gameState, decision);
            }
        }
        if (decision.getText().contains("how many to spot")) {
            return decision.getDecisionParameters().get("max")[0];
        }

        Map<String, String[]> parameters = decision.getDecisionParameters();
        System.out.println("Unknown integer action: " + decision.getText()
                + " [range=" + firstParameter(parameters.get("min"))
                + ".." + firstParameter(parameters.get("max"))
                + "]; using random fallback");
        return super.chooseAction(gameState, decision);
    }

    private static String firstParameter(String[] values) {
        return values == null || values.length == 0 ? "unspecified" : values[0];
    }

    private String chooseMultipleChoiceAction(GameState gameState, AwaitingDecision decision) {
        String[] options = decision.getDecisionParameters().get("results");
        for (DecisionAnswerer trainer : multipleChoiceTrainers) {
            if (trainer.appliesTo(gameState, decision, getName())) {
                return answerTrainer(trainer, gameState, decision);
            }
        }
        if (List.of(options).contains("Heal a companion") && List.of(options).contains("Remove a Shadow condition from a companion")) {
            return new MultipleChoiceAction("Heal a companion").toDecisionString(decision, gameState);
        }
        if (List.of(options).contains("Exert Frodo") && List.of(options).contains("Exert 2 other companions")) {
            return new MultipleChoiceAction("Exert Frodo").toDecisionString(decision, gameState);
        }

        System.out.println("Unknown multiple choice action: " + decision.getText() + " - " + Arrays.toString(options));
        return super.chooseAction(gameState, decision);
    }

    private String answerAssignmentTrainer(
            AbstractAssignmentTrainer trainer,
            GameState gameState,
            AwaitingDecision decision,
            AssignmentLegality legality) {
        if (!hasModel(trainer))
            return super.chooseAction(gameState, decision, legality);
        try {
            return trainer.getAnswer(gameState, decision, getName(), features, modelRegistry, legality);
        } catch (RuntimeException | LinkageError e) {
            if (isInterruptedFailure(e)) {
                Thread.currentThread().interrupt();
                throw e;
            }
            logTrainerFailure(trainer, decision, e);
            return super.chooseAction(gameState, decision, legality);
        }
    }

    private String answerTrainer(DecisionAnswerer trainer, GameState gameState, AwaitingDecision decision) {
        if (!hasModel(trainer))
            return super.chooseAction(gameState, decision);
        try {
            return trainer.getAnswer(gameState, decision, getName(), features, modelRegistry);
        } catch (RuntimeException | LinkageError e) {
            if (isInterruptedFailure(e)) {
                Thread.currentThread().interrupt();
                throw e;
            }
            logTrainerFailure(trainer, decision, e);
            return super.chooseAction(gameState, decision);
        }
    }

    private static boolean isInterruptedFailure(Throwable failure) {
        if (Thread.currentThread().isInterrupted())
            return true;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException)
                return true;
        }
        return false;
    }

    private boolean hasModel(DecisionAnswerer answerer) {
        return !(answerer instanceof Trainer trainer) || modelRegistry.hasModel(trainer.getClass());
    }

    Set<Class<? extends Trainer>> getRoutedTrainerTypes() {
        Set<Class<? extends Trainer>> trainerTypes = new HashSet<>();
        addTrainerTypes(trainerTypes, cardSelectionTrainers);
        addTrainerTypes(trainerTypes, fallBackTrainers);
        addTrainerTypes(trainerTypes, integerTrainers);
        addTrainerTypes(trainerTypes, multipleChoiceTrainers);
        addTrainerTypes(trainerTypes, actionChoiceTrainers);
        addTrainerTypes(trainerTypes, arbitraryCardsTrainers);
        addTrainerTypes(trainerTypes, cardActionTrainers);
        addTrainerTypes(trainerTypes, assignmentTrainers);
        for (DecisionAnswerer answerer : cardActionTrainers) {
            if (answerer instanceof AbstractCardActionAnswerer composite) {
                for (AbstractPlayUseCardTrainer trainer : composite.getRoutedTrainers())
                    trainerTypes.add(trainer.getClass());
            }
        }
        return Set.copyOf(trainerTypes);
    }

    private static void addTrainerTypes(
            Set<Class<? extends Trainer>> trainerTypes,
            List<? extends DecisionAnswerer> answerers) {
        for (DecisionAnswerer answerer : answerers) {
            if (answerer instanceof Trainer trainer)
                trainerTypes.add(trainer.getClass());
        }
    }

    private void logTrainerFailure(DecisionAnswerer trainer, AwaitingDecision decision, Throwable failure) {
        if (trainerFailuresLogged.add(trainer.getClass())) {
            LOG.warn("Bot {} fell back to a base decision for {} after {} failed",
                    getName(), decision.getText(), trainer.getClass().getSimpleName(), failure);
            if (eventReporter != null)
                eventReporter.recordEvent(Severity.WARNING, "decision-model-fallback",
                        trainer.getClass().getSimpleName(),
                        "Bot " + getName() + " fell back to a base decision for \""
                                + decision.getText() + "\" after the trainer failed.", failure);
        }
    }

    @Override
    public void observe(GameState gameState, AwaitingDecision decision, String playerId, String chosenAction, double reward, boolean terminal) {
        // At this stage, we assume `chosenAction` was already used to resolve game state,
        // and we just want to record the final outcome of this decision if needed.
        // Right now, we store full episode and use reward only in `endEpisode`.
    }

    @Override
    public void beginEpisode(String gameId) {
        this.gameId = gameId;
    }

    @Override
    public void endEpisode(double reward) {
        assignTerminalRewardCredit(episodeSteps, reward);
        if (replayBuffer != null) {
            replayBuffer.addEpisode(new ArrayList<>(episodeSteps));
        }
        episodeSteps.clear();
    }

    static void assignTerminalRewardCredit(List<LearningStep> steps, double reward) {
        for (int index = 0; index < steps.size(); index++) {
            LearningStep step = steps.get(index);
            step.reward = reward;
            step.trainingWeight *= Math.pow(TERMINAL_REWARD_DISCOUNT, steps.size() - index - 1);
        }
    }

    @Override
    public void abortEpisode() {
        episodeSteps.clear();
        gameId = null;
    }

    @Override
    public void discardLastDecision() {
        if (!episodeSteps.isEmpty())
            episodeSteps.remove(episodeSteps.size() - 1);
    }
}
