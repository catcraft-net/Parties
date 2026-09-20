# CatCraft clan moderation and homes

This fork is based on upstream Parties 3.2.18 (`97b03f2`), already present on CatCraft master. The supplied 3.2.9 JAR is older; its release source is commit `86f467b`. The custom build identifies itself as **3.2.18-catcraft.1**.

## Server staff commands

`/clanadmin` and `/clanadm` are equivalent. Staff need no clan membership or clan rank. Commands also work from the console. On a proxy deployment, use these commands at the proxy; the backend does not register them while Parties proxy mode is enabled.

| Command | Permission |
| --- | --- |
| `/clanadmin rename <clan> <new-name>` | `parties.admin.clan.rename` |
| `/clanadmin leader <clan> <player-or-uuid>` | `parties.admin.clan.leader` |
| `/clanadmin rank <clan> <player-or-uuid> <rank>` | `parties.admin.clan.rank` |
| `/clanadmin kick <clan> <player-or-uuid>` | `parties.admin.clan.kick` |
| `/clanadmin delhome <clan> <home>` | `parties.admin.clan.delhome` |

On Bukkit/Paper, `parties.admin.clan.*` grants all five; it is also included in `parties.admin.*`. These permissions default to operators. Grant individual nodes to moderators if they should not transfer leadership. Ordinary `parties.user.*` permissions and clan leader ranks do not grant staff access. Proxy permission plugins should grant the individual nodes (wildcard expansion depends on the permission plugin).

Examples with LuckPerms (substitute your actual staff group):

```text
/lp group moderator permission set parties.admin.clan.rename true
/lp group admin permission set parties.admin.clan.* true
```

Player names are matched only against members of the specified clan, using names available in Parties/platform player data. Offline UUIDs work without LastLoginAPI. If an offline name is unavailable or ambiguous, use the UUID. Rank names follow your existing `parties.yml` configuration.

The leader command transfers ownership to an existing member and assigns the old leader the successor's previous rank. The rank command cannot promote/demote the leader; use the leader command. Kick refuses to remove the current leader, regardless of the normal leave/disband setting. Fixed parties cannot receive a leader through this command. Rank slot limits and name rules still apply. Rename and kick honour cancellation by existing API event listeners.

Staff commands bypass ordinary player rename costs and cooldowns. Successful changes emit an unconditional `[ClanAdmin]` server-log entry containing staff identity, clan UUID, action, and before/after details where applicable. This is normal server logging, not a separate durable audit database. Staff mutations are serialized until the existing storage queue drains; overlapping requests receive a retry message. Existing asynchronous storage/error semantics remain in place: this does not introduce transactional leadership changes or guarantee recovery from database failures.

## Size-based homes

The feature is **opt-in**, so an upgrade preserves the current home policy. Merge these settings into the existing `additional.home` section of `plugins/Parties/parties.yml` (at the proxy for proxy-managed Parties):

```yaml
additional:
  home:
    enable: true
    max-homes: 3
    member-tiers:
      enable: true
      second-home-members: 5
      third-home-members: 10
```

All registered clan members count, whether online or offline:

| Members | Homes available |
| --- | --- |
| 1–4 | 1 |
| 5–9 | 2 |
| 10+ | 3 |

With tiers enabled, three is the hard ceiling; a smaller `max-homes` further restricts it. Disabling tiers restores the existing global cap. Invalid thresholds are conservatively clamped: the second unlock is at least two members, the third at least one member above the second.

Use the existing player commands, with your configured root (shown here as `/clan`):

```text
/clan sethome base
/clan home base
/clan sethome remove base
```

`/clan home` lists choices when more than one home is saved. Lists show the allowance, next unlock threshold, and locked homes. The existing single home remains named `default` when multi-home mode is enabled.

Homes are ordered with `default` first, followed by case-insensitive alphabetical names. When membership falls, excess homes remain saved but cannot be used or moved. Regaining members unlocks them automatically. They may still be deleted. The same ordering is reconstructed after restart; there is no home migration or periodic server-wide scan. New/updated home names accept 1–32 ASCII letters, digits, underscores, or hyphens to protect the existing serialized storage format.

Saving rechecks the allowance inside the party lock. Pending home teleports are cancelled when access is lost or the destination changes; the Bukkit teleport also checks again immediately before it invokes the teleport. Existing `parties.admin.home.others` access remains a staff override. Proxy handoff timing has not been live-tested.

## Build and deployment

Build with JDK 25 because the current upstream dependency set targets the modern server APIs:

```sh
mvn -B -ntp package
```

Output: `output/target/Parties-3.2.18-catcraft.1.jar`. Maven Central's repository URL and annotation processing were corrected for the current Java toolchain; CI now uses Java 25.

Keep the existing Parties data, aliases, ranks, permissions, and other settings. Back up the plugin data before replacing the JAR. Merge the home settings above and restart when enabling/registering home commands. Automatic configuration upgrades are controlled by the existing `parties.automatic-upgrade-configs` setting; the new parties.yml schema is Bukkit 11, Bungee 10, Velocity 3. Do not replace a customized configuration wholesale with the example.

The command and policy tests include no-clan staff, console, offline UUIDs, permission denial, duplicate names, event cancellation, leader protection, tier boundaries, shrink/regrowth, serialized home reload, competing saves, and delayed teleport invalidation. A disposable Paper test is separate from live deployment validation. No CatCraft realm has been modified by this task.
