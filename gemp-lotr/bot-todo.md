# Bot TODO List

## Notes

### Card scope

The primary focus for the bot is to be able to play the core game card sets associated with the three movies blocks as well as including up to the Reflections block. All game modes in this collection of cards are what the end goal is to be trained for.

## Code cleanup

- Remove `policy-promotion.marker` (the only thing in the v4 folder)?

## Bugs / Issues

- Keep improving the bot training UI, the current state is not well aligned with the training workflow and some sections seem broken.

- The `training.model.threads` property does not seem to make any major impact on training performance/speed.

- [x] Add all starter decks from all three movie trilogy sets to the library account for the dev account. All 18 official starter products are seeded idempotently; see the implementation roadmap validation log.

- Error:

  ```text
  com.gempukku.lotro.bots.simulation.MovieBlockSimulation$UnresolvableDecisionCycleException: Detected repeated bot decision cycle in king_block but found no distinct legal escape answer: ~deck-train-king_block-16-3-two/SKIRMISH CARD_SELECTION: Choose cards to modify strength of -> 183 (cycle length 1, repeated 4 times)
    at com.gempukku.lotro.bots.simulation.MovieBlockSimulation.getDecisionAndDecide(MovieBlockSimulation.java:228)
    at com.gempukku.lotro.bots.simulation.MovieBlockSimulation.simulateGameInternal(MovieBlockSimulation.java:133)
    at com.gempukku.lotro.bots.simulation.MovieBlockSimulation.simulateGame(MovieBlockSimulation.java:84)
    at com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer.evaluateCandidate(DeckBuilderTrainer.java:314)
    at com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer.lambda$train$1(DeckBuilderTrainer.java:109)
  ```

- [x] Make these DAO checks configurable: scheduled-tournament and
  league-notification polling default to off in the H2 development override
  and on in production; enabled/disabled paths are tested.

  ```text
  TournamentDAO::getUnstartedScheduledTournamentQueues(...) 0ms
  TransferDAO::hasUndeliveredLeagueNotifications(...) 0ms
  ```

- Have a config to turn the discord panel off for the Game Hall (the section at the bottom of the screen). For dev this should be off by default in the config.

## Features / Ideas

- The bot might benefit hugely if it had a way to perform all relevant actions once (there usually aren't that many to choose from) and then evaluate the board state to see which outcome is better. In other words, it could simulate different sequences of actions and choose the one that leads to the most favorable result. This will require the ability to build up a game decision tree and evaluate the outcomes of different action sequences, and will need a well grounded way of scoring the state of the board. There are likely some metrics available that can help with this scoring. The risk would be if the bot learns to maximise this score instead of winning the game, but at the moment the bot isn't making very good decisions to even start with.

- The ability to better score the state of the board would help immensely during training because it would allow for getting feedback after individual moves/turns instead of only at the end of the game. I can see how maybe over time, as the bot learns about taking good enough actions the scoring can be moved from scoring every decision/action to only scoring at key decision points or at the end of each turn. Maybe choosing between policy/model candidates can be judged on the outcome of the game only, to avoid problems. This can maybe take the form of a sliding scale during training, where early on every action is scored, and later only critical actions or end-of-turn states are scored. As well as over time as the model starts getting better the slope of the scale might be changed accordingly.
  - Some metrics are almost universal / robust enough to give a good baseline to work from and might give strong guidance for the bot to avoid some really bad choices it currently makes.
    - The amount of wounds.
    - The amount of burdens.
    - The amount of threats.
    - The number of characters (fellowship vs shadow) in play.
    - The twilight available.
    - The strength among characters in play.
    - The number of cards in hand for each player.

- Have a separate mode for just training the deck builder. It can use the same stable bot the whole time for evaluating candidate decks. It can look at the existing starter decks to improve its deck-building training.
  - Even though multi-culture decks can be fun the game generally favors more focused decks, so the bot should take that into consideration when building decks.

- Does the bot know what card format / block it is being trained on? It might help it if it knows.

- In many cases there are simple algorithms that can provide immense guidance to the bot, such as filling the 4 twilight cost for starting fellowship or prioritising killing fellowship characters in skirmishes.

- It might be a good idea to cap the bot to only select up to 4 burdens, more than that is almost never very safe and going first/second doesn't seem to be a huge advantage in most cases.

- Have a way to maybe pre-fetch the card images and store them locally for faster access during gameplay (and to be a backup in case the wiki goes down). There can be a config to choose if the wiki URL or local URL should be prioritized, with the other being a fallback. It will also need a mechanism to keep the local cache updated with any new or changed images. And I would image it might work like the bot training where it can start on its own (add config to VS Code launch script) and have a simple web UI to show the status of the image cache and trigger the download process.

## Game concepts to improve the bot's understanding of

Below is a list of gameplay "common sense" that the bot should try and be trained to know more about.

### Prevent unrelated skirmish actions

Not sure if the fix is in the game logic or the bot's decision-making process.

Currently the game will suggest using cards during a skirmish even if they are not relevant to the current skirmish. For example to give a dwarf strength +2 when the active skirmish does not involve any dwarves. For human play this can be useful as it is a "trick" to get rid of cards in your hand (double check the rules on this, but I think that is why the game engine allows it). However, for the bot, it would be better if it only considered actions that are relevant to the current skirmish. Especially things like strength boosts or damage modifiers that have specific target requirements.

Again if the board state can be scored then it might help to prevent making one of the common mistakes when a skirmish event or action is performed that boosts the strength of a character but the game doesn't restrict it to characters involved in the current skirmish. For some fancy human tricks this might be useful, but in the fast majority of cases it is better to only consider relevant actions for the current skirmish, especially for strength boosts and damage modifiers.

### Avoid transferring possessions

The bot should be trained to avoid moving weapons unnecessarily. In most cases, moving a possession from one character to another is not beneficial and can even be detrimental. The bot should consider the current characters in play before deciding to move a possession. Sometimes the bot moves the item just to then move it back again. This gameplay loop happens a lot and should be minimized.

This is a huge problem at the moment as it results in the bot generating a massive twilight pool unnecessarily, especially early in the game and for deck that are possession-heavy. It results in them being defeated more easily. Extreme action like preventing possession moves entirely would likely improve the bot's performance significantly compared to a bad model that moved possessions indiscriminately.

That said it would be good if the bot was able to move possessions, maybe restricting a possession to be moved only once is a good way to go and if a metric is introduced that can evaluate fellowship board state then that might be a good tool to use to decide whether moving a possession is beneficial or not.

### Able to take no action

The bot should be trained to recognize situations where taking no action is the optimal choice. In some cases, making a move can be detrimental, and the best strategy is to pass the turn or refrain from using cards. The bot should evaluate the potential outcomes of all available actions and compare them to the outcome of doing nothing, ensuring it only acts when it is advantageous to do so. Just because an action is available does not mean it should be taken.

### Cycling cards in hand

It is generally advantageous for a player to cycle cards in hand when it has cards that are not immediately useful or when it can gain a strategic advantage by drawing new cards. A player's hand gets filled back up after a turn and that a hand full of unplayable fellowship or shadow cards can severely impact the players shadow or fellowship phases respectively. The bot should be trained to recognize situations where discarding and drawing new cards can improve its overall hand quality and increase its chances of success in future turns.

### Starting fellowship

The bot seems to play a very bad starting fellowship, unaware of the game rules for maximising the starting fellowship's twilight cost. It should be trained to select characters that provide the best balance of strength, abilities, and synergy. While keeping to the maximum allowed cost. I can see this being trained repeatedly on its own in addition to being part of the overall deck-building and gameplay training. Given a combination of available characters and the maximum allowed cost, the bot should be able to determine the optimal starting fellowship. Currently it plays starting fellowships that cost 3 while it has combinations available to use the full 4. Often times unique characters with only one copy it meant to be the starting fellowship. A simple algorithm with a few checks to the number of copies, uniqueness, and total cost could significantly improve the bot's starting fellowship selection.

## Keep the game engine stable

Overall these bot related improvements and training strategies should be implemented in a way that keeps the game engine stable. To help prevent bot related code requirements from seeping into the core game logic it might be good to split out the training classes into their own Maven module. This separation ensures that the core game mechanics remain unaffected by experimental bot training code and allows for easier maintenance and updates to the bot's intelligence without risking the stability of the game engine itself. The training module can house documentation to explain the training process and guide future development and improvements.
