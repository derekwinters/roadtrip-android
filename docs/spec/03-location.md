# 03 — Location Tracking (ANDLOC)

**Only the parent phone reports location** (design decision): a foreground service samples GPS
every `ping_interval_s` (from server config, default 300 s) and enqueues `location.ping`
events into the outbox — so tracking works offline and backfills on reconnect. Tablets and kid
profiles never request location permissions.

## Behavior

- Tracking is opt-in per device: a parent enables "This phone is the trip tracker" in
  settings; the toggle exists only for parent profiles on phone-class devices.
- The service runs as a `foregroundServiceType="location"` service with a persistent
  notification, samples once per interval, and survives Doze via `setExactAndAllowWhileIdle`
  scheduling.
- Each sample becomes an outbox event immediately (offline-tolerant); flush follows ANDSYNC
  rules.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDLOC-001 | Each location sample enqueues a `location.ping` outbox event with lat, lon, accuracy, and the sample's timestamp as client_ts. | auto |
| ANDLOC-002 | The scheduler requests the next sample `ping_interval_s` after the previous one and follows server config changes on the next cycle. | auto |
| ANDLOC-003 | The tracker toggle is only available to parent profiles; enabling requires granted fine-location (+ background/foreground-service) permission, walked through an explanatory flow. | auto |
| ANDLOC-004 | Samples taken while offline queue up and sync later without loss or duplication (with ANDSYNC-002/003). | auto |
| ANDLOC-005 | Kid profiles and tablets: the app never requests any location permission (manifest-level: permissions exist, but the runtime request flow is unreachable outside the parent tracker flow). | auto |
| ANDLOC-006 | The foreground service shows a persistent "Trip tracking active" notification and stops cleanly from it or the settings toggle. | manual |
| ANDLOC-007 | A GPS sample failure (timeout/no fix) skips that cycle without crashing and retries next cycle; three consecutive failures surface a quiet in-app warning to parents. | auto |
