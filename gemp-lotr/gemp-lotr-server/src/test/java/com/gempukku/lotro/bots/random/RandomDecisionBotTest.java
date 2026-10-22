package com.gempukku.lotro.bots.random;

import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.decisions.IntegerAwaitingDecision;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class RandomDecisionBotTest {
    @Test
    public void choosesRandomIntegerWithinRequestedRange() {
        RandomDecisionBot bot = new RandomDecisionBot("~random-test");
        IntegerAwaitingDecision decision = new IntegerAwaitingDecision(1, "Choose a value", 3, 7) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
            }
        };
        Set<Integer> observedChoices = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            int choice = Integer.parseInt(bot.chooseAction(null, decision));
            assertTrue("choice must be in the decision's range", choice >= 3 && choice <= 7);
            observedChoices.add(choice);
        }

        assertTrue("random choice should not always be the minimum", observedChoices.size() > 1);
    }

    @Test
    public void supportsTheFullIntegerRange() {
        RandomDecisionBot bot = new RandomDecisionBot("~random-full-range-test");
        IntegerAwaitingDecision decision = new IntegerAwaitingDecision(
                2, "Choose any integer", Integer.MIN_VALUE, Integer.MAX_VALUE) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
            }
        };

        for (int i = 0; i < 100; i++) {
            int choice = Integer.parseInt(bot.chooseAction(null, decision));
            assertTrue("choice must not be below Integer.MIN_VALUE", choice >= Integer.MIN_VALUE);
            assertTrue("choice must not be above Integer.MAX_VALUE", choice <= Integer.MAX_VALUE);
            assertTrue("unbounded choices should stay near zero", Math.abs((long) choice) <= 10);
        }
    }
}
