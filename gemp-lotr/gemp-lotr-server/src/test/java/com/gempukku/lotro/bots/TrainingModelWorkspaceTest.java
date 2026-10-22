package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.training.TrainingModelWorkspace;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrainingModelWorkspaceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesCompleteStagedModelsAndRemovesLegacyModelFiles() throws Exception {
        Path modelDirectory = temporaryFolder.newFolder("models").toPath();
        Path readme = modelDirectory.resolve("README.md");
        Path legacyModel = modelDirectory.resolve("AnotherMoveTrainer.model");
        Path oldFormatDirectory = modelDirectory.resolve("public-observation-v3").resolve("movie");
        Files.createDirectories(oldFormatDirectory);
        Files.writeString(readme, "Keep model documentation.");
        Files.writeString(legacyModel, "legacy");
        Files.writeString(oldFormatDirectory.resolve("AnotherMoveTrainer.model"), "legacy");

        try (TrainingModelWorkspace workspace = TrainingModelWorkspace.create(modelDirectory)) {
            Path stagedPolicyDirectory = ModelIO.resolveModelDirectory(
                    workspace.getStagingDirectory(), "fotr_block");
            Files.createDirectories(stagedPolicyDirectory);
            Files.writeString(stagedPolicyDirectory.resolve("AnotherMoveTrainer.model"), "trained");

            TrainingModelWorkspace.PublicationResult result = workspace.publish();

            assertEquals(1, result.sharedPolicyModels());
            assertEquals(2, result.removedLegacyModels());
        }

        Path activeModel = ModelIO.resolveModelDirectory(modelDirectory, "movie")
                .resolve("AnotherMoveTrainer.model");
        assertTrue(Files.isRegularFile(activeModel));
        assertFalse(Files.exists(legacyModel));
        assertFalse(Files.exists(oldFormatDirectory));
        assertTrue(Files.isRegularFile(readme));
        assertEquals("Keep model documentation.", Files.readString(readme));
    }

    @Test
    public void incompleteTrainingDoesNotReplaceOrCleanExistingModels() throws Exception {
        Path modelDirectory = temporaryFolder.newFolder("incomplete-models").toPath();
        Path legacyModel = modelDirectory.resolve("AnotherMoveTrainer.model");
        Files.writeString(legacyModel, "existing");

        try (TrainingModelWorkspace workspace = TrainingModelWorkspace.create(modelDirectory)) {
            try {
                workspace.publish();
                fail("Expected incomplete model set to be rejected");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("no shared movie-trilogy policy models"));
            }
        }

        assertTrue(Files.isRegularFile(legacyModel));
        assertFalse(Files.exists(modelDirectory.resolve(ModelIO.OBSERVATION_SCHEMA_DIRECTORY)));
    }

    @Test
    public void seedsStagingFromTheActiveReleaseAndPreservesCurriculumReadiness() throws Exception {
        Path modelDirectory = temporaryFolder.newFolder("continuing-models").toPath();
        Path activePolicyDirectory = ModelIO.resolveModelDirectory(modelDirectory, "fotr_block");
        Path activeDeckBuilderDirectory = ModelIO.resolveDeckBuilderDirectory(modelDirectory, "fotr_block");
        Files.createDirectories(activePolicyDirectory);
        Files.createDirectories(activeDeckBuilderDirectory);
        Files.writeString(activePolicyDirectory.resolve("ExistingTrainer.model"), "policy");
        Files.writeString(activePolicyDirectory.resolve("policy-promotion.marker"), "fotr_block");
        Files.writeString(activeDeckBuilderDirectory.resolve("DeckBuilderTrainer.model"), "deck-builder");

        try (TrainingModelWorkspace workspace = TrainingModelWorkspace.create(modelDirectory)) {
            Path stagedPolicyDirectory = ModelIO.resolveModelDirectory(
                    workspace.getStagingDirectory(), "fotr_block");
            Path stagedDeckBuilderDirectory = ModelIO.resolveDeckBuilderDirectory(
                    workspace.getStagingDirectory(), "fotr_block");

            assertEquals("policy", Files.readString(stagedPolicyDirectory.resolve("ExistingTrainer.model")));
            assertEquals("fotr_block",
                    Files.readString(stagedPolicyDirectory.resolve("policy-promotion.marker")));
            assertEquals("deck-builder",
                    Files.readString(stagedDeckBuilderDirectory.resolve("DeckBuilderTrainer.model")));
        }
    }

    @Test
    public void removesAbandonedWorkspaceAndRejectsConcurrentRuns() throws Exception {
        Path modelDirectory = temporaryFolder.newFolder("workspace-lock").toPath();
        Path abandonedWorkspace = modelDirectory.resolve(".training-abandoned");
        Files.createDirectories(abandonedWorkspace);
        Files.writeString(abandonedWorkspace.resolve("Temporary.model"), "stale");

        try (TrainingModelWorkspace workspace = TrainingModelWorkspace.create(modelDirectory)) {
            assertFalse(Files.exists(abandonedWorkspace));
            try {
                TrainingModelWorkspace.create(modelDirectory);
                fail("Expected concurrent training to be rejected");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("already owns"));
            }
        }
    }
}
