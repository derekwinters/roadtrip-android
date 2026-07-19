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
| ANDGAME-018 | The board's end-of-game control is role- and type-aware (mirrors backend GAME-015): for hangman, the creator/setter (host) sees an "End game" control that abandons the game via the resign endpoint, and the guesser sees no end/resign control; for all other game types, any active-game participant still sees "Resign". Spectators and non-active games show no control. | auto |
| ANDGAME-015 | Hangman turn belongs to the single guesser (the joining opponent), never the word-setter/creator: the reducer assigns the turn to the guesser on `game.joined` and keeps it there on every `game.move` — no creator↔opponent alternation — mirroring the backend rule that the setter never takes a turn (`roadtrip-backend` GAME-013; server `Game.turn`). | auto |
| ANDGAME-016 | The hangman board is built from the backend's viewer-aware engine `view` (`display`, `guessed`, `wrong`, `max_wrong`) returned by `GET /api/games/{id}`, not re-folded from the events feed: the word is redacted to `•••` for ongoing games (`roadtrip-backend` GAME-014), so local folding would miscount every guess and garble the mask. The live long-poll re-fetches the view when new game events arrive. | auto |
| ANDGAME-017 | The hangman status line reflects the single guesser's ongoing turn (guesser sees "your turn"; the setter waits on the guesser) and the win/loss end state, and never falls through to the generic "waiting for the other player" — there is no second guesser. | auto |
| ANDGAME-009 | Every lobby row identifies who started the game: rows created by others read "<Name>'s <GameType>", resolved from the profiles cache with a neutral fallback ("Someone") when the creator isn't cached; the profile's own games attribute the opponent ("<GameType> vs <Name>") or "Your <GameType>" when solo/unknown. Game type stays identifiable. Resolution lives in a testable seam (`GameLobbyLabeler`). | auto |
| ANDGAME-010 | The lobby offers both pull-to-refresh and an explicit header refresh button, sharing one reload action (`LobbyRefresher`) that requests a foreground sync and refetches the games cache; the indicator shows while reloading and dismisses on completion, and offline skips the network reload rather than spinning indefinitely. | auto |
| ANDGAME-011 | The lobby list reserves a bottom content inset large enough to clear the floating "new game" button (`GamesLobbyLayout.FAB_CLEARANCE_DP`) so the last row's action stays tappable in every section, not just finished games. | auto |
| ANDGAME-012 | Grid boards (chess, checkers, tic-tac-toe, ultimate) are bounded to the smaller of the available width/height so the full board fits the viewport without scrolling on tablet (`WindowSizeClass` medium/expanded) in portrait and landscape; piece glyphs scale to a fixed fraction (~70%) of their square rather than a fixed point size. The board is kept out of any vertical scroll region. Sizing math lives in `BoardMetrics` (pure/JVM). | auto |
| ANDGAME-013 | Piece rendering uses shared pure mappings (`BoardPieces`): chess draws the SOLID Unicode glyphs for BOTH sides, distinguished by ink — white pieces render as a white fill with a dark outline so they read on the light square, black pieces as black; checkers renders red vs black (never white), both legible on their squares. | auto |
| ANDGAME-014 | When a tic-tac-toe or ultimate game is won, core exposes the winning line cell indices (`tttWinningLine`, plus the ultimate macro line and per-captured-sub-board lines) so the view can highlight exactly those tiles; a finished game (win or draw) reads as clearly over. | auto |
