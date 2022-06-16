package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.IntegerAwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class IntegerAwaitingDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(IntegerAwaitingDecisionBot.class);
    private Random random = new Random();
    
    @Override
    public String getBotChoice(AwaitingDecision awaitingDecision) {
        String intValue = null;
        IntegerAwaitingDecision decision = (IntegerAwaitingDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] min = decision.getDecisionParameters().get("min");
        String[] max = decision.getDecisionParameters().get("max");
        String[] defaultValue = decision.getDecisionParameters().get("defaultValue");
        LOG.trace("PARAM: min = " + getArrayAsString(min));
        LOG.trace("PARAM: max = " + getArrayAsString(max));
        LOG.trace("PARAM: defaultValue = " + getArrayAsString(defaultValue));
        if (defaultValue != null && defaultValue.length > 0) {
            intValue = defaultValue[0];
        }
        else if (min != null && max != null && min.length > 0 && max.length > 0) {
            int minValue = Integer.parseInt(min[0]);
            int maxValue = Integer.parseInt(max[0]);
            intValue = Integer.toString(random.nextInt(maxValue - minValue) + minValue);
        }
        else {
            intValue = min[0];
        }
        LOG.trace("CHOICE: intValue = " + intValue);
        return intValue;
    }

}
