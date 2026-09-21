package com.alessiodp.parties.bukkit.commands.main;

import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandClaim;
import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.recruitment.*;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandConfirm;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandDebug;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandHome;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandSetHome;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandTeleport;
import com.alessiodp.parties.bukkit.configuration.data.BukkitConfigMain;
import com.alessiodp.parties.bukkit.configuration.data.BukkitConfigParties;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.commands.main.CommandParty;
import com.alessiodp.parties.common.configuration.data.ConfigMain;

public class BukkitCommandParty extends CommandParty {
	
	public BukkitCommandParty(PartiesPlugin instance) {
		super(instance);
		
		description = BukkitConfigMain.COMMANDS_MAIN_PARTY_DESCRIPTION;
		
		// Debug
		if (ConfigMain.PARTIES_DEBUG_COMMAND)
			super.register(new BukkitCommandDebug(plugin, this));
		
		if (!((PartiesPlugin) plugin).isBungeeCordEnabled()) {
			if (BukkitConfigParties.RECRUITMENT_ENABLE) {
                BukkitPartiesPlugin bukkit=(BukkitPartiesPlugin)instance;
                super.register(new RecruitmentCommand(bukkit,this,true));
                super.register(new RecruitmentCommand(bukkit,this,false));
                if (BukkitConfigParties.ADDITIONAL_JOIN_ENABLE) {
                    RecruitmentJoinCommand join=new RecruitmentJoinCommand(bukkit,this);
                    super.register(join);
                    bukkit.getRecruitmentMenu().setJoinCommand(join);
                }
            }
            // Claim
			if (BukkitConfigMain.ADDONS_CLAIM_ENABLE)
				super.register(new BukkitCommandClaim(plugin, this));

			// Confirm
			if (BukkitConfigMain.ADDONS_VAULT_ENABLE && BukkitConfigMain.ADDONS_VAULT_CONFIRM_ENABLE)
				super.register(new BukkitCommandConfirm(plugin, this));
			
			// Home
			if (BukkitConfigParties.ADDITIONAL_HOME_ENABLE) {
				super.register(new BukkitCommandHome(plugin, this));
				super.register(new BukkitCommandSetHome(plugin, this));
			}
			
			// Teleport
			if (BukkitConfigParties.ADDITIONAL_TELEPORT_ENABLE)
				super.register(new BukkitCommandTeleport(plugin, this));
		}
	}
}
