package com.gempukku.lotro.league;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import com.gempukku.lotro.collection.CollectionsManager;
import com.gempukku.lotro.db.vo.CollectionType;
import com.gempukku.lotro.game.CardCollection;
import com.gempukku.lotro.game.CardSets;
import com.gempukku.lotro.game.DefaultCardCollection;
import com.gempukku.lotro.game.Player;
import com.google.common.collect.Iterables;

public class SealedLeagueDataTest {

    @Test
    public void testJoinLeagueFirstWeek() {
        SealedLeagueData data = new SealedLeagueData(new CardSets(), null, "fotr_block,20120101,test,Test Collection");
        CollectionType collectionType = new CollectionType("test", "Test Collection");
        for (int i = 20120101; i < 20120108; i++) {
            CollectionsManager collectionsManager = Mockito.mock(CollectionsManager.class);
            Player player = new Player(1, "Test", "pass", "u", null, null, null, null);
            data.joinLeague(collectionsManager, player, i);
            Mockito.verify(collectionsManager, new Times(1)).addPlayerCollection(Mockito.anyBoolean(), Mockito.anyString(), Mockito.eq(player), Mockito.eq(collectionType), 
                Mockito.argThat(
                    new ArgumentMatcher<CardCollection>() {
                        @Override
                        public boolean matches(CardCollection o) {
                            CardCollection cards = (CardCollection) o;
                            if (Iterables.size(cards.getAll()) != 3)
                                return false;
                            if (cards.getItemCount("(S)FotR - Starter") != 1)
                                return false;
                            if (cards.getItemCount("FotR - Booster") != 6)
                                return false;
                            if (cards.getItemCount("1_231") != 2)
                                return false;
                            return true;
                        }
                    }
            ));
            Mockito.verifyNoMoreInteractions(collectionsManager);
        }
    }

    @Test
    public void testJoinLeagueSecondWeek() {
        SealedLeagueData data = new SealedLeagueData(new CardSets(), null, "fotr_block,20120101,test,Test Collection");
        CollectionType collectionType = new CollectionType("test", "Test Collection");
        for (int i = 20120108; i < 20120115; i++) {
            CollectionsManager collectionsManager = Mockito.mock(CollectionsManager.class);
            Player player = new Player(1, "Test", "pass", "u", null, null, null, null);
            data.joinLeague(collectionsManager, player, i);
            Mockito.verify(collectionsManager, new Times(1)).addPlayerCollection(Mockito.anyBoolean(), Mockito.anyString(), Mockito.eq(player), Mockito.eq(collectionType), 
                Mockito.argThat(
                    new ArgumentMatcher<CardCollection>() {
                        @Override
                        public boolean matches(CardCollection o) {
                            CardCollection cards = (CardCollection) o;
                            if (Iterables.size(cards.getAll()) != 6)
                                return false;
                            if (cards.getItemCount("(S)FotR - Starter") != 1)
                                return false;
                            if (cards.getItemCount("FotR - Booster") != 6)
                                return false;
                            if (cards.getItemCount("1_231") != 2)
                                return false;
                            if (cards.getItemCount("(S)MoM - Starter") != 1)
                                return false;
                            if (cards.getItemCount("MoM - Booster") != 3)
                                return false;
                            if (cards.getItemCount("2_51") != 1)
                                return false;
                            return true;
                        }
                    }
            ));
            Mockito.verifyNoMoreInteractions(collectionsManager);
        }
    }

    @Test
    public void testSwitchToFirstWeek() {
        SealedLeagueData data = new SealedLeagueData(new CardSets(), null, "fotr_block,20120101,test,Test Collection");
        for (int i = 20120101; i < 20120108; i++) {
            CollectionsManager collectionsManager = Mockito.mock(CollectionsManager.class);
            Mockito.when(collectionsManager.getPlayersCollection("test")).thenReturn(new HashMap<Player, CardCollection>());
            int result = data.process(collectionsManager, null, 0, i);
            assertEquals(1, result);
            Mockito.verify(collectionsManager, new Times(1)).getPlayersCollection("test");
            Mockito.verifyNoMoreInteractions(collectionsManager);
        }
    }

    @Test
    public void testProcessMidFirstWeek() {
        SealedLeagueData data = new SealedLeagueData(new CardSets(), null, "fotr_block,20120101,test,Test Collection");
        for (int i = 20120101; i < 20120108; i++) {
            CollectionsManager collectionsManager = Mockito.mock(CollectionsManager.class);
            Mockito.when(collectionsManager.getPlayersCollection("test")).thenReturn(new HashMap<Player, CardCollection>());
            int result = data.process(collectionsManager, null, 1, i);
            assertEquals(1, result);
            Mockito.verifyNoMoreInteractions(collectionsManager);
        }
    }

    @Test
    public void testSwitchToSecondWeek() {
        SealedLeagueData data = new SealedLeagueData(new CardSets(), null, "fotr_block,20120101,test,Test Collection");
        CollectionType collectionType = new CollectionType("test", "Test Collection");
        for (int i = 20120108; i < 20120115; i++) {
            CollectionsManager collectionsManager = Mockito.mock(CollectionsManager.class);
            Map<Player, CardCollection> playersInLeague = new HashMap<Player, CardCollection>();
            Player player = new Player(1, "Test", "pass", "u", null, null, null, null);
            playersInLeague.put(player, new DefaultCardCollection());
            Mockito.when(collectionsManager.getPlayersCollection("test")).thenReturn(playersInLeague);
            int result = data.process(collectionsManager, null, 1, i);
            assertEquals(2, result);
            final List<CardCollection.Item> expectedToAdd = new ArrayList<CardCollection.Item>();
            expectedToAdd.add(CardCollection.Item.createItem("(S)MoM - Starter", 1));
            expectedToAdd.add(CardCollection.Item.createItem("MoM - Booster", 3));
            expectedToAdd.add(CardCollection.Item.createItem("2_51", 1));
            Mockito.verify(collectionsManager, new Times(1)).getPlayersCollection("test");
            Mockito.verify(collectionsManager, new Times(1)).addItemsToPlayerCollection(Mockito.anyBoolean(), Mockito.anyString(), Mockito.eq(player), Mockito.eq(collectionType),
                    Mockito.argThat(
                            new ArgumentMatcher<Collection<CardCollection.Item>>() {
                                @Override
                                public boolean matches(Collection<CardCollection.Item> o) {
                                    Collection<CardCollection.Item> argument = (Collection<CardCollection.Item>) o;
                                    if (argument.size() != expectedToAdd.size())
                                        return false;
                                    for (CardCollection.Item item : expectedToAdd) {
                                        if (!argument.contains(item))
                                            return false;
                                    }
                                    return true;
                                }
                            }));
            Mockito.verifyNoMoreInteractions(collectionsManager);
        }
    }

    @Test
    public void testProcessMidSecondWeek() {
        SealedLeagueData data = new SealedLeagueData(new CardSets(), null, "fotr_block,20120101,test,Test Collection");
        for (int i = 20120108; i < 20120115; i++) {
            CollectionsManager collectionsManager = Mockito.mock(CollectionsManager.class);
            Mockito.when(collectionsManager.getPlayersCollection("test")).thenReturn(new HashMap<Player, CardCollection>());
            int result = data.process(collectionsManager, null, 2, i);
            assertEquals(2, result);
            Mockito.verifyNoMoreInteractions(collectionsManager);
        }
    }
}
