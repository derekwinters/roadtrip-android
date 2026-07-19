# 02 — Map & Progress (ANDMAP)

"You are here" view driven entirely by server state from the parent phone's pings
(`GET /api/map` + event feed updates). Map rendering uses osmdroid (OpenStreetMap tiles with
on-device tile cache so the last-viewed region works offline).

## Kid view vs parent view (resolved decision)

- **Kids see three markers: trip start, current position, and the active (next) destination**,
  plus the breadcrumb trail behind the current position and the progress panel. They do not
  see the full future destination list.
- **Parents** additionally see the full ordered destination list and can add/edit/reorder/
  remove destinations from the map screen.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDMAP-001 | The map state reducer exposes exactly start, current position, and active destination markers for kid profiles; the full destination list only for parents. | auto |
| ANDMAP-002 | The progress panel shows: miles driven this leg, straight-line distance remaining to the active destination, and last-update time — all from `GET /api/map`, no client-side geodesy beyond display formatting. | auto |
| ANDMAP-003 | The breadcrumb polyline renders the server-provided trail; new pings arriving via the event feed extend it without a full reload. | auto |
| ANDMAP-004 | With no connectivity, the map screen renders the last cached state and labels its freshness ("as of 14:32"). | auto |
| ANDMAP-005 | Stop journal entries deep-link to the map centered on the stop's pin (see ANDJRNL-004). | auto |
| ANDMAP-006 | Parents can create a destination by long-pressing the map (or entering coordinates), and edit/reorder/remove pending destinations; kids never see these affordances (AND-003). | manual |
| ANDMAP-007 | Map tiles use osmdroid's cache; previously viewed areas render offline (dry-run check). | manual |
| ANDMAP-008 | The add-destination flow offers address search: an explicit search action calls `GET /api/geocode` and lists up to 5 matches; picking one fills coordinates and pre-fills the editable name. | auto |
| ANDMAP-009 | When geocode is unavailable (offline or 503), the address path shows a clear needs-internet state while pin and coordinate entry remain available. | auto |
| ANDMAP-010 | Each marker renders with distinct, kind-based iconography, all center-anchored on the point: current position is a car; trip start is a red flat dot; destinations are green flat dots (the active/next destination emphasized) — never osmdroid's default teardrop pin. The kind→style mapping is derived purely from `MarkerKind` (`markerStyleFor`), independent of which markers a role sees (ANDMAP-001). | auto |

## Marker iconography

Marker styling is presentational and derives only from the reducer's `MarkerKind`, so it changes
no kid-vs-parent visibility rule (ANDMAP-001). The pure `markerStyleFor(MarkerKind)` helper in
core maps each kind to a `MarkerStyle`, which the app module renders as a vector drawable
(center-anchored so the glyph sits on the point):

- `CURRENT` → `CAR` — a car glyph on a contrasting disc (`ic_map_car`).
- `START` → `RED_DOT` — red flat dot (`ic_map_dot_start`).
- `ACTIVE_DESTINATION` → `GREEN_DOT_ACTIVE` — emphasized (ringed, larger) green dot
  (`ic_map_dot_destination_active`) so "the one we're driving to" stands out.
- `DESTINATION` → `GREEN_DOT` — green flat dot (`ic_map_dot_destination`).

Each drawable bakes in its own fill plus a white outline so it stays legible over map tiles and
the breadcrumb polyline in both light and dark themes, rather than depending on a theme color.

## Address search

Address search goes through the backend's `GET /api/geocode` proxy (parent-only; up to 5
matches of `{display_name, lat, lon}`; answers 503 `geocode_unavailable` when the upstream
geocoder can't be reached), so the app ships no third-party geocoding SDK. Search fires only
on an explicit action — never per keystroke — and, like the rest of destination editing, is
gated by the profile's role attribute (AND-003): kid profiles never trigger geocode traffic.
