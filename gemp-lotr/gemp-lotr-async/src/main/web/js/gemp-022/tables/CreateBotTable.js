/**
 * Play ▸ Play Against Bots: a solo game against the bot, Fellowship Block unless the player unlocks other formats.
 * The player's deck may be one of theirs or a Deck Library deck; the bot may use a built-in, generated, or saved
 * player deck.  The player's deck is remembered per format ("bot-table-decks").
 */
class CreateBotTable extends TableFlow {
	playerDeckSelector = null;
	botDeckSelector = null;
	botDeckOptionsForFormat = null;

	unlockFormatsButton = null;
	formatDropdown = null;
	createTableButton = null;

	constructor(mainHall, comm, div, formatManager, deckManager) {
		super(mainHall, comm, div, formatManager, deckManager, $("#bot-result"));
		var that = this;

		this.playerDeckSelector = new SelectDeck(this.comm, $(div).find(".player-deck"), deckManager, "bot-table");
		this.botDeckSelector = new SelectDeck(this.comm, $(div).find(".bot-deck"), deckManager, "bot",
			{fixedOptions: [{id: "", label: "Random fixed bot deck"}]});

		this.unlockFormatsButton = $("#bot-unlock-format").button().click(() => {
			that.unlockFormatsButton.hide();
			that.formatDropdown.prop("disabled", false);
			that.mainHall.showDialog("Experimental bots", "Built-in and generated decks are available for Fellowship, Two Towers, King, and Movie Block. Deck legality is checked by the game format. The decision policy and learned deck builder are experimental; generated decks fall back to random legal construction until enough outcome data is available.<br/><br/>Other formats may allow user-provided decks, but do not have trained bot support.", 300);
		});
		this.formatDropdown = $("#bot-format");

		this.createTableButton = $("#submit-bot-table-button").button().click(this.submitTable(this));

		this.formatManager.registerFormatDropdownUpdate(this.formatDropdown);
		this.formatDropdown.on("change", () => {
			TableFlow.renderFormatInfo($("#help-bot-format"), that.formatManager, that.formatDropdown.val());
			that.refreshBotDeckOptions(that.formatDropdown.val());
		});
		this.refreshBotDeckOptions("fotr_block");
	}

	hide() {
		this.mainDiv.hide();
	}

	show() {
		var format = this.formatDropdown.prop("disabled") ? "fotr_block" : (this.formatDropdown.val() || "fotr_block");
		TableFlow.want(this.formatDropdown, format);
		this.playerDeckSelector.requestRestore(format);
		this.refreshBotDeckOptions(format);
		TableFlow.renderFormatInfo($("#help-bot-format"), this.formatManager, this.formatDropdown.val() || format);
		this.mainDiv.show();
	}

	refreshBotDeckOptions(format) {
		if (format == null || format === "")
			return;
		var that = this;
		this.botDeckOptionsForFormat = null;
		this.botDeckSelector.setFixedOptions([{id: "", label: "Loading bot deck choices..."}]);
		this.comm.getBotDeckOptions(format, function (options) {
			var currentFormat = that.formatDropdown.prop("disabled")
				? "fotr_block"
				: (that.formatDropdown.val() || "fotr_block");
			if (currentFormat !== format)
				return;
			that.botDeckOptionsForFormat = format;
			var fixedOptions = Array.isArray(options) ? options : [];
			if (fixedOptions.length == 0)
				fixedOptions = [{id: "", label: "No built-in bot decks for this format"}];
			that.botDeckSelector.setFixedOptions(fixedOptions);
		});
	}

	enableSubmission() {
		this.createTableButton.removeAttr("disabled");
		this.createTableButton.removeClass("ui-state-disabled")
	}

	disableSubmission() {
		this.createTableButton.attr("disabled", "disabled");
		this.createTableButton.addClass("ui-state-disabled")
		this.createTableButton.removeClass("ui-state-focus")
	}

	submitTable(that) {
		return () => {
			var format = that.formatDropdown.val();

			if(format == null || format === "") {
				that.updateResult("You must select a format.");
				return;
			}

			// The bot-deck options endpoint provides fixed/generated sentinels; ordinary names are saved user decks.
			var botDeck = that.botDeckSelector.getSelectedDeck();
			if (botDeck && botDeck.indexOf("__gemp_bot_") === 0
					&& that.botDeckOptionsForFormat !== format) {
				that.updateResult("Bot deck choices are still loading for this format.");
				return;
			}

			var yourDeck = that.playerDeckSelector.getSelection();

			if(yourDeck == null) {
				that.updateResult("You must select a deck for you to use: one of yours, or one from the Deck Library.");
				return;
			}

			that.playerDeckSelector.remember(yourDeck, format);

			// Success: the game opens from the hall as soon as it starts.
			var result = that.respond({
				title: "Create Bot Table",
				quietSuccess: true,
				onSuccess: function () {
					that.mainHall.tableCreator.closeAfterSuccess();
				}
			});
			that.comm.createSoloTable(format, yourDeck.name, botDeck, false, result.callback, result.errorMap, yourDeck.source);
		};
	}
}
