# LotR TCG rules reference for bot development

This is a bot-oriented digest of core *Lord of the Rings Trading Card Game* rules, organized for quick reference while developing game logic and decision policies. It paraphrases the rules rather than reproducing the full rules text, and is not a substitute for checking a disputed ruling.

**Primary source:** [Comprehensive Rules 4.2 - LOTR-TCG Wiki](https://wiki.lotrtcgpc.net/wiki/Comprehensive_Rules_4.2)

The source is the authority for complete rules, exceptions, examples, and card-specific rulings. Also check the applicable format rules, card text and errata, and this repository's implementation. GEMP supports multiple formats, and format-specific rules can change how a general rule applies.

## Game objective and sides

Each player alternates between the Free Peoples (Fellowship) and Shadow roles. The Fellowship advances a Ring-bearer along a path of nine sites; the Shadow player uses minions and other cards to stop the Fellowship. The Fellowship generally wins by reaching the final site with the Ring-bearer alive and uncorrupted. The Shadow wins if the Ring-bearer is killed or corrupted, subject to the active format's rules.

During a player's turn, that player uses Free Peoples cards and the other player uses Shadow cards. On the next turn they exchange roles. A decision policy must therefore evaluate both roles and must not assume that a player always controls the same side.

## Setup and starting state

Each player has a draw deck and an adventure deck containing the sites for that game. The Ring-bearer begins with The One Ring. Players determine who takes the first turn using the bidding procedure; the resulting burdens matter because they reduce the Ring-bearer's resistance.

After choosing the Ring-bearer, the starting player may choose additional companions from their draw deck, subject to the starting fellowship's total twilight-cost limit. The Ring-bearer's cost is excluded from that limit, and no twilight is added for setting up these characters. Starting companions' play abilities can still matter. Players then draw their opening hands and begin at site 1. Site 1 placement is setup, not an ordinary move.

Consult the current format for any setup changes, including deck construction and site requirements.

## Cards, zones, and characters

Important card types include:

- **Companions:** Free Peoples characters in the fellowship. Companion count can affect rules and card abilities; the Rule of 9 limits companions in play and the dead pile combined.
- **Allies:** Free Peoples characters in a support area. They are not companions and generally participate in archery or skirmishes only at their home site.
- **Minions:** Shadow characters played to oppose the fellowship. A minion played below its roaming site number can cost extra twilight.
- **Possessions and artifacts:** Attachments with bearer, class, culture, race, or character restrictions. Check those restrictions when playing or transferring them.
- **Conditions:** Persistent cards, often in a support area or attached to a character.
- **Events:** One-use cards. They resolve from the play process without becoming cards in play.
- **Sites:** The nine locations on the adventure path. A site has a shadow number and can have keywords that other cards reference.

Cards can be in a hand, draw deck, discard pile, dead pile, adventure path, support area, fellowship, or another game-defined zone. A card's zone determines whether it can be played, used, targeted, or spotted. A card stacked beneath another card is not considered in play. The dead pile is distinct from the discard pile.

Card names and uniqueness matter independently of subtitle. A uniqueness dot limits how many copies may be in play; cards without a dot are non-unique. Apply the exact copy limit indicated by the card and rules. A card in play can prevent playing another copy, while some effects allow a unique card in hand to be discarded to heal a copy in play.

## Ring-bearer and character state

The Ring-bearer is a companion bearing The One Ring. The Ring-bearer has special protections and cannot normally be discarded or returned to hand. A skirmish involving the Ring-bearer normally cannot be cancelled. If the Ring-bearer is killed or corrupted, the game ends for the Fellowship immediately.

Wounds are tracked against vitality. A character is normally killed when its wounds reach its vitality. Exertion adds a wound but cannot generally be chosen when the character has only one unwounded vitality remaining; a wound effect does not share that restriction. Healing removes wounds. Exhaustion is the state of having one vitality remaining before being killed, not a separate wound type.

Burdens are tracked on the Ring-bearer. They reduce resistance, and reaching zero resistance through burdens corrupts the Ring-bearer. Effects can add or remove burdens and can alter how resistance is evaluated.

Threats are tokens associated with the dead pile. Their number is limited by companion count. When a companion or ally is killed, threat effects may require the Free Peoples player to remove threats and assign wounds among companions. Allies are not eligible for every effect that refers to companions.

## Active cards and spotting

Which cards are active depends on turn and side. Ordinarily, a player's Free Peoples cards are active on that player's turn, while the opponent's Shadow cards are active. Inactive cards cannot be used for actions or counted for spotting.

To spot a card, it generally must be both active and in play. Events are not in play; stacked cards and cards in the dead pile are also not spot-able. When a card asks for a number of cards to spot, some effects let the player choose how many qualifying cards to count. A condition phrased as an automatic requirement can instead make the result mandatory. Check the wording and ruling for the specific effect.

## Turn flow

At the start of a turn, clear the twilight pool and perform applicable start-of-turn actions. At a sanctuary (normally site 3 or 6), the Fellowship can heal companions.

The normal phase order is:

1. **Fellowship:** The Fellowship player plays and uses Free Peoples cards and actions, then chooses whether to move.
2. **Shadow:** The opponent plays Shadow cards and uses Shadow actions.
3. **Maneuver:** Players take actions in turn until both pass consecutively.
4. **Archery:** Resolve archery totals and wound assignment, with applicable actions before the shot.
5. **Assignment:** The Fellowship player assigns companions first; the Shadow player then assigns any remaining minions, subject to assignment rules and keywords.
6. **Skirmish:** Resolve each skirmish, including actions, strength comparison, wounds, and resulting triggers. Fierce minions may fight again.
7. **Regroup:** Players take actions, then reconcile their hands and resolve the movement decision.

### Fellowship phase and movement

The Fellowship player may play Free Peoples cards and use Fellowship actions. Playing a card can add twilight for the opponent's Shadow phase. A rule limits how many cards can be drawn or taken into hand during this phase; start-of-turn draws are treated separately.

The player must move unless at a sanctuary. On a move, the Shadow player places the next site from their adventure deck and twilight is added for the site's shadow number and each companion. Movement can be repeated during a turn only within the applicable move limit. Movement effects and site replacement can change the ordinary path behavior.

### Shadow phase

The Shadow player plays minions, attachments, conditions, events, and other eligible Shadow cards, and can use Shadow actions. The twilight pool is the budget for Shadow card costs. The Shadow phase does not guarantee that all twilight is spent; unused twilight is cleared at the next turn's start.

### Maneuver phase

Players alternate available Maneuver actions. The phase ends after both players pass consecutively. If there are no minions after the Shadow phase, the game skips the combat phases and proceeds to Regroup.

### Archery phase

Each side totals eligible archers and applicable modifiers. After any archery actions, the sides assign archery wounds to opposing characters. Assignment restrictions and effects that modify archery totals or wounds must be applied before resolving the shot.

### Assignment phase

The Free Peoples player first assigns companions to minions. After assignment actions, the Shadow player assigns minions that remain unassigned to eligible companions. Assignment keywords and card effects modify this process:

- **Defender +X** allows a character to take additional assignments.
- **Lurker** prevents ordinary assignment to that minion.
- **Ambush** can let the Shadow player assign a minion by paying twilight.
- **Stealth** can provide a way to avoid assignment.
- **Unhasty** can restrict the Free Peoples player's ability to assign a companion, with exceptions defined by the rules or card effects.

Resolve assignment actions in their proper windows; assignments determine the skirmishes that follow.

### Skirmish phase

The Free Peoples player chooses the order in which skirmishes are resolved. In each skirmish, players may use eligible actions, then compare the total strength of the opposing sides. The higher strength wins; a tie is a Shadow victory. The losing side normally takes a wound, and the winner's damage bonus can add wounds.

When one side has at least twice the opposing side's strength, the loser is normally overwhelmed and killed rather than taking only the ordinary loss wound. A positive strength can overwhelm a side at zero strength. If both sides are at zero, Shadow wins but does not overwhelm. Keywords such as Enduring can change the consequence of overwhelm.

Fierce minions can fight again after normal skirmishes, following a new assignment process. A character removed during a skirmish is not automatically treated as its side's loser; if a whole side is removed, the remaining characters and timing of removal affect the result. Resolve all applicable win/loss triggers and responses before leaving the skirmish phase.

### Regroup, reconciliation, and site 9

Players alternate Regroup actions. The Free Peoples player then reconciles their hand to the required size, with the applicable option to discard and draw. The Shadow player discards minions as required by the phase. The Free Peoples player decides whether to move again, if allowed; a new move leads into another Shadow phase.

Site 9 has special endgame timing: there is no ordinary Fellowship phase or Regroup after the final move. The Fellowship wins if the Ring-bearer survives the required final combat. The exact sequence and win checks should be confirmed against the format's game flow.

## Twilight and card play

- Playing a Free Peoples card generally adds its twilight cost to the pool. Playing a Shadow card spends twilight from the pool and requires enough twilight to pay its cost.
- Moving adds the next site's shadow number and one twilight per companion. The pool is cleared at the next turn's start.
- Costs can be modified, but a twilight cost cannot be reduced below zero. Effects that modify the cost of a card generally apply as that card is played, not retroactively to cards already in play.
- A card's playable requirements and costs are checked before its effects resolve. Costs can include twilight, exertions, or discards.
- Events resolve without entering play. Other cards generally enter play after being played and paid for.
- Transferring an attachment is not the same as playing it; do not fire play triggers merely because it changed bearers.
- Minions played below their roaming site number can incur an additional twilight cost.
- Some keywords, such as Toil, can offer a way to reduce a card's cost by paying with characters. Confirm eligibility and payment before applying the reduction.

The play process includes requirements, payment, placing the card in its appropriate zone, response opportunities, and effect resolution. Triggered actions and replacement or prevention effects can alter what happens at these points. Do not treat playing a card as a single state change that bypasses costs or response timing.

## Combat and character state

- A character has vitality; wounds accumulate against it. When wounds reach vitality, it is killed unless an effect changes that outcome.
- Exerting adds a wound but is restricted when the character is nearly exhausted. Wounding does not have the same exertion restriction.
- In archery, each side's total is based on its eligible archers and modifiers. Wounds are assigned to opposing characters according to the applicable rules.
- In a skirmish, compare the total strength of the two sides. The higher side wins; a tie goes to Shadow. The losing side normally takes a wound, with damage bonuses adding wounds.
- Overwhelm occurs when one side's strength is at least double the opposing side's strength; the losing character is normally killed instead of taking the ordinary loss wound. Special keywords such as Enduring can alter this.
- Assignment is not just strength-based: Defender, Lurker, Ambush, Stealth, Unhasty, and other effects can change who may assign whom or how assignments are made.
- Characters removed during a skirmish can change whether it continues or how its outcome is determined. Resolve triggers and response actions at the correct timing point before advancing.

## Ring-bearer, burdens, threats, and spotting

- Burdens reduce the Ring-bearer's resistance. If burdens reach resistance, the Ring-bearer is corrupted and the Fellowship loses.
- The Ring-bearer cannot normally be discarded or returned to hand, and a skirmish involving the Ring-bearer cannot normally be cancelled. Check the format and card text for exceptions.
- Threats are tokens associated with the dead pile. When a companion or ally is killed, threats can cause wounds to be assigned to companions; threat limits and resolution details matter.
- A card is generally spot-able only while it is in play and active. Events, stacked cards, and cards in the dead pile are not in play and cannot be spotted.
- Activity depends on whose turn it is: the acting player's Free Peoples cards and the opposing player's Shadow cards are normally active. Cards in inactive zones should not be treated as available for actions or spotting.

## Combat keywords and other modifiers

Keywords are rules, not flavor text. The following commonly affect bot action legality or outcome evaluation:

- **Archer:** contributes to an archery total.
- **Damage +X:** adds wounds when its side wins a skirmish.
- **Defender +X:** permits additional assignments.
- **Fierce:** can create a second skirmish.
- **Enduring:** changes the normal result of being overwhelmed.
- **Hunter X:** provides a strength bonus based on the applicable count and rules.
- **Ambush (X):** can pay twilight to make an assignment and may interact with other effects.
- **Lurker:** restricts normal assignment.
- **Toil X:** can reduce a play cost by exerting eligible same-culture characters.
- **Ring-bound / Unbound:** classify companions for card effects.

Site keywords (for example, terrain or Sanctuary) and character classifications (for example, Ranger or Knight) are often conditions for card effects. Never assume a keyword grants a universal bonus; evaluate the specific rule or ability that refers to it.

## Hand, initiative, and card limits

The Free Peoples player has initiative when holding at least four cards; otherwise the Shadow player has initiative. Cards can check initiative, so update it whenever hand size changes.

The Rule of 4 restricts how many cards a player may draw or take into hand during the Fellowship phase. The Rule of 9 limits companions in play plus companions in the dead pile. These rules are distinct and should be represented independently in legality checks.

## Format differences and regions

The nine sites are grouped into three regions (sites 1-3, 4-6, and 7-9). Region movement costs are used in some formats, but not uniformly across all blocks. Site movement, cancellation rules, starting rules, deck requirements, and end-of-game flow can all be format-dependent. Check the chosen format's `Adventure` and other rule configuration instead of applying one format's behavior globally.

## Action and response timing

During action phases, players alternate actions until both pass in succession. A trigger may create mandatory or optional actions at a particular timing point. Some effects occur before an event; prevention stops that event, while replacement substitutes another outcome. If an effect replaces the original event, responses that depended on that original event may no longer be applicable.

For a bot, an action is not merely a card selection. It may include a sequence of costs, choices, effects, and responses. Keep the engine's decision queue and effect order authoritative rather than simulating only the visible outcome.

## Bot implementation checklist

- Enumerate legal actions from the current game state; do not infer legality from card text alone. Requirements, costs, active/inactive status, phase, site, modifiers, and format rules all matter.
- Treat decisions as timing-sensitive. Resolve required actions and responses before taking the next phase or state transition; optional actions may require a player choice.
- Track separate state for wounds, vitality, burdens, resistance, threats, twilight, assignments, skirmish order, and site position. A single "health" or "combat score" value is not enough.
- Evaluate the entire skirmish outcome, including tie-breaking, overwhelm, damage, special keywords, and triggered effects. Do not assume the strength winner is always the last character standing.
- Keep format rules configurable or query the format implementation. Rules such as region movement, cancellation, starting fellowship, and end-of-game timing are not necessarily shared by every supported format.
- For changes to engine behavior, add or update scenario tests for the rules edge cases as well as the obvious outcome.

For authoritative detail, use the source above and inspect the relevant game-process, action, and format code before changing bot behavior.
