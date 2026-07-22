package com.realtimetilegame.game.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.dto.ActiveGameView;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameMeldRepository;
import com.realtimetilegame.game.domain.session.GamePlayer;
import com.realtimetilegame.game.domain.session.GamePlayerRepository;
import com.realtimetilegame.game.domain.session.GameRepository;
import com.realtimetilegame.game.domain.session.GameTile;
import com.realtimetilegame.game.domain.session.GameTileRepository;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;
import com.realtimetilegame.user.domain.UserStatus;

@Service
public class GameQueryService {
    private final GameRepository gameRepository;
    private final GamePlayerRepository playerRepository;
    private final GameTileRepository tileRepository;
    private final GameMeldRepository meldRepository;
    private final UserRepository userRepository;
    private final GameStateAssembler stateAssembler;

    public GameQueryService(GameRepository gameRepository, GamePlayerRepository playerRepository,
                            GameTileRepository tileRepository, GameMeldRepository meldRepository,
                            UserRepository userRepository,
                            GameStateAssembler stateAssembler) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.tileRepository = tileRepository;
        this.meldRepository = meldRepository;
        this.userRepository = userRepository;
        this.stateAssembler = stateAssembler;
    }

    @Transactional(readOnly = true)
    public GamePrivateState privateState(long gameId, long userId) {
        requireActiveUser(userId);
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
        if (!playerRepository.existsByGameIdAndUserId(gameId, userId)) {
            throw new BusinessException(ErrorCode.GAME_MEMBERSHIP_REQUIRED);
        }
        List<GamePlayer> players = playerRepository.findByGameId(gameId);
        List<GameTile> tiles = tileRepository.findByGameId(gameId);
        return stateAssembler.privateState(game, players, tiles, meldRepository.findByGameId(gameId), userId);
    }

    @Transactional(readOnly = true)
    public ActiveGameView activeGame(long userId) {
        requireActiveUser(userId);
        return gameRepository.findActiveByUserId(userId)
            .map(game -> ActiveGameView.active(game.id(), game.room().id(), game.status().name()))
            .orElseGet(ActiveGameView::none);
    }

    private User requireActiveUser(long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.status() == UserStatus.BLOCKED) throw new BusinessException(ErrorCode.USER_BLOCKED);
        if (user.status() == UserStatus.DELETED) throw new BusinessException(ErrorCode.USER_DELETED);
        return user;
    }
}
