package com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection;

public class ReconcileTrainer extends AbstractCardSelectionTrainer {
    public static final String OPTIONAL_PASS_EXPERIMENT_PROPERTY =
            "gemp.lotr.bot.reconcileOptionalPassExperiment";

    @Override
    protected String getTextTrigger() {
        return "reconcile";  // or "reconcile" if you want case-insensitive matching
    }

    @Override
    protected boolean useNotChosen() {
        return true;
    }

    @Override
    protected boolean includePassOption() {
        String value = System.getProperty(OPTIONAL_PASS_EXPERIMENT_PROPERTY, "false");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException(OPTIONAL_PASS_EXPERIMENT_PROPERTY + " must be true or false");
        return Boolean.parseBoolean(value);
    }
}
