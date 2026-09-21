package com.alessiodp.parties.bukkit.recruitment;
import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.configuration.data.BukkitConfigParties;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecruitmentServiceTest {
    @TempDir Path dir;
    RecruitmentService service;
    BukkitPartiesPlugin plugin;
    PartyImpl clan;
    UUID id=UUID.randomUUID(), player=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        BukkitConfigParties.RECRUITMENT_ENABLE=true;
        ConfigParties.ADDITIONAL_JOIN_ENABLE=true;
        ConfigParties.ADDITIONAL_JOIN_OPENCLOSE_ENABLE=true;
        BukkitConfigParties.RECRUITMENT_KICK_HOURS=24;
        plugin=mock(BukkitPartiesPlugin.class,RETURNS_DEEP_STUBS);
        clan=mock(PartyImpl.class);
        when(clan.getId()).thenReturn(id);when(clan.getName()).thenReturn("Clan");
        when(clan.getMembers()).thenReturn(new HashSet<>(Collections.singleton(UUID.randomUUID())));
        when(clan.isOpen()).thenReturn(true);
        when(plugin.getPartyManager().getParty(id)).thenReturn(clan);
        service=new RecruitmentService(plugin,dir.resolve("r.properties"));
        service.start().get(3,TimeUnit.SECONDS);
    }
    @AfterEach void stop(){service.close();BukkitConfigParties.RECRUITMENT_ENABLE=false;}
    @Test void oldOpenClansAreNotListedAndPlaytimeDoesNotGateOrdinaryJoins() {
        service.onPartyChanged(clan);
        assertTrue(service.entries().isEmpty());
        assertTrue(service.allowsPublicJoin(clan,player));
    }
    @Test void persistedOptInLoadsOfflineClanAndClosedOrFullClanDisappears() throws Exception {
        service.close();
        RecruitmentRegistry r=RecruitmentRegistry.load(dir.resolve("r.properties"));r.setRecruiting(id,true);r.save();
        service=new RecruitmentService(plugin,dir.resolve("r.properties"));service.start().get(3,TimeUnit.SECONDS);
        assertEquals(1,service.entries().size());
        when(clan.isFull()).thenReturn(true);service.onPartyChanged(clan);
        assertTrue(service.entries().isEmpty());
        when(clan.isFull()).thenReturn(false);service.onPartyChanged(clan);
        assertEquals(1,service.entries().size());
        when(clan.isOpen()).thenReturn(false);service.onPartyChanged(clan);
        assertTrue(service.entries().isEmpty());
        assertFalse(service.allowsPublicJoin(clan,player));
    }
    @Test void kickBlockAppliesImmediatelyToPublicAdmission() {
        service.recordKick(clan,player);
        assertFalse(service.allowsPublicJoin(clan,player));
    }
    @Test void rebuildReevaluatesCapacityAfterReload() throws Exception {
        service.close();
        RecruitmentRegistry r=RecruitmentRegistry.load(dir.resolve("r.properties"));r.setRecruiting(id,true);r.save();
        service=new RecruitmentService(plugin,dir.resolve("r.properties"));service.start().get(3,TimeUnit.SECONDS);
        when(clan.isFull()).thenReturn(true);
        service.rebuild().get(3,TimeUnit.SECONDS);
        assertTrue(service.entries().isEmpty());
        when(clan.isFull()).thenReturn(false);
        service.rebuild().get(3,TimeUnit.SECONDS);
        assertEquals(1,service.entries().size());
    }
    @Test void onlyAuthorizedClanMemberCanToggleAndChoicePersists() throws Exception {
        PartyPlayerImpl actor=mock(PartyPlayerImpl.class);when(actor.getPartyId()).thenReturn(id);
        when(plugin.getPlayerManager().getPlayer(player)).thenReturn(actor);
        when(plugin.getPartyManager().getPartyOfPlayer(actor)).thenReturn(clan);
        when(clan.getLeader()).thenReturn(UUID.randomUUID());
        assertTrue(service.setRecruiting(player,true).get(3,TimeUnit.SECONDS).contains("Only your clan leader"));
        assertFalse(service.isRecruiting(id));
        when(clan.getLeader()).thenReturn(player);
        when(plugin.getDatabaseManager().executeSafelyAsync(any(Runnable.class))).thenReturn(CompletableFuture.completedFuture(null));
        assertTrue(service.setRecruiting(player,true).get(3,TimeUnit.SECONDS).contains("enabled"));
        assertTrue(RecruitmentRegistry.load(dir.resolve("r.properties")).isRecruiting(id));
        when(clan.getPassword()).thenReturn("hash");
        assertTrue(service.setRecruiting(player,true).get(3,TimeUnit.SECONDS).contains("password"));
        assertTrue(service.setRecruiting(player,false).get(3,TimeUnit.SECONDS).contains("disabled"));
        assertFalse(RecruitmentRegistry.load(dir.resolve("r.properties")).isRecruiting(id));
    }
    @Test void reloadCannotEnableRegistryAfterMalformedStartup() throws Exception {
        service.close();
        java.nio.file.Files.write(dir.resolve("bad.properties"),"clan.bad=true".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        service=new RecruitmentService(plugin,dir.resolve("bad.properties"));
        assertThrows(ExecutionException.class,()->service.start().get(3,TimeUnit.SECONDS));
        assertFalse(service.isAvailable());
        assertThrows(ExecutionException.class,()->service.rebuild().get(3,TimeUnit.SECONDS));
        assertFalse(service.isAvailable());
    }
    @Test void proxyModeCannotEnableRealmLocalBrowser() {
        when(plugin.isBungeeCordEnabled()).thenReturn(true);
        assertFalse(service.isAvailable());
    }
}
