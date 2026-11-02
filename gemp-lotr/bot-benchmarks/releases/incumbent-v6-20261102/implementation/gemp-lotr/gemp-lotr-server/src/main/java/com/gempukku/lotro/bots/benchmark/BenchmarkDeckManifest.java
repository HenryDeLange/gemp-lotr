package com.gempukku.lotro.bots.benchmark;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.gempukku.lotro.bots.MovieBlockDeckFactory;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.db.DeckSerialization;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.gempukku.lotro.packs.ProductLibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BenchmarkDeckManifest(int version, long generationSeed, List<DeckPool> pools) {
    public static final List<String> FORMATS = List.of("fotr_block", "ttt_block", "king_block", "movie");
    private static final int VERSION = 1;

    public record DeckEntry(String name, String contents, String fingerprint) {
        static DeckEntry from(String format, LotroDeck deck) {
            return new DeckEntry(deck.getDeckName(), DeckSerialization.buildContentsFromDeck(deck),
                    DeckBuilderTrainer.fingerprint(format, deck));
        }

        LotroDeck toDeck(String formatCode, LotroFormat format) {
            if (name == null || name.isBlank() || contents == null || fingerprint == null)
                throw new IllegalArgumentException("Incomplete deck in " + formatCode + " manifest");
            LotroDeck deck = DeckSerialization.buildDeckFromContents(name, contents, formatCode, "");
            if (!DeckBuilderTrainer.fingerprint(formatCode, deck).equals(fingerprint))
                throw new IllegalArgumentException("Deck fingerprint mismatch for " + name + " in " + formatCode);
            var problems = format.validateDeck(deck);
            if (!problems.isEmpty())
                throw new IllegalArgumentException("Illegal benchmark deck " + name + " in "
                        + formatCode + ": " + problems);
            return deck;
        }
    }

    public record DeckPool(String format, List<DeckEntry> starters, List<DeckEntry> generated) {
    }

    public static BenchmarkDeckManifest create(
            LotroFormatLibrary formats, LotroCardBlueprintLibrary cards, ProductLibrary products,
            int generatedPerFormat, long seed) {
        if (generatedPerFormat < 2)
            throw new IllegalArgumentException("At least two generated decks per format are required");
        List<DeckPool> pools = new ArrayList<>();
        for (String code : FORMATS) {
            LotroFormat format = Objects.requireNonNull(formats.getFormat(code), "Missing format " + code);
            List<DeckEntry> starters = MovieBlockDeckFactory.createStarterDecks(code, format, cards, products)
                    .stream().map(deck -> DeckEntry.from(code, deck)).toList();
            Set<String> seen = new HashSet<>();
            starters.forEach(entry -> seen.add(entry.fingerprint()));
            List<DeckEntry> generated = new ArrayList<>();
            for (int attempt = 0; generated.size() < generatedPerFormat && attempt < generatedPerFormat * 100; attempt++) {
                LotroDeck deck = MovieBlockDeckFactory.createRandomLegalDeck(
                        code, format, cards, seed + 31L * code.hashCode() + attempt);
                DeckEntry entry = DeckEntry.from(code, deck);
                if (seen.add(entry.fingerprint()))
                    generated.add(entry);
            }
            if (generated.size() != generatedPerFormat)
                throw new IllegalStateException("Could not generate distinct benchmark decks for " + code);
            pools.add(new DeckPool(code, starters, List.copyOf(generated)));
        }
        BenchmarkDeckManifest manifest = new BenchmarkDeckManifest(VERSION, seed, List.copyOf(pools));
        manifest.validate(formats);
        return manifest;
    }

    public static BenchmarkDeckManifest read(Path file, LotroFormatLibrary formats) throws IOException {
        BenchmarkDeckManifest manifest = JSON.parseObject(Files.readString(file, StandardCharsets.UTF_8),
                BenchmarkDeckManifest.class);
        if (manifest == null)
            throw new IllegalArgumentException("Empty benchmark deck manifest: " + file);
        manifest.validate(formats);
        return manifest;
    }

    public void writeNew(Path file) throws IOException {
        Files.writeString(file, JSON.toJSONString(this, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public void validate(LotroFormatLibrary formats) {
        if (version != VERSION || pools == null || pools.size() != FORMATS.size())
            throw new IllegalArgumentException("Benchmark manifest must contain version 1 and all four formats");
        Set<String> seenFormats = new HashSet<>();
        for (DeckPool pool : pools) {
            if (pool == null || !FORMATS.contains(pool.format()) || !seenFormats.add(pool.format()))
                throw new IllegalArgumentException("Unknown or duplicate benchmark format");
            if (pool.starters() == null || pool.starters().size() < 2
                    || pool.generated() == null || pool.generated().size() < 2)
                throw new IllegalArgumentException("At least two decks of each kind are required for " + pool.format());
            LotroFormat format = Objects.requireNonNull(formats.getFormat(pool.format()),
                    "Missing format " + pool.format());
            Set<String> generatedFingerprints = new HashSet<>();
            Set<String> starterFingerprints = new HashSet<>();
            for (DeckEntry deck : pool.starters()) {
                if (deck == null)
                    throw new IllegalArgumentException("Null starter deck in " + pool.format());
                deck.toDeck(pool.format(), format);
                starterFingerprints.add(deck.fingerprint());
            }
            for (DeckEntry deck : pool.generated()) {
                if (deck == null)
                    throw new IllegalArgumentException("Null generated deck in " + pool.format());
                deck.toDeck(pool.format(), format);
                if (!generatedFingerprints.add(deck.fingerprint()) || starterFingerprints.contains(deck.fingerprint()))
                    throw new IllegalArgumentException("Duplicate or overlapping generated deck in " + pool.format());
            }
        }
    }
}
