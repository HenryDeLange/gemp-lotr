package com.gempukku.lotro.bots;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.simulation.GameResult;
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.simulation.SimulationOutcome;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotNull;

public class MovieBlockSimulationTest extends AbstractAtTest {
    @Test
    public void completesBackToBackGamesWithBasePolicyInAllMovieBlocks() {
        CardFeatures.init(_cardLibrary);

        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var decks = MovieBlockDeckFactory.createBotDecks(formatCode, format, _cardLibrary, 2, 5678L);
            RLGameStateFeatures features = new MovieTrilogyRLGameStateFeatures(formatCode);
            var simulation = new MovieBlockSimulation(format, decks, _cardLibrary);

            for (int gameIndex = 0; gameIndex < 2; gameIndex++) {
                String gameSuffix = formatCode + "-" + gameIndex;
                var bot = new FotrStarterBot(features, "~model-less-" + gameSuffix, new ModelRegistry(), null);
                var random = new RandomDecisionBot("~random-" + gameSuffix);

                SimulationOutcome outcome = simulation.simulateGameWithMetrics(bot, random);
                GameResult result = outcome.result();
                assertNotNull(formatCode + " game result", result);
                assertNotNull(formatCode + " tactical metrics", outcome.tacticalMetrics().orElse(null));
            }
        }
    }
}
