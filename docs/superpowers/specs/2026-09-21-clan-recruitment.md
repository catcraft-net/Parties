# Approved clan recruitment design

Players open `/clan browse` after a configurable amount of local Minecraft playtime (120 minutes by default). Playtime gates browser access only: no playtime appears in clan listings, and normal joins/invitations retain their playtime rules. Recruiting clans can be joined with one click, subject to existing membership permissions, capacity, events and economy confirmation.

Recruitment is globally disabled by default. Existing and new clans are individually unlisted until a leader explicitly runs `/clan recruitment on`. `off` unlists and closes public joining; invitations remain available. Clan ranks may receive `party.edit.recruitment`. Existing open clans are never opted in automatically. Password-protected and fixed clans cannot opt in.

A 54-slot inventory has up to 28 clan items, previous/next/refresh/close controls, names/descriptions/member and online counts. Online clans sort first; pages use stable snapshots. Full clans are omitted. Selection uses UUIDs, never names or item text. No automatic teleport on joining.

Revalidate state when joining, prevent duplicate/cross-clan admissions, honor cancellations, and record a configurable 24-hour public rejoin block after a kick. Invitations deliberately bypass that block. Persist recruitment and blocks across restart. Failure loading recruitment data disables the feature rather than treating data as empty.

Implementation scope is standalone Bukkit/Paper realm-managed Parties. The existing proxy-managed mode must refuse this feature clearly; network playtime and a proxy GUI bridge are not approved implementation scope. A sidecar recruitment file belongs to the local realm and must be backed up with the plugin data.

No tick scans or synchronous database reads from GUI events. Use immutable directory snapshots updated on party changes, asynchronous persistence, and main-thread Bukkit inventory/statistic operations. Verify correctness under stale menus, restarts, concurrent admission and cancelled events; do not claim live realm or network validation.
