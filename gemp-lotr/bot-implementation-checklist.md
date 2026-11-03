# Bot implementation checklist

This is the execution checklist for the remaining work in
[bot-implementation-roadmap.md](bot-implementation-roadmap.md) and
[bot-todo.md](bot-todo.md). The roadmap retains historical evidence and
release gates; this file separates coding deliverables from experiments.

Work in priority order, but do not wait for a long benchmark to implement an
independent coding task. A coding checkbox means the implementation,
regression tests, configuration and documentation are complete; it does not
mean a model is stronger. Record the evidence beside the item. Keep evaluation
and promotion unchecked until their separate requirements actually pass.
Use `SKIPPED` only with a reason and evidence; none are currently skipped.
Do not delete historical training data or publish into the live model release.

## 1. Data quality and training throughput

- [x] Implement an opt-in versioned shared decision corpus that stores each
  relevant observation once rather than once per trainer. Readers must accept
  old plain/gzip and new data together, preserve trainer membership, rewards,
  weights, game IDs and deck fingerprints, and apply existing holdouts before
  sampling. Reject incompatible compaction; preserve old files and atomic
  model publication. Test old-only, new-only and mixed reads, corruption,
  concatenated writes, holdouts, deterministic caps and byte accounting.
  Implemented 2026-11-03; old/new trainer inputs also match for one completed
  simulated game. Representative performance and strength remain separate below.
- [x] Add category-aware, bounded sampling so rare decision categories survive
  the historical cap; keep whole games on one side of validation and leave
  evaluation examples/weights unchanged. Test rare and common categories,
  deterministic results and backward compatibility.
  Implemented 2026-11-03; overflow caps allocate samples across decision type
  and normalized prompt categories, with excess prompts sharing a bounded
  type bucket. Uncapped reads retain original order and weights. Existing
  whole-game validation and evaluation weighting remain unchanged; persistence
  regression tests cover rare/common categories and deterministic sampling.
- [x] Measure actual disk bytes, records, decision/category coverage and fit
  time for equivalent old, new and mixed corpora. Use the measured coverage
  and storage/throughput tradeoff to decide whether further work addresses a
  real bottleneck; do not treat an estimated percentage as a pass/fail gate or
  infer savings from synthetic compression alone.
  Real-corpus benchmark (2026-11-03): 100 games per supported format (400
  total), selected from 99 current-trainer gzip files (299,798,098 source
  bytes). The benchmark preserves per-game gzip append boundaries and source
  trainer membership/multiplicity. It found 36,841 distinct observations and
  40,306 trainer memberships; coverage matched exactly (40,306 loaded records,
  448 trainer/decision categories). Per-trainer used 13,944,076 bytes;
  shared-v1 used 11,830,125 bytes (15.16% fewer); mixed used 12,969,789 bytes.
  The original 50% estimate was not met, but only 3,465 of 40,306 trainer
  memberships overlap across trainers (8.6%). Shared-v1 remains opt-in; the
  measured 15.16% byte reduction preserved coverage, and the bounded fit
  sample did not demonstrate a run-level bottleneck that justifies redesign.
  Treat the current result as sufficient and revisit only if full-run data
  shows a material storage or throughput problem.
  The GenericCardActionChoiceTrainer fit sample loaded 18,013 records; per-trainer,
  shared-v1 and mixed load/wall/fit times were 293/3,317/3,149 ms,
  577/3,036/2,937 ms and 432/3,105/2,970 ms respectively. These are bounded
  sample measurements, not full-corpus results.
- [x] Account for cycle-tainted decisions and reconcile per-stage versus
  run-wide cycle/transfer/Pass counts and game denominators; regression-test
  aggregation, skipped games, JSONL and dashboard.
  Implemented 2026-11-03: successful deferred cycle recovery is counted once;
  per-batch and cumulative diagnostics include tainted decisions, transfer and
  Pass counts, and skipped-game denominators. JSONL aggregation and dashboard
  regression tests cover these metrics.
- [SKIPPED] Capture a full-run profile of simulation, I/O, compaction, fitting,
  memory and worker counts before changing `training.model.threads` defaults.
  Profiling telemetry is implemented for stage/runtime, decision and deck I/O,
  compaction, model load/wall/fit, worker counts and heap. The real-corpus
  sample above provides bounded I/O and fit measurements, but no full-run
  simulation, compaction and memory profile is claimed. The initial isolated
  launches ended before creating run artifacts. A corrected four-format
  configuration passed config/model/workspace preflight, but the debugger
  again reported startup and then no active session, with no training metrics,
  data files or locks and no accessible terminal error. Full-run capture is
  skipped at the user's direction until launch output or another cause can be
  identified. Worker defaults remain unchanged.

## 2. Playing decisions

- [PARTIAL] Implement and test a starting-fellowship selection experiment that
  uses engine-offered legal options, budget, uniqueness and own-deck synergy;
  preserve optional pass and unusual format/card effects. Retrain its model,
  then evaluate on held-out games without changing the engine.
  Selection trusts the engine's selectable options for cost, uniqueness,
  requirements and format-specific card types; candidate features use own-deck
  culture and in-play strategy synergy, and optional pass remains available.
  Tests cover selectable options, pass labels and a format-enabled non-companion
  option. Retraining and held-out wins remain pending; prior isolated training
  launches ended before producing run artifacts.
- [x] Add curated assignment and skirmish-order positions covering ties,
  overwhelm, Damage, Defender, Ring-bearer exposure and multiple minions;
  improve scoring only where those tests reveal a concrete defect.
  Tests cover tie wins, overwhelm, Damage, Defender capacity, Ring-bearer
  exposure, multiple minions and nested skirmish-order choices. They exposed
  and fixed assignment features counting Shadow Damage even when Shadow loses.
- [PARTIAL] Add relevance-aware skirmish/action and reconcile/hand-cycling
  experiments; preserve legal indirect effects, intentional cycling and
  mandatory decisions. Test hidden-information boundaries and live/training
  parity before held-out evaluation.
  An opt-in reconcile experiment compares optional discard candidates against
  a learned pass candidate while honoring mandatory minimums. It remains
  disabled until a model is retrained with pass examples. Live scoring and
  training share the card-feature hook. Hidden-information and decision-action
  serialization tests pass; held-out game evaluation remains pending.
- [x] Review transfer-guard and Pass-veto overrides on concrete positions;
  refine beneficial-transfer handling and action usefulness only with
  evidence. Test exploration, card effects and attachments.
  Tests cover voluntary possession/artifact transfers, card-effect movement,
  attachment targets, optional and mandatory decisions, and exploratory choices.
  No further override change was justified by these cases; the generic Pass
  gate remains disabled by default.
- [BLOCKED] Implement a calibrated public-observation value-prediction/credit
  assignment experiment, including temporal-difference alternatives and
  reward-exploitation checks. Compare with terminal-outcome training and
  held-out game wins before adopting it.
  Current training still uses discounted terminal-outcome credit. No calibrated
  value predictor or TD alternative is adopted without a completed held-out run
  and reward-exploitation review.
- [BLOCKED] Build a bounded lookahead prototype only after an isolated legal
  simulation can preserve hidden-information boundaries and engine behavior;
  test branch limits, legality, reproducibility and computational cost.
  The mutable game state has no supported snapshot/copy path for isolated legal
  branches. Lookahead remains gated until that engine foundation exists.

## 3. Deck-builder completion

- [x] Add an opt-in deck-only training mode with a frozen complete playing
  policy, fixed disjoint reference manifest, paired argument orders and no
  decision-model training. Two-generation/four-format synthetic integration
  and policy-byte preservation passed on 2026-11-03.
- [x] Record actual seat and starting-player strata and skipped pairings for
  deck-builder candidates; calculate a balanced comparison only when both
  strata have data. Do not equate swapped arguments with swapped seats.
  Simulations now report engine-resolved seats and first player; pairing skips
  and per-candidate strata are retained, and balanced rates remain unset until
  both strata have completed games. Focused tests pass.
- [x] Attach frozen-policy and reference-manifest provenance to new
  deck-builder examples, prevent incompatible historical outcomes being
  treated as matched, and preserve legacy data. Test new/legacy/mixed files.
  New schema-5 records include policy, reference-pool and manifest fingerprints;
  fitting requires matching provenance, while legacy schema-4 data stays
  untouched and excluded. New/legacy/mixed-data tests pass.
- [x] Add explicit focused and multiculture candidate generation, alternate
  both styles during ranking, and verify legal decks plus composition-sensitive
  strategy-synergy features without changing the ranker feature shape.
- [x] Add a local deck-builder training UI and config-only `--headless` mode,
  with run-only overrides, a game-budget estimate, progress/results and a
  single-run guard. Focused API and dashboard tests pass.
- [x] Generate the frozen independent deck-training reference manifest before
  starting the deck-only profile. The deck launch's pre-task creates it only
  when absent; the generated manifest uses the independent pool and contains
  eight decks per supported format. The generator completed successfully.
- [ ] Evaluate both styles against varied held-out references with the frozen
  playing policy and retain the reports. The manifest is ready, but no
  real-policy training or held-out evaluation has run; no gameplay result is
  claimed.

## 4. Measurement and release

- [ ] Produce controlled, fingerprinted run pairs and compare all per-batch
  rates with matching corpora, models and holdouts. Explain missing or
  incomparable historical controls rather than fabricating deltas.
- [ ] Run matched Pass-gate, transfer-guard and burden-cap experiments with
  independent frozen opponents and both argument orders. Review useful
  overridden actions and per-format reversals, cycles, skips and wins.
- [ ] Retrain/evaluate the starting-fellowship, assignment, combat and
  reconcile changes against held-out decks and stronger frozen opponents.
- [ ] Demonstrate the 90% reversal/cycle reduction and no strength loss with
  matched per-format data; investigate reproducible simulation and disclose
  remaining uncontrolled randomness.
- [ ] Run final promotion evaluation with per-format and aggregate confidence
  intervals, seating, skips, fallbacks and resource costs; target 1,000 games
  per seat per format. Publish only after all release gates pass.

## 5. Coverage and development operations

- [x] Inventory movie-era formats, modes and Reflections variants against the
  four currently trained formats; implement missing eligible variants and
  validate legal decks and evaluation coverage. The selected core scope has
  no missing format: `fotr_block` (sets 1-3), `ttt_block` (4-6), `king_block`
  (7, 8, 10), and `movie` (sets 1-10, including Reflections set 9). Deck
  generation and full legal-card coverage are tested, including explicit
  Reflections set-9 coverage; the frozen benchmark/evaluator covers all four
  formats and both argument orders. Excluded variants are PC/errata and
  playtest formats with additional V-sets or errata; movie subsets and alternate
  modes (`movie7/8/9`, `movie_sans9`, `movie_exp`, `movie_czech`, Poorman,
  Highlander and set-specific blocks/standards); `ts_reflections` (sets 1-6,
  9, 14 and 16 with Two Towers rules); broader formats with later blocks; and
  limited formats requiring draft/sealed deck construction. Those are outside
  the selected core training/evaluation scope.
- [ ] Add a local image cache with wiki/local preference and fallback,
  refresh/update handling, dev launch integration and status/refresh UI.
  Test stale/missing/error paths without depending on an external site.
- [x] Make the Hall Discord panel configurable and disabled by default in dev;
  test both settings and normal production defaults.
- [x] Make scheduled tournament and league-notification polling configurable,
  disabled by default in dev and enabled in production; test enabled/disabled
  paths. `scheduled.tournaments.polling.enabled` gates scheduled queue loading;
  `league.notifications.polling.enabled` gates only the per-request league
  notification check. The production defaults are `true`; the H2 dev override
  sets both to `false`.
- [x] Refine the bot-training dashboard for clearer progress, outcomes and
  optional diagnostics, and prepare isolated large-run profiles. The policy
  profile plans 24,000 games (2,000 per format across three generations); the
  deck-ranker profile plans 20,480 candidate games. They share the isolated
  model snapshot and training corpus, so workspace locks require sequential
  runs. Launch profiles and README instructions are updated; dashboard and
  focused Java tests pass. No training or model-strength result is claimed.
- [ ] Map runtime/training dependencies and decide whether a separate training
  Maven module is safe; implement the split only without circular dependencies.
- [ ] Establish ownership and recovery requirements before deciding whether old
  promotion markers or training files can be removed; do not delete by default.
