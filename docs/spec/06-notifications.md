# 06 — Local Notifications (ANDNOTIF)

Resolved decision: local notifications fire on **phones and tablets** for **both** triggers —
challenge received and journal activity. There is no push service; notifications derive from
the backend per-profile feed (`GET /api/notifications`, NOTIF-001..005) pulled by the sync
engine (foreground long-poll; background periodic WorkManager).

## Channels

| Channel | Content | Default importance |
|---------|---------|--------------------|
| `challenges` | "<name> challenged you to <game>" → opens the game | high |
| `journal` | new journal activity (posts, crossings, arrivals, stops, results) → opens the journal (or deep-link target) | default |

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDNOTIF-001 | Notification items from the backend feed map to the two channels: `challenge_received` → challenges, `journal_activity` → journal, with correct text and deep-link target. | auto |
| ANDNOTIF-002 | The device never notifies for the signed-in profile's own actions (backend already excludes the actor; the client additionally guards against it). | auto |
| ANDNOTIF-003 | The notification cursor advances only after items are shown, so a killed app surfaces missed items on next sync rather than dropping them. | auto |
| ANDNOTIF-004 | Notifications arriving while the target screen is visible are suppressed (no self-noise: e.g. journal notification while looking at the journal). | auto |
| ANDNOTIF-005 | Tapping a notification opens its deep-link target (challenge → that game; journal activity → journal/deep-link). | manual |
| ANDNOTIF-006 | Multiple pending journal items collapse into one summarized notification per sync pass (no notification storms after long offline stretches). | auto |
