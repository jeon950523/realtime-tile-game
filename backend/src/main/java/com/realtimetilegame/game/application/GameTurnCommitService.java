package com.realtimetilegame.game.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.dto.CommitTableMeldCommand;
import com.realtimetilegame.game.application.dto.CommitTilePlacementCommand;
import com.realtimetilegame.game.application.dto.CommitTurnCommand;
import com.realtimetilegame.game.application.dto.GamePlayerPublicView;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GamePublicState;
import com.realtimetilegame.game.application.dto.GameTurnCommandResult;
import com.realtimetilegame.game.application.dto.MeldsCommittedPayload;
import com.realtimetilegame.game.domain.rule.model.ValidatedMeld;
import com.realtimetilegame.game.domain.rule.model.ValidatedTurn;
import com.realtimetilegame.game.domain.rule.model.ValidationFailure;
import com.realtimetilegame.game.domain.rule.model.ValidationResult;
import com.realtimetilegame.game.domain.rule.model.ValidationSuccess;
import com.realtimetilegame.game.domain.rule.rearrangement.TableCandidateDeriver;
import com.realtimetilegame.game.domain.rule.rearrangement.TableGridLayoutValidator;
import com.realtimetilegame.game.domain.rule.turn.TurnCommitValidator;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameMeld;
import com.realtimetilegame.game.domain.session.GameMeldRepository;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class GameTurnCommitService {
    public static final String COMMIT = "COMMIT";
    private static final TableCandidateDeriver CANDIDATE_DERIVER = new TableCandidateDeriver();

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GamePlayerRepository playerRepository;
    private final GameTileRepository tileRepository;
    private final GameMeldRepository meldRepository;
    private final GameTurnStateFactory stateFactory;
    private final TurnCommitValidator turnCommitValidator;
    private final GameStateAssembler stateAssembler;
    private final GameEventPublisher eventPublisher;
    private final Clock clock;

    public GameTurnCommitService(UserRepository userRepository, GameRepository gameRepository,
                                 GamePlayerRepository playerRepository, GameTileRepository tileRepository,
                                 GameMeldRepository meldRepository, GameTurnStateFactory stateFactory,
                                 TurnCommitValidator turnCommitValidator, GameStateAssembler stateAssembler,
                                 GameEventPublisher eventPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.tileRepository = tileRepository;
        this.meldRepository = meldRepository;
        this.stateFactory = stateFactory;
        this.turnCommitValidator = turnCommitValidator;
        this.stateAssembler = stateAssembler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GameTurnCommandResult commit(long gameId, long requesterUserId, CommitTurnCommand command) {
        requireActiveUser(requesterUserId);
        Game game = gameRepository.findByIdForUpdate(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        if (game.status() != GameStatus.IN_PROGRESS) throw new BusinessException(ErrorCode.GAME_NOT_IN_PROGRESS);
        List<GamePlayer> players = orderedPlayers(gameId);
        GamePlayer requester = players.stream()
            .filter(player -> player.user().id() == requesterUserId)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED));
        validateTurnEnvelope(game, requesterUserId, command);

        List<GameTile> tiles = tileRepository.findByGameId(gameId);
        List<GameMeld> existingMelds = meldRepository.findByGameId(gameId);
        Map<String, GameTile> tileById = new LinkedHashMap<>();
        tiles.forEach(tile -> tileById.put(tile.tileId().value(), tile));
        List<CommitTilePlacementCommand> placements = normalizePlacements(command);
        validatePlacementTable(placements, requester, tileById);
        List<CommitTableMeldCommand> candidate = deriveCandidateTable(
            placements, requester, existingMelds, tiles
        );
        CommitTurnCommand normalizedCommand = new CommitTurnCommand(
            command.actionId(), command.gameVersion(), placements, candidate
        );

        ValidationResult<ValidatedTurn> validation = turnCommitValidator.validate(
            stateFactory.create(game, players, tiles, existingMelds, requester, normalizedCommand)
        );
        if (validation instanceof ValidationFailure<ValidatedTurn> failure) {
            throw new BusinessException(RuleViolationMapper.toErrorCode(failure.violations().get(0)));
        }
        ValidatedTurn validated = ((ValidationSuccess<ValidatedTurn>) validation).value();
        Map<String, ValidatedMeld> validatedById = validated.validatedMelds().stream()
            .collect(java.util.stream.Collectors.toMap(
                meld -> meld.meldId().value(), meld -> meld, (left, right) -> left, LinkedHashMap::new
            ));

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        ReconciliationSummary reconciliation = reconcileCandidateTable(
            game, requester, candidate, validatedById, tiles, existingMelds, tileById, now
        );

        if (validated.initialMeldCompletedAfterValidation()) requester.completeInitialMeld();
        playerRepository.saveAllAndFlush(List.of(requester));
        GamePlayer nextPlayer = nextPlayer(players, game.currentTurnSeatOrder());
        game.advanceAfterMeld(
            nextPlayer.user(), nextPlayer.seatOrder(), UUID.randomUUID().toString(), now,
            game.room().turnTimeLimitSeconds()
        );
        gameRepository.saveAndFlush(game);

        List<GameTile> committedTiles = tileRepository.findByGameId(gameId);
        List<GameMeld> committedMelds = meldRepository.findByGameId(gameId);
        GamePublicState publicState = stateAssembler.publicState(game, players, committedTiles, committedMelds);
        Map<Long, GamePrivateState> privateStates = stateAssembler.privateStates(
            game, players, committedTiles, committedMelds
        );
        int rackCount = publicState.players().stream()
            .filter(player -> player.userId() == requesterUserId)
            .findFirst().map(GamePlayerPublicView::rackTileCount).orElseThrow();
        OffsetDateTime occurredAt = OffsetDateTime.of(now, ZoneOffset.UTC);
        eventPublisher.publish(new GameTurnCommittedEvent(
            game.id(), "MELDS_COMMITTED", occurredAt,
            new MeldsCommittedPayload(
                game.id(), game.version(), requesterUserId, rackCount,
                requester.initialMeldCompleted(), validated.initialMeldScore(),
                reconciliation.changedMeldIds(), validated.rackToTableTiles().size(),
                reconciliation.tableRecomposed(), game.currentTurnUser().id(),
                game.currentTurnSeatOrder(), game.turnNumber(), game.currentTurnId(),
                OffsetDateTime.of(game.currentTurnStartedAt(), ZoneOffset.UTC),
                OffsetDateTime.of(game.currentTurnDeadlineAt(), ZoneOffset.UTC),
                game.consecutivePassCount()
            ),
            privateStates
        ));
        return new GameTurnCommandResult(game.id(), COMMIT, game.version());
    }

    private static void validateTurnEnvelope(Game game, long requesterUserId, CommitTurnCommand command) {
        if (command == null || command.gameVersion() == null) {
            throw new BusinessException(ErrorCode.INVALID_COMMIT_PAYLOAD);
        }
        if (command.gameVersion() != game.version()) throw new BusinessException(ErrorCode.STALE_GAME_VERSION);
        if (game.currentTurnUser().id() != requesterUserId) throw new BusinessException(ErrorCode.NOT_CURRENT_TURN);
        if ((command.tilePlacements() == null || command.tilePlacements().isEmpty())
            && (command.tableMelds() == null || command.tableMelds().isEmpty())) {
            throw new BusinessException(ErrorCode.EMPTY_MELD_SUBMISSION);
        }
    }

    private static List<CommitTilePlacementCommand> normalizePlacements(CommitTurnCommand command) {
        if (command.tilePlacements() != null) return List.copyOf(command.tilePlacements());
        List<CommitTilePlacementCommand> placements = new ArrayList<>();
        for (CommitTableMeldCommand meld : command.tableMelds()) {
            if (meld == null || meld.tileIds() == null || meld.gridRow() == null || meld.gridColumn() == null) {
                throw new BusinessException(ErrorCode.INVALID_COMMIT_PAYLOAD);
            }
            for (int position = 0; position < meld.tileIds().size(); position++) {
                placements.add(new CommitTilePlacementCommand(
                    meld.tileIds().get(position), meld.gridRow(), meld.gridColumn() + position
                ));
            }
        }
        return List.copyOf(placements);
    }

    private static void validatePlacementTable(List<CommitTilePlacementCommand> candidate,
                                               GamePlayer requester,
                                               Map<String, GameTile> tileById) {
        if (candidate == null || candidate.isEmpty() || candidate.size() > 106) {
            throw new BusinessException(ErrorCode.INVALID_COMMIT_PAYLOAD);
        }
        Set<String> candidateTileIds = new LinkedHashSet<>();
        Set<String> occupiedCells = new LinkedHashSet<>();
        for (CommitTilePlacementCommand placement : candidate) {
            if (placement == null || placement.tileId() == null || placement.tileId().isBlank()
                || placement.gridRow() == null || placement.gridColumn() == null) {
                throw new BusinessException(ErrorCode.INVALID_COMMIT_PAYLOAD);
            }
            if (placement.gridRow() < 0 || placement.gridRow() >= TableGridLayoutValidator.ROWS
                || placement.gridColumn() < 0 || placement.gridColumn() >= TableGridLayoutValidator.COLUMNS) {
                throw new BusinessException(ErrorCode.INVALID_TABLE_LAYOUT);
            }
            String tileId = placement.tileId().trim();
            if (!candidateTileIds.add(tileId)) throw new BusinessException(ErrorCode.DUPLICATE_TILE_IN_TURN);
            if (!occupiedCells.add(placement.gridRow() + ":" + placement.gridColumn())) {
                throw new BusinessException(ErrorCode.INVALID_TABLE_LAYOUT);
            }
            GameTile tile = tileById.get(tileId);
            boolean requesterRack = tile != null && tile.location() == GameTileLocation.RACK
                && tile.owner() != null && tile.owner().id().equals(requester.id());
            boolean existingTable = tile != null && tile.location() == GameTileLocation.TABLE;
            if (!requesterRack && !existingTable) throw new BusinessException(ErrorCode.TILE_NOT_IN_RACK);
        }
        Set<String> baselineTableTileIds = tileById.values().stream()
            .filter(tile -> tile.location() == GameTileLocation.TABLE)
            .map(tile -> tile.tileId().value())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!candidateTileIds.containsAll(baselineTableTileIds)) {
            throw new BusinessException(ErrorCode.INVALID_TABLE_LAYOUT);
        }
    }

    private static List<CommitTableMeldCommand> deriveCandidateTable(
        List<CommitTilePlacementCommand> placements,
        GamePlayer requester,
        List<GameMeld> existingMelds,
        List<GameTile> allTiles
    ) {
        List<TableCandidateDeriver.DerivedCandidate> derived = CANDIDATE_DERIVER.derive(
            placements.stream().map(placement -> new TableCandidateDeriver.TilePlacement(
                placement.tileId().trim(), placement.gridRow(), placement.gridColumn()
            )).toList()
        );
        Map<String, List<String>> existingTileIds = new LinkedHashMap<>();
        existingMelds.stream().sorted(Comparator.comparingInt(GameMeld::positionOrder)).forEach(meld ->
            existingTileIds.put(meld.meldId(), allTiles.stream()
                .filter(tile -> tile.location() == GameTileLocation.TABLE)
                .filter(tile -> tile.meld().id().equals(meld.id()))
                .sorted(Comparator.comparingInt(GameTile::positionOrder))
                .map(tile -> tile.tileId().value()).toList())
        );
        Set<String> usedMeldIds = new LinkedHashSet<>();
        List<CommitTableMeldCommand> result = new ArrayList<>();
        for (TableCandidateDeriver.DerivedCandidate candidate : derived) {
            String meldId = existingTileIds.entrySet().stream()
                .filter(entry -> !usedMeldIds.contains(entry.getKey()))
                .filter(entry -> entry.getValue().equals(candidate.tileIds()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
            if (meldId == null && requester.initialMeldCompleted()) {
                meldId = existingTileIds.entrySet().stream()
                    .filter(entry -> !usedMeldIds.contains(entry.getKey()))
                    .max(Comparator.comparingLong(entry -> entry.getValue().stream()
                        .filter(candidate.tileIds()::contains).count()))
                    .filter(entry -> entry.getValue().stream().anyMatch(candidate.tileIds()::contains))
                    .map(Map.Entry::getKey).orElse(null);
            }
            if (meldId == null) meldId = UUID.randomUUID().toString();
            usedMeldIds.add(meldId);
            result.add(new CommitTableMeldCommand(
                meldId, candidate.tileIds(), candidate.gridRow(), candidate.gridColumn()
            ));
        }
        return List.copyOf(result);
    }

    private ReconciliationSummary reconcileCandidateTable(
        Game game,
        GamePlayer requester,
        List<CommitTableMeldCommand> candidate,
        Map<String, ValidatedMeld> validatedById,
        List<GameTile> allTiles,
        List<GameMeld> existingMelds,
        Map<String, GameTile> tileById,
        LocalDateTime now
    ) {
        Map<String, GameMeld> existingById = existingMelds.stream().collect(
            java.util.stream.Collectors.toMap(GameMeld::meldId, meld -> meld, (left, right) -> left, LinkedHashMap::new)
        );
        Set<String> changedIds = changedMeldIds(candidate, existingMelds, allTiles, validatedById);
        boolean tableRecomposed = existingMelds.stream().anyMatch(meld -> changedIds.contains(meld.meldId()));

        int maxPosition = existingMelds.stream().mapToInt(GameMeld::positionOrder).max().orElse(-1);
        int temporaryMeldBase = Math.max(1_000, Math.addExact(maxPosition, candidate.size() + existingMelds.size() + 2));
        for (int index = 0; index < existingMelds.size(); index++) {
            existingMelds.get(index).stagePosition(Math.addExact(temporaryMeldBase, index), now);
        }
        if (!existingMelds.isEmpty()) meldRepository.saveAllAndFlush(existingMelds);

        Map<String, GameMeld> candidateById = new LinkedHashMap<>();
        List<GameMeld> newMelds = new ArrayList<>();
        for (int index = 0; index < candidate.size(); index++) {
            CommitTableMeldCommand submitted = candidate.get(index);
            GameMeld persisted = existingById.get(submitted.meldId());
            if (persisted == null) {
                ValidatedMeld validMeld = requiredValidated(validatedById, submitted.meldId());
                persisted = GameMeld.committed(
                    game, submitted.meldId(), Math.addExact(temporaryMeldBase, existingMelds.size() + index),
                    submitted.gridRow(), submitted.gridColumn(),
                    validMeld.meldType(), validMeld.score(), requester, now
                );
                newMelds.add(persisted);
            }
            candidateById.put(submitted.meldId(), persisted);
        }
        if (!newMelds.isEmpty()) {
            meldRepository.saveAllAndFlush(newMelds);
        }

        List<GameTile> tableTiles = allTiles.stream()
            .filter(tile -> tile.location() == GameTileLocation.TABLE)
            .toList();
        int temporaryTileBase = Math.max(1_000, Math.addExact(allTiles.size(), 2));
        for (int index = 0; index < tableTiles.size(); index++) {
            tableTiles.get(index).stageWithinTable(Math.addExact(temporaryTileBase, index), now);
        }
        if (!tableTiles.isEmpty()) tileRepository.saveAllAndFlush(tableTiles);

        List<GameTile> candidateTiles = new ArrayList<>();
        for (CommitTableMeldCommand submitted : candidate) {
            GameMeld target = candidateById.get(submitted.meldId());
            for (int position = 0; position < submitted.tileIds().size(); position++) {
                GameTile tile = tileById.get(submitted.tileIds().get(position));
                if (tile.location() == GameTileLocation.RACK) tile.commitToTable(target, position, now);
                else tile.moveWithinTable(target, position, now);
                candidateTiles.add(tile);
            }
        }
        tileRepository.saveAllAndFlush(candidateTiles);

        List<GameMeld> orphaned = existingMelds.stream()
            .filter(meld -> !candidateById.containsKey(meld.meldId()))
            .toList();
        if (!orphaned.isEmpty()) meldRepository.deleteAllAndFlush(orphaned);

        List<GameMeld> finalMelds = new ArrayList<>();
        for (int position = 0; position < candidate.size(); position++) {
            CommitTableMeldCommand submitted = candidate.get(position);
            GameMeld meld = candidateById.get(submitted.meldId());
            ValidatedMeld validMeld = requiredValidated(validatedById, submitted.meldId());
            meld.revalidateAndReposition(
                position, submitted.gridRow(), submitted.gridColumn(),
                validMeld.meldType(), validMeld.score(), now
            );
            finalMelds.add(meld);
        }
        meldRepository.saveAllAndFlush(finalMelds);
        return new ReconciliationSummary(List.copyOf(changedIds), tableRecomposed);
    }

    private static Set<String> changedMeldIds(List<CommitTableMeldCommand> candidate,
                                               List<GameMeld> existingMelds,
                                               List<GameTile> tiles,
                                               Map<String, ValidatedMeld> validatedById) {
        Map<String, GameMeld> existingById = existingMelds.stream().collect(
            java.util.stream.Collectors.toMap(GameMeld::meldId, meld -> meld)
        );
        Set<String> changed = new LinkedHashSet<>();
        for (int position = 0; position < candidate.size(); position++) {
            CommitTableMeldCommand submitted = candidate.get(position);
            GameMeld baseline = existingById.get(submitted.meldId());
            ValidatedMeld validated = requiredValidated(validatedById, submitted.meldId());
            if (baseline == null) {
                changed.add(submitted.meldId());
                continue;
            }
            List<String> baselineTileIds = tiles.stream()
                .filter(tile -> tile.location() == GameTileLocation.TABLE)
                .filter(tile -> tile.meld().id().equals(baseline.id()))
                .sorted(Comparator.comparingInt(GameTile::positionOrder))
                .map(tile -> tile.tileId().value()).toList();
            if (baseline.positionOrder() != position || !baselineTileIds.equals(submitted.tileIds())
                || baseline.gridRow() != submitted.gridRow() || baseline.gridColumn() != submitted.gridColumn()
                || baseline.meldType() != validated.meldType() || baseline.score() != validated.score()) {
                changed.add(submitted.meldId());
            }
        }
        Set<String> candidateIds = candidate.stream().map(CommitTableMeldCommand::meldId)
            .collect(java.util.stream.Collectors.toSet());
        existingMelds.stream().map(GameMeld::meldId).filter(id -> !candidateIds.contains(id)).forEach(changed::add);
        return changed;
    }

    private static ValidatedMeld requiredValidated(Map<String, ValidatedMeld> validatedById, String meldId) {
        ValidatedMeld validated = validatedById.get(meldId);
        if (validated == null) throw new IllegalStateException("validated candidate meld is missing");
        return validated;
    }

    private User requireActiveUser(long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.status() == UserStatus.BLOCKED) throw new BusinessException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new BusinessException(ErrorCode.USER_DELETED);
        return user;
    }

    private List<GamePlayer> orderedPlayers(long gameId) {
        List<GamePlayer> players = playerRepository.findByGameId(gameId).stream()
            .sorted(Comparator.comparingInt(GamePlayer::seatOrder)).toList();
        if (players.size() < 2 || players.size() > 4) {
            throw new IllegalStateException("an in-progress game must have two to four players");
        }
        return players;
    }

    private static GamePlayer nextPlayer(List<GamePlayer> players, int currentSeatOrder) {
        return players.stream().filter(player -> player.seatOrder() > currentSeatOrder).findFirst()
            .orElse(players.get(0));
    }

    private record ReconciliationSummary(List<String> changedMeldIds, boolean tableRecomposed) {
    }
}
