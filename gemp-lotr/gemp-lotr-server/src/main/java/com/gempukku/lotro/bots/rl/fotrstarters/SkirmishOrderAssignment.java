package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.game.state.Assignment;
import com.gempukku.lotro.game.state.NestedAssignment;

import java.util.List;
import java.util.Optional;

public final class SkirmishOrderAssignment {
    private SkirmishOrderAssignment() {
    }

    public static Optional<Assignment> findForChoice(List<Assignment> assignments, int cardId) {
        for (Assignment assignment : assignments) {
            if (assignment instanceof NestedAssignment nested) {
                for (Assignment pending : nested.getPendingSubskirmishes()) {
                    if (pending.getShadowCharacters().stream().anyMatch(card -> card.getCardId() == cardId))
                        return Optional.of(pending);
                }
            } else if (assignment.getFellowshipCharacter() != null
                    && assignment.getFellowshipCharacter().getCardId() == cardId) {
                return Optional.of(assignment);
            }
        }
        return Optional.empty();
    }
}
