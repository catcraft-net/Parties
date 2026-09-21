package com.alessiodp.parties.bukkit.recruitment;
import com.alessiodp.core.common.commands.utils.ADPMainCommand;
import com.alessiodp.core.common.commands.utils.CommandData;
import com.alessiodp.core.common.user.User;
import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.commands.list.BukkitCommands;
import com.alessiodp.parties.common.commands.utils.PartiesCommandData;
import com.alessiodp.parties.common.commands.utils.PartiesSubCommand;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.utils.PartiesPermission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class RecruitmentCommand extends PartiesSubCommand {
    private final BukkitPartiesPlugin parties;
    private final boolean browse;
    public RecruitmentCommand(BukkitPartiesPlugin plugin,ADPMainCommand main,boolean browse) {
        super(plugin,main,browse?BukkitCommands.BROWSE:BukkitCommands.RECRUITMENT,
                browse?PartiesPermission.USER_BROWSE:PartiesPermission.USER_RECRUITMENT,browse?"browse":"recruitment",false);
        this.parties=plugin;this.browse=browse;
        syntax=baseSyntax()+(browse?"":" [on|off]");
        description=browse?"Browse recruiting clans":"Manage public clan recruitment";
        help=syntax+" - "+description;
    }
    @Override public boolean preRequisites(@NotNull CommandData data) {
        return handlePreRequisitesFull(data,browse?null:true,1,browse?1:2);
    }
    @Override public void onCommand(@NotNull CommandData data) {
        RecruitmentService service=parties.getRecruitmentService();
        PartyPlayerImpl member=((PartiesCommandData)data).getPartyPlayer();
        if(service==null || !service.isAvailable()) {
            data.getSender().sendMessage("&cRecruitment is unavailable. Staff: enable recruitment, join and open-close in parties.yml on standalone Paper.",true);return;
        }
        if(browse) {
            boolean inClan=member.isInParty();
            plugin.getScheduler().getSyncExecutor().execute(()->{
                Player player=Bukkit.getPlayer(data.getSender().getUUID());
                if(player!=null)parties.getRecruitmentMenu().open(player,inClan);
            });
        } else if(data.getArgs().length==1) {
            data.getSender().sendMessage(service.isRecruiting(member.getPartyId())?"&aRecruitment is on. Use /clan recruitment off to close it.":"&eRecruitment is off. Use /clan recruitment on to accept public joins.",true);
        } else {
            String value=data.getArgs()[1];
            if(!value.equalsIgnoreCase("on")&&!value.equalsIgnoreCase("off")) {
                data.getSender().sendMessage("&eUsage: /clan recruitment [on|off]",true);return;
            }
            service.setRecruiting(member.getPlayerUUID(),value.equalsIgnoreCase("on"))
                    .thenAccept(message->data.getSender().sendMessage("&e"+message,true))
                    .exceptionally(error->{data.getSender().sendMessage("&cRecruitment could not be saved. Contact staff.",true);return null;});
        }
    }
    @Override public List<String> onTabComplete(@NotNull User sender,String[] args) {
        if(browse||args.length!=2)return Collections.emptyList();
        List<String> values=new ArrayList<>();
        for(String value:Arrays.asList("on","off"))if(value.startsWith(args[1].toLowerCase(Locale.ROOT)))values.add(value);
        return values;
    }
}
