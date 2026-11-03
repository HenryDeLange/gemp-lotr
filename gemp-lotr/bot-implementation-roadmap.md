# Bot improvement implementation roadmap

## Goal and constraints

Improve bot play, training quality, and training efficiency across the trilogy
formats, including Reflections. Keep the game engine and card rules unchanged:
the engine remains the authority for legal actions. Bot heuristics and learning
experiments must work in both live play and training, respect hidden information,
and be evaluated against frozen opponents before replacing a published model.

Maintain this document as work progresses: mark only verified work complete,
record newly discovered problems in the appropriate phase, and keep the next
action and outstanding validation visible.
Use [the implementation checklist](bot-implementation-checklist.md) for
prioritized coding deliverables and their separate evaluation requirements.

## Baseline

The completed 2026-10-27 run recorded 1,368 cycle escapes across 298 batches,
679 invalid-assignment fallback warnings, and no promotions among 50 individual
policy candidates. The active v8 training corpus occupied about 58.2 GiB when
inspected on 2026-11-02. Repeated decisions are a suspected contributor, but
the corpus also stores separate per-trainer records and deck-builder outcomes.
Existing run records cannot establish how much each cause contributes.

The bot's selected core scope is covered by `fotr_block` (sets 1-3),
`ttt_block` (4-6), `king_block` (7, 8, 10), and `movie` (sets 1-10, including
Reflections set 9). A catalog review found no additional eligible core format.
PC/errata, playtest, subset, alternate-ban/deck-rule, broader-set and limited
variants are catalogued as out of scope in the implementation checklist.

## Completed increment: baseline instrumentation and assignment parity

- [x] Pass the live game's assignment-legality checks to bots in both training
  simulators, including random and exploratory decisions. Regression-test the
  simulator integration and the restricted-assignment behavior.
- [x] Record completed-game decision counts by phase and type, pass and transfer action
  counts, and same-turn possession/artifact attachment reversals. Expose batch
  summaries and decision-count percentiles in the training dashboard and run
  metrics. Define attachment reversals as observations, not proof of a mistake.
- [x] Fix the bot's existing action-repeat guard so its state does not leak
  between games or turns, changed answers reset the streak, and an emergency
  pass does not leave the guard stuck passing.
- [x] Run focused tests and a simulation smoke test; document commands and
  results below. No policy model or training corpus is replaced by this work.

## Current next phase: benchmark and gameplay safeguards

- [x] Add an isolated, read-only diagnostic benchmark with a reusable legal
  deck manifest, strict in-memory policy snapshot and hashes, paired deck
  matchups in both simulator argument orders, all four formats, per-format
  and aggregate outcomes, nominal Wilson intervals, skips, event counts,
  and completed-game tactical and decision metrics. Add focused regression
  tests; document all limitations in the model README.
- [x] Persist a reference deck manifest and run an initial diagnostic benchmark
  with the current policy against the random bot: 400 games total, including
  100 per format in both simulator argument orders. Record model hashes and
  Git/dirty provenance. The 43 policy files matched the tracked release at
  the recorded revision; the bot implementation itself was uncommitted.
- [x] Archive a frozen model release and precisely identify its implementation
  for future reference comparisons. The archive at
  `bot-benchmarks/releases/incumbent-v6-20261102/` contains all 47 models, the
  completed run report, and a hash-verified snapshot of 110 bot Java sources.
  Its manifest records the base revision, source/model tree hashes, and the
  effective configuration captured by the run.
- [x] Establish a leakage-safe generated-deck holdout and document unavoidable
  prior overlap. The checked-in training config reserves the generated pool in
  `bot-benchmarks/reference-decks-v1.json`, created for the read-only benchmark
  after the completed run; starter decks remain known training decks. New
  decision episodes carry both deck fingerprints, and fitting excludes
  reserved-deck episodes plus legacy rows without provenance. Deck-builder
  history, reference decks, and newly generated candidates also exclude the
  reserved keys. Existing decision-history overlap and incumbent-model
  exposure cannot be reconstructed; run metadata records the manifest hash.
- [x] Add an independently frozen policy-opponent benchmark path and report
  actual seating and starting-player strata. `--opponent-models` snapshots and
  hashes a separate complete model release (the archived v6 incumbent is
  available); `--deck-pool=generated` selects the reserved deck pool.
  Completed games report candidate seat and actual starting player, with a
  seat-balanced win rate when both start-player strata were sampled. Seating is
  measured, not forced; shuffles and bidding ties remain stochastic. The
  archived incumbent has not yet been shown stronger than a future challenger,
  so comparative-strength conclusions still require a non-identical candidate
  and a sufficiently large benchmark.
- [x] Instrument per-format input decisions, duplicated trainer records, and
  actual appended bytes separately from deck-builder outcomes and bytes.
  Show cumulative totals, completed-batch game denominators, and provisional
  per-game rates in the dashboard and JSONL. Compaction is explicitly separate.
- [x] Analyze the 2026-11-02 run's writes, candidate outcomes, events, and
  decision diagnostics with explicit training-game denominators.
- [x] Add per-batch cycle-recovery, transfer-constraint, and Pass-veto counts
  with simulated-game denominators to JSONL and dashboard details.
- [x] Add per-batch model-fallback and invalid-answer-fallback counts while
  retaining one detailed warning per batch/model to avoid noisy event logs.
- [x] Limit deck-ranker fitting to a deterministic sample of up to 10,000
  historical examples per format, while retaining every current-generation
  outcome and leaving append-only source files untouched.
- [ ] Compare all per-batch rates across controlled runs. A read-only
  `bot-benchmarks/compare-training-rates.cjs` utility compares matched batches,
  flags metadata differences, and suppresses deltas when run controls differ.
  Run metadata and comparison safeguards are implemented: starting model files
  are fingerprinted, full-corpus hashing is opt-in because it scans every
  training JSONL file, and deltas require valid matching model/corpus
  fingerprints and holdout-manifest hashes. The complete 10:44Z and 13:27Z
  reports predate those fingerprints and use different holdout controls, so
  their observed rates still are not a controlled comparison.
- [x] Add an experimental shared-model Pass veto: if the generic action model
  ranks Pass above every offered action, it can override a phase-specific
  model's action choice. Keep it disabled for ordinary live play by default,
  enable it only in the isolated training config, and count vetoes.
- [ ] Measure the veto's impact with matched benchmarks and review how often
  it overrides actions that were useful. Calibrate specialist models before
  comparing their scores across action categories; test optional zero-card
  selection without changing mandatory choices. Candidate and opponent
  safeguards are now independently configurable; benchmark reports include
  candidate veto counts by phase and transfer constraints per matchup.
  Version-5 reports also retain per-game bids, cap-constrained choices,
  transfer reversals, and cycle recoveries; the comparator reports per-100
  attempted-game rates, including skipped games. It still cannot identify
  individual action utility. The matched evaluation and usefulness review
  remain pending.
  Regression tests confirm a minimum-zero arbitrary-card choice remains
  separate from Pass gating, mandatory card choices cannot be skipped, and
  non-card-action choices are not gated.
- [x] Implement and unit-/engine-test a switchable bot-only safeguard that
  allows the first completed voluntary Fellowship transfer but filters later
  voluntary transfers of that item in the same phase. Preserve mandatory
  selections and card effects; omit constrained menus from learning examples.
- [ ] Replace or refine the conservative safeguard if evaluation shows that
  suppressing beneficial second transfers harms play. Test exploration and
  more varied card-effect/attachment scenarios and improve reversal attribution.
  The new candidate-only constraint counts and privacy-safe per-game reversal
  attribution support matched review; no card identities are written to reports.
  Do not change the rule until results show whether it suppresses beneficial
  plays.
- [x] Extend assignment scoring with predicted Shadow wins (including ties),
  overwhelm, Damage, projected Fellowship casualties, and actual Ring-bearer
  exposure. Ring-bearer context is serialized with bot training actions, and
  legacy assignment records without it are excluded from assignment-model
  fitting. Existing models use their original feature prefix until retrained.
  These matchup predictions use printed strength and Damage, not every active
  modifier; candidate legality still comes from the game engine.
- [ ] Evaluate broader skirmish relevance and assignment strategy while
  preserving useful indirect actions and intentional card cycling. Skirmish
  order scores the actual pending one-minion subskirmish when assignments are
  nested, preserves the exact selected minion, and records its Fellowship
  character for training. Free Peoples candidates include additional Defender
  assignments, and the 10,000-candidate cap is distributed across legal
  branches for both sides. Curated-position tests, retraining, and held-out
  gameplay evaluation remain necessary; no strength gain is claimed.
- [ ] Improve combat risk, Ring-bearer safety, assignment, reconcile, and hand
  cycling using curated positions and full-game evaluation. Active skirmishes
  now expose Ring-bearer danger even when no pending assignment remains,
  including Shadow-side decisions and Shadow-win ties, without changing the
  feature-vector size. The existing risk value now distinguishes a survivable
  loss from projected death by overwhelm or wounds plus available Damage
  bonuses. Focused feature tests cover ties, lethal Damage bonuses, and
  overwhelm; retraining and held-out gameplay evaluation remain pending.
- [x] Add starting-fellowship candidate features for candidate culture,
  culture/strategy overlap with the selected fellowship, same-culture cards
  left in the bot's deck, and candidate cost relative to cost already spent.
  Selection still considers only the engine's reachable legal options,
  including its cost and uniqueness restrictions; no opponent deck information
  is exposed. Legacy state layouts safely omit unrecoverable synergy values,
  and legacy models use the original candidate-vector prefix until retrained.
- [ ] Retrain and evaluate starting-fellowship selection on held-out games.
  The existing 0.5 stop threshold and pass training labels remain in place;
  no strength gain is claimed until controlled evaluation.
- [ ] Evaluate, rather than blindly impose, a configurable low-burden bid.
  An opt-in 0-9 bid cap is now supported in training and as independent
  candidate/frozen-opponent benchmark settings; the engine's legal minimum
  always takes precedence. The default remains 9. Version-5 benchmark reports
  capture actual bid amounts, whether the cap constrained a model or observed
  fallback choice, and when the engine minimum exceeded the cap. Matched
  gameplay evaluation is still pending.
- [ ] Target at least 90% fewer reversals and cycle escapes without loss of
  benchmark strength. Version-5 reports count completed transfer reversals and
  cycle recoveries per format; comparison rates use attempted games, including
  skipped games, as the denominator. Verify the target and strength gate with
  matched per-format runs.

The 2026-11-02 user-started run completed all four formats: 2,800 games and
43 model-fitting tasks over 42.9 minutes, with zero skipped games and no
warning/error events. Its 1,600 training games recorded 335,351 input steps,
305,735 trainer records, and 1,157,961,855 bytes (1.08 GiB) of decision JSON.
That's about 210 input steps, 191 trainer records, and 0.72 MB of decision JSON
per training game. Four deck-builder runs appended 400 examples and about
1.39 MB of JSON total. The corpus was not compacted.

All 38 fitted decision candidates not individually tested remain unselected;
the five tested candidates all failed their game gate. Tested candidates had
aggregate win rates from 31.9% to 55.0% and aggregate 95%-lower-bounds from
25.2% to 47.3%, below the >50% acceptance gate. The small per-format sample
(40 games per candidate) is noisy and does not establish that the models are
intrinsically weaker. No decision or deck-ranking model was selected; all 47
experimental-release model files remain identical to the live incumbent. The
four deck builders appended only 100 outcomes each to histories of about
250,000 examples each, so this run supplied little fresh ranker data. The next
fit now samples at most 10,000 historical examples per format and always adds
all outcomes from the current generation, without rewriting the history.

The repeat-transfer guard constrained 4,584 optional choices across the run.
Cycle recovery events totaled 32 across 2,800 simulated games (mostly King
and Movie self-play); none became skipped games. This is diagnostic only and
not a same-opponent comparison. A matched random-opponent diagnostic recorded
fewer attachment reversals but no reduction in total decisions or cycle
escapes, so no strength improvement has been established. The existing cycle
guard remains a backstop, not targeted prevention of all game loops.

The shared-model Pass veto remains experimental. A second training run with
the gate enabled recorded 19,452 vetoes across 2,800 simulations (6.95 per
game), versus no gate counters in the prior run. The two noisy runs reported
77/80 and 79/80 random-opponent wins respectively, 8 versus 32 cycle recoveries,
and 298,289 versus 335,351 input decision steps. The changes are directionally
interesting but not causally attributable: game shuffles, bids, and random
choices are not seeded, and the historical data changed between runs. Neither
run promoted a decision or deck-ranking model. Keep the veto off in live play
until it passes a matched benchmark; the current training config is enabled
only for the isolated experiment.
The 2026-11-02 gate-on run also preceded the historical deck-ranker cap and
corpus lock; its ranker loaded the full 250,200 examples per format.

The latest user-started run, `20261102T104417.934Z`, completed 2,800 games and
43 model fits in 42.8 minutes, with no skipped games or warning/error events.
It recorded 298,570 input steps and 273,802 trainer records from 1,600 training
games, adding 1,035,822,284 bytes of decision JSON (85,453,445 compressed
bytes). The four deck-builder stages appended 400 examples (1,391,891 JSON
bytes; 49,078 compressed bytes). Compaction was disabled. Seven cycle
recoveries occurred across 2,400 policy-simulation games; the transfer guard
constrained 2,243 menus and the Pass gate vetoed 19,748 actions. No model was
promoted. All 47 output model hashes match the live v6 incumbent.

Of 43 fitted decision models, 38 were not individually evaluated and five
were tested. Their aggregate win rates ranged from 35.6% to 56.3%, with 95%
lower bounds from 28.6% to 48.5%; none passed the >50% acceptance gate. All
four deck-ranker candidates were also rejected. The run is not a matched
strength comparison: game randomness and training data differed from prior
runs, and the candidate pool remains small.

## Latest user-started measurement run

- [x] Fix current and legacy dashboard stage labels, actual generation order,
  argument-order versus starting-player wording, stage order, and data totals.
- [x] Add an explicit non-compacting data-load mode, configured off for
  compaction in the next run; input files are still appended and fully read.
- [x] Seed `bot-training-runs/transfer-guard-experiment-models/` with a
  hash-verified copy of all 47 current v6 policy and deck-builder model files.
  The run publishes only into that isolated directory, not the live release;
  it fails before starting if required incumbent models are missing or invalid.
- [x] Configure one generation, four formats, 400 training games and 100
  deck-builder games per format, and up to 100 evaluation games per simulator
  argument position per format. This plans at most 2,800 games in total.
- [x] Enable the shared-model Pass veto for the next isolated training run;
  ordinary live bots remain unchanged unless their JVM property opts in.
- [x] User-started run completed; retain its JSONL report at
  `bot-benchmarks/releases/incumbent-v6-20261102/training-run.jsonl` and inspect dashboard
  warnings, skips, data totals, decision percentiles, guard counts, per-format
  outcomes, fitting time, and compaction setting. The existing corpus is
  appended, not replaced, and may take a long time to scan. Do not run another
  trainer concurrently against that corpus; separate model directories have
  separate locks.
- [x] Add a corpus-level lock so independent model workspaces cannot write or
  compact the same training data concurrently.
- [x] Measure compressed append bytes and compression ratio from a completed
  run; preserve the pre-existing v8 files. Fitting time/peak-memory comparison
  remains pending.
- [x] Add the shared-model Pass veto to training configuration and metrics;
  leave the live default disabled until a matched benchmark measures its
  effect on wins, decisions, passes, transfers, and cycle recoveries.
- [x] Add gzip-compressed JSONL append files for new decision/deck-builder
  examples while reading both compressed and legacy uncompressed files.
  Report JSON bytes separately from physical append bytes; block accidental
  decision-corpus compaction after gzip archives exist.
- [x] Add a configurable historical deck-ranker example cap; default 10,000
  historical records plus all outcomes from the current generation, with
  deterministic sampling and no corpus compaction.
- [ ] Run a matched gate-on/off benchmark on the same saved models and deck
  pairs. Earlier Java debugger attempts failed during Maven startup; the latest
  debug launch started but ended without producing a report. The 13:27 training
  run was not a matched strength benchmark, so the gate's gameplay impact
  remains unmeasured.
- [ ] Compare the experimental release with its frozen incumbent and stronger
  held-out opponents before considering any live promotion. Investigate cycle
  regressions and whether filtered second transfers include useful plays.

The subsequent user-started run,
`[bot-training-20261102T132743.432Z-c9886439-d94b-4983-ba7a-1d82cf3b4284.jsonl](bot-training-runs/bot-training-20261102T132743.432Z-c9886439-d94b-4983-ba7a-1d82cf3b4284.jsonl),
completed 2,800 simulations and 42 decision-model fits in 253 seconds, with
no skipped games or warning/error events. Across 2,400 policy-simulation
games it recorded 9 cycle recoveries (5 King Block, 4 Movie), 1,962 transfer
constraints, and 16,641 Pass vetoes; there were no model or invalid-answer
fallbacks. The 1,600 training games added 297,774 input steps and 272,409
trainer records, corresponding to 1,073,806,056 bytes of decision JSON and
89,159,730 physical append bytes. Each deck builder added 100 outcomes, and
the 10,000-example historical cap limited each ranker's loaded set to 10,100
examples; compaction remained disabled. No policy or deck-builder candidate
was promoted.

Elapsed time was about 4m13s versus 42m45s for the prior full run, while the
checked run metadata differed only in the holdout-manifest reference and hash.
This is a promising observed runtime reduction, not a causal result: the
reports do not hash the starting model or corpus, the corpus changed, and
stochastic game choices are not fully seeded. The new manifest also means the
evaluation deck pools were not identical.

The later user-started run,
`[bot-training-20261102T154153.629Z-376f33cc-a2b2-46bb-ba3f-d51bbe79acff.jsonl](bot-training-runs/bot-training-20261102T154153.629Z-376f33cc-a2b2-46bb-ba3f-d51bbe79acff.jsonl),
completed all 2,843 planned tasks in 295.5 seconds, with 47 starting models
and 43 model fits. It emitted no warning/error events and selected no policy
or deck-builder candidate. Across 2,400 diagnostic games it recorded 4 cycle
recoveries, 826 transfer constraints, and 9,146 Pass vetoes, with no model or
invalid-answer fallbacks. The 1,600 training games added 210,554 input steps,
181,242 trainer records, 709,849,890 bytes of decision JSON, and 58,429,415
physical append bytes. Starting-model fingerprints were recorded, but full
corpus hashing was disabled.

Comparison with the 13:27Z report returned `configuration-mismatch` and
suppressed all 60 rate deltas: the earlier report lacks the starting-model
fingerprint, and the later run lacks a corpus fingerprint. The lower observed
diagnostic and data counts are therefore not evidence of improvement.

The latest user-started run,
`[bot-training-20261103T053633.194Z-ee5d6d7d-cd1b-4024-923d-6d31df17aa68.jsonl](bot-training-runs/bot-training-20261103T053633.194Z-ee5d6d7d-cd1b-4024-923d-6d31df17aa68.jsonl),
completed 2,843 planned tasks in 306.7 seconds with no warning/error events
or skipped games. It attempted 43 decision-model fits and trained 38;
20 policy-comparison records covered five candidates at 40 games per format.
The 1,600 training games added 210,841 input steps and 180,845 trainer
records, with 708,349,399 bytes of decision JSON written as 58,234,695
physical bytes (91.8% smaller). Four deck builders each added 100 outcomes.
The 47 starting-model fingerprints were recorded, but full corpus hashing was
disabled.

`GenericArbitraryCardsTrainer` was selected into the isolated experiment
release after 96/160 games (60%, aggregate 95% Wilson lower bound 52.3%):
26/40 Fellowship, 27/40 Towers, 22/40 King, and 21/40 Movie. This is a
screening result, not a release-grade strength claim: each format has only
40 games and game randomness is not fully controlled; no live model was
promoted. `ReconcileTrainer` reached 87.41% held-out accuracy versus 85.99%
for its incumbent across 231,467 examples, but was not selected for game
evaluation. No deck-builder candidate was selected.

Batch diagnostics across 2,400 policy-simulation games recorded 7 cycle
recoveries, 759 transfer constraints, and 9,015 generic Pass vetoes. The
run-level guard totals instead report 917 transfer constraints and 10,726
Pass vetoes; these scopes do not reconcile yet and must not be collapsed into
a single count. Neither those totals nor this run establish the causal impact
of the Pass gate or transfer guard.

## Later: training data and model quality

- [ ] Measure bytes and examples per game, duplication across trainers, and
  cycle contamination separately from deck-builder data.
- [x] Add opt-in deterministic game/label-balanced sampling to policy-model
  fitting. It operates after whole-game splitting, leaves held-out weights and
  stored examples unchanged, records its setting in run metadata, defaults
  off, and is enabled only in the isolated training configuration. This
  binary-label experiment does not yet address rare-category coverage or
  versioned corpus storage.
- [ ] Design a versioned compact corpus and category-aware
  sampling. Preserve game-level holdouts, rare decisions, provenance, existing
  data, and atomic model publication; compare old, new, and mixed datasets.
  The opt-in `shared-v1` append layout stores one copy of each relevant step,
  retaining the matching trainer names and original game/deck provenance.
  New readers merge it with legacy files, and compaction is forbidden. Tests
  verify matching trainer inputs, mixed files, holdouts and corruption handling.
  It scans the shared file per trainer, so fitting cost, category-aware
  sampling, representative space/coverage comparisons and strength remain open.
- [ ] Aim for at least 50% fewer bytes per equivalent game without losing
  decision coverage or benchmark strength.
- [ ] Investigate calibrated value prediction and credit assignment, including
  temporal-difference learning, as controlled experiments. Compare against
  terminal-outcome training using held-out games and actual win rates; prevent
  hidden-information leakage and reward exploitation.
- [ ] Prototype bounded lookahead only if isolated simulations can be built
  without changing the engine or consulting hidden opponent information.

## Later: tooling, coverage, and architecture

- [ ] Add independent deck-builder training with a frozen playing policy and a
  diverse, seat-balanced reference pool. An opt-in deck-builder-only mode now
  loads a complete policy once from the staging snapshot and verifies its
  policy files before publication. It uses a fixed, validated, holdout-disjoint
  manifest of starter and generated reference decks and skips decision-data
  collection and policy fitting. Each reference matchup is paired across
  simulator argument positions; actual seats are still assigned by bidding and
  have not been balanced or measured for these runs. Historical ranker data
  lacks policy/reference provenance, so use an isolated corpus for a truly
  frozen-policy dataset. Actual-seat accounting and held-out gameplay
  evaluation remain pending.
- [ ] Profile simulation, data I/O, compaction, fitting, memory, and worker
  counts before changing `training.model.threads` or hardware assumptions.
- [x] Inventory eligible game modes and Reflections variants beyond the
  currently supported four training formats; see the scope and exclusions in
  the implementation checklist.
- [ ] Consider a separate training Maven module after the runtime/training
  dependency direction is clear; do not introduce a circular dependency.
- [ ] Verify ownership and recovery requirements before removing old promotion
  markers or historical training data.

## Development operations and account setup

Track these development-environment and account tasks separately from bot
strength and training work.

- [ ] Build an optional local card-image cache with configurable wiki/local URL
  preference and fallback, refresh support for new or changed images, dev
  launch integration, and a status/refresh UI.
- [x] Add a Game Hall Discord-panel setting that is off by default in
  development and on in production. Test both settings and the production
  default.
- [x] Make scheduled tournament-queue and undelivered league-notification DAO
  polling configurable, off by default in development and on in production.
  The tournament setting skips scheduled-queue DAO polling; the notification
  setting suppresses only its per-request DAO lookup, leaving other delivery
  checks unchanged. Enabled/disabled behavior and both configuration defaults
  are covered by focused tests.
- [x] Seed all 18 official Fellowship/Towers/King starter products into the
  local H2 `Librarian` deck library, idempotently and with catalog-verified
  names, formats, and serialized contents.

## Evaluation and release gates

Use paired deck matchups with seats swapped and frozen opponents; keep held-out
games and decks separate from fitting and tuning. Final evaluation should report
aggregate and per-format win rates, confidence intervals, skips, fallbacks,
decision/transfer metrics, and resource costs. Target 1,000 games per seat per
supported format for final promotion when resources permit. Keep existing
skipped-game and atomic-release safeguards; do not promote a candidate solely
because it matches historical decisions.

The current evaluation seed controls deck selection, not all game randomness.
Investigate reproducible simulation without changing core game code and report
the limitation until it is resolved.

## Validation log

- 2026-11-03: Added opt-in shared-v1 decision records, mixed-layout readers,
  layout metadata and separate logical-membership/physical-record telemetry.
  A single completed base-policy Fellowship Block simulation produced 26
  decisions, 25 trainer memberships and 15 shared records; appending the same
  episode took 11,100 per-trainer bytes versus 4,402 shared bytes. Every
  trainer's loaded examples matched. A later stochastic test game used 25,649
  versus 13,511 bytes (71 input decisions, 62 memberships, 43 shared records).
  These individual games and append batches are not a representative corpus,
  throughput or strength comparison. The full-corpus 50% target and evaluation
  are still open. Focused validation passed 47 Java tests with one optional
  incumbent test skipped, nine Node tests and `git diff --check`.

  ```powershell
  mvn -q -f gemp-lotr\pom.xml -pl gemp-lotr-server -am "-Dtest=FotrStartersLearningStepsPersistenceTest,MovieBlockSimulationTest,BotModelTrainingPlanTest,TrainingRunMetricsWriterTest,AbstractTrainerValidationTest,TrainingModelWorkspaceTest,DeckBuilderOnlyTrainingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  node --test gemp-lotr\gemp-lotr-server\src\test\js\bot-training-dashboard.test.cjs gemp-lotr\gemp-lotr-server\src\test\js\training-run-comparison.test.cjs
  ```

- 2026-11-03: Implemented deck-builder-only mode and corrected the existing
  reference schedule so opposite simulator argument positions use the same
  reference deck. Focused integration tests run two generations in all four
  formats with temporary synthetic policies and a temporary reference manifest,
  verify the 43 policy model files are unchanged after publication, check that
  only deck-builder outcome files are written, and reject reference/holdout
  overlap. The focused Maven run passed 37 tests with one optional incumbent
  snapshot test skipped; all eight comparison/dashboard Node tests and
  `git diff --check` passed. These are functionality tests, not a gameplay
  strength evaluation.

  ```powershell
  mvn -q -f gemp-lotr\pom.xml -pl gemp-lotr-server -am "-Dtest=DeckBuilderOnlyTrainingTest,BotModelTrainingPlanTest,DeckBuilderTrainerTest,DeckBuilderTrainerDataSplitTest,BotBenchmarkTest,TrainingModelWorkspaceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  node --test gemp-lotr\gemp-lotr-server\src\test\js\training-run-comparison.test.cjs gemp-lotr\gemp-lotr-server\src\test\js\bot-training-dashboard.test.cjs
  ```

- 2026-11-02: The training-rate comparison now suppresses per-batch deltas
  when run metadata is incompatible. All three Node comparison tests passed;
  comparing the retained 10:44Z and 13:27Z reports correctly flags the changed
  holdout manifest and emits no deltas.

- 2026-11-02: Starting-model and opt-in training-corpus fingerprints are
  recorded in run metadata, and the comparator now requires valid matching
  fingerprints and file counts. The focused Maven tests passed (16 tests,
  1 skipped) and all three Node comparison tests passed. The retained reports
  are missing required fingerprints or holdout controls, so all 60 batch
  deltas remain suppressed.

- 2026-11-02: Starting-fellowship selection now honors optional stopping using
  the model's 0.5 score threshold and labels pass choices against candidate
  cards. Starting-fellowship observations include only the bot's own remaining
  deck composition; opponent deck information stays count-only. All 14 focused
  Java tests passed, including the hidden-information boundary tests. Held-out
  strength evaluation remains pending.

- 2026-11-02: 55 tests across 13 focused suites passed, including full simulated
  games in all four formats, checked assignment restrictions, repeat-guard
  lifecycle, metric aggregation/serialization, persistence, information
  boundaries, and the dashboard server. Command:

  ```powershell
  mvn -q -f gemp-lotr\pom.xml -pl gemp-lotr-server -am "-Dtest=FotrStarterBotRepeatGuardTest,FotrStarterBotRoutingTest,GameDecisionMetricsTest,MovieBlockSimulationTest,BotAssignmentRestrictionTest,BotInformationBoundaryTest,RandomDecisionBotTest,DecisionCycleGuardTest,SimpleBatchSimulationRunnerTest,TemporalCreditAssignmentTest,FotrStartersLearningStepsPersistenceTest,TrainingProgressServerTest,TrainingRunMetricsWriterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```

- 2026-11-02: A Node.js check of the dashboard's batch-detail renderer passed
  for both the new metrics and older records without decision metrics.

- 2026-11-02: The benchmark manifest, full command against temporary synthetic
  models, model/manifest integrity, both argument orders in all four formats,
  reporting, and existing simulation/model-loading tests passed (13 tests).
  An initial server-only Java launcher build failed; a server-only Maven
  compile reproduced errors caused by stale locally installed sibling modules.
  Launching against the verified reactor-built classpath succeeded. Command:

  ```powershell
  mvn -q -f gemp-lotr\pom.xml -pl gemp-lotr-server -am "-Dtest=BotBenchmarkTest,BotModelLoadingTest,MovieBlockSimulationTest,TrainingRunMetricsWriterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```

- 2026-11-02: [Reference manifest](bot-benchmarks/reference-decks-v1.json)
  and [random-opponent report](bot-benchmarks/baseline-random-v1.json):
  388/400 policy wins (97.0%), 0 skips, and 1 reported cycle escape.
  Fellowship, Towers, King, and Movie finished 99/100, 99/100, 99/100,
  and 91/100 respectively. Across both bots, the games recorded 31,353
  decisions and 150 observed same-turn attachment reversals. These
  reversals are not necessarily mistakes, and this weak-opponent sample
  does not characterize training self-play or demonstrate an improvement.
  Actual game seating and all game randomness were not controlled; the pools
  are not established as held out. Verified the manifest SHA-256 and all
  43 model SHA-256 values against the files used.

- 2026-11-02: Matched-deck diagnostic with the guard enabled:
  [report](bot-benchmarks/transfer-guard-matched-v1.json). Both 400-game runs
  used the same manifest, all 43 policy model hashes, pairing seed and deck
  pairs; shuffles, tie-breaks and random choices remain uncontrolled, and
  the baseline implementation was uncommitted. Baseline versus guarded:
  388/400 versus 387/400 wins, zero skips in each; 150 versus 26 observed
  reversals across both bots; 31,353 versus 32,059 decisions; one versus
  three cycle escapes. The guard constrained 86 menus across all formats.
  Per-format reversals changed 34→3, 50→9, 19→5, and 47→9. This observed
  82.7% aggregate decrease does not meet the 90% target, identify actual bad
  transfers, or demonstrate improved strength. A separate different-seed
  diagnostic is in [transfer-guard-random-v1.json](bot-benchmarks/transfer-guard-random-v1.json).

- 2026-11-02: User-started run report:
  [bot-training-20261102T064657.056Z-334317dd-3ca4-462d-ac23-19e852275633.jsonl](bot-training-runs/bot-training-20261102T064657.056Z-334317dd-3ca4-462d-ac23-19e852275633.jsonl).
  It completed 2,800 simulated games plus 43 model tasks, with no skips,
  warnings, or errors. Thirty-two cycle recoveries were counted across 2,800
  games; the transfer guard constrained 4,584 action menus. Decision-data
  writes added 335,351 input steps and 305,735 trainer records, representing
  1,157,961,855 bytes of uncompressed JSON (1.08 GiB), or about 0.72 MB/game
  over 1,600 training games. The four deck-builder formats appended 100
  examples each (about 1.39 MB JSON total). No decision or deck-builder model
  was selected; all 47 experimental release files are identical to the live
  incumbent. Compaction was disabled.

- 2026-11-02: Follow-up user-started run with the shared Pass gate enabled:
  [bot-training-20261102T081906.504Z-03749b76-d1b9-4e61-81b3-50130de8c5c7.jsonl](bot-training-runs/bot-training-20261102T081906.504Z-03749b76-d1b9-4e61-81b3-50130de8c5c7.jsonl).
  It completed 2,800 simulations in 42.8 minutes, with no skips, warnings, or
  errors. The shared gate vetoed 19,452 specialist actions; the transfer guard
  constrained 2,311 menus; there were 8 cycle recoveries. It recorded 298,289
  input steps over 1,600 training games (186.4/game), with 986.9 MiB of
  uncompressed decision JSON appended as 81.3 MiB on disk (91.8% smaller).
  Deck outcomes compressed from 1.33 MiB to about 0.05 MiB. No policy or deck
  model was promoted; the 47-model experimental release remains unchanged.
  The random-opponent stages won 77/80 games, versus 79/80 in the previous
  small run. Because shuffles and decisions are not seeded, and the corpus
  changed between runs, the differences in wins, steps, cycles (8 vs 32), and
  transfer constraints do not isolate the Pass gate's causal impact.

- 2026-11-02: Follow-up user-started run with the shared Pass gate enabled:
  [bot-training-20261102T081906.504Z-03749b76-d1b9-4e61-81b3-50130de8c5c7.jsonl](bot-training-runs/bot-training-20261102T081906.504Z-03749b76-d1b9-4e61-81b3-50130de8c5c7.jsonl).
  It completed 2,800 simulations in 42.8 minutes, with no skips, warnings, or
  errors. The shared gate vetoed 19,452 specialist actions; the transfer guard
  constrained 2,311 menus; there were 8 cycle recoveries. It recorded 298,289
  input steps over 1,600 training games (186.4/game), with 986.9 MiB of
  uncompressed decision JSON appended as 81.3 MiB on disk (91.8% smaller).
  Deck outcomes compressed from 1.33 MiB to about 0.05 MiB. No policy or deck
  model was promoted; the 47-model experimental release remains unchanged.
  The random-opponent stages won 77/80 games, versus 79/80 in the previous
  small run. Because shuffles and decisions are not seeded, and the corpus
  changed between runs, the differences in wins, steps, cycles (8 vs 32), and
  transfer constraints do not isolate the Pass gate's causal impact.

- 2026-11-02: A 5,000-record sample from an existing Fellowship Block
  decision-data file compressed from 19,382,422 bytes to 2,865,274 bytes
  (85.2% smaller at gzip fast-compression level). This is a sample, not a
  measured full-corpus or training-run saving; no existing data was modified.

- 2026-11-02: 80 tests in 17 focused Java suites passed, covering the real engine's legal transfer
  menu, guard, historical-file preservation, metrics, simulator, benchmark,
  plan, and dashboard server. The required-incumbent check also deserialized
  all 47 models in the experimental snapshot. Three Node tests rendered
  current and legacy dashboard snapshots.

  ```powershell
  mvn -q -f gemp-lotr\pom.xml -pl gemp-lotr-server -am "-Dgemp.test.bot.incumbent=C:\CODE\MyWild\gemp-lotr\gemp-lotr\bot-training-runs\transfer-guard-experiment-models" "-Dtest=FellowshipTransferGuardEngineTest,FellowshipTransferGuardTest,FotrStarterBotRepeatGuardTest,FotrStarterBotRoutingTest,FotrStartersLearningStepsPersistenceTest,TrainingRunMetricsWriterTest,BotBenchmarkTest,MovieBlockSimulationTest,BotModelTrainingPlanTest,TrainingProgressServerTest,GameDecisionMetricsTest,BotAssignmentRestrictionTest,BotInformationBoundaryTest,RandomDecisionBotTest,DecisionCycleGuardTest,SimpleBatchSimulationRunnerTest,TemporalCreditAssignmentTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  node --test gemp-lotr\gemp-lotr-server\src\test\js\bot-training-dashboard.test.cjs
  ```

- 2026-11-02: A browser smoke test against synthetic local progress data
  confirmed the active generation, current stage labels, per-format data
  table, guard count, and generation navigation. The temporary server was
  stopped; no training was started.

- 2026-11-02: 85 tests in 19 focused Java suites passed after adding
  compressed-data compatibility, concatenated gzip appends, mixed legacy/new
  decision and deck-builder loading, compaction preflight, and JSON-versus-disk
  byte telemetry. The three dashboard rendering tests passed, including the
  new uncompressed-JSON and physical-append byte columns. `git diff --check`
  passed. The full existing corpus and live/experimental model files were not
  modified by these tests.

- 2026-11-02: 94 tests in 21 focused Java suites passed after adding the
  shared-model Pass veto, per-batch cycle/transfer/pass/fallback diagnostics,
  corpus locking, bounded deck-ranker sampling, and run configuration. Three
  dashboard tests passed and `git diff --check` was clean. The dashboard shows
  attempted-game denominators and cycle recoveries per 1,000 games.

- 2026-11-02: A matched random-opponent benchmark remains pending. The first
  Java debugger attempt failed during Maven build; after rebuilding the reactor,
  a second session started but produced no report before closing. No further
  launch or training run was made. Gate strength impact remains unmeasured.

- 2026-11-02: Added the 12 missing official starter decks to the development
  H2 Librarian seed (18 total across the three trilogy blocks). The H2
  initialization test passes after executing the seed twice and verifies each
  Librarian deck's name, format, and contents against the official product
  catalog; duplicate inserts are prevented.

- 2026-11-02: Verified the completed 10:44Z training report and archived its
  unchanged 47-model incumbent, full JSONL report, and 110-file bot-source
  snapshot under `bot-benchmarks/releases/incumbent-v6-20261102/`. The release
  manifest records and verifies model, source, and report hashes, the Git
  revision, and the effective run configuration.

- 2026-11-02: Added the JSONL batch-rate comparison utility and redacted,
  stage-limited diagnostic traces. Three Node tests, 18 focused Java tests, and
  the production reactor compile passed. The focused Java run used a temporary
  source include list to avoid unrelated test-compilation blockers; the POM was
  restored afterward. The standard full test compile remains blocked by the
  duplicate helper in `H2DatabaseInitializationTest`. No controlled A/B pair
  or original King Block trace is retained, so empirical comparison and exact
  historical reproduction remain pending.

- 2026-11-02: Added independent candidate/opponent safeguard controls to the
  diagnostic benchmark, per-matchup candidate veto/constraint counts, and a
  matched-report comparator that rejects model, manifest, opponent, and deck
  schedule mismatches. Eighteen focused Java tests, five Node tests, and the
  production reactor compile passed. No new gameplay benchmark was run; game
  randomness remains uncontrolled, and per-game outcomes do not establish
  counterfactual action usefulness or justify changing the transfer guard.

- 2026-11-02: Fixed the server's undeclared direct dependency on
  `gemp-lotr-logic`, which left Java Debug without `AdventureLibrary` despite
  successful reactor compilation. The reactor compile passes with the explicit
  dependency. The failed debugger launch was not retried; VS Code may need a
  Maven-project reload before the corrected classpath is used.

- 2026-11-02: Improved high-cardinality assignment search by distributing the
  10,000-candidate limit across legal branches for both players, with stable
  card-ID traversal. Regression tests verify that capped searches still
  consider every first-minion/character branch. Also corrected the assignment
  test model's feature stride and emit canonical answers without empty
  character groups. The focused run passed 27 tests across assignment,
  information-boundary, and candidate-sampling suites; `git diff --check`
  passed. No training or gameplay benchmark was run, so no strength gain is
  claimed.

- 2026-11-02: Added an opt-in maximum-burden bid cap (default 9) to live bot
  configuration and training settings. The engine's minimum bid remains
  authoritative if it exceeds the cap. Diagnostic benchmark v4 records
  independent candidate/opponent caps, and the matched-report comparator
  allows a cap as its sole candidate treatment; training-rate comparisons also
  require and compare the configured cap. Twenty-five focused Java tests (one
  skipped), seven Node tests, and `git diff --check` passed. The 15:41Z training
  report is documented above, but it does not evaluate a bid-cap treatment;
  matched gameplay evaluation remains pending.

- 2026-11-02: Added starting-fellowship culture/strategy/deck/cost interactions
  and assignment outcome/Ring-bearer features. Assignment training actions now
  preserve Ring-bearer context; old actions without it are not used to fit the
  revised assignment models. The obsolete historical King Block trace item was
  removed. Twenty-five focused Java tests passed, including compatibility with
  the current shipped starting-fellowship model; `git diff --check` passed.
  No training run or gameplay benchmark was launched, so evaluation remains
  open.

- 2026-11-03: Added deterministic, opt-in game/label-balanced sampling for
  shared-policy fitting. It runs after the whole-game split, leaves validation
  weights and stored examples unchanged, defaults off, and is enabled only in
  the isolated training configuration; run metadata records the setting.
  Trainer and configuration tests passed, and `git diff --check` passed. No
  full training run or gameplay benchmark was launched.

## Focused-versus-multiculture deck generation

- Added explicit focused and multiculture composition modes to legal deck
  generation. Generated candidate pools alternate both styles, while the
  benchmark can pair each candidate against all references from a held-out
  manifest and preserve both simulator argument orders.
- Tests passed for all four formats, validating legal focused/multiculture
  decks, distinct culture concentration, strategy-synergy features, held-out
  pool disjointness, and complete candidate/reference pairing. The three
  focused suites passed 15 tests with no failures.
- Verified all 47 archived incumbent model hashes against the release manifest
  and prepared a separate 43-policy candidate copy. The standalone benchmark
  launch ended without writing its requested manifest; no active debug session
  or source diagnostic remained. The gameplay comparison is still pending, and
  no strength conclusion is drawn.
