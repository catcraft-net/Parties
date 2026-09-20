package com.alessiodp.parties.bukkit.commands.sub;

import com.alessiodp.core.common.ADPPlugin;
import com.alessiodp.core.common.commands.utils.ADPMainCommand;
import com.alessiodp.parties.common.commands.sub.CommandSetHome;
import com.alessiodp.parties.common.parties.objects.PartyHomeImpl;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import org.bukkit.Bukkit;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.configuration.data.Messages;
import com.alessiodp.parties.common.parties.ClanHomePolicy;
import com.alessiodp.parties.common.utils.RankPermission;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BukkitCommandSetHome extends CommandSetHome {
	
	public BukkitCommandSetHome(ADPPlugin plugin, ADPMainCommand mainCommand) {
		super(plugin, mainCommand);
	}
	
	@Override
	protected void getLocationAndSave(@NotNull PartyPlayerImpl sender, @NotNull PartyImpl party, @NotNull String name) {
        plugin.getScheduler().getSyncExecutor().execute(() -> {
            Player player = Bukkit.getPlayer(sender.getPlayerUUID());
            if (player == null) return;
            if (!player.hasPermission("parties.user.sethome")
                    || !((PartiesPlugin) plugin).getRankManager().checkPlayerRank(sender, RankPermission.EDIT_HOME)) return;
            PartyHomeImpl location = getHomeLocationOfPlayer(sender, name, "");
            plugin.getScheduler().runAsync(() -> {
                boolean saved;
                synchronized (party) {
                    saved = party.getId().equals(sender.getPartyId())
                            && party.getMembers().contains(sender.getPlayerUUID())
                            && trySavePartyHome(party, location);
                }
                if (saved) {
                    sender.sendMessage(Messages.ADDCMD_SETHOME_CHANGED);
                    party.broadcastMessage(Messages.ADDCMD_SETHOME_BROADCAST, sender);
                } else {
                    sender.sendMessage("&cThis home is locked, your membership changed, or your clan has reached its home limit.");
                    sender.sendMessage(ClanHomePolicy.allowance(party));
                }
            });
        });
	}
	
	public static PartyHomeImpl getHomeLocationOfPlayer(PartyPlayerImpl sender, String name, String server) {
		Player bukkitPlayer = Bukkit.getPlayer(sender.getPlayerUUID());
		if (bukkitPlayer != null) {
			Location location = bukkitPlayer.getLocation();
			return new PartyHomeImpl(
					name,
					location.getWorld() != null ? location.getWorld().getName() : "",
					location.getX(),
					location.getY(),
					location.getZ(),
					location.getYaw(),
					location.getPitch(),
					server
			);
		}
		return null;
	}
}