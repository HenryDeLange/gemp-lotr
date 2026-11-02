const {test} = require("node:test");
const assert = require("node:assert/strict");
const {compareReports} = require("../../../../bot-benchmarks/compare-bot-benchmarks.cjs");

function report(gate, outcomes, vetoes, guardConstraints = 0) {
  return {
    version: 3,
    status: "completed",
    manifestSha256: "manifest-hash",
    modelSha256: {policy: "model-hash"},
    opponentModelSha256: {policy: "opponent-hash"},
    pairingSeed: 17,
    pairsPerFormat: 2,
    deckPoolSelection: "generated",
    opponent: "incumbent",
    opponentModelDirectory: "incumbent-models",
    opponentTransferGuardEnabled: false,
    opponentGenericActionPassGateEnabled: false,
    resourcesPath: "resources",
    code: {revision: "abc123", dirty: true},
    candidateTransferGuardEnabled: false,
    candidateGenericActionPassGateEnabled: gate,
    formats: [{
      format: "movie",
      candidateTransferGuardConstraints: guardConstraints,
      matchups: [{
        pool: "generated",
        candidateDeck: "deck-a",
        opponentDeck: "deck-b",
        candidateAsBot1: outcomes[0],
        candidateAsBot2: outcomes[1],
        candidatePassGateVetoesAsBot1: vetoes[0],
        candidatePassGateVetoesAsBot2: vetoes[1]
      }, {
        pool: "generated",
        candidateDeck: "deck-c",
        opponentDeck: "deck-d",
        candidateAsBot1: outcomes[2],
        candidateAsBot2: outcomes[3],
        candidatePassGateVetoesAsBot1: {},
        candidatePassGateVetoesAsBot2: {}
      }]
    }]
  };
}

test("compares only a single candidate treatment on the same decks and models", () => {
  const baseline = report(false, ["loss", "win", "loss", "win"], [{}, {}], 3);
  const treatment = report(true, ["win", "win", "loss", "loss"], [
    {FELLOWSHIP: 2}, {SHADOW: 1}
  ], 1);
  const result = compareReports(baseline, treatment);

  assert.equal(result.treatment, "candidateGenericActionPassGateEnabled");
  assert.equal(result.totals.matchedCompletedGames, 4);
  assert.equal(result.totals.baselineWins, 2);
  assert.equal(result.totals.treatmentWins, 2);
  assert.equal(result.totals.treatmentOnlyWins, 1);
  assert.equal(result.totals.baselineOnlyWins, 1);
  assert.equal(result.formats[0].candidatePassGateVetoesByPhase.treatment.FELLOWSHIP, 2);
  assert.equal(result.formats[0].candidateTransferGuardConstraints.baseline, 3);
  assert.equal(result.formats[0].candidateTransferGuardConstraints.treatment, 1);
  assert.equal(result.limitations.length, 3);
});

test("rejects model, setting, and deck-schedule mismatches", () => {
  const baseline = report(false, ["win", "loss", "win", "loss"], [{}, {}]);
  const treatment = report(true, ["win", "loss", "win", "loss"], [{}, {}]);
  treatment.modelSha256.policy = "different-model";
  assert.throws(() => compareReports(baseline, treatment), /modelSha256/);

  treatment.modelSha256.policy = "model-hash";
  treatment.candidateTransferGuardEnabled = true;
  assert.throws(() => compareReports(baseline, treatment), /Exactly one candidate safeguard/);

  treatment.candidateTransferGuardEnabled = false;
  treatment.formats[0].matchups[0].candidateDeck = "different-deck";
  assert.throws(() => compareReports(baseline, treatment), /Deck schedule differs/);
});
