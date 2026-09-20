# Verification — Parties 3.2.18-catcraft.1

## Automated tests

Full Maven reactor test suite: **101 tests, 0 failures, 0 errors**.

- Common: 90 (including 16 staff-command tests, 9 tier-policy tests, capacity-race and pending-delay checks).
- Bukkit: 5 (configuration, final teleport guard, sync location capture/async broadcast separation).
- Bungee: 3 configuration tests.
- Velocity: 3 configuration tests.

The capacity regression first failed with four saved homes when the maximum was three. The new serialized-home test caught null versus empty server-name normalization; this was fixed. The overlapping-staff test first failed before adding the shared moderation lock and storage-queue barrier. The temporary failure in the Bukkit thread test was a missing command-name configuration in its fixture, corrected before the final passing suite.

Built with OpenJDK 25 and Maven 3.9.9. Local runs used a task-local settings file mapping Central/Sonatype repository aliases to Maven Central to avoid obsolete upstream repository endpoints. Source Maven Central URL, compiler version/annotation processing, and CI Java version were corrected as part of this change.

## Disposable runtime smoke test

Paper **26.2 build 124**, Java 25, bound to localhost only, using an independent test directory and synthetic YAML clan records. No player connection and no live CatCraft realm were involved.

Verified both `/clanadmin` and `/clanadm`, console help, refusal to kick a leader, rename, offline-UUID leadership transfer, offline-UUID rank change, home deletion, and offline-UUID member removal. Each successful operation produced an unconditional staff audit log entry.

Stopped and restarted Paper. Verified the leader-removal safeguard against the transferred leader again. Inspected persisted YAML after restart:

- Clan renamed from OldClan to CleanClan.
- Second member is the sole leader at rank 20.
- Former leader retained at the requested Moderator rank 10.
- Kicked third member removed from membership and player records.
- Deleted mine home absent; default home preserved.

Paper emitted a flat-world fixture generator warning during the first boot; there were no Parties startup or staff-command exceptions. The first boot downloaded Parties' usual runtime libraries; the second reused them.

## Artifact and review

The JAR passed ZIP integrity, plugin version, staff permission descriptor, command/policy class presence, and all three configuration-schema checks. The smoke-test JAR and delivery JAR hashes are identical.

Independent read-only review found and verified fixes for:

1. Bukkit sethome broadcasts needing the async event path.
2. Concurrent staff commands loading separate copies of offline clans.
3. Home access changing between queueing and the final Bukkit teleport invocation.

## Boundaries

The target CatCraft server version/configuration has not been supplied. Paper 26.2 is the disposable test runtime, not an assertion about the live realm. Actual player movement/teleport integration, proxy handoff, production economy configuration, load/soak performance, and database-failure recovery were not live-tested. Existing model methods perform asynchronous nontransactional writes; the staff barrier orders writes but does not turn them into atomic transactions or propagate every inherited storage failure. There is no guarantee of zero lag. No deployment or remote branch update has occurred.
