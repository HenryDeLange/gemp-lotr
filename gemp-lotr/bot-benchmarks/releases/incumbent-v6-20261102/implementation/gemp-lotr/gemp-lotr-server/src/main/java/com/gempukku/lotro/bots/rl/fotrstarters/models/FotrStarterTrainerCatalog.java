package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.CardFromDiscardTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.StartingFellowshipTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.assignment.FpAssignmentTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.assignment.ShadowAssignmentTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.GenericArbitraryCardsTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.FellowshipCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.ManeuverCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.ArcheryCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.AssignmentCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericActionChoiceTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericCardActionChoiceTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.OptionalResponsesCardActionTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.RegroupCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.ShadowCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.SkirmishFpCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.SkirmishShadowCardActionAnswerer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.ArcheryWoundTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.AttachItemTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.DiscardFromHandTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.DiscardFromPlayTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.ExertTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.FallBackCardSelectionTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.HealTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.PlayFromHandTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.ReconcileTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.SanctuaryTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection.SkirmishOrderTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.BurdenTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.GenericIntegerTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.AnotherMoveTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.GenericMultipleChoiceTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.GoFirstTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice.MulliganTrainer;

import java.util.List;

public final class FotrStarterTrainerCatalog {
    private FotrStarterTrainerCatalog() {
    }

    public static List<Trainer> createAll() {
        return List.of(
                new GoFirstTrainer(),
                new MulliganTrainer(),
                new AnotherMoveTrainer(),
                new GenericMultipleChoiceTrainer(),
                new BurdenTrainer(),
                new GenericIntegerTrainer(),
                new ReconcileTrainer(),
                new SanctuaryTrainer(),
                new ArcheryWoundTrainer(),
                new AttachItemTrainer(),
                new SkirmishOrderTrainer(),
                new HealTrainer(),
                new DiscardFromHandTrainer(),
                new ExertTrainer(),
                new DiscardFromPlayTrainer(),
                new PlayFromHandTrainer(),
                new FallBackCardSelectionTrainer(),
                new CardFromDiscardTrainer(),
                new StartingFellowshipTrainer(),
                new GenericArbitraryCardsTrainer(),
                new GenericActionChoiceTrainer(),
                new GenericCardActionChoiceTrainer(),
                new FellowshipCardActionAnswerer.FellowshipPlayCardTrainer(),
                new FellowshipCardActionAnswerer.FellowshipUseCardTrainer(),
                new FellowshipCardActionAnswerer.FellowshipHealTrainer(),
                new FellowshipCardActionAnswerer.FellowshipTransferTrainer(),
                new ShadowCardActionAnswerer.ShadowPlayCardTrainer(),
                new ShadowCardActionAnswerer.ShadowUseCardTrainer(),
                new ManeuverCardActionAnswerer.ManeuverPlayCardTrainer(),
                new ManeuverCardActionAnswerer.ManeuverUseCardTrainer(),
                new ArcheryCardActionAnswerer.ArcheryPlayCardTrainer(),
                new ArcheryCardActionAnswerer.ArcheryUseCardTrainer(),
                new AssignmentCardActionAnswerer.AssignmentPlayCardTrainer(),
                new AssignmentCardActionAnswerer.AssignmentUseCardTrainer(),
                new SkirmishFpCardActionAnswerer.SkirmishFpPlayCardTrainer(),
                new SkirmishFpCardActionAnswerer.SkirmishFpUseCardTrainer(),
                new SkirmishShadowCardActionAnswerer.SkirmishShadowPlayCardTrainer(),
                new SkirmishShadowCardActionAnswerer.SkirmishShadowUseCardTrainer(),
                new RegroupCardActionAnswerer.RegroupPlayCardTrainer(),
                new RegroupCardActionAnswerer.RegroupUseCardTrainer(),
                new OptionalResponsesCardActionTrainer(),
                new ShadowAssignmentTrainer(),
                new FpAssignmentTrainer()
        );
    }
}
