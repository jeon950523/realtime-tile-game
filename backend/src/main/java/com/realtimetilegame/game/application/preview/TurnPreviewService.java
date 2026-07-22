package com.realtimetilegame.game.application.preview;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.dto.TurnPreviewCancelCommand;
import com.realtimetilegame.game.application.dto.TurnPreviewCandidateMeld;
import com.realtimetilegame.game.application.dto.TurnPreviewClearedPayload;
import com.realtimetilegame.game.application.dto.TurnPreviewCommand;
import com.realtimetilegame.game.application.dto.TurnPreviewSnapshot;
import com.realtimetilegame.game.application.dto.TurnPreviewTilePlacement;
import com.realtimetilegame.game.domain.rule.rearrangement.TableGridLayoutValidator;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;

@Service
public class TurnPreviewService {
    private static final int MAX_TILES = 106;

    private final GameRepository gameRepository;
    private final GamePlayerRepository playerRepository;
    private final GameTileRepository tileRepository;
    private final TurnPreviewStore store;
    private final Clock clock;

    public TurnPreviewService(GameRepository gameRepository, GamePlayerRepository playerRepository,
                              GameTileRepository tileRepository, TurnPreviewStore store, Clock clock) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.tileRepository = tileRepository;
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UpdateResult update(long pathGameId, long requesterUserId, TurnPreviewCommand command) {
        return update(pathGameId, requesterUserId, null, command);
    }

    @Transactional(readOnly = true)
    public UpdateResult update(long pathGameId, long requesterUserId, String ownerSessionId,
                               TurnPreviewCommand command) {
        PreviewContext context = context(pathGameId, requesterUserId);
        if (!validEnvelope(pathGameId, context.game(), requesterUserId, command)) {
            return UpdateResult.rejected();
        }
        Optional<List<TurnPreviewTilePlacement>> normalized = normalizePlacements(
            rawPlacements(command), context.requester(), tileRepository.findByGameId(pathGameId)
        );
        if (normalized.isEmpty()) return UpdateResult.rejected();

        TurnPreviewSnapshot snapshot = new TurnPreviewSnapshot(
            pathGameId,
            requesterUserId,
            command.baseGameVersion(),
            command.previewRevision(),
            normalized.orElseThrow(),
            null,
            OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        return store.saveIfNewer(snapshot, ownerSessionId)
            ? new UpdateResult(Decision.ACCEPTED, snapshot)
            : UpdateResult.ignored();
    }

    @Transactional(readOnly = true)
    public ClearResult cancel(long pathGameId, long requesterUserId, TurnPreviewCancelCommand command) {
        PreviewContext context = context(pathGameId, requesterUserId);
        if (command == null || command.gameId() == null || command.gameId() != pathGameId
            || command.baseGameVersion() == null || command.baseGameVersion() != context.game().version()
            || command.previewRevision() == null || command.previewRevision() < 1
            || context.game().status() != GameStatus.IN_PROGRESS
            || context.game().currentTurnUser().id() != requesterUserId) {
            return ClearResult.rejected();
        }
        boolean accepted = store.clearIfNewer(
            pathGameId, requesterUserId, command.baseGameVersion(), command.previewRevision()
        );
        if (!accepted) return ClearResult.ignored();
        return new ClearResult(Decision.ACCEPTED, new TurnPreviewClearedPayload(
            pathGameId, requesterUserId, command.baseGameVersion(), command.previewRevision(), "CANCEL"
        ));
    }

    @Transactional(readOnly = true)
    public Optional<TurnPreviewSnapshot> current(long gameId, long requesterUserId) {
        if (!playerRepository.existsActiveByGameIdAndUserId(gameId, requesterUserId)) {
            throw new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED);
        }
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        Optional<TurnPreviewSnapshot> preview = store.find(gameId);
        if (preview.isPresent()) {
            TurnPreviewSnapshot snapshot = preview.orElseThrow();
            if (game.status() != GameStatus.IN_PROGRESS
                || snapshot.baseGameVersion() != game.version()
                || snapshot.turnPlayerId() != game.currentTurnUser().id()) {
                store.remove(gameId);
                return Optional.empty();
            }
        }
        return preview;
    }

    private PreviewContext context(long gameId, long requesterUserId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        GamePlayer requester = playerRepository.findByGameIdAndUserId(gameId, requesterUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED));
        return new PreviewContext(game, requester);
    }

    private static boolean validEnvelope(long pathGameId, Game game, long requesterUserId,
                                         TurnPreviewCommand command) {
        return command != null
            && command.gameId() != null
            && command.gameId() == pathGameId
            && command.baseGameVersion() != null
            && command.baseGameVersion() == game.version()
            && command.previewRevision() != null
            && command.previewRevision() >= 1
            && game.status() == GameStatus.IN_PROGRESS
            && game.currentTurnUser().id() == requesterUserId;
    }

    private static Optional<List<TurnPreviewTilePlacement>> normalizePlacements(
        List<TurnPreviewTilePlacement> candidate,
        GamePlayer requester,
        List<GameTile> gameTiles
    ) {
        if (candidate == null || candidate.isEmpty() || candidate.size() > MAX_TILES) return Optional.empty();
        Map<String, GameTile> tileById = new LinkedHashMap<>();
        gameTiles.forEach(tile -> tileById.put(tile.tileId().value(), tile));
        Set<String> baselineTableIds = new LinkedHashSet<>();
        gameTiles.stream().filter(tile -> tile.location() == GameTileLocation.TABLE)
            .forEach(tile -> baselineTableIds.add(tile.tileId().value()));
        Set<String> candidateTileIds = new LinkedHashSet<>();
        Set<String> occupiedCells = new LinkedHashSet<>();
        List<TurnPreviewTilePlacement> normalized = new ArrayList<>();

        for (TurnPreviewTilePlacement placement : candidate) {
            if (placement == null || placement.tileId() == null || placement.tileId().isBlank()
                || placement.gridRow() == null || placement.gridColumn() == null
                || placement.gridRow() < 0 || placement.gridRow() >= TableGridLayoutValidator.ROWS
                || placement.gridColumn() < 0 || placement.gridColumn() >= TableGridLayoutValidator.COLUMNS) {
                return Optional.empty();
            }
            String tileId = placement.tileId().trim();
            if (!candidateTileIds.add(tileId)
                || !occupiedCells.add(placement.gridRow() + ":" + placement.gridColumn())) return Optional.empty();
            GameTile tile = tileById.get(tileId);
            boolean committedTable = tile != null && tile.location() == GameTileLocation.TABLE;
            boolean ownRack = tile != null && tile.location() == GameTileLocation.RACK
                && tile.owner() != null && tile.owner().id().equals(requester.id());
            if (!committedTable && !ownRack) return Optional.empty();
            normalized.add(new TurnPreviewTilePlacement(
                tileId, placement.gridRow(), placement.gridColumn(),
                committedTable ? "COMMITTED_TABLE" : "CURRENT_PLAYER_RACK"
            ));
        }
        if (!candidateTileIds.containsAll(baselineTableIds)) return Optional.empty();
        return Optional.of(List.copyOf(normalized));
    }

    private static List<TurnPreviewTilePlacement> rawPlacements(TurnPreviewCommand command) {
        if (command == null) return null;
        if (command.tilePlacements() != null) return command.tilePlacements();
        if (command.candidateMelds() == null) return null;
        List<TurnPreviewTilePlacement> legacy = new ArrayList<>();
        for (TurnPreviewCandidateMeld meld : command.candidateMelds()) {
            if (meld == null || meld.tileIds() == null || meld.gridRow() == null || meld.gridColumn() == null) {
                return null;
            }
            for (int position = 0; position < meld.tileIds().size(); position++) {
                legacy.add(new TurnPreviewTilePlacement(
                    meld.tileIds().get(position), meld.gridRow(), meld.gridColumn() + position, null
                ));
            }
        }
        return legacy;
    }

    public enum Decision { ACCEPTED, REJECTED, IGNORED }

    public record UpdateResult(Decision decision, TurnPreviewSnapshot snapshot) {
        private static UpdateResult rejected() { return new UpdateResult(Decision.REJECTED, null); }
        private static UpdateResult ignored() { return new UpdateResult(Decision.IGNORED, null); }
    }

    public record ClearResult(Decision decision, TurnPreviewClearedPayload payload) {
        private static ClearResult rejected() { return new ClearResult(Decision.REJECTED, null); }
        private static ClearResult ignored() { return new ClearResult(Decision.IGNORED, null); }
    }

    private record PreviewContext(Game game, GamePlayer requester) {}
}
