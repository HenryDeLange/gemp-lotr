const {test} = require("node:test");
const assert = require("node:assert/strict");
const {compareReports} = require("../../../../bot-benchmarks/compare-bot-benchmarks.cjs");

function bid(bidAmount = 9, capConstrained = false, engineMinimumExceedsCap = false) {
  return {bidAmount, capConstrained, engineMinimumExceedsCap};
}

function report(gate, outcomes, vetoes, guardConstraints = 0, diagnostics = {}) {
  return {
    version: 5,
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
    candidateMaximumBurdensToBid: 9,
    opponentMaximumBurdensToBid: 9,
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
        candidatePassGateVetoesAsBot2: vetoes[1],
        candidateTransferReversalsAsBot1: diagnostics.reversals?.[0] ?? 0,
        candidateTransferReversalsAsBot2: diagnostics.reversals?.[1] ?? 0,
        cycleRecoveriesAsBot1: diagnostics.cycleRecoveries?.[0] ?? 0,
        cycleRecoveriesAsBot2: diagnostics.cycleRecoveries?.[1] ?? 0,
        candidateBidAsBot1: diagnostics.bids?.[0] ?? bid(),
        candidateBidAsBot2: diagnostics.bids?.[1] ?? bid(),
        opponentBidAsBot1: bid(),
        opponentBidAsBot2: bid()
      }, {
        pool: "generated",
        candidateDeck: "deck-c",
        opponentDeck: "deck-d",
        candidateAsBot1: outcomes[2],
        candidateAsBot2: outcomes[3],
        candidatePassGateVetoesAsBot1: {},
        candidatePassGateVetoesAsBot2: {},
        candidateTransferReversalsAsBot1: diagnostics.reversals?.[2] ?? 0,
        candidateTransferReversalsAsBot2: diagnostics.reversals?.[3] ?? 0,
        cycleRecoveriesAsBot1: diagnostics.cycleRecoveries?.[2] ?? 0,
        cycleRecoveriesAsBot2: diagnostics.cycleRecoveries?.[3] ?? 0,
        candidateBidAsBot1: diagnostics.bids?.[2] ?? bid(),
        candidateBidAsBot2: diagnostics.bids?.[3] ?? bid(),
        opponentBidAsBot1: bid(),
        opponentBidAsBot2: bid()
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
  assert.equal(result.formats[0].safeguardDiagnostics.gamesAttempted, 4);
  assert.equal(result.formats[0].safeguardDiagnostics.candidatePassGateVetoes
    .treatmentPer100AttemptedGames, 75);
  assert.equal(result.formats[0].safeguardDiagnostics.candidateTransferGuardConstraints
    .baselinePer100AttemptedGames, 75);
  assert.equal(result.formats[0].safeguardDiagnostics.candidateBids.baseline
    .observedGames, 4);
  assert.equal(result.limitations.length, 3);
});

test("reports per-format reversal, cycle, and bid-cap rates using attempted games", () => {
  const baseline = report(false, ["win", "skipped-unresolvable-cycle", "loss", "win"], [{}, {}], 0, {
    reversals: [1, 0, 0, 0],
    cycleRecoveries: [1, 0, 0, 0],
    bids: [bid(3, false), bid(2, false), bid(1, false), bid(4, false)]
  });
  const treatment = report(true, ["win", "loss", "loss", "win"], [{}, {}], 0, {
    reversals: [0, 1, 0, 0],
    cycleRecoveries: [0, 1, 0, 0],
    bids: [bid(1, true), bid(1, true), bid(1, true), bid(1, true)]
  });

  const diagnostics = compareReports(baseline, treatment).formats[0].safeguardDiagnostics;

  assert.equal(diagnostics.gamesAttempted, 4);
  assert.equal(diagnostics.candidateTransferReversals.baselinePer100AttemptedGames, 25);
  assert.equal(diagnostics.candidateTransferReversals.treatmentPer100AttemptedGames, 25);
  assert.equal(diagnostics.cycleRecoveries.baselinePer100AttemptedGames, 25);
  assert.equal(diagnostics.cycleRecoveries.treatmentPer100AttemptedGames, 25);
  assert.equal(diagnostics.unresolvableCycleGames.baseline, 1);
  assert.equal(diagnostics.unresolvableCycleGames.baselinePer100AttemptedGames, 25);
  assert.equal(diagnostics.candidateBids.treatment.capConstrainedGames, 4);
  assert.equal(diagnostics.candidateBids.capConstrainedPer100AttemptedGames
    .treatmentPer100AttemptedGames, 100);
  assert.equal(diagnostics.candidateBids.capConstrainedPer100AssessedBids.treatment, 100);
  assert.equal(diagnostics.candidateBids.treatment.bidAmounts["1"], 4);
});

test("rejects model, setting, and deck-schedule mismatches", () => {
  const baseline = report(false, ["win", "loss", "win", "loss"], [{}, {}]);
  const treatment = report(true, ["win", "loss", "win", "loss"], [{}, {}]);
  treatment.modelSha256.policy = "different-model";
  assert.throws(() => compareReports(baseline, treatment), /modelSha256/);

  treatment.modelSha256.policy = "model-hash";
  treatment.candidateTransferGuardEnabled = true;
  assert.throws(() => compareReports(baseline, treatment), /Exactly one candidate policy treatment/);

  treatment.candidateTransferGuardEnabled = false;
  treatment.formats[0].matchups[0].candidateDeck = "different-deck";
  assert.throws(() => compareReports(baseline, treatment), /Deck schedule differs/);
});

test("preserves compatibility for matched version-4 reports without new diagnostics", () => {
  const baseline = report(false, ["win", "loss", "win", "loss"], [{}, {}]);
  const treatment = report(true, ["win", "loss", "win", "loss"], [{}, {}]);
  for (const result of [baseline, treatment]) {
    result.version = 4;
    for (const matchup of result.formats[0].matchups) {
      delete matchup.candidateTransferReversalsAsBot1;
      delete matchup.candidateTransferReversalsAsBot2;
      delete matchup.cycleRecoveriesAsBot1;
      delete matchup.cycleRecoveriesAsBot2;
      delete matchup.candidateBidAsBot1;
      delete matchup.candidateBidAsBot2;
    }
  }

  const diagnostics = compareReports(baseline, treatment).formats[0].safeguardDiagnostics;
  assert.equal(diagnostics.candidateTransferReversals.baseline, null);
  assert.equal(diagnostics.cycleRecoveries.treatment, null);
  assert.equal(diagnostics.candidateBids.baseline, null);
});

test("compares a bid-cap change as a single candidate treatment", () => {
  const baseline = report(false, ["loss", "win", "loss", "win"], [{}, {}]);
  const treatment = report(false, ["win", "win", "loss", "win"], [{}, {}]);
  treatment.candidateMaximumBurdensToBid = 1;

  const result = compareReports(baseline, treatment);

  assert.equal(result.treatment, "candidateMaximumBurdensToBid");
  assert.equal(result.settings.baseline, 9);
  assert.equal(result.settings.treatment, 1);
});
