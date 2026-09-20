package com.alessiodp.parties.common.commands;

import com.alessiodp.core.common.commands.utils.ADPSubCommand;
import com.alessiodp.core.common.commands.utils.ADPPermission;
import com.alessiodp.core.common.user.User;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.commands.main.CommandClanAdmin;
import com.alessiodp.parties.common.commands.utils.PartiesCommandData;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.players.objects.PartyRankImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClanAdminCommandTest {
    PartiesPlugin plugin;
    User staff;
    PartyImpl party;
    PartyPlayerImpl leader, member;
    CommandClanAdmin command;
    @BeforeEach void setup() {
        plugin = mock(PartiesPlugin.class, RETURNS_DEEP_STUBS);
        staff = mock(User.class);
        when(plugin.getDatabaseManager().executeSafelyAsync(any(Runnable.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(staff.isPlayer()).thenReturn(true);
        when(staff.getUUID()).thenReturn(UUID.randomUUID());
        when(staff.getName()).thenReturn("Staff");
        when(staff.hasPermission(any(ADPPermission.class))).thenReturn(true);
        ConfigParties.RANK_SET_HIGHER = 20;
        ConfigParties.RANK_SET_DEFAULT = 5;
        ConfigParties.GENERAL_NAME_MINLENGTH = 3;
        ConfigParties.GENERAL_NAME_MAXLENGTH = 16;
        ConfigParties.GENERAL_NAME_ALLOWEDCHARS = "[a-zA-Z0-9]+";
        ConfigParties.GENERAL_NAME_CENSORREGEX = "";
        party = new PartyImpl(plugin, UUID.randomUUID()) {
            public void sendPacketUpdate() { }
            public void sendPacketExperience(double e, PartyPlayer p, boolean g) { }
            public void sendPacketLevelUp(int l) { }
            public CompletableFuture<Void> updateParty() { return CompletableFuture.completedFuture(null); }
        };
        leader = player("Leader", 20);
        member = player("OfflineMember", 5);
        party.setup("OldClan", leader.getPlayerUUID().toString());
        when(plugin.getPartyManager().getParty("OldClan")).thenReturn(party);
        when(plugin.getPartyManager().existsParty(anyString())).thenReturn(false);
        when(plugin.getPlayerManager().getPlayer(staff.getUUID())).thenReturn(null);
        command = new CommandClanAdmin(plugin);
    }
    PartyPlayerImpl player(String name, int rank) {
        UUID id = UUID.randomUUID();
        when(plugin.getOfflinePlayer(id).getName()).thenReturn(name);
        PartyPlayerImpl p = mock(PartyPlayerImpl.class, withSettings().useConstructor(plugin, id).defaultAnswer(CALLS_REAL_METHODS));
        when(plugin.getDatabaseManager().updatePlayer(p)).thenReturn(CompletableFuture.completedFuture(null));
        p.setAccessible(true);
        p.addIntoParty(party.getId(), rank);
        p.setAccessible(false);
        party.getMembers().add(id);
        when(plugin.getPlayerManager().getPlayer(id)).thenReturn(p);
        return p;
    }
    boolean run(String... args) {
        ADPSubCommand sub = command.getSubCommandsByEnum().values().stream().filter(s -> s.getCommandName().equals(args[0])).findFirst().get();
        PartiesCommandData data = new PartiesCommandData();
        data.setSender(staff); data.setArgs(args); data.setCommandLabel("clanadmin");
        if (!sub.preRequisites(data)) return false;
        sub.onCommand(data);
        return true;
    }
    @Test void staffWithoutClanCanRenameAnotherClan() {
        when(plugin.getEventManager().preparePartyPreRenameEvent(party, "OldClan", "NewClan", null, true).getNewPartyName()).thenReturn("NewClan");
        assertTrue(run("rename", "OldClan", "NewClan"));
        assertEquals("NewClan", party.getName());
    }
    @Test void missingStaffPermissionCannotRename() {
        when(staff.hasPermission(any(ADPPermission.class))).thenReturn(false);
        assertFalse(run("rename", "OldClan", "NewClan"));
        assertEquals("OldClan", party.getName());
    }
    @Test void cancelledRenameLeavesNameUntouched() {
        when(plugin.getEventManager().preparePartyPreRenameEvent(party, "OldClan", "NewClan", null, true).isCancelled()).thenReturn(true);
        run("rename", "OldClan", "NewClan");
        assertEquals("OldClan", party.getName());
    }
    @Test void eventCannotIntroduceDuplicateName() {
        when(plugin.getEventManager().preparePartyPreRenameEvent(party, "OldClan", "NewClan", null, true).getNewPartyName()).thenReturn("Taken");
        when(plugin.getPartyManager().existsParty("Taken")).thenReturn(true);
        run("rename", "OldClan", "NewClan");
        assertEquals("OldClan", party.getName());
    }
    @Test void transferToOfflineMemberPreservesOneLeader() {
        run("leader", "OldClan", "OfflineMember");
        assertEquals(member.getPlayerUUID(), party.getLeader());
        assertEquals(20, member.getRank());
        assertEquals(5, leader.getRank());
    }
    @Test void kickLeaderDoesNotDisbandOrRemoveAnyone() {
        run("kick", "OldClan", "Leader");
        assertEquals(2, party.getMembers().size());
        assertEquals(leader.getPlayerUUID(), party.getLeader());
    }
    @Test void kickOfflineMemberByUuid() {
        run("kick", "OldClan", member.getPlayerUUID().toString());
        assertEquals(Collections.singleton(leader.getPlayerUUID()), party.getMembers());
        assertNull(member.getPartyId());
    }
    @Test void cancelledKickPreservesMembership() {
        when(plugin.getEventManager().preparePlayerPreLeaveEvent(member, party, com.alessiodp.parties.api.enums.LeaveCause.KICK, null).isCancelled()).thenReturn(true);
        run("kick", "OldClan", "OfflineMember");
        assertEquals(2, party.getMembers().size());
        assertEquals(party.getId(), member.getPartyId());
    }
    @Test void ambiguousStoredNamesRequireUuid() {
        player("OfflineMember", 5);
        run("kick", "OldClan", "OfflineMember");
        assertEquals(3, party.getMembers().size());
    }
    @Test void targetFromAnotherClanCannotBeChanged() {
        run("kick", "OldClan", UUID.randomUUID().toString());
        assertEquals(2, party.getMembers().size());
    }
    @Test void rankCommandCannotDemoteLeader() {
        PartyRankImpl rank = new PartyRankImpl("member", "Member", "Member", 5, 0, null, Collections.emptyList(), true);
        when(plugin.getRankManager().searchRankByName("Member")).thenReturn(rank);
        run("rank", "OldClan", "Leader", "Member");
        assertEquals(20, leader.getRank());
    }
    @Test void offlineMemberCanBePromoted() {
        PartyRankImpl rank = new PartyRankImpl("mod", "Moderator", "Moderator", 10, 0, null, Collections.emptyList(), false);
        when(plugin.getRankManager().searchRankByName("Moderator")).thenReturn(rank);
        run("rank", "OldClan", "OfflineMember", "Moderator");
        assertEquals(10, member.getRank());
    }
    @Test void deleteOnlySelectedHome() {
        party.getHomes().add(HomeCapacityTest.home("base", 1));
        party.getHomes().add(HomeCapacityTest.home("mine", 2));
        run("delhome", "OldClan", "BASE");
        assertEquals(1, party.getHomes().size());
        assertEquals("mine", party.getHomes().iterator().next().getName());
    }
    @Test void consoleCanModerateWithoutPlayerIdentity() {
        when(staff.isPlayer()).thenReturn(false);
        when(staff.getUUID()).thenReturn(null);
        when(staff.hasPermission(any(ADPPermission.class))).thenReturn(false);
        run("leader", "OldClan", "OfflineMember");
        assertEquals(member.getPlayerUUID(), party.getLeader());
    }
    @Test void noPermissionDoesNotLeakTabSuggestions() {
        when(staff.hasPermission(any(ADPPermission.class))).thenReturn(false);
        assertTrue(command.onTabComplete(staff, new String[]{""}).isEmpty());
    }
    @Test void overlappingStaffMutationWaitsForEarlierStorageWrites() throws Exception {
        java.util.concurrent.CountDownLatch flushing = new java.util.concurrent.CountDownLatch(1);
        CompletableFuture<Void> persisted = new CompletableFuture<>();
        when(plugin.getDatabaseManager().executeSafelyAsync(any(Runnable.class))).thenAnswer(invocation -> {
            flushing.countDown(); return persisted;
        });
        when(plugin.getEventManager().preparePartyPreRenameEvent(party, "OldClan", "NewClan", null, true).getNewPartyName()).thenReturn("NewClan");
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<?> first = executor.submit(() -> run("rename", "OldClan", "NewClan"));
            assertTrue(flushing.await(2, java.util.concurrent.TimeUnit.SECONDS));
            run("kick", "OldClan", "OfflineMember");
            assertEquals(2, party.getMembers().size());
            persisted.complete(null); first.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally { persisted.complete(null); executor.shutdownNow(); }
    }
}
