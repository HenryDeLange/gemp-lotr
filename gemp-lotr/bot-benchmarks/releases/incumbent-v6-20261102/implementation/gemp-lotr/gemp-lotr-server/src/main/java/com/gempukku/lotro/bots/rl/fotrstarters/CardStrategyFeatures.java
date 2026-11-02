package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.game.LotroCardBlueprint;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class CardStrategyFeatures {
    public static final int BURDEN_ADD = 0;
    public static final int BURDEN_REMOVE = 1;
    public static final int THREAT_ADD = 2;
    public static final int THREAT_REMOVE = 3;
    public static final int WOUNDING = 4;
    public static final int HEALING = 5;
    public static final int WOUND_PREVENTION = 6;
    public static final int COMBAT_BOOST = 7;
    public static final int ARCHERY = 8;
    public static final int SITE_CONTROL = 9;
    public static final int CARD_DRAW = 10;
    public static final int CARD_DISCARD = 11;
    public static final int TWILIGHT_ADD = 12;
    public static final int TWILIGHT_REMOVE = 13;
    public static final int MOVE_AGAIN = 14;
    public static final int RING_BEARER = 15;
    public static final int DECK_SEARCH = 16;
    public static final int CARD_CYCLE = 17;
    public static final int EXERTION = 18;
    public static final int HEALING_PREVENTION = 19;
    public static final int MINION_FROM_DISCARD = 20;
    public static final int FEATURE_COUNT = 21;

    private static final Pattern BURDEN_ADD_PATTERN =
            Pattern.compile("\\b(?:add|adds|place|places|put)\\b(?:\\s+[a-z0-9+/-]+){0,4}\\s+burdens?\\b");
    private static final Pattern BURDEN_REMOVE_PATTERN =
            Pattern.compile("\\bremove\\b(?:\\s+[a-z0-9+/-]+){0,4}\\s+burdens?\\b");
    private static final Pattern THREAT_ADD_PATTERN =
            Pattern.compile("\\b(?:add|adds|place|places|put)\\b(?:\\s+[a-z0-9+/-]+){0,4}\\s+threats?\\b");
    private static final Pattern THREAT_REMOVE_PATTERN =
            Pattern.compile("\\bremove\\b(?:\\s+[a-z0-9+/-]+){0,4}\\s+threats?\\b");
    private static final Pattern WOUND_PATTERN =
            Pattern.compile("\\b(?:wound|wounds|wounded|wounding|kill|kills|killed|killing)\\b");
    private static final Pattern EXERTION_PATTERN =
            Pattern.compile("\\b(?:exert|exerts|exerted|exerting)\\b");
    private static final Pattern HEAL_PATTERN =
            Pattern.compile("\\b(?:heal|heals|healed|healing)\\b|\\bremove\\b(?:\\s+[a-z0-9+/-]+){0,3}\\s+wounds?\\b");
    private static final Pattern WOUND_PREVENTION_PATTERN =
            Pattern.compile("\\bprevent\\w*\\b.{0,48}\\bwound\\w*\\b|\\bcannot be wounded\\b|\\bmay not be wounded\\b");
    private static final Pattern HEALING_PREVENTION_PATTERN =
            Pattern.compile("\\b(?:prevent\\w*|cannot|can't|may not)\\b.{0,48}\\bheal\\w*\\b");
    private static final Pattern SITE_CONTROL_PATTERN =
            Pattern.compile("\\bcontrol\\w*\\b.{0,40}\\bsite\\b|\\bsite\\b.{0,40}\\bcontrol\\w*\\b");
    private static final Pattern CARD_DRAW_PATTERN =
            Pattern.compile("\\bdraw\\w*\\b.{0,32}\\bcards?\\b|\\bcards?\\b.{0,32}\\bdraw\\w*\\b");
    private static final Pattern CARD_DISCARD_PATTERN =
            Pattern.compile("\\bdiscard\\w*\\b.{0,32}\\b(?:cards?|hand)\\b|\\b(?:cards?|hand)\\b.{0,32}\\bdiscard\\w*\\b");
    private static final Pattern TWILIGHT_ADD_PATTERN =
            Pattern.compile("\\b(?:add|adds|place|places|put)\\b(?:\\s+[a-z0-9+/-]+){0,4}\\btwilight\\b");
    private static final Pattern TWILIGHT_REMOVE_PATTERN =
            Pattern.compile("\\bremove\\b(?:\\s+[a-z0-9+/-]+){0,4}\\btwilight\\b");
    private static final Pattern SITE_MOVE_PATTERN =
            Pattern.compile("\\b(?:move again|another move|additional move|move twice)\\b");
    private static final Pattern DECK_SEARCH_PATTERN =
            Pattern.compile("\\bsearch\\w*\\b.{0,48}\\b(?:draw )?deck\\b");
    private static final Pattern MINION_DISCARD_RETRIEVAL_PATTERN =
            Pattern.compile("\\b(?:play|take|return|put|place|move|retrieve|choose|replace)\\b.{0,72}\\bminions?\\b.{0,72}\\bdiscard pile\\b"
                    + "|\\b(?:play|take|return|put|place|move|retrieve|choose|replace)\\b.{0,72}\\bdiscard pile\\b.{0,72}\\bminions?\\b");
    private static final ConcurrentHashMap<String, double[]> CACHE = new ConcurrentHashMap<>();

    private CardStrategyFeatures() {
    }

    public static double[] extract(LotroCardBlueprint blueprint) {
        String blueprintId = blueprint.getId();
        if (blueprintId == null)
            return extractUncached(blueprint);
        return CACHE.computeIfAbsent(blueprintId, ignored -> extractUncached(blueprint)).clone();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static double[] extractUncached(LotroCardBlueprint blueprint) {
        String text = blueprint.getGameText();
        if (text == null)
            text = "";
        text = text.toLowerCase(Locale.ROOT).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ");

        boolean cardDraw = CARD_DRAW_PATTERN.matcher(text).find();
        boolean cardDiscard = CARD_DISCARD_PATTERN.matcher(text).find();
        return new double[]{
                feature(BURDEN_ADD_PATTERN.matcher(text).find()),
                feature(BURDEN_REMOVE_PATTERN.matcher(text).find()),
                feature(THREAT_ADD_PATTERN.matcher(text).find()),
                feature(THREAT_REMOVE_PATTERN.matcher(text).find()),
                feature(WOUND_PATTERN.matcher(text).find()),
                feature(HEAL_PATTERN.matcher(text).find()),
                feature(WOUND_PREVENTION_PATTERN.matcher(text).find()),
                feature(blueprint.getKeywordCount(Keyword.DAMAGE) > 0
                        || blueprint.getKeywordCount(Keyword.DEFENDER) > 0
                        || blueprint.getKeywordCount(Keyword.AMBUSH) > 0
                        || blueprint.getKeywordCount(Keyword.HUNTER) > 0
                        || blueprint.getKeywordCount(Keyword.FIERCE) > 0
                        || blueprint.getKeywordCount(Keyword.ENDURING) > 0
                        || text.contains("strength")),
                feature(blueprint.hasKeyword(Keyword.ARCHER) || text.contains("archery")),
                feature(blueprint.hasKeyword(Keyword.BESIEGER) || SITE_CONTROL_PATTERN.matcher(text).find()),
                feature(cardDraw),
                feature(cardDiscard),
                feature(TWILIGHT_ADD_PATTERN.matcher(text).find()),
                feature(TWILIGHT_REMOVE_PATTERN.matcher(text).find()),
                feature(SITE_MOVE_PATTERN.matcher(text).find()),
                feature(blueprint.hasKeyword(Keyword.RING_BEARER)
                        || text.contains("ring-bearer") || text.contains("ringbearer")),
                feature(DECK_SEARCH_PATTERN.matcher(text).find()),
                feature(cardDraw && cardDiscard),
                feature(EXERTION_PATTERN.matcher(text).find()),
                feature(HEALING_PREVENTION_PATTERN.matcher(text).find()),
                feature(MINION_DISCARD_RETRIEVAL_PATTERN.matcher(text).find())
        };
    }

    private static double feature(boolean present) {
        return present ? 1.0 : 0.0;
    }
}
