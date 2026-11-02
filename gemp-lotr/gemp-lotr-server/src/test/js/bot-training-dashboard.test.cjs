const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const html = fs.readFileSync(path.join(__dirname, "..", "..", "main", "resources",
  "bot-training-dashboard.html"), "utf8");
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/\s+refresh\(\);\s+pollingTimer = setInterval\(refresh, 2000\);/, "");

class Element {
  constructor() {
    this.children = [];
    this.style = {};
    this.value = "";
    this.textContent = "";
  }
  addEventListener() {}
  setAttribute() {}
  appendChild(child) { this.children.push(child); }
  replaceChildren(...children) { this.children = children; }
}

function dashboard() {
  const elements = new Map();
  const document = {
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, new Element());
      return elements.get(id);
    },
    createElement() { return new Element(); },
    querySelectorAll() { return []; },
    addEventListener() {}
  };
  const context = vm.createContext({document, console, clearInterval() {}});
  vm.runInContext(script, context);
  return {context, elements};
}

test("labels current and legacy training stages and correct bot identities", () => {
  const {context} = dashboard();
  assert.equal(context.stageName("movie generation 2 deck-builder-evaluation"), "Choose and test deck options");
  assert.match(context.stageName("movie generation 1 training vs-random-starter"), /same starter/);
  assert.match(context.stageName("movie generation 1 training self-play-different-starters"), /different starters/);
  assert.match(context.stageName("movie generation 1 training self-play-generated"), /generated decks/);
  assert.equal(context.stageName("movie generation 1 vs-random"), "Learning bot vs random bot");
  assert.equal(context.stageName("movie generation 1 self-play"), "Learning bot self-play");
  assert.match(context.stageOutcome({name: "movie self-play-generated", result: "4 wins / 2 losses"}),
    /Bot 1 4–2 bot 2/);
  const candidate = context.describeBatch({label: "policy-change-2-candidate-second", wins: 3, losses: 7});
  assert.match(candidate.comparison, /candidate bot 2/);
  assert.match(candidate.result, /Candidate model 7–3/);
});

test("renders generation work in plan order and run-wide data with completed-game denominator", () => {
  const {context, elements} = dashboard();
  context.render({
    status: "running", format: "movie", generation: 1, generationCount: 2,
    stage: "movie generation 1 deck-builder-evaluation", completed: 1, total: 10,
    stages: [
      {format: "movie", generation: 1, name: "movie generation 1 deck-builder-evaluation", state: "running", completed: 1, total: 10},
      {format: "movie", generation: 1, name: "movie generation 1 training vs-random-starter", state: "pending", completed: 0, total: 10},
      {format: "shared-policy", generation: 1, name: "decision-model-fitting", state: "pending", completed: 0, total: 1}
    ],
    batches: [
      {format: "movie", generation: 1, label: "movie generation 1 training vs-random-starter", wins: 6, losses: 4},
      {format: "movie", generation: 1, label: "movie generation 1 evaluation policy-change-1-candidate-first", wins: 5, losses: 5}
    ],
    trainingData: [{format: "movie", inputSteps: 100, trainerRecords: 250,
      decisionJsonBytes: 4000, decisionBytes: 3000, deckExamples: 3,
      deckJsonBytes: 500, deckBytes: 400}],
    transferGuard: [{format: "movie", generation: 1, constrainedDecisions: 2}],
    genericPassGate: [{format: "movie", generation: 1, passOverrides: 4}],
    batchDecisionDiagnostics: [{format: "movie", generation: 1,
      label: "movie generation 1 training vs-random-starter", games: 10,
      cycleRecoveries: 2, transferConstraints: 3, genericPassVetoes: 4,
      decisionModelFallbacks: 2, invalidAnswerFallbacks: 1}],
    deckBuilders: [{format: "movie", generation: 1, candidates: 10, games: 100,
      wins: 50, losses: 50, skippedGames: 0, accumulatedExamples: 10100,
      recentExamples: 100, trainingExamples: 8000, validationExamples: 2100,
      algorithm: "Smile 7 logistic regression", validationAccuracy: 70,
      incumbentAccuracy: 68, promoted: false}]
  });
  assert.equal(elements.get("active-generation").textContent, "Generation 1 of 2");
  const stages = elements.get("stages").children;
  assert.equal(stages[0].children[3].textContent, "Choose and test deck options");
  assert.match(stages[1].children[3].textContent, /random bot/);
  assert.equal(stages[2].children[3].textContent, "Train decision models");
  assert.match(elements.get("generation-view-summary").textContent, /across 1 format /);
  const data = elements.get("training-data").children[0].children.map(cell => cell.textContent);
  assert.deepEqual(data.slice(1).map(value => value.replace(/[,\u00a0\u202f]/g, "")),
    ["10", "100", "250", "4000", "3000", "10.0", "300.0", "3", "500", "400"]);
  assert.match(elements.get("bot-action-gates-summary").textContent, /^2 optional/);
  assert.match(elements.get("bot-action-gates-summary").textContent, /4 phase-specific actions vetoed/);
  const randomBatch = elements.get("batches").children.find(row =>
    row.children[2].textContent.includes("random bot"));
  assert.match(randomBatch.children[6].textContent,
    /2 cycle recoveries \(200.0 per 1,000 games\)/);
  assert.match(randomBatch.children[6].textContent, /2 model fallbacks/);
  assert.match(randomBatch.children[6].textContent, /1 invalid-answer fallbacks/);
  assert.equal(elements.get("deck-builders").children[0].children[8].textContent, "100");
});

test("renders legacy snapshots without new telemetry", () => {
  const {context, elements} = dashboard();
  context.render({status: "running", generation: 0, generationCount: 1, stages: [], batches: []});
  assert.equal(elements.get("active-generation").textContent, "Setup before generation 1");
  assert.equal(elements.get("training-data-empty").hidden, false);
});
