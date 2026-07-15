package com.realtimetilegame.game.domain.rule.meld;

import com.realtimetilegame.game.domain.rule.model.MeldCandidate;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.tile.TileCatalog;

public interface MeldValidator {
    ValidationResult<ValidatedMeld> validate(MeldCandidate candidate, TileCatalog tileCatalog);
}
