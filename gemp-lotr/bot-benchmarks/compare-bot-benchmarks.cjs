#!/usr/bin/env node
"use strict";

const fs = require("node:fs");

const FIXED_FIELDS = [
  "manifestSha256",
  "modelSha256",
  "opponentModelSha256",
  "pairingSeed",
  "pairsPerFormat",
  "deckPoolSelection",
  "opponent",
  "opponentModelDirectory",
  "opponentTransferGuardEnabled",
  "opponentGenericActionPassGateEnabled",
  "resourcesPath",
  "code"
];
const TREATMENT_FIELDS = [
  "candidateTransferGuardEnabled",
  "candidateGenericActionPassGateEnabled"
];

function stableJson(value) {
  if (Array.isArray(value))
    return `[${value.map(stableJson).join(",")}]`;
  if (value !== null && typeof value === "object")
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(",")}}`;
  return JSON.stringify(value);
}

function requireReport(report, label) {
  if (!report || report.version !== 3 || !["completed", "completed-with-skips"].includes(report.status))
    throw new Error(`${label} must be a completed version-3 benchmark report`);
  if (!Array.isArray(report.formats) || report.formats.length === 0)
    throw new Error(`${label} has no format results`);
  if (!report.modelSha256 || !report.opponentModelSha256)
    throw new Error(`${label} is missing model fingerprints`);
}

function validateCompatibility(baseline, treatment) {
  requireReport(baseline, "Baseline");
  requireReport(treatment, "Treatment");

  const mismatches = FIXED_FIELDS.filter(field => stableJson(baseline[field]) !== stableJson(treatment[field]));
  if (mismatches.length)
    throw new Error(`Benchmark controls differ: ${mismatches.join(", ")}`);

  const changedTreatments = TREATMENT_FIELDS.filter(
    field => baseline[field] !== treatment[field]
  );
  if (changedTreatments.length !== 1)
    throw new Error("Exactly one candidate safeguard must differ between reports");

  const baselineFormats = new Map(baseline.formats.map(format => [format.format, format]));
  const treatmentFormats = new Map(treatment.formats.map(format => [format.format, format]));
  if (baselineFormats.size !== treatmentFormats.size
      || [...baselineFormats.keys()].some(format => !treatmentFormats.has(format)))
    throw new Error("Benchmark format sets differ");

  return {baselineFormats, treatmentFormats, changedTreatments};
}

function sumPhaseCounts(matchups, field) {
  const total = {};
  for (const matchup of matchups) {
    for (const phaseField of [field + "AsBot1", field + "AsBot2"]) {
      const counts = matchup[phaseField] ?? {};
      if (typeof counts !== "object" || Array.isArray(counts))
        throw new Error(`Invalid ${phaseField} counts`);
      for (const [phase, count] of Object.entries(counts)) {
        if (!Number.isInteger(count) || count < 0)
          throw new Error(`Invalid ${phaseField}.${phase} count`);
        total[phase] = (total[phase] ?? 0) + count;
      }
    }
  }
  return total;
}

function compareFormat(baseline, treatment) {
  if (!Array.isArray(baseline.matchups) || !Array.isArray(treatment.matchups)
      || baseline.matchups.length !== treatment.matchups.length)
    throw new Error(`Matched deck count differs for ${baseline.format}`);

  let matchedCompletedGames = 0;
  let baselineWins = 0;
  let treatmentWins = 0;
  let treatmentOnlyWins = 0;
  let baselineOnlyWins = 0;
  let skippedInEitherRun = 0;
  let unchangedOutcomes = 0;

  for (let index = 0; index < baseline.matchups.length; index++) {
    const before = baseline.matchups[index];
    const after = treatment.matchups[index];
    for (const key of ["pool", "candidateDeck", "opponentDeck"]) {
      if (before[key] !== after[key])
        throw new Error(`Deck schedule differs for ${baseline.format}, matchup ${index}, field ${key}`);
    }

    for (const field of ["candidateAsBot1", "candidateAsBot2"]) {
      const beforeOutcome = before[field];
      const afterOutcome = after[field];
      const validOutcome = outcome => outcome === "win" || outcome === "loss"
        || outcome === "skipped-decision-limit" || outcome === "skipped-unresolvable-cycle";
      if (!validOutcome(beforeOutcome) || !validOutcome(afterOutcome))
        throw new Error(`Invalid game outcome for ${baseline.format}, matchup ${index}, field ${field}`);
      const beforeCompleted = beforeOutcome === "win" || beforeOutcome === "loss";
      const afterCompleted = afterOutcome === "win" || afterOutcome === "loss";
      if (!beforeCompleted || !afterCompleted) {
        skippedInEitherRun++;
        continue;
      }
      matchedCompletedGames++;
      const beforeWin = beforeOutcome === "win";
      const afterWin = afterOutcome === "win";
      baselineWins += Number(beforeWin);
      treatmentWins += Number(afterWin);
      if (beforeWin === afterWin)
        unchangedOutcomes++;
      else if (afterWin)
        treatmentOnlyWins++;
      else
        baselineOnlyWins++;
    }
  }

  const baselinePassGateVetoes = sumPhaseCounts(baseline.matchups, "candidatePassGateVetoes");
  const treatmentPassGateVetoes = sumPhaseCounts(treatment.matchups, "candidatePassGateVetoes");
  for (const [label, result] of [["baseline", baseline], ["treatment", treatment]]) {
    if (!Number.isInteger(result.candidateTransferGuardConstraints)
        || result.candidateTransferGuardConstraints < 0)
      throw new Error(`Invalid candidate transfer-guard count in ${label} ${baseline.format}`);
  }
  return {
    format: baseline.format,
    matchedCompletedGames,
    baselineWins,
    treatmentWins,
    deltaWinRate: matchedCompletedGames === 0
      ? null
      : (treatmentWins - baselineWins) / matchedCompletedGames,
    treatmentOnlyWins,
    baselineOnlyWins,
    unchangedOutcomes,
    skippedInEitherRun,
    candidatePassGateVetoesByPhase: {
      baseline: baselinePassGateVetoes,
      treatment: treatmentPassGateVetoes
    },
    candidateTransferGuardConstraints: {
      baseline: baseline.candidateTransferGuardConstraints,
      treatment: treatment.candidateTransferGuardConstraints
    }
  };
}

function compareReports(baseline, treatment) {
  const {baselineFormats, treatmentFormats, changedTreatments} =
    validateCompatibility(baseline, treatment);
  const formats = [...baselineFormats.keys()].sort().map(format =>
    compareFormat(baselineFormats.get(format), treatmentFormats.get(format)));

  const totals = formats.reduce((acc, result) => {
    for (const field of [
      "matchedCompletedGames", "baselineWins", "treatmentWins", "treatmentOnlyWins",
      "baselineOnlyWins", "unchangedOutcomes", "skippedInEitherRun"
    ])
      acc[field] += result[field];
    return acc;
  }, {
    matchedCompletedGames: 0,
    baselineWins: 0,
    treatmentWins: 0,
    treatmentOnlyWins: 0,
    baselineOnlyWins: 0,
    unchangedOutcomes: 0,
    skippedInEitherRun: 0
  });
  totals.deltaWinRate = totals.matchedCompletedGames === 0
    ? null : (totals.treatmentWins - totals.baselineWins) / totals.matchedCompletedGames;

  return {
    treatment: changedTreatments[0],
    settings: {
      baseline: baseline[changedTreatments[0]],
      treatment: treatment[changedTreatments[0]]
    },
    totals,
    formats,
    limitations: [
      "Deck pairs, model hashes, manifest, and listed controls match; in-game shuffles, bidding, and bot randomness are not seeded.",
      "Outcome changes are descriptive paired results, not proof that any individual veto or transfer constraint was useful.",
      "A dirty Git flag does not identify the exact contents of uncommitted code."
    ]
  };
}

function main(args) {
  if (args.length !== 2)
    throw new Error("Usage: node compare-bot-benchmarks.cjs <baseline.json> <treatment.json>");
  const baseline = JSON.parse(fs.readFileSync(args[0], "utf8"));
  const treatment = JSON.parse(fs.readFileSync(args[1], "utf8"));
  process.stdout.write(`${JSON.stringify(compareReports(baseline, treatment), null, 2)}\n`);
}

if (require.main === module) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}

module.exports = {compareReports};
