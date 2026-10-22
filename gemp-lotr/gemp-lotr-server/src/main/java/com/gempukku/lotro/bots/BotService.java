package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.gempukku.lotro.db.DeckSerialization;
import com.gempukku.lotro.db.LoginInvalidException;
import com.gempukku.lotro.db.PlayerDAO;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.gempukku.lotro.packs.ProductLibrary;
import smile.classification.Classifier;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BotService {
    private static final Logger LOG = LogManager.getLogger(BotService.class);
    private static final List<String> SUPPORTED_BLOCK_FORMATS =
            List.of("fotr_block", "ttt_block", "king_block", "movie");

    public static final String GENERAL_BOT_NAME = "~bot";
    public static final String GENERATED_BOT_DECK_SELECTION = "__gemp_bot_generated_deck__";
    private static final String FIXED_BOT_DECK_SELECTION_PREFIX = "__gemp_bot_fixed_deck__:";
    private static final int GENERATED_DECK_CANDIDATE_POOL_SIZE = 16;

    private final LotroCardBlueprintLibrary library;
    private final LotroFormatLibrary formatLibrary;
    private final PlayerDAO playerDAO;
    private final ProductLibrary productLibrary;

    private final Random random = new Random();
    private final Map<String, List<BotWithDeck>> formatBotsMap = new HashMap<>();

    private final Map<String, ModelRegistry> modelRegistries = new ConcurrentHashMap<>();
    private final Map<String, Classifier<double[]>> deckBuilderModels = new ConcurrentHashMap<>();

    public BotService(
            LotroCardBlueprintLibrary library,
            LotroFormatLibrary formatLibrary,
            PlayerDAO playerDAO,
            ProductLibrary productLibrary) {
        this.library = library;
        CardFeatures.init(library);
        this.formatLibrary = formatLibrary;
        this.playerDAO = playerDAO;
        this.productLibrary = productLibrary;

        loadBotModels();
        fillBotParticipantList();
    }

    private void loadBotModels() {
        List<Trainer> trainers = FotrStarterTrainerCatalog.createAll();
        Path baseDirectory = ModelIO.getModelDirectory();
        for (String formatCode : SUPPORTED_BLOCK_FORMATS) {
            Path modelDirectory = ModelIO.resolveModelDirectory(baseDirectory, formatCode);
            Path deckBuilderDirectory = ModelIO.resolveDeckBuilderDirectory(baseDirectory, formatCode);
            ModelRegistry registry = new ModelRegistry();
            List<String> missingModels = new ArrayList<>();
            for (Trainer trainer : trainers) {
                try {
                    registry.registerModel(trainer.getClass(),
                            ModelIO.loadModel(trainer.getClass(), modelDirectory));
                } catch (IllegalStateException e) {
                    missingModels.add(trainer.getClass().getSimpleName());
                }
            }
            modelRegistries.put(formatCode, registry);
            try {
                Classifier<double[]> deckBuilderModel =
                        ModelIO.loadModel(DeckBuilderTrainer.class, deckBuilderDirectory);
                deckBuilderModels.put(formatCode, deckBuilderModel);
                LOG.info("Loaded deck-builder model for {} from {}", formatCode, deckBuilderDirectory);
            } catch (IllegalStateException e) {
                LOG.info("No deck-builder model for {} at {}; generated decks use random legal sampling",
                        formatCode, deckBuilderDirectory);
            }
            if (missingModels.isEmpty()) {
                LOG.info("Loaded {} bot models for {} from {}", trainers.size(), formatCode, modelDirectory);
            } else {
                LOG.warn("Loaded {}/{} bot models for {} from {}. Missing models use the safe base policy: {}",
                        registry.getRegisteredTrainerTypes().size(), trainers.size(), formatCode,
                        modelDirectory, String.join(", ", missingModels));
            }
        }
    }

    public BotWithDeck getBotParticipant(LotroFormat lotroFormat) {
        List<BotWithDeck> botList = formatBotsMap.get(lotroFormat.getCode());
        if (botList == null || botList.isEmpty()) {
            return null;
        }
        BotWithDeck selected = botList.get(random.nextInt(botList.size()));
        String botName = selected.getBotPlayer().getName();
        return new BotWithDeck(
                new FotrStarterBot(featuresFor(lotroFormat.getCode()), botName,
                        modelRegistryFor(lotroFormat.getCode()), null),
                copyDeck(selected.getLotroDeck()));
    }

    public boolean hasBotParticipant(LotroFormat format) {
        return formatBotsMap.containsKey(format.getCode());
    }


    public BotWithDeck getBotForDeck(LotroDeck deck) {
        String formatCode = Objects.requireNonNullElse(deck.getTargetFormat(), "");
        String selection = deck.getDeckName();
        if (GENERATED_BOT_DECK_SELECTION.equals(selection))
            return createGeneratedBot(formatCode);

        Integer fixedIndex = fixedDeckIndex(selection);
        if (fixedIndex != null)
            return createFixedBot(formatCode, fixedIndex);

        return new BotWithDeck(new FotrStarterBot(
                featuresFor(formatCode), GENERAL_BOT_NAME, modelRegistryFor(formatCode), null), deck);
    }

    public List<BotDeckOption> getBotDeckOptions(String formatCode) {
        List<BotWithDeck> botList = formatBotsMap.get(formatCode);
        if (botList == null || botList.isEmpty())
            return List.of();

        List<BotDeckOption> options = new ArrayList<>(botList.size() + 2);
        options.add(new BotDeckOption("", "Random fixed bot deck"));
        options.add(new BotDeckOption(GENERATED_BOT_DECK_SELECTION, "Model-guided generated deck"));
        for (int i = 0; i < botList.size(); i++)
            options.add(new BotDeckOption(fixedDeckSelection(i), botList.get(i).getLotroDeck().getDeckName()));
        return List.copyOf(options);
    }

    public static String fixedDeckSelection(int index) {
        if (index < 0)
            throw new IllegalArgumentException("Fixed bot deck index cannot be negative");
        return FIXED_BOT_DECK_SELECTION_PREFIX + index;
    }

    public static boolean isBotDeckSelection(String selection) {
        return GENERATED_BOT_DECK_SELECTION.equals(selection) || fixedDeckIndex(selection) != null;
    }

    public static LotroDeck createBotDeckSelectionMarker(String formatCode, String selection) {
        if (!isBotDeckSelection(selection))
            throw new IllegalArgumentException("Unknown bot deck selection: " + selection);
        LotroDeck marker = new LotroDeck(selection);
        marker.setTargetFormat(formatCode);
        return marker;
    }

    private BotWithDeck createGeneratedBot(String formatCode) {
        LotroFormat format = formatLibrary.getFormat(formatCode);
        if (format == null)
            throw new IllegalArgumentException("Unsupported generated bot deck format: " + formatCode);
        LotroDeck generatedDeck;
        try {
            generatedDeck = DeckBuilderTrainer.createDeck(format, library,
                    deckBuilderModels.get(formatCode), GENERATED_DECK_CANDIDATE_POOL_SIZE, random.nextLong());
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Deck-builder prediction failed for {}; generating a random legal deck instead",
                    formatCode, e);
            generatedDeck = DeckBuilderTrainer.createDeck(format, library, null, 1, random.nextLong());
        }
        return new BotWithDeck(new FotrStarterBot(
                featuresFor(formatCode), GENERAL_BOT_NAME, modelRegistryFor(formatCode), null), generatedDeck);
    }

    private BotWithDeck createFixedBot(String formatCode, int index) {
        List<BotWithDeck> botList = formatBotsMap.get(formatCode);
        if (botList == null || index >= botList.size())
            throw new IllegalArgumentException("Unknown fixed bot deck " + index + " for " + formatCode);
        return new BotWithDeck(new FotrStarterBot(
                featuresFor(formatCode), GENERAL_BOT_NAME, modelRegistryFor(formatCode), null),
                copyDeck(botList.get(index).getLotroDeck()));
    }

    private static Integer fixedDeckIndex(String selection) {
        if (selection == null || !selection.startsWith(FIXED_BOT_DECK_SELECTION_PREFIX))
            return null;
        try {
            int index = Integer.parseInt(selection.substring(FIXED_BOT_DECK_SELECTION_PREFIX.length()));
            return index >= 0 ? index : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LotroDeck copyDeck(LotroDeck deck) {
        return DeckSerialization.buildDeckFromContents(
                deck.getDeckName(),
                DeckSerialization.buildContentsFromDeck(deck),
                deck.getTargetFormat(),
                deck.getNotes());
    }

    private void fillBotParticipantList() {
        try {
            playerDAO.registerBot(GENERAL_BOT_NAME);
            for (String formatCode : SUPPORTED_BLOCK_FORMATS) {
                LotroFormat format = formatLibrary.getFormat(formatCode);
                if (format == null)
                    throw new IllegalStateException("Missing movie block format " + formatCode);

                List<LotroDeck> botDecks = MovieBlockDeckFactory.createStarterDecks(
                        formatCode, format, library, productLibrary);
                List<BotWithDeck> bots = new ArrayList<>(botDecks.size());
                for (int i = 0; i < botDecks.size(); i++) {
                    String botName = botName(formatCode, i);
                    playerDAO.registerBot(botName);
                    bots.add(new BotWithDeck(
                            new FotrStarterBot(featuresFor(formatCode), botName,
                                    modelRegistryFor(formatCode), null),
                            botDecks.get(i)));
                }
                formatBotsMap.put(formatCode, bots);
            }
        } catch (LoginInvalidException e) {
            throw new IllegalStateException("Wrong bot name - must start with ~ character.");
        }
    }

    private static String botName(String formatCode, int index) {
        if ("fotr_block".equals(formatCode) && index < 2)
            return index == 0 ? "~AragornBot" : "~GandalfBot";
        String prefix = switch (formatCode) {
            case "fotr_block" -> "~FotrBlockBot";
            case "ttt_block" -> "~TttBlockBot";
            case "king_block" -> "~KingBlockBot";
            case "movie" -> "~MovieBlockBot";
            default -> throw new IllegalArgumentException("Unsupported movie block format " + formatCode);
        };
        return prefix + (index + 1);
    }

    private ModelRegistry modelRegistryFor(String formatCode) {
        return modelRegistries.computeIfAbsent(formatCode, ignored -> new ModelRegistry());
    }

    private com.gempukku.lotro.bots.rl.RLGameStateFeatures featuresFor(String formatCode) {
        return new MovieTrilogyRLGameStateFeatures(formatCode);
    }

    public class BotWithDeck {
        private BotPlayer botPlayer;
        private LotroDeck lotroDeck;

        public BotWithDeck(BotPlayer botPlayer, LotroDeck lotroDeck) {
            this.botPlayer = botPlayer;
            this.lotroDeck = lotroDeck;
        }

        public BotPlayer getBotPlayer() {
            return botPlayer;
        }

        public LotroDeck getLotroDeck() {
            return lotroDeck;
        }
    }

    public record BotDeckOption(String id, String label) {
    }
}
