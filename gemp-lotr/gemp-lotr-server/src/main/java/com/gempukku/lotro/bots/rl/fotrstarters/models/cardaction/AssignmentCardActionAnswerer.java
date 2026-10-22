package com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction;

public class AssignmentCardActionAnswerer extends AbstractCardActionAnswerer {
    private static final String TRIGGER = "Play Assignment action or Pass";
    private final AssignmentPlayCardTrainer playTrainer = new AssignmentPlayCardTrainer();
    private final AssignmentUseCardTrainer useTrainer = new AssignmentUseCardTrainer();

    @Override
    protected String getTextTrigger() {
        return TRIGGER;
    }

    @Override
    protected AbstractPlayUseCardTrainer getPlayTrainer() {
        return playTrainer;
    }

    @Override
    protected AbstractPlayUseCardTrainer getUseTrainer() {
        return useTrainer;
    }

    @Override
    protected AbstractPlayUseCardTrainer getHealTrainer() {
        return null;
    }

    @Override
    protected AbstractPlayUseCardTrainer getTransferTrainer() {
        return null;
    }

    public static class AssignmentPlayCardTrainer extends AbstractPlayUseCardTrainer {
        @Override
        protected String getTextTrigger() {
            return TRIGGER;
        }

        @Override
        protected boolean isPlayTrainer() {
            return true;
        }

        @Override
        protected boolean isUseTrainer() {
            return false;
        }

        @Override
        protected boolean isTransferTrainer() {
            return false;
        }

        @Override
        protected boolean isHealTrainer() {
            return false;
        }
    }

    public static class AssignmentUseCardTrainer extends AbstractPlayUseCardTrainer {
        @Override
        protected String getTextTrigger() {
            return TRIGGER;
        }

        @Override
        protected boolean isPlayTrainer() {
            return false;
        }

        @Override
        protected boolean isUseTrainer() {
            return true;
        }

        @Override
        protected boolean isTransferTrainer() {
            return false;
        }

        @Override
        protected boolean isHealTrainer() {
            return false;
        }
    }
}
