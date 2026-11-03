const fs = require("node:fs");
const path = require("node:path");

const REQUIRED_CONTROL_FIELDS = [
  "formats",
  "generations",
  "gamesPerGeneration",
  "evaluationGamesPerSeat",
  "evaluationSeed",
  "maximumBurdensToBid",
  "modelObservationSchema",
  "trainingDataSchema",
  "holdoutDeckManifestSha256",
  "startingModelFileCount",
  "startingModelFilesSha256",
  "startingTrainingDataFileCount",
  "startingTrainingDataFilesSha256"
];
const TREATMENT_FIELDS = new Set([
  "transferGuardEnabled",
  "genericActionPassGateEnabled",
  "maximumBurdensToBid"
]);
const RUN_SPECIFIC_FIELDS = new Set([
  "configFile",
  "holdoutDeckManifest",
  "deckBuilderReferenceManifest",
  "modelsDirectory",
  "metricsDirectory",
  "trainingDataDirectory"
]);
const VICTORY_REASONS = [
  ["siteNineWinRate", "siteNineWins"],
  ["ringBearerCorruptionWinRate", "ringBearerCorruptionWins"],
  ["ringBearerDeathWinRate", "ringBearerDeathWins"],
  ["otherWinRate", "otherWins"]
];
const TACTICAL_TOTALS_PER_GAME = [
  "overwhelmingSkirmishes",
  "p1SitePositionTotal",
  "p2SitePositionTotal",
  "p1SitesControlledTotal",
  "p2SitesControlledTotal",
  "p1FinalBurdensTotal",
  "p2FinalBurdensTotal",
  "p1RingBearerWoundsTotal",
  "p2RingBearerWoundsTotal",
  "p1BurdensAddedTotal",
  "p2BurdensAddedTotal",
  "p1ThreatsAddedTotal",
  "p2ThreatsAddedTotal",
  "p1CompanionsKilledTotal",
  "p2CompanionsKilledTotal",
  "p1FellowshipTwilightGeneratedTotal",
  "p2FellowshipTwilightGeneratedTotal",
  "p1ShadowTwilightSpentTotal",
  "p2ShadowTwilightSpentTotal",
  "maximumTwilightTotal"
];
const UNVERIFIABLE_CONTROLS = [
  "complete game randomness (evaluationSeed controls deck selection, not every random choice)"
];

function parseRunFile(filePath) {
  return parseRunText(fs.readFileSync(filePath, "utf8"), path.basename(filePath));
}

function parseRunText(text, label = "run") {
  let metadata;
  let runId;
  let completionStatus = "not-recorded";
  const diagnostics = new Map();
  const batches = new Map();

  for (const [index, line] of text.split(/\r?\n/).entries()) {
    if (!line.trim())
      continue;

    let record;
    try {
      record = JSON.parse(line);
    } catch (error) {
      throw new Error(`${label}:${index + 1} is not valid JSON: ${error.message}`);
    }

    if (record.schemaVersion !== 1 || typeof record.type !== "string" || !record.data)
      throw new Error(`${label}:${index + 1} has an unsupported training metrics record`);
    if (runId !== undefined && record.runId !== runId)
      throw new Error(`${label}:${index + 1} belongs to a different run`);
    runId = record.runId;

    if (record.type === "run_started") {
      if (metadata !== undefined)
        throw new Error(`${label} contains more than one run_started record`);
      metadata = record.data;
    } else if (record.type === "run_finished") {
      completionStatus = record.data.status ?? "unknown";
    } else if (record.type === "batch_decision_diagnostics") {
      addUnique(diagnostics, record.data, label, record.type);
    } else if (record.type === "batch") {
      addUnique(batches, record.data, label, record.type);
    }
  }

  if (!metadata)
    throw new Error(`${label} is missing its run_started record`);
  return {label, metadata, completionStatus, diagnostics, batches};
}

function addUnique(records, data, label, type) {
  if (typeof data.format !== "string" || typeof data.label !== "string"
      || !Number.isInteger(data.generation))
    throw new Error(`${label} has an invalid ${type} record`);

  const key = batchKey(data);
  if (records.has(key))
    throw new Error(`${label} contains duplicate ${type} data for ${data.format}/${data.label}`);
  records.set(key, data);
}

function batchKey(data) {
  return JSON.stringify([data.format, data.generation, data.label]);
}

function compareRuns(baseline, candidate) {
  const metadata = compareMetadata(baseline.metadata, candidate.metadata);
  const deltasAllowed = metadata.status === "metadata-compatible";
  const keys = new Set([...baseline.diagnostics.keys(), ...baseline.batches.keys(),
    ...candidate.diagnostics.keys(), ...candidate.batches.keys()]);
  const rows = [...keys].sort().map(key => {
    const baselineData = batchData(baseline, key);
    const candidateData = batchData(candidate, key);
    const batch = baselineData.batch || candidateData.batch
      || baselineData.diagnostics || candidateData.diagnostics;
    const baselineRates = calculateRates(baselineData);
    const candidateRates = calculateRates(candidateData);

    return {
      format: batch.format,
      generation: batch.generation,
      label: batch.label,
      baseline: baselineRates,
      candidate: candidateRates,
      delta: deltasAllowed ? calculateDeltas(baselineRates, candidateRates) : null,
      deltaStatus: deltasAllowed ? "metadata-compatible" : "suppressed-configuration-mismatch",
      missingIn: [
        ...(!baselineData.batch && !baselineData.diagnostics ? ["baseline"] : []),
        ...(!candidateData.batch && !candidateData.diagnostics ? ["candidate"] : [])
      ]
    };
  });

  return {
    baseline: baseline.label,
    candidate: candidate.label,
    completion: {
      baseline: baseline.completionStatus,
      candidate: candidate.completionStatus
    },
    metadata,
    rows,
    limitations: UNVERIFIABLE_CONTROLS
  };
}

function compareMetadata(baseline, candidate) {
  const keys = new Set([...Object.keys(baseline), ...Object.keys(candidate)]);
  const configurationDifferences = [];
  const treatmentDifferences = [];

  for (const key of [...keys].sort()) {
    if (RUN_SPECIFIC_FIELDS.has(key))
      continue;
    if (sameValue(baseline[key], candidate[key]))
      continue;

    if (TREATMENT_FIELDS.has(key))
      treatmentDifferences.push(key);
    else
      configurationDifferences.push(key);
  }

  const missingControlFields = REQUIRED_CONTROL_FIELDS.filter(key =>
    !hasControlField(key, baseline[key]) || !hasControlField(key, candidate[key]));
  const metadataCompatible = configurationDifferences.length === 0
    && missingControlFields.length === 0
    && treatmentDifferences.length <= 1;

  return {
    status: metadataCompatible ? "metadata-compatible" : "configuration-mismatch",
    configurationDifferences,
    treatmentDifferences,
    missingControlFields
  };
}

function hasControlField(key, value) {
  if (key === "holdoutDeckManifestSha256" && value === null)
    return true;
  if (key.endsWith("Sha256"))
    return typeof value === "string" && /^[a-f0-9]{64}$/i.test(value);
  if (key === "startingModelFileCount")
    return Number.isInteger(value) && value > 0;
  if (key === "startingTrainingDataFileCount")
    return Number.isInteger(value) && value >= 0;
  return value !== undefined && value !== null;
}

function sameValue(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function batchData(run, key) {
  return {
    batch: run.batches.get(key),
    diagnostics: run.diagnostics.get(key)
  };
}

function calculateRates({batch, diagnostics}) {
  if (!batch && !diagnostics)
    return null;

  const tactical = batch?.tacticalMetrics;
  const decision = tactical?.decisionMetrics;
  const completedGames = tactical?.gamesMeasured ?? ((batch?.wins ?? 0) + (batch?.losses ?? 0));
  const attemptedGames = diagnostics?.games
    ?? (completedGames + (batch?.skippedGames ?? 0));

  const rates = {
    attemptedGames,
    completedGames,
    winRate: ratio(batch?.wins, completedGames),
    skipRate: ratio(batch?.skippedGames, attemptedGames),
    cycleRecoveriesPer1000Attempted: ratio(diagnostics?.cycleRecoveries, attemptedGames, 1000),
    transferConstraintsPer1000Attempted: ratio(diagnostics?.transferConstraints, attemptedGames, 1000),
    passVetoesPer1000Attempted: ratio(diagnostics?.genericPassVetoes, attemptedGames, 1000),
    decisionModelFallbacksPer1000Attempted: ratio(
      diagnostics?.decisionModelFallbacks, attemptedGames, 1000),
    invalidAnswerFallbacksPer1000Attempted: ratio(
      diagnostics?.invalidAnswerFallbacks, attemptedGames, 1000),
    decisionsPerCompletedGame: ratio(decision?.decisionsTotal, decision?.gamesMeasured),
    actionPassesPerChoice: ratio(decision?.cardActionPasses, decision?.cardActionChoices),
    transfersPerCompletedGame: ratio(decision?.transferActions, decision?.gamesMeasured),
    observedAttachmentReversalsPerCompletedGame: ratio(
      decision?.attachmentReversals, decision?.gamesMeasured)
  };

  for (const [rateName, countName] of VICTORY_REASONS)
    rates[rateName] = ratio(tactical?.[countName], completedGames);
  for (const totalName of TACTICAL_TOTALS_PER_GAME)
    rates[`${totalName}PerCompletedGame`] = ratio(tactical?.[totalName], completedGames);

  rates.decisionsByPhasePerCompletedGame = ratesByDenominator(
    decision?.decisionsByPhase, decision?.gamesMeasured);
  rates.decisionsByTypePerCompletedGame = ratesByDenominator(
    decision?.decisionsByType, decision?.gamesMeasured);
  rates.medianDecisionsPerGame = finiteOrNull(decision?.medianDecisions);
  rates.p95DecisionsPerGame = finiteOrNull(decision?.p95Decisions);
  rates.maximumDecisionsPerGame = finiteOrNull(decision?.maximumDecisions);
  return rates;
}

function ratesByDenominator(counts, denominator) {
  if (!counts || typeof counts !== "object")
    return null;
  return Object.fromEntries(Object.entries(counts).map(([key, count]) => [
    key, ratio(count, denominator)
  ]));
}

function finiteOrNull(value) {
  return Number.isFinite(value) ? value : null;
}

function ratio(numerator, denominator, multiplier = 1) {
  if (!Number.isFinite(numerator) || !Number.isFinite(denominator) || denominator <= 0)
    return null;
  return numerator / denominator * multiplier;
}

function calculateDeltas(baseline, candidate) {
  if (!baseline || !candidate)
    return null;

  const result = {};
  for (const key of Object.keys(baseline)) {
    if (key === "attemptedGames" || key === "completedGames")
      continue;
    result[key] = difference(baseline[key], candidate[key]);
  }
  return result;
}

function difference(baseline, candidate) {
  if (Number.isFinite(baseline) && Number.isFinite(candidate))
    return candidate - baseline;
  if (!baseline || !candidate || typeof baseline !== "object" || typeof candidate !== "object")
    return null;
  const keys = new Set([...Object.keys(baseline), ...Object.keys(candidate)]);
  return Object.fromEntries([...keys].sort().map(key => [
    key, difference(baseline[key], candidate[key])
  ]));
}

function main(args) {
  if (args.length !== 2)
    throw new Error("Usage: node compare-training-rates.cjs <baseline.jsonl> <candidate.jsonl>");
  const result = compareRuns(parseRunFile(args[0]), parseRunFile(args[1]));
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

if (require.main === module) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 2;
  }
}

module.exports = {compareRuns, parseRunText};
