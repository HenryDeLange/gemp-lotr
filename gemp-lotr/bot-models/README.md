# Bot models and training

For a concise rules overview focused on bot decisions, see [LotR TCG rules reference for bot development](../bot-game-rules.md). The [Comprehensive Rules 4.2](https://wiki.lotrtcgpc.net/wiki/Comprehensive_Rules_4.2) remains the authoritative source.

All four supported formats use the same 43 decision policies and one shared policy-weight set: Fellowship Block (`fotr_block`), Two Towers Block (`ttt_block`), King Block (`king_block`), and combined Movie Block (`movie`). Every observation has the same feature shape, with a format indicator so the shared model can learn both cross-block mechanics and format-specific differences. The deck-builder ranker remains separate per format because legal cards and sites differ. The bot deck picker offers built-in decks, a model-guided generated legal deck, and the player's saved decks.

Deck generation and validation use GEMP's real format and card libraries. At the start of every generation, the per-format deck builder evaluates candidates with the incumbent decision policy, playing each candidate in both simulator argument positions against a mixed reference pool of official starters and the preceding generation's generated decks. Bidding determines actual starting order. The builder learns from those outcomes, then ranks the candidate pool with the selected deck-ranking model; its highest-ranked fifth becomes the generated-deck pool for that generation's policy games. Its features include card side/type, culture, race, keywords, possession classes, cost/stat curves, starting cards, sites, and strategy tags for burdens, threats, wounds, healing/prevention, exertion, combat, archery, site control, movement, twilight, and card cycling. It also encodes key strategy combinations. Deck-ranker examples describe candidate-deck outcomes only, and every example for a candidate fingerprint stays in one held-out fold; reused reference decks are not mislabeled as independent examples. When there is no usable deck-ranking model, candidate decks are sampled legally and ranked by the deck-quality heuristic.

Each deck-ranker fit uses a deterministic reservoir sample of at most
`training.deckbuilder.max.history.records` historical outcomes per format
(default 10,000), plus every outcome from the current generation. The full
append-only plain/gzip history remains intact; only the in-memory fit sample is
bounded. This keeps new results from being diluted by an indefinitely growing
history while retaining whole-deck holdout grouping.

## Diagnostic benchmark

`com.gempukku.lotro.bots.benchmark.BotBenchmarkMain` runs separately from
training. It never writes training examples or publishes models. It loads all
43 policy files into memory, fingerprints them before and after loading, and
fails if any are missing or change during loading. For future comparisons,
keep a separate, unchanged copy of the complete model release and retain the
deck manifest and reports.

The frozen incumbent at
[`bot-benchmarks/releases/incumbent-v6-20261102/`](../bot-benchmarks/releases/incumbent-v6-20261102/)
is a byte-for-byte copy of the 47-model v6 release after the 2026-11-02 run.
Its `release-manifest.json` verifies every model and bot-source file, records
the base Git revision and effective run configuration, and links the retained
JSONL run report. The run promoted no models. Use the archived
`public-observation-v6/movie-trilogy` directory for policy benchmarks and keep
benchmark reports outside it. Do not edit the archived files; verify them
against the manifest before comparisons.

Launch the main class in the server Java project with these program arguments
(replace the example absolute paths and create the output directory first):

```text
--resources=C:\path\to\gemp-lotr\gemp-lotr-cards\src\main\resources
--create-manifest=C:\path\to\benchmarks\reference-decks.json
--generated=8
--seed=1234
```

Then launch the same class again with:

```text
--resources=C:\path\to\gemp-lotr\gemp-lotr-cards\src\main\resources
--manifest=C:\path\to\benchmarks\reference-decks.json
--models=C:\path\to\candidate-models\movie-trilogy
--opponent-models=C:\path\to\frozen-models\public-observation-v6\movie-trilogy
--opponent-label=incumbent-v6
--output=C:\path\to\benchmarks\baseline.json
--pairs=100
--seed=1234
--deck-pool=generated
```

Neither command overwrites an existing file. Reports must be outside the model
directory. A manifest records the exact legal starter and generated decks for
all four formats, using the same deck fingerprints as the deck builder. Each
sampled matchup runs twice with the same deck assigned to each contestant and
the simulator's bot arguments reversed. The report includes each matchup,
model and manifest SHA-256 hashes, Git revision and dirty state, per-format and
aggregate win rates, nominal 95% Wilson intervals, skips, warning counts, and
completed-game tactical and decision metrics. Version-3 reports also record
candidate and opponent safeguard settings separately, candidate Pass-gate
vetoes by phase for each matchup, and candidate transfer-guard constraints by
matchup. This permits descriptive outcome review without treating a game's
eventual win/loss as a counterfactual measure of whether any individual
overridden action was useful.
The optional `--opponent-models` path loads a second complete policy release,
independently fingerprints all 43 model files, and runs it as the frozen
opponent; omitting it preserves the random-opponent diagnostic. Reports include
the opponent label, path, and hashes. `--deck-pool=mixed|starter|generated`
selects the manifest pool; `generated` is the mode to use for a generated-deck
holdout benchmark.
The JVM properties `gemp.lotr.bot.fellowshipTransferGuard` and
`gemp.lotr.bot.genericActionPassGate` provide defaults (true and false,
respectively). Use `--candidate-transfer-guard` and
`--candidate-generic-pass-gate` to change the candidate independently of the
frozen opponent; `--opponent-transfer-guard` and
`--opponent-generic-pass-gate` can independently set the opponent. The legacy
`--generic-pass-gate` option still sets both bots and cannot be combined with
the candidate/opponent Pass-gate options.

To compare two reports from the same manifest, saved models, pairing seed, and
deck schedule, with exactly one candidate safeguard changed, run:

```powershell
node gemp-lotr\bot-benchmarks\compare-bot-benchmarks.cjs control.json treatment.json
```

The comparator rejects differences in model hashes, opponent settings, deck
pairs, or other listed controls, and summarizes paired win/loss changes and
per-phase veto counts. Deck pairing is controlled; shuffles, bidding, and bot
randomness are not. Outcome changes do not identify counterfactual action
utility.

This remains a diagnostic, not a promotion gate. The bot-argument swap does
**not** control actual game seating or starting player; both are recorded from
the completed game state, and results include performance stratified by whether
the candidate actually started. The seat-balanced rate equally weights those
two strata when both are sampled. The seed fixes deck-pair selection only:
shuffles and bidding tie-breaks remain stochastic. Wilson intervals assume
independent games, so repeated/paired deck matchups make them approximate.
Official starter decks have been used in training and are not held out.
Generated decks are only protected when the same manifest is configured in
training with `training.holdout.deck.manifest`; old decision records without
deck fingerprints and records containing a reserved fingerprint are excluded
from fitting, and deck-builder history is filtered by its persisted deck key.
Historical decision-deck overlap remains unverifiable. Holdout mode cannot be
combined with decision-data compaction.
The code revision plus dirty flag does not identify the exact contents of
uncommitted changes; the frozen release manifest preserves the bot-source
snapshot for that specific reference.
Skipped games do not contribute game-end metrics, and event counts include
both bots and skipped games. A report is marked failed if no game finishes in
any one format.

An initial run and its reusable [deck manifest](../bot-benchmarks/reference-decks-v1.json)
and [report](../bot-benchmarks/baseline-random-v1.json) are recorded in the
[implementation roadmap](../bot-implementation-roadmap.md). A server-only Maven
build can pick up stale installed sibling modules; build the current reactor and
ensure the Java launch classpath includes those freshly built module classes.
The server directly imports game-engine APIs from `gemp-lotr-logic`, so its POM
declares that module explicitly. If VS Code Java Debug still reports a missing
logic class after the reactor build, reload the Maven projects before launching.

## Running training

Training parameters live in [`bot-training.properties`](../bot-training.properties). Run the cross-platform Docker Compose service from the repository root:

```sh
docker compose -f gemp-lotr/docker/docker-compose.yml --profile training run --build --service-ports --rm bot-training
```

The checked-in settings currently prepare a bounded diagnostic run: one
generation, 400 training games and 100 deck-builder games per format, and up to
100 policy-evaluation games per simulator argument position per format. With
five candidate slots this is at most 2,800 games across the four formats,
including 20 games per argument position per candidate and format. The small
evaluation is useful for diagnostics, not a sufficient final strength gate.
The experimental `models.directory` is seeded from the current release and
publishes under `bot-training-runs/transfer-guard-experiment-models/`, not the
live `bot-models/`. A successful run can still replace that experimental release;
do not manually promote it to the live directory without further evaluation.
`training.require.incumbent.models=true` checks all 43 shared policy models and
the four deck-ranking models before training begins. If the ignored experimental
snapshot is missing or corrupted, the run fails instead of silently starting
from scratch; recreate the snapshot from a trusted release before starting it.

The configured data directory remains the existing historical corpus. New
decision examples and deck outcomes are **appended** as concatenated gzip
members in `*.jsonl.gz` files; older uncompressed `*.jsonl` files remain
readable and are not rewritten. A sample of 5,000 existing Fellowship Block
decision rows compressed from 19.38 MB of JSON to 2.87 MB (85.2% smaller);
actual savings vary by record type, and compression's effect on fitting time
has not yet been measured. `training.data.compact.on.load=false` prevents
training from replacing oversized uncompressed decision files with reservoir
samples. The configured per-trainer/per-format cap still bounds each fit's
sample, but all legacy and compressed records must be read. On the existing
large corpus this can take substantial time and disk bandwidth. Compaction is
refused before training when compressed decision files exist, rather than
silently leaving a second archive or replacing historical records. A
corpus-level `.training.lock` now prevents concurrent runs from sharing one
`training.data.directory`, even when each run uses a different model directory.

The checked-in config reserves the generated decks in
[`reference-decks-v1.json`](../bot-benchmarks/reference-decks-v1.json) via
`training.holdout.deck.manifest`. This manifest was created for the read-only
benchmark after the completed training run; its generated pool, not its starter
pool, is reserved for future training. The manifest is validated against all four
formats before simulation; only its generated-deck fingerprints are reserved.
New policy episodes store both deck fingerprints, and fitting excludes any
episode using a reserved deck. Since older decision rows have no deck identity,
strict holdout mode excludes those unattributed rows entirely; deck-builder
history is filtered by its persisted fingerprint. Run metadata records the
manifest path and SHA-256. Do not enable data compaction together with a holdout,
because compaction would rewrite the historical corpus.

Training uses the configured number of decision-policy games per generation and format. Random-opponent games default to 5%, split evenly between starter and generated decks. Only the policy bot's decisions are recorded, at the configurable `training.random.baseline.example.weight`; the random opponent's actions are never training labels. The remaining games are self-play: half use starters (split evenly between matching and different starters) and half use generated decks. Thus, for a 1,000-game batch at the default setting, the groups are 25 same-starter vs-random, 25 generated-vs-random, 238 same-starter self-play, 237 different-starter self-play, and 475 generated-deck self-play games. Starter decks never face generated decks in policy training or evaluation.

Training self-play uses configurable epsilon-style action exploration: a small share of policy decisions are replaced with legal random decisions. The rate decays by generation. `training.exploration.profile=auto` selects the higher fresh schedule when no incumbent models are available and the lower refinement schedule otherwise; this changes exploration only and does not erase the active model release or persistent examples. Exploratory decisions are kept at a lower configurable example weight. This adds behavioral coverage without treating noisy actions as equally reliable as normal policy actions.

After all formats finish their generation games, the shared decision models are fitted once from records pooled across all four formats. Game outcomes assign terminal rewards, with a per-step training weight discounted by 0.995 per decision from the end of the episode; decision and episode weights are multiplied, and deterministic weighted resampling makes them affect Smile's unweighted logistic-regression fit. Legacy records without a weight use 1.0.

Candidate models are ranked by improvement on held-out decision examples, but a configurable number of evaluation slots are reserved for exploratory challengers that regress by no more than `training.evaluation.max.validation.regression` percentage points. This avoids filtering out every model that differs from incumbent behavior before game testing. Candidates are checked individually, not bundled. `training.evaluation.games.per.seat` is the total per-argument-position budget per generation and is divided among up to `training.evaluation.max.policy.changes` candidates. Each change plays against the current working policy on identical deck pairs with the simulator arguments reversed in every format; bidding still determines actual starting order. It is accepted only if the aggregate 95% Wilson lower bound is above 50%, no format shows a statistically clear result below 45%, and no more than 1% of games are skipped. Accepted changes become the opponent for later tests and enter the next generation's policy. These promotion gates are unchanged: extra exploration creates more varied challengers, not easier acceptance. `training.evaluation.seed` repeats deck-pair selection across runs; game shuffles remain stochastic. Later rounds include more of the data created earlier in the run. The dashboard shows learning/check match rates separately from each model's paired game result, and labels whether each individual model was promoted, retained, or included in the final release. Model match rates measure decision imitation, not game strength. Set `training.model.threads` to increase training parallelism when the JVM heap can accommodate multiple training datasets. Set `training.models.minimum.validation.accuracy` (0-100 percent) to keep candidates below a chosen check-example match rate from being evaluated; `0` disables this absolute gate.

All current training game simulations (self-play, random-baseline diagnostics, deck-builder candidate evaluation, and policy checks) use the same loop safeguards. A repeated sequence of up to 16 decisions and responses is detected after four repetitions only when the player-visible game state also repeats, giving stochastic choices a little longer before an escape is triggered. The simulator tries a distinct legal response (preferring an explicit decline for optional choices); if the repeated decision cannot change its response, it can schedule a distinct response for the next repeating card-action choice. These escape responses are not recorded as model choices. Successful escapes are summarized per simulation batch instead of printing one warning per escape. Cycle signatures exclude hidden deck contents and unrevealed opponent hands, and ignore twilight so loops that only accumulate twilight remain detectable. If no safe alternate exists in the cycle, that game is aborted, its partial learning episode is discarded, and the failure is recorded; it does not terminate the entire run. Evaluation gates reject candidates when skipped games exceed 1% in aggregate or in any format. The 10,000-decision-per-game limit remains as a final backstop.

Simulation bots receive the same live-game assignment-legality checks as bots in player games, including exploratory and random choices. The bot's card-action repeat guard resets between training episodes, turns, and phases, and clears its history after an emergency pass.

The separate Fellowship repeat-transfer safeguard is enabled for both model and
exploratory choices by `training.transfer.guard.enabled=true`; live bots use the
JVM property above. After a successful normal voluntary transfer, it removes
subsequent voluntary transfer actions for that physical item during the same
Fellowship phase and turn. It leaves the engine's legal actions and compulsory
or card-effect choices unchanged, but can suppress a genuinely useful second
transfer. Filtered action-menu decisions are omitted from learning examples
because the original menu no longer describes the bot's available options.
This is an experimental gameplay tradeoff, not a perfect reversal classifier.

The optional shared-model card-action Pass gate is configured separately:
`training.action.pass.gate.enabled=true` enables it during training, while the
live bot requires the JVM property
`gemp.lotr.bot.genericActionPassGate=true` (default `false`). If the shared
generic card-action model ranks Pass above every current option, the gate
replaces the phase-specific trainer's selected action with Pass. It compares
options using one shared model rather than comparing uncalibrated scores from
different specialist models, and never selects a different action. It is not
used for exploratory random choices. The dashboard and run metrics count these
vetoes separately; evaluate their game impact before enabling the gate for live
play.

Game wins/losses remain the training objective. The dashboard and logs also report *diagnostic* strategy outcomes: site-9 wins, Ring-bearer corruption/death, overwhelmed skirmishes, site progress/control, Ring-bearer burdens/wounds, burdens and threats added, companion casualties, and twilight generated during Fellowship versus spent during Shadow. These diagnostics are not added as arbitrary bonus rewards; players are scored by player identity while each plays both Fellowship and Shadow roles.

Completed simulation batches report accepted decisions from both players, including fallback and cycle-escape answers, per game (mean, nearest-rank median and p95, and maximum), decisions by phase and type, card-action passes, and selected transfer actions. The decision histogram and phase/type totals are included in the JSON metrics. An observed attachment reversal is a possession or artifact moving from one bearer to another and back during the same Fellowship phase and turn, as seen at successive decision boundaries. Effects can cause these changes too, and changes between observations may not be visible, so this is a diagnostic rather than a count of necessarily bad transfers. Skipped games do not contribute game-end metrics.

The training report and dashboard also attach cycle-recovery, invalid-answer,
model-fallback, transfer-guard, and shared-model Pass-veto counts to each
completed simulation batch, with the attempted-game denominator; the dashboard
expresses cycle recoveries per 1,000 attempted games. These counts are
diagnostic, not rewards or proof that a veto was beneficial.

During training, open [http://localhost:8765/](http://localhost:8765/) for a read-only progress page. Its individual-policy table reports each model's format win rate, aggregate confidence bound, held-out validation match rate versus the incumbent, skipped games, and promotion status. This is a more direct measure of game performance than how often a model repeats recorded choices, but it is still a self-play benchmark rather than a fixed external-opponent rating. The decision-model table separately shows the share of recorded choices matched on learning examples and on complete games kept aside for a check; these scores help diagnose training data and are not win rates. Random-baseline results are only a weak-play sanity check, not a promotion metric. Warnings, errors, and their stack traces remain available through `/api/events` while the page is running. The JSON endpoints are `/api/status` and `/api/events`.

Each run also writes an append-only JSON Lines report to `training.metrics.directory` (default `bot-training-runs`, relative to this properties file). A new `bot-training-<run-id>.jsonl` file is created per run. Its versioned records include the run configuration, plan, model and deck-builder results, individual policy comparisons, game batches and tactical summaries, stage outcomes, and all run events including warning/error details and stack traces. Per-format `training_data_totals` records and the dashboard distinguish input decisions, per-trainer records, uncompressed JSON bytes, actual on-disk append bytes (including compression), and deck-builder outcome examples. These count writes during this run, not retained corpus size or compaction; the dashboard's per-game denominator counts completed training batches, so ratios can temporarily include in-progress batch writes. `transfer_guard_totals` and `generic_pass_gate_totals` separately report repeat-transfer constraints and action-to-Pass vetoes; neither count alone demonstrates a gameplay improvement. The report is written as the run progresses, so completed metrics and events remain available if training stops before completion. Set `training.dashboard.port=0` to disable the progress server; metrics output is independent of the dashboard. For Docker, omit `--service-ports` so Compose does not publish the dashboard port. Docker publishes the dashboard only on the host loopback interface.

### Comparing per-batch rates

Compare two training JSONL reports with:

```powershell
node gemp-lotr\bot-benchmarks\compare-training-rates.cjs <baseline.jsonl> <candidate.jsonl>
```

The JSON output matches batches by format, generation, and label, then reports
win/skip rates, cycle/transfer/Pass/fallback rates per 1,000 attempted games,
and decision, phase/type, tactical-outcome, pass, transfer, and observed
attachment-reversal rates per completed game. Missing legacy metrics remain
`null`, not zero. The tool reports configuration
mismatches and allowed treatment toggles; `metadata-compatible` is not proof
that a pair is controlled. Current run metadata does not hash starting model
contents or the training corpus, and the evaluation seed does not control all
game randomness. Use only runs with independently verified inputs for causal
claims. The checked-in archive contains one full training report, so it cannot
by itself provide an A/B comparison.

### Diagnostic traces

Rare invalid-answer fallbacks and unresolvable decision cycles can add a
`bot_diagnostic_trace` record to the training JSONL. Traces contain the format,
phase, decision type, option counts, answer category/index, and process-keyed
fingerprints of the decision and the acting bot's visible state. They omit raw
prompts, option values, card/player IDs, answers, and hidden opponent state.
Only the first trace of each kind per simulation stage is retained. These
fingerprints support within-run correlation; they are not game replays, and
the historical King Block failure cannot be reconstructed without its original
trace and reproducible game inputs.

## Running training directly on the host

Docker is optional: training uses the project’s normal Java/Maven dependencies. From the repository root, install the reactor modules once (and again after source or dependency changes):

```sh
mvn -f gemp-lotr/pom.xml -pl gemp-lotr-server -am "-Dmaven.test.skip=true" install
```

Then launch the trainer with the properties file:

```powershell
$env:MAVEN_OPTS = '-Xms16g -Xmx32g'
mvn -f gemp-lotr/gemp-lotr-server/pom.xml org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.args=--config=gemp-lotr/bot-training.properties"
```

`exec:java` runs inside Maven's JVM, so set the heap with `MAVEN_OPTS` (not `-Dexec.args`, which only passes arguments to the trainer). The values above suit a machine with about 64 GB RAM; adjust them for your available memory. For another properties file, change the `--config` path. Test compilation is skipped for the install step; Docker also reuses the mounted Maven cache and project `target` directories, so its first run is the slowest.

In VS Code, choose **Run Bot Model Training** from Run and Debug. Its pre-launch task performs a clean compile of all Maven reactor modules before starting the trainer, so compilation must finish successfully before training starts. It uses `bot-training.properties` and the same `-Xms16g -Xmx32g` heap settings.

Training writes models into a unique, locked hidden workspace under `models.directory`; that directory's active release is untouched while games and model training run. After all four formats complete and at least one shared policy model exists, the trainer swaps in `public-observation-v6/` (using an atomic directory move when supported) and removes `.model` artifacts outside that active release within the configured directory. Failures before publication discard the workspace and preserve that directory's active release; abandoned workspaces are cleaned on the next run. Cleanup errors are reported rather than hidden. The usual live layout is `bot-models/public-observation-v6/movie-trilogy/` for shared policies and `bot-models/public-observation-v6/deck-builder/<format>/` for format-specific deck builders. New records are stored under `bot-training-data/public-observation-v8/<format>/`; new decision and deck-builder writes use gzip-compressed JSONL while legacy plain JSONL remains readable. Random-opponent policy decisions use the configured example weight and deck-ranker examples contain candidate-deck outcomes. The observation schema remains unchanged: pending-skirmish risk, combat-boost event inventory, visible hand/discard contents, and minion-recovery pressure are included. An opponent's hand contents are included only while revealed by a game effect; an opponent's discard contents are included only in formats with public discard piles. Draw-deck contents are never included; only deck counts are observed. Training files are compacted to the deterministic reservoir only when `training.data.compact.on.load=true`; runs refuse this option if compressed decision files already exist. Legacy Smile 2.6 artifacts and prior-schema models are not loaded. When no usable model is available, decisions use the safe legal base policy and generated decks use random legal sampling.

## Smile 7 and GPU

The project uses Smile 7.0.0 logistic-regression classifiers with fresh, versioned serialization. The 43 decision policies are logistic regression; MLP training and its configuration were removed because the comparison depended on a native BLAS runtime that is not bundled here and did not establish a reliable benefit. GPU/LibTorch is also not enabled: full-game simulation dominates training time, so another native runtime would add deployment cost without a measured speedup. Revisit a CPU MLP or GPU model only after benchmarking it against logistic regression on the same held-out game/deck splits and a representative training corpus.
