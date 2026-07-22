package com.realtimetilegame.game.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.realtimetilegame.game.application.dto.CommitTurnCommand;
import com.realtimetilegame.game.domain.rule.model.TurnValidationContext;
import com.realtimetilegame.game.domain.rule.policy.ClassicRulePolicy;
import com.realtimetilegame.game.domain.rule.policy.RulePolicy;
import com.realtimetilegame.game.domain.rule.policy.SpeedRulePolicy;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameMeld;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.state.MeldId;
import com.realtimetilegame.game.domain.state.MeldState;
import com.realtimetilegame.game.domain.state.ParticipantId;
import com.realtimetilegame.game.domain.state.RackState;
import com.realtimetilegame.game.domain.state.TableState;
import com.realtimetilegame.game.domain.state.TileLocationState;
import com.realtimetilegame.game.domain.state.TilePoolState;
import com.realtimetilegame.game.domain.tile.TileCatalog;
import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.domain.tile.TileSetFactory;

@Component
public class GameTurnStateFactory {
    private final TileCatalog tileCatalog = new TileCatalog(TileSetFactory.createStandardSet());

    public TurnValidationContext create(Game game, List<GamePlayer> players, List<GameTile> tiles,
                                        List<GameMeld> melds, GamePlayer requester,
                                        CommitTurnCommand command) {
        TileLocationState turnStartState = persistedState(players, tiles, melds);
        ParticipantId requesterId = participantId(requester);
        Set<TileId> candidateTableTileIds = command.tableMelds().stream()
            .flatMap(meld -> meld.tileIds().stream())
            .map(TileId::new)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<TileId> contributed = new LinkedHashSet<>(turnStartState.rackOf(requesterId).tileIds());
        contributed.retainAll(candidateTableTileIds);

        Map<ParticipantId, RackState> candidateRacks = new LinkedHashMap<>(turnStartState.racks());
        candidateRacks.put(requesterId, turnStartState.rackOf(requesterId).without(contributed));

        List<MeldState> candidateMelds = command.tableMelds().stream().map(meld -> new MeldState(
            new MeldId(meld.meldId()),
            meld.tileIds().stream().map(TileId::new).toList()
        )).toList();
        TileLocationState candidateState = new TileLocationState(
            turnStartState.tilePool(),
            candidateRacks,
            new TableState(candidateMelds)
        );
        RulePolicy policy = switch (game.gameMode()) {
            case CLASSIC -> new ClassicRulePolicy();
            case SPEED -> new SpeedRulePolicy();
        };
        return new TurnValidationContext(
            game.gameMode(),
            requesterId,
            tileCatalog,
            tileCatalog.tileIds(),
            turnStartState,
            candidateState,
            requester.initialMeldCompleted(),
            policy
        );
    }

    private static TileLocationState persistedState(List<GamePlayer> players, List<GameTile> tiles,
                                                    List<GameMeld> melds) {
        List<TileId> pool = tiles.stream()
            .filter(tile -> tile.location() == GameTileLocation.POOL)
            .sorted(Comparator.comparingInt(GameTile::positionOrder))
            .map(GameTile::tileId)
            .toList();
        Map<ParticipantId, RackState> racks = new LinkedHashMap<>();
        players.stream().sorted(Comparator.comparingInt(GamePlayer::seatOrder)).forEach(player -> {
            List<TileId> rack = tiles.stream()
                .filter(tile -> tile.location() == GameTileLocation.RACK)
                .filter(tile -> tile.owner().id().equals(player.id()))
                .sorted(Comparator.comparingInt(GameTile::positionOrder))
                .map(GameTile::tileId)
                .toList();
            racks.put(participantId(player), new RackState(rack));
        });
        List<MeldState> table = melds.stream()
            .sorted(Comparator.comparingInt(GameMeld::positionOrder))
            .map(meld -> new MeldState(
                new MeldId(meld.meldId()),
                tiles.stream()
                    .filter(tile -> tile.location() == GameTileLocation.TABLE)
                    .filter(tile -> tile.meld().id().equals(meld.id()))
                    .sorted(Comparator.comparingInt(GameTile::positionOrder))
                    .map(GameTile::tileId)
                    .toList()
            ))
            .toList();
        return new TileLocationState(new TilePoolState(pool), racks, new TableState(table));
    }

    private static ParticipantId participantId(GamePlayer player) {
        return new ParticipantId(Long.toString(player.user().id()));
    }
}
