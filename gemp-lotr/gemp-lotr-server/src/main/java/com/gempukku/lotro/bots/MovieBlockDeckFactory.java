package com.gempukku.lotro.bots;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.db.DeckSerialization;
import com.gempukku.lotro.game.CardCollection;
import com.gempukku.lotro.game.LotroCardBlueprint;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.gempukku.lotro.packs.PackBox;
import com.gempukku.lotro.packs.ProductLibrary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class MovieBlockDeckFactory {
    private static final int DECK_GENERATION_ATTEMPTS = 500;
    private static final List<String> FELLOWSHIP_STARTERS = List.of(
            "FotR - Aragorn Starter", "FotR - Gandalf Starter",
            "MoM - Gandalf Starter", "MoM - Gimli Starter",
            "RotEL - Boromir Starter", "RotEL - Legolas Starter");
    private static final List<String> TOWERS_STARTERS = List.of(
            "TTT - Aragorn Starter", "TTT - Theoden Starter",
            "BoHD - Eowyn Starter", "BoHD - Legolas Starter",
            "EoF - Faramir Starter", "EoF - Witch-king Starter");
    private static final List<String> KING_STARTERS = List.of(
            "RotK - Aragorn Starter", "RotK - Eomer Starter",
            "SoG - Merry Starter", "SoG - Pippin Starter",
            "MD - Frodo Starter", "MD - Sam Starter");

    private MovieBlockDeckFactory() {
    }

    public static List<LotroDeck> createStarterDecks(
            String formatCode,
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            ProductLibrary productLibrary) {
        List<String> productNames = switch (formatCode) {
            case "fotr_block" -> FELLOWSHIP_STARTERS;
            case "ttt_block" -> TOWERS_STARTERS;
            case "king_block" -> KING_STARTERS;
            case "movie" -> java.util.stream.Stream.of(
                    FELLOWSHIP_STARTERS, TOWERS_STARTERS, KING_STARTERS)
                    .flatMap(List::stream)
                    .toList();
            default -> throw new IllegalArgumentException("Unsupported bot block format: " + formatCode);
        };
        List<LotroDeck> decks = new ArrayList<>(productNames.size());
        for (String productName : productNames) {
            PackBox product = productLibrary.GetProduct(productName);
            if (product == null)
                throw new IllegalStateException("Missing official starter product: " + productName);

            LotroDeck deck = new LotroDeck(productName);
            deck.setTargetFormat(formatCode);
            for (CardCollection.Item item : product.openPack()) {
                if (item.getType() != CardCollection.Item.Type.CARD)
                    throw new IllegalStateException("Starter product " + productName
                            + " contains a non-card item: " + item.getBlueprintId());
                String blueprintId = item.getBlueprintId();
                LotroCardBlueprint blueprint = library.getBaseCards().get(blueprintId);
                if (blueprint == null)
                    throw new IllegalStateException("Starter product " + productName
                            + " references unknown card " + blueprintId);

                for (int count = 0; count < item.getCount(); count++) {
                    if (blueprint.getCardType() == CardType.SITE) {
                        deck.addSite(blueprintId);
                    } else if (blueprint.getCardType() == CardType.THE_ONE_RING) {
                        if (deck.getRing() != null)
                            throw new IllegalStateException("Starter product " + productName
                                    + " contains multiple One Rings");
                        deck.setRing(blueprintId);
                    } else if (blueprint.getCardType() == CardType.COMPANION
                            && blueprint.canStartWithRing()) {
                        if (deck.getRingBearer() != null)
                            throw new IllegalStateException("Starter product " + productName
                                    + " contains multiple Ring-bearers");
                        deck.setRingBearer(blueprintId);
                    } else {
                        deck.addCard(blueprintId);
                    }
                }
            }

            decks.add(format.validateDeck(deck).isEmpty()
                    ? deck
                    : makeStarterFormatLegal(productName, formatCode, format, library, deck));
        }
        return List.copyOf(decks);
    }

    private static LotroDeck makeStarterFormatLegal(
            String productName,
            String formatCode,
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            LotroDeck starter) {
        DeckPools pools = createPools(format, library);
        Random random = new Random(31L * productName.hashCode() + formatCode.hashCode());
        CardOption ringBearer = find(pools.ringBearers(), starter.getRingBearer());
        if (ringBearer == null)
            ringBearer = choose(pools.ringBearers(), random);
        CardOption ring = find(pools.rings(), starter.getRing());
        if (ring == null)
            ring = choose(pools.rings(), random);

        List<CardOption> sites = new ArrayList<>(9);
        for (int siteNumber = 1; siteNumber <= 9; siteNumber++) {
            int requiredSiteNumber = siteNumber;
            CardOption originalSite = starter.getSites().stream()
                    .map(blueprintId -> new CardOption(blueprintId, library.getBaseCards().get(blueprintId)))
                    .filter(option -> option.blueprint() != null
                            && option.blueprint().getSiteNumber() == requiredSiteNumber
                            && format.validateSite(option.blueprintId()) == null)
                    .findFirst()
                    .orElse(null);
            sites.add(originalSite == null
                    ? choose(pools.sitesByNumber().get(siteNumber), random)
                    : originalSite);
        }

        int freePeopleCount = (pools.drawDeckSize() + 1) / 2;
        int shadowCount = pools.drawDeckSize() - freePeopleCount;
        List<CardOption> freePeopleCards = starter.getDrawDeckCards().stream()
                .map(blueprintId -> new CardOption(blueprintId, library.getBaseCards().get(blueprintId)))
                .filter(option -> option.blueprint() != null
                        && option.blueprint().getSide() == Side.FREE_PEOPLE
                        && format.validateCard(option.blueprintId()) == null)
                .limit(freePeopleCount)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<CardOption> shadowCards = starter.getDrawDeckCards().stream()
                .map(blueprintId -> new CardOption(blueprintId, library.getBaseCards().get(blueprintId)))
                .filter(option -> option.blueprint() != null
                        && option.blueprint().getSide() == Side.SHADOW
                        && format.validateCard(option.blueprintId()) == null)
                .limit(shadowCount)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Set<String> selectedTitles = new HashSet<>();
        selectedTitles.add(ringBearer.blueprint().getTitle());
        freePeopleCards.forEach(card -> selectedTitles.add(card.blueprint().getTitle()));
        shadowCards.forEach(card -> selectedTitles.add(card.blueprint().getTitle()));
        addRandomCards(freePeopleCards, pools.freePeople(),
                freePeopleCount - freePeopleCards.size(), selectedTitles, Set.of(), random);
        addRandomCards(shadowCards, pools.shadow(),
                shadowCount - shadowCards.size(), selectedTitles, Set.of(), random);

        LotroDeck legalDeck = new LotroDeck(productName + " (" + formatCode + " legal)");
        legalDeck.setTargetFormat(formatCode);
        legalDeck.setRingBearer(ringBearer.blueprintId());
        legalDeck.setRing(ring.blueprintId());
        for (CardOption site : sites)
            legalDeck.addSite(site.blueprintId());
        for (CardOption card : freePeopleCards)
            legalDeck.addCard(card.blueprintId());
        for (CardOption card : shadowCards)
            legalDeck.addCard(card.blueprintId());

        List<String> validationErrors = format.validateDeck(legalDeck);
        if (!validationErrors.isEmpty())
            throw new IllegalStateException("Official starter " + productName + " could not be made legal in "
                    + formatCode + ": " + String.join("; ", validationErrors));
        return legalDeck;
    }

    private static CardOption find(List<CardOption> options, String blueprintId) {
        if (blueprintId == null)
            return null;
        return options.stream().filter(option -> option.blueprintId().equals(blueprintId))
                .findFirst().orElse(null);
    }

    public static List<LotroDeck> createBotDecks(
            String formatCode,
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            int deckCount,
            long seed) {
        if (deckCount < 1)
            throw new IllegalArgumentException("At least one bot deck must be requested");

        if ("fotr_block".equals(formatCode))
            return createFellowshipStarterDecks(deckCount);
        if (!List.of("ttt_block", "king_block", "movie").contains(formatCode))
            throw new IllegalArgumentException("Unsupported bot block format: " + formatCode);

        DeckPools pools = createPools(format, library);
        Random random = new Random(seed);
        List<LotroDeck> decks = new ArrayList<>(deckCount);
        for (int i = 0; i < deckCount; i++)
            decks.add(createLegalDeck(formatCode, format, pools, random, i + 1));
        return List.copyOf(decks);
    }

    public static LotroDeck createRandomLegalDeck(
            String formatCode,
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            long seed) {
        if (!List.of("fotr_block", "ttt_block", "king_block", "movie").contains(formatCode))
            throw new IllegalArgumentException("Unsupported bot block format: " + formatCode);
        return createLegalDeck(formatCode, format, createPools(format, library), new Random(seed), 1);
    }

    public static List<LotroDeck> createCardCoverageDecks(
            String formatCode,
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            long seed) {
        DeckPools pools = createPools(format, library);
        Set<String> uncovered = deckableCardIds(pools);
        Random random = new Random(seed);
        List<LotroDeck> decks = new ArrayList<>();
        int maximumDecks = Math.max(100, uncovered.size());
        int noProgressAttempts = 0;

        while (!uncovered.isEmpty() && decks.size() < maximumDecks && noProgressAttempts < 25) {
            LotroDeck deck = createLegalDeck(formatCode, format, pools, random,
                    decks.size() + 1, Set.copyOf(uncovered), "card coverage");
            Set<String> deckCardIds = deckCardIds(deck);
            if (uncovered.removeAll(deckCardIds)) {
                decks.add(deck);
                noProgressAttempts = 0;
            } else {
                noProgressAttempts++;
            }
        }

        if (!uncovered.isEmpty())
            throw new IllegalStateException("Could not cover all format-legal deckable cards for "
                    + formatCode + "; missing card IDs: "
                    + String.join(", ", uncovered.stream().limit(25).toList()));
        return List.copyOf(decks);
    }

    private static Set<String> deckableCardIds(DeckPools pools) {
        Set<String> result = new LinkedHashSet<>();
        pools.ringBearers().forEach(card -> result.add(card.blueprintId()));
        pools.rings().forEach(card -> result.add(card.blueprintId()));
        pools.freePeople().forEach(card -> result.add(card.blueprintId()));
        pools.shadow().forEach(card -> result.add(card.blueprintId()));
        pools.sitesByNumber().values().forEach(siteList ->
                siteList.forEach(card -> result.add(card.blueprintId())));
        return result;
    }

    private static Set<String> deckCardIds(LotroDeck deck) {
        Set<String> result = new HashSet<>(deck.getDrawDeckCards());
        result.add(deck.getRingBearer());
        result.add(deck.getRing());
        result.addAll(deck.getSites());
        return result;
    }

    private static List<LotroDeck> createFellowshipStarterDecks(int deckCount) {
        List<LotroDeck> starters = List.of(
                DeckSerialization.buildDeckFromContents("Aragorn Starter",
                        "1_290|1_2|1_320,1_327,1_340,1_346,1_349,1_351,1_355,1_358,1_361|1_365,1_365,1_92,1_94,1_94,1_97," +
                                "1_97,1_101,1_104,1_104,1_106,1_107,1_107,1_296,1_296,1_299,1_299,1_51,1_108,1_108,1_110,1_110,1_309," +
                                "1_311,1_116,1_116,1_116,1_117,1_117,1_117,1_121,1_121,1_121,1_133,1_133,1_141,1_141,1_145,1_150," +
                                "1_150,1_150,1_150,1_151,1_151,1_151,1_152,1_152,1_152,1_152,1_153,1_153,1_153,1_153,1_154,1_154," +
                                "1_154,1_157,1_157,1_158,1_158", "fotr_block", "Aragorn Starter"),
                DeckSerialization.buildDeckFromContents("Gandalf Starter",
                        "1_290|1_2|1_326,1_331,1_337,1_345,1_349,1_350,1_353,1_359,1_360|1_70,1_97,1_286,1_286,1_286,1_37," +
                                "1_37,1_364,1_364,1_12,1_12,1_296,1_296,1_299,1_76,1_76,1_76,1_51,1_51,1_78,1_78,1_78,1_78,1_304," +
                                "1_304,1_84,1_312,1_26,1_26,1_86,1_168,1_168,1_168,1_176,1_176,1_176,1_176,1_177,1_178,1_178,1_178" +
                                ",1_178,1_179,1_179,1_179,1_180,1_180,1_180,1_180,1_181,1_181,1_181,1_187,1_187,1_187,1_191," +
                                "1_191,1_191,1_196,1_196", "fotr_block", "Gandalf Starter"));

        List<LotroDeck> result = new ArrayList<>(deckCount);
        for (int i = 0; i < deckCount; i++) {
            LotroDeck starter = starters.get(i % starters.size());
            result.add(DeckSerialization.buildDeckFromContents(
                    starter.getDeckName(), DeckSerialization.buildContentsFromDeck(starter),
                    starter.getTargetFormat(), starter.getNotes()));
        }
        return List.copyOf(result);
    }

    private static DeckPools createPools(LotroFormat format, LotroCardBlueprintLibrary library) {
        List<CardOption> ringBearers = new ArrayList<>();
        List<CardOption> rings = new ArrayList<>();
        List<CardOption> freePeople = new ArrayList<>();
        List<CardOption> shadow = new ArrayList<>();
        Map<Integer, List<CardOption>> sitesByNumber = new java.util.HashMap<>();

        library.getBaseCards().forEach((blueprintId, blueprint) -> {
            if (blueprint.getCardType() == CardType.THE_ONE_RING && format.validateCard(blueprintId) == null)
                rings.add(new CardOption(blueprintId, blueprint));
            if (blueprint.getCardType() == CardType.SITE
                    && blueprint.getSiteBlock() == format.getSiteBlock()
                    && blueprint.getSiteNumber() >= 1 && blueprint.getSiteNumber() <= 9
                    && format.validateSite(blueprintId) == null) {
                sitesByNumber.computeIfAbsent(blueprint.getSiteNumber(), ignored -> new ArrayList<>())
                        .add(new CardOption(blueprintId, blueprint));
            }
            if (blueprint.getCardType() == CardType.SITE
                    || blueprint.getCardType() == CardType.THE_ONE_RING
                    || blueprint.getSide() == null
                    || format.validateCard(blueprintId) != null)
                return;

            CardOption option = new CardOption(blueprintId, blueprint);
            if (blueprint.getSide() == Side.FREE_PEOPLE) {
                freePeople.add(option);
                if (blueprint.getCardType() == CardType.COMPANION && blueprint.canStartWithRing())
                    ringBearers.add(option);
            } else if (blueprint.getSide() == Side.SHADOW) {
                shadow.add(option);
            }
        });

        for (int siteNumber = 1; siteNumber <= 9; siteNumber++) {
            if (sitesByNumber.getOrDefault(siteNumber, List.of()).isEmpty())
                throw new IllegalStateException("No legal site " + siteNumber + " for " + format.getCode());
        }
        var formatDefinition = format.Serialize();
        int drawDeckSize = formatDefinition.minimumDeckSize;
        if (drawDeckSize < 30)
            drawDeckSize = 30;
        if (formatDefinition.validateShadowFPCount && drawDeckSize % 2 != 0)
            drawDeckSize++;
        int freePeopleCards = (drawDeckSize + 1) / 2;
        int shadowCards = drawDeckSize - freePeopleCards;
        if (ringBearers.isEmpty() || rings.isEmpty()
                || freePeople.size() < freePeopleCards || shadow.size() < shadowCards)
            throw new IllegalStateException("Insufficient legal cards to generate " + format.getCode() + " bot decks");
        return new DeckPools(ringBearers, rings, freePeople, shadow, sitesByNumber, drawDeckSize);
    }

    private static LotroDeck createLegalDeck(
            String formatCode,
            LotroFormat format,
            DeckPools pools,
            Random random,
            int deckIndex) {
        return createLegalDeck(formatCode, format, pools, random, deckIndex, Set.of(), "generated bot deck");
    }

    private static LotroDeck createLegalDeck(
            String formatCode,
            LotroFormat format,
            DeckPools pools,
            Random random,
            int deckIndex,
            Set<String> coveragePriority,
            String deckType) {
        List<String> lastValidation = List.of();
        for (int attempt = 0; attempt < DECK_GENERATION_ATTEMPTS; attempt++) {
            CardOption ringBearer = choose(pools.ringBearers(), random, coveragePriority);
            CardOption ring = choose(pools.rings(), random, coveragePriority);
            List<CardOption> selectedSites = new ArrayList<>(9);
            for (int siteNumber = 1; siteNumber <= 9; siteNumber++)
                selectedSites.add(choose(pools.sitesByNumber().get(siteNumber), random, coveragePriority));

            List<CardOption> companions = pools.freePeople().stream()
                    .filter(card -> card.blueprint().getCardType() == CardType.COMPANION)
                    .filter(card -> !card.blueprint().getTitle().equals(ringBearer.blueprint().getTitle()))
                    .toList();
            Culture fellowshipCulture = ringBearer.blueprint().getCulture();
            List<CardOption> cultureCompanions = companions.stream()
                    .filter(card -> card.blueprint().getCulture() == fellowshipCulture)
                    .toList();
            if (cultureCompanions.size() >= 4)
                companions = cultureCompanions;

            List<CardOption> allMinions = pools.shadow().stream()
                    .filter(card -> card.blueprint().getCardType() == CardType.MINION)
                    .toList();
            List<CardOption> minions = allMinions;
            if (companions.size() < 4 || minions.size() < 4)
                throw new IllegalStateException("Insufficient companions or minions for " + formatCode);
            List<Culture> viableShadowCultures = allMinions.stream()
                    .map(card -> card.blueprint().getCulture())
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .filter(culture -> allMinions.stream()
                            .filter(card -> card.blueprint().getCulture() == culture)
                            .count() >= 4)
                    .toList();
            List<CardOption> shadowCards = pools.shadow();
            if (!viableShadowCultures.isEmpty()) {
                Culture shadowCulture = chooseCulture(viableShadowCultures, random);
                minions = allMinions.stream()
                        .filter(card -> card.blueprint().getCulture() == shadowCulture)
                        .toList();
            }

            int freePeopleDrawCount = (pools.drawDeckSize() + 1) / 2;
            int shadowDrawCount = pools.drawDeckSize() - freePeopleDrawCount;
            List<CardOption> drawCards = new ArrayList<>(pools.drawDeckSize());
            Set<String> selectedTitles = new HashSet<>();
            selectedTitles.add(ringBearer.blueprint().getTitle());
            int companionCount = Math.min(6, freePeopleDrawCount / 2);
            int minionCount = Math.min(6, shadowDrawCount / 2);
            int freePeopleStart = drawCards.size();
            addRandomCards(drawCards, companions, companionCount, selectedTitles, coveragePriority, random);
            int selectedCompanions = drawCards.size() - freePeopleStart;
            addRandomCards(drawCards, pools.freePeople(), freePeopleDrawCount - selectedCompanions,
                    selectedTitles, coveragePriority, random);
            int selectedFreePeople = drawCards.size() - freePeopleStart;
            if (selectedFreePeople < freePeopleDrawCount)
                addRandomCards(drawCards, pools.freePeople(), freePeopleDrawCount - selectedFreePeople,
                        selectedTitles, coveragePriority, random);

            int shadowStart = drawCards.size();
            addRandomCards(drawCards, minions, minionCount, selectedTitles, coveragePriority, random);
            addRandomCards(drawCards, shadowCards, shadowDrawCount - minionCount,
                    selectedTitles, coveragePriority, random);
            int selectedShadow = drawCards.size() - shadowStart;
            if (selectedShadow < shadowDrawCount)
                addRandomCards(drawCards, pools.shadow(), shadowDrawCount - selectedShadow,
                        selectedTitles, coveragePriority, random);
            if (drawCards.size() != pools.drawDeckSize())
                throw new IllegalStateException("Unable to fill a balanced draw deck for " + formatCode);

            LotroDeck deck = new LotroDeck(formatCode + " " + deckType + " " + deckIndex);
            deck.setTargetFormat(formatCode);
            deck.setRingBearer(ringBearer.blueprintId());
            deck.setRing(ring.blueprintId());
            for (CardOption site : selectedSites)
                deck.addSite(site.blueprintId());
            for (CardOption card : drawCards)
                deck.addCard(card.blueprintId());

            lastValidation = format.validateDeck(deck);
            if (lastValidation.isEmpty())
                return deck;
        }
        throw new IllegalStateException("Could not generate a valid " + formatCode + " " + deckType + " after "
                + DECK_GENERATION_ATTEMPTS + " attempts: " + String.join("; ", lastValidation));
    }

    private static void addRandomCards(
            List<CardOption> result,
            List<CardOption> candidates,
            int requested,
            Set<String> selectedTitles,
            Set<String> coveragePriority,
            Random random) {
        if (requested <= 0)
            return;
        int targetSize = result.size() + requested;
        List<CardOption> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);
        for (int priorityPass = 1; priorityPass >= 0; priorityPass--) {
            for (CardOption card : shuffled) {
                if (coveragePriority.contains(card.blueprintId()) != (priorityPass == 1))
                    continue;
                if (selectedTitles.add(card.blueprint().getTitle()))
                    result.add(card);
                if (result.size() >= targetSize)
                    return;
            }
        }
    }

    private static CardOption choose(List<CardOption> options, Random random) {
        return options.get(random.nextInt(options.size()));
    }

    private static CardOption choose(List<CardOption> options, Random random, Set<String> coveragePriority) {
        List<CardOption> preferred = options.stream()
                .filter(option -> coveragePriority.contains(option.blueprintId()))
                .toList();
        return choose(preferred.isEmpty() ? options : preferred, random);
    }

    private static Culture chooseCulture(List<Culture> cultures, Random random) {
        return cultures.get(random.nextInt(cultures.size()));
    }

    private record CardOption(String blueprintId, LotroCardBlueprint blueprint) {
    }

    private record DeckPools(
            List<CardOption> ringBearers,
            List<CardOption> rings,
            List<CardOption> freePeople,
            List<CardOption> shadow,
            Map<Integer, List<CardOption>> sitesByNumber,
            int drawDeckSize) {
    }
}
