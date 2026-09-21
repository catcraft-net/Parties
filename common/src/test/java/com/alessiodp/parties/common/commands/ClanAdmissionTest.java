package com.alessiodp.parties.common.commands;

import com.alessiodp.parties.api.enums.JoinCause;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClanAdmissionTest {
    final PartiesPlugin plugin=mock(PartiesPlugin.class, RETURNS_DEEP_STUBS);
    PartyImpl clan() {
        ConfigParties.GENERAL_MEMBERS_LIMIT=25;
        return new PartyImpl(plugin,UUID.randomUUID()) {
            public void sendPacketUpdate() { }
            public void sendPacketExperience(double e, PartyPlayer p, boolean g) { }
            public void sendPacketLevelUp(int l) { }
            public void sendPacketAddMember(PartyPlayerImpl p, JoinCause c, PartyPlayerImpl i) { }
            public CompletableFuture<Void> updateParty(){return CompletableFuture.completedFuture(null);}
        };
    }
    PartyPlayerImpl player() {
        UUID id=UUID.randomUUID();
        when(plugin.getOfflinePlayer(id).getName()).thenReturn("Recruit");
        PartyPlayerImpl p=mock(PartyPlayerImpl.class, withSettings().useConstructor(plugin,id).defaultAnswer(CALLS_REAL_METHODS));
        p.setAccessible(true);
        return p;
    }
    @Test void deletedClanCannotBeResurrectedByAPendingJoin() {
        PartyImpl clan=clan();PartyPlayerImpl player=player();
        clan.delete();
        assertFalse(clan.addMember(player));
        assertNull(player.getPartyId());
    }
    @Test void playerAlreadyInClanCannotBeAddedToAnother() {
        PartyImpl first=clan(), second=clan(); PartyPlayerImpl player=player();
        assertTrue(first.addMember(player));
        assertFalse(second.addMember(player));
        assertEquals(first.getId(),player.getPartyId());
        assertFalse(second.getMembers().contains(player.getPlayerUUID()));
    }
    @Test void concurrentJoinsToDifferentClansAdmitOnlyOnce() throws Exception {
        PartyImpl first=clan(),second=clan(); PartyPlayerImpl player=player();
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Boolean> a=executor.submit(()->{start.await();return first.addMember(player);});
            Future<Boolean> b=executor.submit(()->{start.await();return second.addMember(player);});
            start.countDown();
            assertNotEquals(a.get(3,TimeUnit.SECONDS),b.get(3,TimeUnit.SECONDS));
            assertEquals(1,first.getMembers().size()+second.getMembers().size());
        } finally { executor.shutdownNow(); }
    }
}
