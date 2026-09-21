package com.alessiodp.parties.bukkit.recruitment;

import com.alessiodp.core.common.commands.utils.ADPMainCommand;
import com.alessiodp.core.common.commands.utils.CommandData;
import com.alessiodp.core.common.user.User;
import com.alessiodp.parties.api.enums.JoinCause;
import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.players.objects.BukkitPartyPlayerImpl;
import com.alessiodp.parties.bukkit.utils.LastConfirmedCommand;
import com.alessiodp.parties.common.commands.sub.CommandJoin;
import com.alessiodp.parties.common.commands.utils.PartiesCommandData;
import com.alessiodp.parties.common.configuration.data.ConfigMain;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import java.util.UUID;

/** Normal joins and GUI joins share events/fees, but GUI selections retain their UUID. */
public final class RecruitmentJoinCommand extends CommandJoin {
    private final BukkitPartiesPlugin parties;
    public RecruitmentJoinCommand(BukkitPartiesPlugin plugin,ADPMainCommand main){super(plugin,main);parties=plugin;}
    public void joinFromBrowser(User user,UUID clanId) {
        RecruitmentService service=parties.getRecruitmentService();
        PartyImpl party=parties.getPartyManager().getParty(clanId);
        if(!user.hasPermission("parties.user.browse") || service==null || !service.isAvailable()
                || party==null || !service.isListed(party)) {
            user.sendMessage("&cThis clan is no longer available. Refresh the browser.",true);return;
        }
        Selection data=new Selection(clanId);
        data.setSender(user);data.setCommandLabel(ConfigMain.COMMANDS_MAIN_PARTY_COMMAND);
        data.setArgs(new String[]{ConfigMain.COMMANDS_SUB_JOIN,party.getName()});
        if(preRequisites(data))onCommand(data);
    }
    @Override protected PartyImpl resolveParty(CommandData data) {
        return data instanceof Selection ? parties.getPartyManager().getParty(((Selection)data).clan) : super.resolveParty(data);
    }
    @Override protected boolean isPublicJoinAllowed(PartyImpl party,PartyPlayerImpl player) {
        RecruitmentService service=parties.getRecruitmentService();
        return service==null || service.allowsPublicJoin(party,player.getPlayerUUID());
    }
    @Override protected boolean admit(CommandData data,PartyImpl party,PartyPlayerImpl player) {
        if(!(data instanceof Selection))return super.admit(data,party,player);
        return party.addMember(player,JoinCause.JOIN,player,()-> {
            RecruitmentService service=parties.getRecruitmentService();
            return service!=null && service.isAvailable() && service.isListed(party);
        });
    }
    @Override protected boolean payToJoin(CommandData data,PartyPlayerImpl player,PartyImpl party) {
        BukkitPartyPlayerImpl bukkit=(BukkitPartyPlayerImpl)player;
        LastConfirmedCommand before=bukkit.getLastConfirmedCommand();
        // A browser confirmation cannot be consumed by an unrelated typed join.
        if(before!=null && before.getRecruitmentClan()!=null
                && (!(data instanceof Selection) || !before.getRecruitmentClan().equals(((Selection)data).clan)))
            bukkit.setLastConfirmedCommand(null);
        boolean pending=super.payToJoin(data,player,party);
        LastConfirmedCommand after=bukkit.getLastConfirmedCommand();
        if(pending && data instanceof Selection && after!=null && after!=before)
            after.setRecruitmentClan(((Selection)data).clan);
        return pending;
    }
    private static final class Selection extends PartiesCommandData {
        final UUID clan;
        Selection(UUID clan){this.clan=clan;}
    }
}
