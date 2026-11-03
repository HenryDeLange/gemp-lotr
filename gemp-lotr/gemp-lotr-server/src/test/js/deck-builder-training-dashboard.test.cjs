const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const html = fs.readFileSync(path.join(__dirname, "..", "..", "main", "resources",
  "deck-builder-training-dashboard.html"), "utf8");
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/\r?\n    for \(const input of document\.querySelectorAll\("#run-form input"\)\)\r?\n      input\.addEventListener\("input", updateGameBudget\);[\s\S]*$/, "");

class Element {
  constructor() {
    this.children = [];
    this.style = {};
    this.attributes = {};
    this.value = "";
    this.textContent = "";
    this.checked = false;
    this.disabled = false;
    this.hidden = false;
  }
  addEventListener() {}
  setAttribute(name, value) { this.attributes[name] = value; }
  appendChild(child) { this.children.push(child); }
  replaceChildren(...children) { this.children = children; }
}

function dashboard(fetch = async () => ({ok: true, json: async () => ({})})) {
  const elements = new Map();
  const document = {
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, new Element());
      return elements.get(id);
    },
    createElement() { return new Element(); },
    createTextNode(value) {
      const element = new Element();
      element.textContent = value;
      return element;
    },
    querySelectorAll(selector) {
      const formats = elements.get("formats")?.children || [];
      if (selector === "#formats input:checked")
        return formats.map(label => label.children[0]).filter(input => input.checked);
      if (selector === "#run-form input")
        return ["generations", "candidates", "games", "threads"].map(id => this.getElementById(id))
          .concat(formats.map(label => label.children[0]));
      return [];
    },
    querySelector(selector) {
      if (selector === '[role="progressbar"]') {
        if (!elements.has("progressbar")) elements.set("progressbar", new Element());
        return elements.get("progressbar");
      }
      return null;
    }
  };
  const context = vm.createContext({document, console, fetch});
  vm.runInContext(script, context);
  return {context, elements};
}

test("loads property defaults and estimates the selected-format simulation budget", () => {
  const {context, elements} = dashboard();
  context.renderConfig({
    configFile: "bot-training-deckbuilder.properties",
    formats: ["fotr_block", "movie"],
    supportedFormats: ["fotr_block", "ttt_block", "king_block", "movie"],
    formatLabels: {
      fotr_block: "Fellowship of the Ring",
      ttt_block: "The Two Towers",
      king_block: "The Return of the King",
      movie: "Movie block"
    },
    generations: 4,
    candidatesPerGeneration: 40,
    gamesPerCandidate: 32,
    simulationThreads: 8,
    modelDirectory: "bot-training-runs/candidate-models",
    trainingDataDirectory: "bot-training-data",
    referenceManifest: "bot-benchmarks/deck-builder-training-references-v1.json",
    holdoutManifest: "bot-benchmarks/reference-decks-v1.json"
  });

  assert.equal(elements.get("config-file").textContent, "bot-training-deckbuilder.properties");
  assert.equal(elements.get("generations").value, 4);
  assert.equal(elements.get("candidates").value, 40);
  assert.equal(elements.get("games").value, 32);
  assert.equal(elements.get("threads").value, 8);
  assert.equal(elements.get("formats").children.length, 4);
  assert.match(elements.get("game-budget").textContent, /10[\s,]240 games across 2 formats/);
  assert.match(elements.get("data-path").textContent, /Holdout: bot-benchmarks\/reference-decks-v1.json/);
});

test("submits run-only overrides and renders completed ranker results", async () => {
  const requests = [];
  const {context, elements} = dashboard(async (url, options) => {
    requests.push({url, options});
    return {
      ok: true,
      json: async () => ({
        runState: "completed",
        status: "completed",
        overallPercent: 100,
        overallCompleted: 320,
        overallTotal: 320,
        generation: 4,
        generationCount: 4,
        format: "movie",
        elapsedMillis: 65000,
        stage: "complete",
        stages: [],
        error: "",
        deckBuilders: [{
          format: "movie",
          generation: 4,
          candidates: 40,
          games: 320,
          wins: 176,
          losses: 144,
          skippedGames: 0,
          validationAccuracy: 72.5,
          incumbentAccuracy: 68,
          promoted: true
        }]
      })
    };
  });
  context.renderConfig({
    configFile: "training.properties",
    formats: ["movie"],
    supportedFormats: ["movie"],
    formatLabels: {movie: "Movie block"},
    generations: 4,
    candidatesPerGeneration: 40,
    gamesPerCandidate: 32,
    simulationThreads: 8,
    modelDirectory: "candidate-models",
    trainingDataDirectory: "training-data",
    referenceManifest: "references.json",
    holdoutManifest: "holdout.json"
  });
  elements.get("games").value = "24";
  await context.startTraining({preventDefault() {}});

  assert.equal(requests[0].url, "/api/start");
  assert.deepEqual(JSON.parse(requests[0].options.body), {
    formats: ["movie"],
    generations: 4,
    candidatesPerGeneration: 40,
    gamesPerCandidate: 24,
    simulationThreads: 8
  });
  assert.equal(elements.get("run-status").textContent, "Completed");
  assert.equal(elements.get("results").children.length, 1);
  assert.equal(elements.get("results").children[0].children[4].textContent, "176-144 (55.0%)");
  assert.equal(elements.get("results").children[0].children[7].textContent, "New ranker selected");
});

test("does not submit an odd number of games per candidate", async () => {
  const requests = [];
  const {context, elements} = dashboard(async (...args) => {
    requests.push(args);
    return {ok: true, json: async () => ({})};
  });
  context.renderConfig({
    configFile: "training.properties",
    formats: ["movie"],
    supportedFormats: ["movie"],
    formatLabels: {movie: "Movie block"},
    generations: 1,
    candidatesPerGeneration: 2,
    gamesPerCandidate: 4,
    simulationThreads: 1,
    modelDirectory: "candidate-models",
    trainingDataDirectory: "training-data",
    referenceManifest: "references.json",
    holdoutManifest: "holdout.json"
  });
  elements.get("games").value = "3";
  await context.startTraining({preventDefault() {}});

  assert.equal(requests.length, 0);
  assert.match(elements.get("message").textContent, /even number/);
});
