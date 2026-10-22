package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.fotrstarters.models.Trainer;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class FotrStarterBotRoutingTest {
    @Test
    public void decisionRouterUsesEveryCatalogTrainer() {
        FotrStarterBot bot = new FotrStarterBot(null, "~routing-test", new ModelRegistry(), null);
        Set<Class<?>> catalogTypes = FotrStarterTrainerCatalog.createAll().stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        assertEquals(catalogTypes, bot.getRoutedTrainerTypes());
    }
}
