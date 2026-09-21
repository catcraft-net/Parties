# Clan Recruitment Implementation Plan

**Goal:** Deliver the approved opt-in clan browser with a browser-only playtime gate.
**Spec:** ../specs/2026-09-21-clan-recruitment.md
**Architecture:** A realm-local recruitment registry persists explicit opt-ins and kick expirations. Bukkit owns directory snapshots, GUI and commands; final membership admission checks remain at the common party mutation boundary.
**Tech stack:** Existing Maven/Java plugin, Bukkit inventory/statistics API, JUnit 5, Mockito; no added runtime dependency.

## Tasks
- [x] 1. Persist UUID recruitment states and kick expirations with atomic file replacement; test opt-in defaults, restart, expiry and malformed data. Create `bukkit/.../recruitment/RecruitmentRegistry.java` and registry tests.
- [x] 2. Prevent duplicate/cross-clan admission and report failed joins accurately. Add a final admission hook to `PartyImpl`, exercise real membership state with concurrent tests. Preserve existing invitation/event/payment paths.
- [x] 3. Add Bukkit recruitment service, configuration and commands. Build directory from persisted opted-in UUIDs asynchronously; update snapshots from BukkitPartyImpl mutations. Test browser playtime boundaries, filtering, toggle authorization and final checks.
- [x] 4. Add inventory menu and click/drag/session handling. UUID slot mapping, pagination, stale state checks, no item movement or playtime in listings. Tests cover last-page changes, other inventories, doubles, and player disconnect.
- [x] 5. Run full tests/package, disposable startup/restart and player GUI probe if available; independent review; address material findings; document deployment and update PR.

## Review focus
Stale menu after recruitment closes; simultaneous joins to different clans; recruitment persistence failure; reload/disable while loading; proxy mode accidentally offering a local-only browser.

## Rulings and progress
- Existing clean feature branch is the isolated task checkout; user authorized starting after approving the in-chat design.
- Use local-realm playtime; user did not select network-wide playtime.
- Playtime applies only at browser entry; public join and invite routes intentionally do not check it.
- Continue the existing PR per the established delivery request. No live deployment or merge.

## Execution record
- Registry, policy, membership, selection, service and GUI tests passed; full reactor: 124/124.
- Package built with Java 25; real Paper startup/GUI gate/command admission/kick/invitation/restart probe passed.
- Review findings addressed and regression-tested; no live deployment.
- Ruling: existing join/open-close settings must also be enabled; browser explains unavailable configuration instead of silently changing join policy.
- Ruling: use a local sidecar registry to avoid unnecessary changes to each supported membership database schema; proxy-managed browser is explicitly unsupported.
