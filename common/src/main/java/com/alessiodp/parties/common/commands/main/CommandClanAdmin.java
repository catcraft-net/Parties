package com.alessiodp.parties.common.commands.main;

import com.alessiodp.core.common.commands.utils.ADPMainCommand;
import com.alessiodp.core.common.commands.utils.ADPSubCommand;
import com.alessiodp.core.common.commands.utils.CommandData;
import com.alessiodp.core.common.user.User;
import com.alessiodp.parties.api.enums.LeaveCause;
import com.alessiodp.parties.api.events.common.party.IPartyPreRenameEvent;
import com.alessiodp.parties.api.events.common.player.IPlayerPreLeaveEvent;
import com.alessiodp.parties.api.interfaces.PartyHome;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.commands.list.CommonCommands;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.players.objects.PartyRankImpl;
import com.alessiodp.parties.common.utils.PartiesPermission;
import java.util.*;
import java.util.regex.Pattern;

/** Server moderation, independent of the sender's clan and clan rank. */
public class CommandClanAdmin extends ADPMainCommand {
    private final java.util.concurrent.locks.ReentrantLock moderationLock = new java.util.concurrent.locks.ReentrantLock();
    public CommandClanAdmin(PartiesPlugin plugin) {
        super(plugin, CommonCommands.CLANADMIN, "clanadmin", true);
        aliases = Collections.singletonList("clanadm");
        description = "Moderate any clan";
        subCommands = new HashMap<>();
        subCommandsByEnum = new HashMap<>();
        tabSupport = true;
        register(new Action(plugin, this, moderationLock, "rename", CommonCommands.CLANADMIN_RENAME, PartiesPermission.ADMIN_CLAN_RENAME));
        register(new Action(plugin, this, moderationLock, "leader", CommonCommands.CLANADMIN_LEADER, PartiesPermission.ADMIN_CLAN_LEADER));
        register(new Action(plugin, this, moderationLock, "rank", CommonCommands.CLANADMIN_RANK, PartiesPermission.ADMIN_CLAN_RANK));
        register(new Action(plugin, this, moderationLock, "kick", CommonCommands.CLANADMIN_KICK, PartiesPermission.ADMIN_CLAN_KICK));
        register(new Action(plugin, this, moderationLock, "delhome", CommonCommands.CLANADMIN_DELHOME, PartiesPermission.ADMIN_CLAN_DELHOME));
    }

    @Override public boolean onCommand(User sender, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            boolean allowed = false;
            for (ADPSubCommand sub : subCommands.values()) {
                if (!sender.isPlayer() || sender.hasPermission(sub.getPermission())) {
                    sender.sendMessage("&e/" + sub.getSyntax(), true);
                    allowed = true;
                }
            }
            if (!allowed) sender.sendMessage("&cYou do not have permission to moderate clans.", true);
        } else {
            ADPSubCommand sub = getSubCommand(args[0].toLowerCase(Locale.ROOT));
            if (sub == null) sender.sendMessage("&cUnknown staff command. Use /clanadmin help.", true);
            else plugin.getCommandManager().getCommandUtils().executeCommand(sender, getCommandName(), sub, args);
        }
        return true;
    }

    @Override public List<String> onTabComplete(User sender, String[] args) {
        // Never load clans/players from storage on the tab-completion thread.
        List<String> result = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (ADPSubCommand sub : subCommands.values()) {
                if ((!sender.isPlayer() || sender.hasPermission(sub.getPermission())) && sub.getCommandName().startsWith(prefix))
                    result.add(sub.getCommandName());
            }
            Collections.sort(result);
        }
        return result;
    }

    private static class Action extends ADPSubCommand {
        private final PartiesPlugin parties;
        private final java.util.concurrent.locks.ReentrantLock moderationLock;
        Action(PartiesPlugin plugin, ADPMainCommand main, java.util.concurrent.locks.ReentrantLock lock, String name, CommonCommands id, PartiesPermission perm) {
            super(plugin, main, id, perm, name, true);
            parties = plugin;
            moderationLock = lock;
            listedInHelp = false; // This command has its own permission-filtered help.
            syntax = "clanadmin " + name + " <clan> " + (name.equals("rename") ? "<new-name>" : name.equals("delhome") ? "<home>" : "<player|uuid>")
                    + (name.equals("rank") ? " <rank>" : "");
        }
        @Override public boolean preRequisites(CommandData data) {
            if (data.getSender().isPlayer() && !data.getSender().hasPermission(permission)) {
                data.getSender().sendMessage("&cMissing permission: " + permission, true);
                return false;
            }
            if (data.getArgs().length != (commandName.equals("rank") ? 4 : 3)) {
                data.getSender().sendMessage("&eUsage: /" + syntax, true);
                return false;
            }
            return true;
        }
        @Override public void onCommand(CommandData data) {
            if (!moderationLock.tryLock()) {
                error(data.getSender(), "Another staff clan change is still saving. Please try again.");
                return;
            }
            try { execute(data); } finally { moderationLock.unlock(); }
        }
        private void execute(CommandData data) {
            User sender = data.getSender();
            String[] args = data.getArgs();
            PartyImpl party = parties.getPartyManager().getParty(args[1]);
            if (party == null) { error(sender, "Clan not found."); return; }
            PartyPlayerImpl actor = sender.isPlayer() ? parties.getPlayerManager().getPlayer(sender.getUUID()) : null;
            try {
                String detail;
                if (commandName.equals("rename")) {
                    detail = rename(party, args[2], actor);
                } else if (commandName.equals("delhome")) {
                    synchronized (party) {
                        Set<PartyHome> homes = new HashSet<>(party.getHomes());
                        if (!homes.removeIf(h -> args[2].equalsIgnoreCase(h.getName())))
                            throw new IllegalArgumentException("Home not found.");
                        party.setHomes(homes);
                        detail = "removed home=" + args[2];
                    }
                } else {
                    PartyPlayerImpl target = resolveMember(party, args[2]);
                    if (commandName.equals("kick")) {
                        // Events run outside the party lock; listeners may schedule server work.
                        IPlayerPreLeaveEvent event = parties.getEventManager().preparePlayerPreLeaveEvent(target, party, LeaveCause.KICK, actor);
                        parties.getEventManager().callEvent(event);
                        if (event.isCancelled()) throw new IllegalArgumentException("Removal was cancelled by another plugin.");
                    }
                    synchronized (party) {
                        requireMember(party, target);
                        boolean isLeader = target.getPlayerUUID().equals(party.getLeader());
                        if (commandName.equals("kick")) {
                            if (isLeader) throw new IllegalArgumentException("Transfer leadership before removing the leader.");
                            if (!party.removeMember(target, LeaveCause.KICK, actor)) throw new IllegalArgumentException("The player is no longer in this clan.");
                            detail = "removed player=" + target.getPlayerUUID();
                        } else if (commandName.equals("leader")) {
                            if (party.isFixed()) throw new IllegalArgumentException("A fixed clan does not have a leader.");
                            if (isLeader) throw new IllegalArgumentException("That player is already the leader.");
                            UUID previous = party.getLeader();
                            PartyPlayerImpl oldLeader = parties.getPlayerManager().getPlayer(previous);
                            requireMember(party, oldLeader);
                            party.changeLeader(target);
                            detail = "leader=" + previous + " -> " + target.getPlayerUUID();
                        } else {
                            PartyRankImpl rank = parties.getRankManager().searchRankByName(args[3]);
                            if (rank == null) throw new IllegalArgumentException("Unknown rank.");
                            if (isLeader || rank.getLevel() == ConfigParties.RANK_SET_HIGHER)
                                throw new IllegalArgumentException("Use /clanadmin leader to change leadership.");
                            if (rank.getLevel() == target.getRank()) throw new IllegalArgumentException("That player already has this rank.");
                            if (rank.getSlot() > 0) {
                                int count = 0;
                                for (UUID id : party.getMembers()) {
                                    PartyPlayerImpl member = parties.getPlayerManager().getPlayer(id);
                                    if (member != null && member.getRank() == rank.getLevel()) count++;
                                }
                                if (count >= rank.getSlot()) throw new IllegalArgumentException("This rank has no free slots.");
                            }
                            int previous = target.getRank();
                            target.setRank(rank.getLevel());
                            detail = "player=" + target.getPlayerUUID() + " rank=" + previous + " -> " + rank.getLevel();
                        }
                    }
                }
                // The database executor is FIFO/single-threaded. Keep other staff mutations out
                // until the existing model writes finish, including fully offline (uncached) clans.
                parties.getDatabaseManager().executeSafelyAsync(() -> { }).join();
                // Always logged, regardless of debug/party-chat logging settings.
                plugin.getLogger().info("[ClanAdmin] actor=" + clean(sender.getName()) + " uuid=" + sender.getUUID()
                        + " action=" + commandName + " clan=" + party.getId() + " " + clean(detail));
                sender.sendMessage("&aClan updated: " + party.getName() + ".", true);
            } catch (IllegalArgumentException ex) {
                error(sender, ex.getMessage());
            }
        }
        private String rename(PartyImpl party, String requested, PartyPlayerImpl actor) {
            validateName(party, requested);
            String oldName = party.getName();
            IPartyPreRenameEvent event = parties.getEventManager().preparePartyPreRenameEvent(party, oldName, requested, actor, true);
            parties.getEventManager().callEvent(event);
            if (event.isCancelled()) throw new IllegalArgumentException("Rename was cancelled by another plugin.");
            // Revalidate listener changes and serialize staff renames across clans.
            synchronized (parties.getPartyManager()) {
                synchronized (party) {
                    if (!Objects.equals(oldName, party.getName())) throw new IllegalArgumentException("The clan was renamed during this command. Try again.");
                    validateName(party, event.getNewPartyName());
                    party.rename(event.getNewPartyName(), actor, true);
                    return "name=" + oldName + " -> " + party.getName();
                }
            }
        }
        private void validateName(PartyImpl party, String name) {
            if (name == null || name.length() < ConfigParties.GENERAL_NAME_MINLENGTH || name.length() > ConfigParties.GENERAL_NAME_MAXLENGTH)
                throw new IllegalArgumentException("The new name is outside the configured length limits.");
            if (!Pattern.matches(ConfigParties.GENERAL_NAME_ALLOWEDCHARS, name)
                    || (!ConfigParties.GENERAL_NAME_CENSORREGEX.isEmpty() && Pattern.compile(ConfigParties.GENERAL_NAME_CENSORREGEX, Pattern.CASE_INSENSITIVE).matcher(name).find()))
                throw new IllegalArgumentException("The new name is not allowed.");
            if (name.equalsIgnoreCase(party.getName()) || parties.getPartyManager().existsParty(name))
                throw new IllegalArgumentException("That clan name is already in use.");
        }
        private PartyPlayerImpl resolveMember(PartyImpl party, String input) {
            List<UUID> ids;
            synchronized (party) { ids = new ArrayList<>(party.getMembers()); }
            UUID uuid = null;
            try { uuid = UUID.fromString(input); } catch (IllegalArgumentException ignored) { }
            if (uuid != null) {
                if (!ids.contains(uuid)) throw new IllegalArgumentException("That UUID is not a member of this clan.");
                PartyPlayerImpl result = parties.getPlayerManager().getPlayer(uuid);
                requireMember(party, result);
                return result;
            }
            PartyPlayerImpl found = null;
            for (UUID id : ids) {
                PartyPlayerImpl member = parties.getPlayerManager().getPlayer(id);
                if (member != null && input.equalsIgnoreCase(member.getName())) {
                    if (found != null) throw new IllegalArgumentException("Multiple members have that stored name. Use a UUID.");
                    found = member;
                }
            }
            if (found == null) throw new IllegalArgumentException("Member not found in this clan. Try their UUID.");
            return found;
        }
        private void requireMember(PartyImpl party, PartyPlayerImpl member) {
            if (member == null || !party.getMembers().contains(member.getPlayerUUID()) || !party.getId().equals(member.getPartyId()))
                throw new IllegalArgumentException("The player is no longer in this clan.");
        }
        private void error(User user, String message) { user.sendMessage("&c" + message, true); }
        private String clean(String text) { return text == null ? "null" : text.replaceAll("[\\r\\n\\t]", " "); }
    }
}
