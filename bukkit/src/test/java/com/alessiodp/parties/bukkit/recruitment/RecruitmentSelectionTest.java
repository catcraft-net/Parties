package com.alessiodp.parties.bukkit.recruitment;
import com.alessiodp.core.common.commands.utils.*;
import com.alessiodp.core.common.user.User;
import com.alessiodp.parties.api.enums.JoinCause;
import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.parties.objects.BukkitPartyImpl;
import com.alessiodp.parties.bukkit.players.objects.BukkitPartyPlayerImpl;
import com.alessiodp.parties.common.configuration.data.*;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RecruitmentSelectionTest {
    BukkitPartiesPlugin plugin;RecruitmentService service;BukkitPartyImpl clan;BukkitPartyPlayerImpl member;User user;
    RecruitmentJoinCommand command;
    @BeforeEach void setup() {
        ConfigMain.COMMANDS_SUB_JOIN="join";ConfigMain.COMMANDS_MAIN_PARTY_COMMAND="clan";
        ConfigParties.GENERAL_MEMBERS_LIMIT=25;ConfigParties.ADDITIONAL_JOIN_OPENCLOSE_ENABLE=true;
        Messages.ADDCMD_JOIN_JOINED="Joined";Messages.ADDCMD_JOIN_PLAYERJOINED="New member";
        Messages.ADDCMD_JOIN_OPENCLOSE_CANNOT_JOIN="Unavailable";Messages.PARTIES_COMMON_PARTYNOTFOUND="Missing %party%";
        plugin=mock(BukkitPartiesPlugin.class,RETURNS_DEEP_STUBS);service=mock(RecruitmentService.class);
        when(plugin.getRecruitmentService()).thenReturn(service);when(service.isAvailable()).thenReturn(true);
        when(service.allowsPublicJoin(any(),any())).thenReturn(true);
        UUID id=UUID.randomUUID();when(plugin.getOfflinePlayer(id).getName()).thenReturn("Recruit");
        member=new BukkitPartyPlayerImpl(plugin,id);member.setAccessible(true);
        when(plugin.getPlayerManager().getPlayer(id)).thenReturn(member);
        user=mock(User.class);when(user.isPlayer()).thenReturn(true);when(user.getUUID()).thenReturn(id);
        when(user.hasPermission(any(ADPPermission.class))).thenReturn(true);
        when(user.hasPermission("parties.user.browse")).thenReturn(true);
        clan=new BukkitPartyImpl(plugin,UUID.randomUUID()) {
            public CompletableFuture<Void> updateParty(){return CompletableFuture.completedFuture(null);}
            public void sendPacketAddMember(PartyPlayerImpl p,JoinCause c,PartyPlayerImpl i) { }
            public void broadcastMessage(String m,com.alessiodp.parties.api.interfaces.PartyPlayer p) { }
        };
        clan.setAccessible(true);clan.setName("RenamedClan");clan.setOpen(true);
        when(plugin.getPartyManager().getParty(clan.getId())).thenReturn(clan);
        when(service.isListed(clan)).thenReturn(true);
        command=new RecruitmentJoinCommand(plugin,mock(ADPMainCommand.class));
    }
    @Test void browserSelectionResolvesUUIDInsteadOfAReusedClanName() {
        PartyImpl impostor=mock(PartyImpl.class);when(plugin.getPartyManager().getParty(anyString())).thenReturn(impostor);
        command.joinFromBrowser(user,clan.getId());
        assertEquals(clan.getId(),member.getPartyId());
        verify(plugin.getPartyManager(),never()).getParty(anyString());
    }
    @Test void closingRecruitmentDuringPreJoinEventRejectsFinalAdmission() {
        com.alessiodp.parties.common.events.EventManager events=plugin.getEventManager();
        doAnswer(a->{when(service.isListed(clan)).thenReturn(false);return null;}).when(events).callEvent(any());
        command.joinFromBrowser(user,clan.getId());
        assertNull(member.getPartyId());assertTrue(clan.getMembers().isEmpty());
    }
    @Test void paymentConfirmationKeepsSelectedUUIDAfterRename() throws Exception {
        com.alessiodp.parties.bukkit.configuration.data.BukkitConfigMain.COMMANDS_SUB_CONFIRM="confirm";
        com.alessiodp.parties.bukkit.configuration.data.BukkitConfigMain.ADDONS_VAULT_CONFIRM_TIMEOUT=300000;
        com.alessiodp.parties.bukkit.configuration.data.BukkitMessages.ADDCMD_VAULT_CONFIRM_CONFIRMED="Confirmed";
        com.alessiodp.parties.bukkit.configuration.data.BukkitMessages.ADDCMD_VAULT_CONFIRM_NOCMD="Nothing to confirm";
        java.lang.reflect.Constructor<com.alessiodp.parties.bukkit.utils.LastConfirmedCommand> constructor=
            com.alessiodp.parties.bukkit.utils.LastConfirmedCommand.class.getDeclaredConstructor(long.class,String.class);
        constructor.setAccessible(true);
        com.alessiodp.parties.common.utils.EconomyManager economy=plugin.getEconomyManager();
        when(economy.payCommand(any(),eq(member),anyString(),any(String[].class))).thenAnswer(a->{
            if(member.getLastConfirmedCommand()!=null && member.getLastConfirmedCommand().isConfirmed()) {
                member.setLastConfirmedCommand(null);return false;
            }
            member.setLastConfirmedCommand(constructor.newInstance(System.currentTimeMillis(),"clan join RenamedClan"));return true;
        });
        command.joinFromBrowser(user,clan.getId());
        assertNull(member.getPartyId());
        assertEquals(clan.getId(),member.getLastConfirmedCommand().getRecruitmentClan());
        clan.setName("NewName");
        when(plugin.getPartyManager().getParty("RenamedClan")).thenReturn(mock(PartyImpl.class));
        RecruitmentMenu menu=new RecruitmentMenu(plugin,service);menu.setJoinCommand(command);
        when(plugin.getRecruitmentMenu()).thenReturn(menu);when(plugin.getPlayer(user.getUUID())).thenReturn(user);
        when(service.submit(any(java.util.concurrent.Callable.class))).thenAnswer(a->CompletableFuture.completedFuture(((java.util.concurrent.Callable<?>)a.getArgument(0)).call()));
        com.alessiodp.parties.bukkit.commands.sub.BukkitCommandConfirm confirm=
            new com.alessiodp.parties.bukkit.commands.sub.BukkitCommandConfirm(plugin,mock(ADPMainCommand.class));
        com.alessiodp.parties.common.commands.utils.PartiesCommandData data=new com.alessiodp.parties.common.commands.utils.PartiesCommandData();
        data.setSender(user);data.setArgs(new String[]{"confirm"});
        assertTrue(confirm.preRequisites(data));confirm.onCommand(data);
        assertEquals(clan.getId(),member.getPartyId());
        verify(plugin.getPartyManager(),never()).getParty(anyString());
    }
    @Test void cancelledJoinEventDoesNotAdmit() {
        when(plugin.getEventManager().preparePlayerPreJoinEvent(member,clan,JoinCause.JOIN,member).isCancelled()).thenReturn(true);
        command.joinFromBrowser(user,clan.getId());
        assertNull(member.getPartyId());
    }
}
