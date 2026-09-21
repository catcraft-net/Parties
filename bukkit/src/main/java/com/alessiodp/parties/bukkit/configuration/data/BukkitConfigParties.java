package com.alessiodp.parties.bukkit.configuration.data;

import com.alessiodp.core.common.configuration.ConfigOption;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.configuration.PartiesConstants;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import lombok.Getter;

public class BukkitConfigParties extends ConfigParties {
	@Getter private final String fileName = "parties.yml";
	@Getter private final String resourceName = "bukkit/parties.yml";
	@Getter private final int latestVersion = PartiesConstants.VERSION_BUKKIT_CONFIG_PARTIES;
	
	@ConfigOption(path = "additional.recruitment.enable")
	public static boolean RECRUITMENT_ENABLE;
	@ConfigOption(path = "additional.recruitment.minimum-playtime-minutes")
	public static int RECRUITMENT_MINUTES = 120;
	@ConfigOption(path = "additional.recruitment.clans-per-page")
	public static int RECRUITMENT_PAGE_SIZE = 28;
	@ConfigOption(path = "additional.recruitment.kick-rejoin-block-hours")
	public static int RECRUITMENT_KICK_HOURS = 24;

	// Additional settings
	@ConfigOption(path = "additional.home.cancel.hit")
	public static boolean		ADDITIONAL_HOME_CANCEL_HIT;
	@ConfigOption(path = "additional.home.cancel.moving")
	public static boolean		ADDITIONAL_HOME_CANCEL_MOVING;
	@ConfigOption(path = "additional.home.cancel.distance")
	public static int			ADDITIONAL_HOME_CANCEL_DISTANCE;
	@ConfigOption(path = "additional.home.cancel.reset-cooldown")
	public static boolean		ADDITIONAL_HOME_CANCEL_RESET_COOLDOWN;
	
	@ConfigOption(path = "additional.teleport.cancel.hit")
	public static boolean		ADDITIONAL_TELEPORT_CANCEL_HIT;
	@ConfigOption(path = "additional.teleport.cancel.moving")
	public static boolean		ADDITIONAL_TELEPORT_CANCEL_MOVING;
	@ConfigOption(path = "additional.teleport.cancel.distance")
	public static int			ADDITIONAL_TELEPORT_CANCEL_DISTANCE;
	@ConfigOption(path = "additional.teleport.cancel.reset-cooldown")
	public static boolean		ADDITIONAL_TELEPORT_CANCEL_RESET_COOLDOWN;
	
	
	public BukkitConfigParties(PartiesPlugin plugin) {
		super(plugin);
	}
}
