package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.deckbuilding.StandardizedClassifier;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import smile.classification.Classifier;
import smile.classification.LogisticRegression;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BotModelLoadingTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void observationSchemaModelsAreIsolatedFromLegacyArtifacts() {
        Path legacyDirectory = Path.of("..", "bot-models").toAbsolutePath().normalize();
        Path releaseDirectory = legacyDirectory.resolve(ModelIO.OBSERVATION_SCHEMA_DIRECTORY);
        Path sharedPolicyDirectory = releaseDirectory.resolve("movie-trilogy");

        for (String format : new String[]{"fotr_block", "ttt_block", "king_block", "movie"})
            assertEquals(sharedPolicyDirectory, ModelIO.resolveModelDirectory(legacyDirectory, format));
        assertEquals(releaseDirectory.resolve("deck-builder").resolve("movie"),
                ModelIO.resolveDeckBuilderDirectory(legacyDirectory, "movie"));
    }

    @Test
    public void savesAndLoadsSmileSevenClassifierModels() throws Exception {
        double[][] features = {
                {-1.0, -1.0}, {-0.8, -1.0}, {0.8, 1.0}, {1.0, 1.0}
        };
        Classifier<double[]> trained = new StandardizedClassifier(
                LogisticRegression.fit(features, new int[]{0, 0, 1, 1}),
                new double[]{0.5, 0.5}, new double[]{0.5, 0.5});
        Path modelDirectory = temporaryFolder.newFolder("models").toPath();
        Class<?> trainerType = BotModelLoadingTest.class;

        ModelIO.saveModel(trainerType, trained, modelDirectory);
        Classifier<double[]> loaded = ModelIO.loadModel(trainerType, modelDirectory);

        assertNotNull(loaded);
        assertEquals(trained.predict(new double[]{0.9, 1.0}),
                loaded.predict(new double[]{0.9, 1.0}));
    }

}
