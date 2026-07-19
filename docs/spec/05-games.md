# 05 — Games UI (ANDGAME)

Client for the backend game platform (`roadtrip-backend/docs/spec/08-games.md`). Game actions
are **online-only** (server arbitrates turns); the UI must degrade gracefully offline.

Screens: Lobby (open games, my games, challenge inbox) → Board (per game type) →
Replay (finished games, play/pause/step) and Spectate (live view of someone else's game).

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDGAME-001 | The lobby lists open games to join, the profile's active games (with turn indicator), and incoming challenges; data reduces from `GET /api/games` + the event feed. | auto |
| ANDGAME-002 | Creating a game offers mode (open/challenge with profile picker) and game-specific options (hangman: word/phrase entry with dictionary toggle and live cap validation mirroring GAME-013 rules). | auto |
| ANDGAME-003 | Boards render server state (engine `view`) and submit moves via `POST /api/games/{id}/moves`; a rejected move (400/409) restores the pre-move UI state with the server's reason shown. | auto |
| ANDGAME-004 | All five games have playable boards: chess, checkers, tic-tac-toe, ultimate tic-tac-toe (with dictated-sub-board highlighting), hangman (masked phrase with visible word boundaries, gallows progress). | manual |
| ANDGAME-005 | While a game screen is open, the client long-polls the game event stream so the opponent's move appears without manual refresh (~1s VPN latency tolerated). | auto |
| ANDGAME-006 | Replay mode steps through the recorded move stream with play/pause/step controls, rebuilding board states locally from events (client-side replay determinism against the core reducers). | auto |
| ANDGAME-007 | Spectate mode is replay pinned to live tail: it follows the stream as moves arrive; any profile can spectate any active game. | auto |
| ANDGAME-008 | Offline: game actions are disabled with an explanatory banner; finished-game replays cached locally still work. | auto |
| ANDGAME-009 | Every lobby row identifies who started the game: rows created by others read "<Name>'s <GameType>", resolved from the profiles cache with a neutral fallback ("Someone") when the creator isn't cached; the profile's own games attribute the opponent ("<GameType> vs <Name>") or "Your <GameType>" when solo/unknown. Game type stays identifiable. Resolution lives in a testable seam (`GameLobbyLabeler`). | auto |
| ANDGAME-010 | The lobby offers both pull-to-refresh and an explicit header refresh button, sharing one reload action (`LobbyRefresher`) that requests a foreground sync and refetches the games cache; the indicator shows while reloading and dismisses on completion, and offline skips the network reload rather than spinning indefinitely. | auto |
| ANDGAME-011 | The lobby list reserves a bottom content inset large enough to clear the floating "new game" button (`GamesLobbyLayout.FAB_CLEARANCE_DP`) so the last row's action stays tappable in every section, not just finished games. | auto |
