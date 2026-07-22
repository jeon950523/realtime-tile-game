package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.realtimetilegame.game.application.dto.TurnPreviewCancelCommand;
import com.realtimetilegame.game.application.dto.TurnPreviewCandidateMeld;
import com.realtimetilegame.game.application.dto.TurnPreviewCommand;
import com.realtimetilegame.game.application.dto.TurnPreviewTilePlacement;
import com.realtimetilegame.game.application.preview.TurnPreviewService;
import com.realtimetilegame.game.application.preview.TurnPreviewService.Decision;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileLocation;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.game.domain.tile.TileId;
import com.realtimetilegame.game.event.AfterCommitGameTurnEventListener;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.game.event.TurnPreviewDisconnectListener;
import com.realtimetilegame.game.websocket.InMemoryTurnPreviewStore;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.websocket.auth.StompPrincipal;

class TurnPreviewServiceTest {
    private static final long GAME_ID = 33L;
    private static final long TURN_USER_ID = 1L;
    private static final long OBSERVER_USER_ID = 2L;
    private static final long BASE_VERSION = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T08:00:00Z"), ZoneOffset.UTC);

    private GameRepository gameRepository;
    private GamePlayerRepository playerRepository;
    private GameTileRepository tileRepository;
    private InMemoryTurnPreviewStore store;
    private TurnPreviewService service;
    private Game game;
    private GamePlayer turnPlayer;

    @BeforeEach
    void setUp() {
        gameRepository = mock(GameRepository.class);
        playerRepository = mock(GamePlayerRepository.class);
        tileRepository = mock(GameTileRepository.class);
        store = new InMemoryTurnPreviewStore();
        game = mock(Game.class);
        turnPlayer = mock(GamePlayer.class);
        GamePlayer observer = mock(GamePlayer.class);
        User turnUser = mock(User.class);
        when(turnUser.id()).thenReturn(TURN_USER_ID);
        when(game.status()).thenReturn(GameStatus.IN_PROGRESS);
        when(game.version()).thenReturn(BASE_VERSION);
        when(game.currentTurnUser()).thenReturn(turnUser);
        when(turnPlayer.id()).thenReturn(101L);
        when(observer.id()).thenReturn(102L);
        when(gameRepository.findById(GAME_ID)).thenReturn(java.util.Optional.of(game));
        when(playerRepository.findByGameIdAndUserId(GAME_ID, TURN_USER_ID))
            .thenReturn(java.util.Optional.of(turnPlayer));
        when(playerRepository.findByGameIdAndUserId(GAME_ID, OBSERVER_USER_ID))
            .thenReturn(java.util.Optional.of(observer));
        when(playerRepository.existsActiveByGameIdAndUserId(GAME_ID, TURN_USER_ID)).thenReturn(true);
        when(playerRepository.existsActiveByGameIdAndUserId(GAME_ID, OBSERVER_USER_ID)).thenReturn(true);
        List<GameTile> gameTiles = List.of(
            tile("RED-01-A", GameTileLocation.TABLE, null),
            tile("RED-02-A", GameTileLocation.TABLE, null),
            tile("RED-03-A", GameTileLocation.TABLE, null),
            tile("RED-04-A", GameTileLocation.RACK, turnPlayer),
            tile("BLUE-04-A", GameTileLocation.RACK, observer),
            tile("BLACK-13-A", GameTileLocation.POOL, null)
        );
        when(tileRepository.findByGameId(GAME_ID)).thenReturn(gameTiles);
        service = new TurnPreviewService(gameRepository, playerRepository, tileRepository, store, CLOCK);
    }

    @Test
    void beP7D001_acceptsAValidSnapshotWithoutRequiringValidMeldShapes() {
        var result = service.update(GAME_ID, TURN_USER_ID, validCommand(1));

        assertThat(result.decision()).isEqualTo(Decision.ACCEPTED);
        assertThat(result.snapshot().tilePlacements()).extracting(placement -> placement.tileId())
            .containsExactly("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A");
        assertThat(result.snapshot().tilePlacements()).extracting(placement -> placement.gridRow())
            .containsOnly(2);
        assertThat(result.snapshot().tilePlacements()).extracting(placement -> placement.gridColumn())
            .containsExactly(4, 5, 6, 7);
        assertThat(result.snapshot().updatedAt()).isEqualTo(OffsetDateTime.parse("2026-07-18T08:00:00Z"));
    }

    @Test
    void beP7D002_rejectsAnUpdateFromAnotherParticipant() {
        assertThat(service.update(GAME_ID, OBSERVER_USER_ID, validCommand(1)).decision())
            .isEqualTo(Decision.REJECTED);
        assertThat(store.find(GAME_ID)).isEmpty();
    }

    @Test
    void beP7D003_rejectsAStaleBaseGameVersion() {
        TurnPreviewCommand stale = new TurnPreviewCommand(
            GAME_ID, BASE_VERSION - 1, 1L, validCommand(1).tilePlacements(), null
        );
        assertThat(service.update(GAME_ID, TURN_USER_ID, stale).decision()).isEqualTo(Decision.REJECTED);
    }

    @Test
    void beP7D004_ignoresARepeatedOrLowerRevision() {
        assertThat(service.update(GAME_ID, TURN_USER_ID, validCommand(2)).decision()).isEqualTo(Decision.ACCEPTED);
        assertThat(service.update(GAME_ID, TURN_USER_ID, validCommand(1)).decision()).isEqualTo(Decision.IGNORED);
        assertThat(store.find(GAME_ID).orElseThrow().previewRevision()).isEqualTo(2);
    }

    @Test
    void beP7D005_rejectsForeignRackPoolAndDuplicateTiles() {
        assertRejected(List.of(meld(null, List.of(
            "RED-01-A", "RED-02-A", "RED-03-A", "BLUE-04-A"
        ), 0, 0)));
        assertRejected(List.of(meld(null, List.of(
            "RED-01-A", "RED-02-A", "RED-03-A", "BLACK-13-A"
        ), 0, 0)));
        assertRejected(List.of(meld(null, List.of(
            "RED-01-A", "RED-02-A", "RED-03-A", "RED-01-A", "RED-04-A"
        ), 0, 0)));
    }

    @Test
    void beP7D006_rejectsGridCollisionsAndOutOfBoundsCoordinates() {
        assertRejected(List.of(
            meld("M1", List.of("RED-01-A", "RED-02-A"), 0, 0),
            meld("M2", List.of("RED-03-A", "RED-04-A"), 0, 1)
        ));
        assertRejected(List.of(meld(null, List.of(
            "RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A"
        ), 18, 0)));
        assertRejected(List.of(meld(null, List.of(
            "RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A"
        ), 0, 18)));
    }

    @Test
    void beP7D007_previewDoesNotWriteTheGameOrIncrementItsVersion() {
        assertThat(service.update(GAME_ID, TURN_USER_ID, validCommand(1)).decision()).isEqualTo(Decision.ACCEPTED);
        verify(gameRepository, never()).save(any(Game.class));
        verify(gameRepository, never()).saveAndFlush(any(Game.class));
        assertThat(game.version()).isEqualTo(BASE_VERSION);
    }

    @Test
    void beP7D008_cancelCommitAndDisconnectClearThePreview() {
        service.update(GAME_ID, TURN_USER_ID, validCommand(1));
        assertThat(service.cancel(GAME_ID, TURN_USER_ID,
            new TurnPreviewCancelCommand(GAME_ID, BASE_VERSION, 2L)).decision()).isEqualTo(Decision.ACCEPTED);
        assertThat(store.find(GAME_ID)).isEmpty();

        service.update(GAME_ID, TURN_USER_ID, validCommand(3));
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        new AfterCommitGameTurnEventListener(template, store).on(new GameTurnCommittedEvent(
            GAME_ID, "MELDS_COMMITTED", OffsetDateTime.now(CLOCK), Map.of(), Map.of()
        ));
        assertThat(store.find(GAME_ID)).isEmpty();

        service.update(GAME_ID, TURN_USER_ID, validCommand(4));
        SessionDisconnectEvent disconnect = mock(SessionDisconnectEvent.class);
        when(disconnect.getUser()).thenReturn(new StompPrincipal(TURN_USER_ID, CLOCK.instant().plusSeconds(60)));
        new TurnPreviewDisconnectListener(store, template, CLOCK).onDisconnect(disconnect);
        assertThat(store.find(GAME_ID)).isEmpty();
    }

    @Test
    void beP7D009_observerReconnectCanReadTheCurrentValidPreview() {
        service.update(GAME_ID, TURN_USER_ID, validCommand(5));
        assertThat(service.current(GAME_ID, OBSERVER_USER_ID)).get()
            .extracting(snapshot -> snapshot.previewRevision()).isEqualTo(5L);
    }

    private void assertRejected(List<TurnPreviewCandidateMeld> melds) {
        TurnPreviewCommand command = new TurnPreviewCommand(GAME_ID, BASE_VERSION, 1L, melds);
        assertThat(service.update(GAME_ID, TURN_USER_ID, command).decision()).isEqualTo(Decision.REJECTED);
    }

    private TurnPreviewCommand validCommand(long revision) {
        List<String> tileIds = List.of("RED-01-A", "RED-02-A", "RED-03-A", "RED-04-A");
        return new TurnPreviewCommand(GAME_ID, BASE_VERSION, revision,
            java.util.stream.IntStream.range(0, tileIds.size())
                .mapToObj(index -> new TurnPreviewTilePlacement(
                    tileIds.get(index), 2, 4 + index, null
                )).toList(),
            null
        );
    }

    private static TurnPreviewCandidateMeld meld(String meldId, List<String> tileIds, int row, int column) {
        return new TurnPreviewCandidateMeld(meldId, tileIds, row, column);
    }

    private static GameTile tile(String tileId, GameTileLocation location, GamePlayer owner) {
        GameTile tile = mock(GameTile.class);
        when(tile.tileId()).thenReturn(new TileId(tileId));
        when(tile.location()).thenReturn(location);
        when(tile.owner()).thenReturn(owner);
        return tile;
    }
}
