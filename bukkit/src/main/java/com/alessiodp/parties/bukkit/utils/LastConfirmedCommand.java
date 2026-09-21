package com.alessiodp.parties.bukkit.utils;

import lombok.Getter;
import lombok.Setter;

public class LastConfirmedCommand {
	@Getter @Setter private long timestamp;
	@Getter @Setter private String command;
	@Getter @Setter private boolean confirmed;
    @Getter @Setter private java.util.UUID recruitmentClan;
	
	LastConfirmedCommand(long timestamp, String command) {
		this.timestamp = timestamp;
		this.command = command;
		confirmed = false;
	}
}
