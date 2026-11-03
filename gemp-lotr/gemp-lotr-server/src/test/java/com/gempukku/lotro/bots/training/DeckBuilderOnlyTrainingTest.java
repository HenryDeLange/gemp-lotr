package com.gempukku.lotro.bots.training;

import com.alibaba.fastjson2.JSON;
import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.benchmark.BenchmarkDeckManifest;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.classification.Classifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeckBuilderOnlyTrainingTest extends AbstractAtTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void referencePoolIsFixedDistinctAndRejectsHeldOutDecks() {
        var manifest = BenchmarkDeckManifest.create(_formatLibrary, _cardLibrary, _productLibrary, 2, 1234L);
        var format = _formatLibrary.getFormat("fotr_block");
        var references = BotModelTrainingMain.deckBuilderReferences(manifest, format, Set.of());
        var pool = manifest.pools().getFirst();
        assertTrue(references.size() >= 4);
        assertThrows(IllegalArgumentException.class, () -> BotModelTrainingMain.deckBuilderReferences(
                manifest, format, Set.of(pool.generated().getFirst().fingerprint())));
        assertThrows(IllegalArgumentException.class, () -> BotModelTrainingMain.deckBuilderReferences(
                manifest, format, Set.of(pool.starters().getFirst().fingerprint())));
    }

    @Test
    void runsDeckOnlyAcrossGenerationsWithoutChangingPolicyOrWritingDecisionExamples() throws Exception {
        Path models = temporaryDirectory.resolve("models");
        Path policy = ModelIO.resolveModelDirectory(models, "movie");
        for (var trainer : FotrStarterTrainerCatalog.createAll())
            ModelIO.saveModel(trainer.getClass(), new ConstantClassifier(), policy);
        var before = TrainingInputFingerprints.modelFiles(policy);
        Path manifest = temporaryDirectory.resolve("references.json");
        BenchmarkDeckManifest.create(_formatLibrary, _cardLibrary, _productLibrary, 2, 1234L).writeNew(manifest);
        Path resources = Path.of("..", "gemp-lotr-cards", "src", "main", "resources").toAbsolutePath().normalize();
        Path config = temporaryDirectory.resolve("training.properties");
        Files.writeString(config, String.join("\n",
                "training.mode=deck-builder-only",
                "training.deckbuilder.reference.manifest=references.json",
                "training.generations=2",
                "training.deckbuilder.candidates.per.generation=10",
                "training.deckbuilder.games.per.candidate=2",
                "training.cpu.threads=2",
                "training.dashboard.port=0",
                "training.progress.interval.games=100",
                "cards.resources.directory=" + resources.toString().replace("\\", "\\\\"),
                "models.directory=models",
                "training.data.directory=data",
                "training.metrics.directory=metrics") + "\n");

        BotModelTrainingMain.main(new String[]{"--config=" + config});

        assertEquals(before, TrainingInputFingerprints.modelFiles(policy));
        Path data = temporaryDirectory.resolve("data").resolve(ModelIO.TRAINING_DATA_SCHEMA_DIRECTORY);
        for (String format : BenchmarkDeckManifest.FORMATS) {
            try (var files = Files.list(data.resolve(format))) {
                List<String> names = files.map(path -> path.getFileName().toString()).toList();
                assertEquals(List.of("deck-builder-outcomes-v5.jsonl.gz"), names);
            }
        }
        Path report;
        try (var files = Files.list(temporaryDirectory.resolve("metrics"))) {
            report = files.findFirst().orElseThrow();
        }
        var records = Files.readAllLines(report).stream().map(JSON::parseObject).toList();
        var plan = records.stream().filter(record -> "run_plan".equals(record.getString("type")))
                .findFirst().orElseThrow().getJSONArray("data");
        assertEquals(8, plan.size());
        assertTrue(plan.stream().allMatch(stage -> stage.toString().contains("deck-builder-evaluation")));
        assertTrue(records.stream().anyMatch(record -> "run_finished".equals(record.getString("type"))
                && "completed".equals(record.getJSONObject("data").getString("status"))));
        assertTrue(records.stream().anyMatch(record -> "deck_builder_pairing".equals(record.getString("type"))
                && record.getJSONObject("data").getString("referenceDeckKey") != null
                && record.getJSONObject("data").getString("candidateArgumentPosition") != null));
        assertTrue(records.stream().anyMatch(record -> "deck_builder_candidate_strata".equals(record.getString("type"))
                && record.getJSONObject("data").getJSONArray("candidates") != null));
    }

    private static final class ConstantClassifier implements Classifier<double[]> {
        @Override public int numClasses() { return 2; }
        @Override public int[] classes() { return new int[]{0, 1}; }
        @Override public int predict(double[] features) { return 0; }
        @Override public int predict(double[] features, double[] posterior) {
            posterior[0] = 1;
            posterior[1] = 0;
            return 0;
        }
    }
}
