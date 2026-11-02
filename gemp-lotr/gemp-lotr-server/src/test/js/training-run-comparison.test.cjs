const {test} = require("node:test");
const assert = require("node:assert/strict");
const {compareRuns, parseRunText} = require("../../../../bot-benchmarks/compare-training-rates.cjs");

function run(label, passGate, wins, counters, tactical = {}) {
  const runId = `${label}-id`;
  const metadata = {
    formats: ["movie"],
    generations: 1,
    gamesPerGeneration: 400,
    evaluationGamesPerSeat: 100,
    evaluationSeed: 17,
    modelObservationSchema: "public-observation-v6",
    trainingDataSchema: "public-observation-v8",
    genericActionPassGateEnabled: passGate,
    transferGuardEnabled: true,
    modelsDirectory: `${label}-models`
  };
  const diagnostics = {
    format: "movie",
    generation: 1,
    label: "movie generation 1 evaluation candidate-first",
    games: 10,
    cycleRecoveries: counters.cycles,
    transferConstraints: counters.transfers,
    genericPassVetoes: counters.vetoes,
    decisionModelFallbacks: counters.modelFallbacks,
    invalidAnswerFallbacks: counters.invalidFallbacks
  };
  const batch = {
    format: "movie",
    generation: 1,
    label: diagnostics.label,
    wins,
    losses: 10 - wins,
    skippedGames: 0,
    tacticalMetrics: {
      gamesMeasured: 10,
      decisionMetrics: {
        gamesMeasured: 10,
        decisionsTotal: tactical.decisions ?? 1000,
        decisionsByPhase: {FELLOWSHIP: tactical.fellowship ?? 400, SKIRMISH: 200},
        decisionsByType: {CARD_ACTION_CHOICE: 700, CARD_SELECTION: 300},
        cardActionChoices: 100,
        cardActionPasses: tactical.passes ?? 25,
        transferActions: tactical.transferActions ?? 5,
        attachmentReversals: tactical.reversals ?? 2
      }
    }
  };

  return parseRunText([
    JSON.stringify({schemaVersion: 1, runId, type: "run_started", data: metadata}),
    JSON.stringify({schemaVersion: 1, runId, type: "batch_decision_diagnostics", data: diagnostics}),
    JSON.stringify({schemaVersion: 1, runId, type: "batch", data: batch}),
    JSON.stringify({schemaVersion: 1, runId, type: "run_finished", data: {status: "completed"}})
  ].join("\n"), label);
}

test("compares all batch rates using their documented game denominators", () => {
  const result = compareRuns(
    run("baseline", false, 6, {
      cycles: 2, transfers: 3, vetoes: 4, modelFallbacks: 1, invalidFallbacks: 2
    }, {decisions: 1200, passes: 20, transferActions: 8, reversals: 4}),
    run("candidate", true, 7, {
      cycles: 1, transfers: 2, vetoes: 8, modelFallbacks: 0, invalidFallbacks: 1
    }, {decisions: 1100, passes: 30, transferActions: 6, reversals: 2}));

  assert.equal(result.metadata.status, "metadata-compatible");
  assert.deepEqual(result.completion, {baseline: "completed", candidate: "completed"});
  assert.deepEqual(result.metadata.treatmentDifferences, ["genericActionPassGateEnabled"]);
  assert.equal(result.rows.length, 1);
  assert.equal(result.rows[0].baseline.winRate, 0.6);
  assert.equal(result.rows[0].candidate.winRate, 0.7);
  assert.equal(result.rows[0].baseline.cycleRecoveriesPer1000Attempted, 200);
  assert.equal(result.rows[0].candidate.passVetoesPer1000Attempted, 800);
  assert.equal(result.rows[0].baseline.decisionsPerCompletedGame, 120);
  assert.equal(result.rows[0].candidate.decisionsByPhasePerCompletedGame.FELLOWSHIP, 40);
  assert.equal(result.rows[0].delta.decisionsByPhasePerCompletedGame.FELLOWSHIP, 0);
  assert.equal(result.rows[0].candidate.actionPassesPerChoice, 0.3);
  assert.equal(result.rows[0].delta.observedAttachmentReversalsPerCompletedGame, -0.2);
  assert.equal(result.limitations.length, 3);
});

test("flags metadata mismatches and missing legacy control fields", () => {
  const baseline = run("baseline", false, 5, {
    cycles: 0, transfers: 0, vetoes: 0, modelFallbacks: 0, invalidFallbacks: 0
  });
  const candidate = run("candidate", true, 5, {
    cycles: 0, transfers: 0, vetoes: 0, modelFallbacks: 0, invalidFallbacks: 0
  });
  candidate.metadata.evaluationSeed = 18;
  candidate.metadata.holdoutDeckManifestSha256 = "different-holdout";
  delete candidate.metadata.trainingDataSchema;

  const result = compareRuns(baseline, candidate);
  assert.equal(result.metadata.status, "configuration-mismatch");
  assert.deepEqual(result.metadata.configurationDifferences,
    ["evaluationSeed", "holdoutDeckManifestSha256", "trainingDataSchema"]);
  assert.deepEqual(result.metadata.missingControlFields, ["trainingDataSchema"]);
  assert.equal(result.rows[0].delta, null);
  assert.equal(result.rows[0].deltaStatus, "suppressed-configuration-mismatch");
});

test("does not turn absent legacy tactical metrics into zero rates", () => {
  const parsed = parseRunText(JSON.stringify({
    schemaVersion: 1,
    runId: "legacy-id",
    type: "run_started",
    data: {
      formats: ["movie"],
      generations: 1,
      gamesPerGeneration: 1,
      evaluationGamesPerSeat: 1,
      evaluationSeed: 1,
      modelObservationSchema: "v1",
      trainingDataSchema: "v1"
    }
  }));
  const candidate = run("candidate", false, 5, {
    cycles: 0, transfers: 0, vetoes: 0, modelFallbacks: 0, invalidFallbacks: 0
  });
  const result = compareRuns(parsed, candidate);

  assert.equal(result.rows[0].baseline, null);
  assert.equal(result.rows[0].candidate.winRate, 0.5);
  assert.equal(result.rows[0].missingIn.includes("baseline"), true);
});
