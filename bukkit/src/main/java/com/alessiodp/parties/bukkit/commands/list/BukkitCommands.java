package com.alessiodp.parties.bukkit.commands.list;

import com.alessiodp.core.common.commands.list.ADPCommand;

public enum BukkitCommands implements ADPCommand {
	BROWSE,
	RECRUITMENT,
	CLAIM,
	CONFIRM;
	
	@Override
	public String getOriginalName() {
		return this.name();
	}
}
