package com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardStrategyFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.Test;
import smile.classification.Classifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StartingFellowshipCandidateFeaturesTest extends AbstractAtTest {
    @Test
    public void candidateFeaturesExposeCultureTeamAndDeckSynergyAndStartingCostPressure()
            throws CardNotFoundException {
        CardFeatures.init(_cardLibrary);
        String candidateId = "1_89";
        Culture culture = CardFeatures.getCardCulture(candidateId);
        int cultureIndex = List.of(Culture.values()).indexOf(culture);
        double[] state = new double[MovieTrilogyRLGameStateFeatures.STATE_FEATURE_COUNT];
        state[MovieTrilogyRLGameStateFeatures.SELF_IN_PLAY_CULTURE_FEATURE_OFFSET + cultureIndex] = 2;
        state[MovieTrilogyRLGameStateFeatures.SELF_DECK_CULTURE_FEATURE_OFFSET + cultureIndex] = 3;
        state[MovieTrilogyRLGameStateFeatures.SELF_IN_PLAY_STRATEGY_FEATURE_OFFSET
                + CardStrategyFeatures.COMBAT_BOOST] = 2;
        state[MovieTrilogyRLGameStateFeatures.RAW_TWILIGHT_POOL_FEATURE_INDEX] = 2;

        double[] cardFeatures = CardFeatures.getCardFeatures(candidateId, 0);
        cardFeatures[cardFeatures.length - CardStrategyFeatures.FEATURE_COUNT
                + CardStrategyFeatures.COMBAT_BOOST] = 1;
        StartingFellowshipTrainer trainer = new StartingFellowshipTrainer() {
            @Override
            protected double[] getCardVector(String blueprintId) {
                return cardFeatures;
            }
        };
        double[] result = trainer.getCandidateVector(state, candidateId);
        int featureOffset = state.length + cardFeatures.length + CardCandidateFeatures.INTERACTION_FEATURE_COUNT;

        assertEquals(1.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.CULTURE_ONE_HOT_OFFSET + cultureIndex], 0.0);
        assertEquals(2.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.EXISTING_CULTURE_SYNERGY_OFFSET + cultureIndex], 0.0);
        assertEquals(2.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.REMAINING_DECK_CULTURE_OFFSET + cultureIndex], 0.0);
        assertEquals(2.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.EXISTING_STRATEGY_SYNERGY_OFFSET
                + CardStrategyFeatures.COMBAT_BOOST], 0.0);
        assertEquals(Math.min(1.0, cardFeatures[17] * 2.0 / 16.0),
                result[featureOffset + StartingFellowshipCandidateFeatures.COST_PRESSURE_OFFSET], 0.0);
    }

    @Test
    public void legacyStateVectorsKeepCandidateCultureButOmitUnrecoverableSynergyContext()
            throws CardNotFoundException {
        CardFeatures.init(_cardLibrary);
        String candidateId = "1_89";
        Culture culture = CardFeatures.getCardCulture(candidateId);
        int cultureIndex = List.of(Culture.values()).indexOf(culture);
        double[] state = new double[MovieTrilogyRLGameStateFeatures.TACTICAL_CONTEXT_FEATURE_COUNT];
        double[] cardFeatures = CardFeatures.getCardFeatures(candidateId, 0);

        double[] result = StartingFellowshipCandidateFeatures.append(state, cardFeatures, candidateId);
        int featureOffset = state.length + cardFeatures.length + CardCandidateFeatures.INTERACTION_FEATURE_COUNT;

        assertEquals(1.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.CULTURE_ONE_HOT_OFFSET + cultureIndex], 0.0);
        assertEquals(0.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.EXISTING_CULTURE_SYNERGY_OFFSET + cultureIndex], 0.0);
        assertEquals(0.0, result[featureOffset
                + StartingFellowshipCandidateFeatures.COST_PRESSURE_OFFSET], 0.0);
    }

    @Test
    public void currentShippedModelUsesItsCompatibleFeaturePrefix() throws Exception {
        CardFeatures.init(_cardLibrary);
        Path modelDirectory = ModelIO.resolveModelDirectory(Path.of("..", "bot-models"), "movie");
        Path modelFile = modelDirectory.resolve("StartingFellowshipTrainer.model");
        assumeTrue("current model release is not available", Files.isRegularFile(modelFile));
        Classifier<double[]> model = ModelIO.loadModel(StartingFellowshipTrainer.class, modelDirectory);

        double[] state = new double[MovieTrilogyRLGameStateFeatures.STATE_FEATURE_COUNT];
        double[] candidate = new StartingFellowshipTrainer().getCandidateVector(state, "1_89");
        double[] compatibleCandidate = ModelIO.adaptModelInputVector(model, candidate,
                candidate.length - StartingFellowshipCandidateFeatures.FEATURE_COUNT);
        assertEquals(candidate.length - StartingFellowshipCandidateFeatures.FEATURE_COUNT,
                compatibleCandidate.length);
        model.predict(compatibleCandidate, new double[2]);

        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(decision.getText()).thenReturn("Starting fellowship");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"0"},
                "max", new String[]{"1"},
                "cardId", new String[]{"candidate"},
                "blueprintId", new String[]{"1_89"},
                "selectable", new String[]{"true"}));
        ModelRegistry registry = new ModelRegistry();
        registry.registerModel(StartingFellowshipTrainer.class, model);
        String answer = new StartingFellowshipTrainer().getAnswer(
                mock(GameState.class), decision, "~model-compatibility",
                (gameState, awaitingDecision, playerName) -> state, registry);
        assertNotNull(answer);
    }
}
