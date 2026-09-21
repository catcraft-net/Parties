package com.alessiodp.parties.common.commands;

import com.alessiodp.parties.common.parties.ClanHomePolicy;
import com.alessiodp.parties.common.parties.objects.PartyHomeImpl;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.commands.sub.CommandSetHome;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ClanHomePolicyTest {
    PartyImpl party;
    @BeforeEach void setup() {
        party = HomeCapacityTest.party();
        ConfigParties.ADDITIONAL_HOME_MAX_HOMES = 3;
        ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED = true;
        ConfigParties.ADDITIONAL_HOME_SIZE_SECOND = 5;
        ConfigParties.ADDITIONAL_HOME_SIZE_THIRD = 10;
    }
    void members(int count) {
        party.getMembers().clear();
        for (int i=0; i<count; i++) party.getMembers().add(UUID.randomUUID());
    }
    @Test void thresholdsCountAllRegisteredMembers() {
        int[] sizes = {1,4,5,9,10,100}; int[] limits = {1,1,2,2,3,3};
        for(int i=0;i<sizes.length;i++) { members(sizes[i]); assertEquals(limits[i], ClanHomePolicy.limit(party)); }
    }
    @Test void disabledTiersKeepConfiguredGlobalCap() {
        ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED=false;
        ConfigParties.ADDITIONAL_HOME_MAX_HOMES=6;
        members(1); assertEquals(6, ClanHomePolicy.limit(party));
    }
    @Test void enabledTiersNeverExceedThreeOrConfiguredCap() {
        members(50); ConfigParties.ADDITIONAL_HOME_MAX_HOMES=50;
        assertEquals(3, ClanHomePolicy.limit(party));
        ConfigParties.ADDITIONAL_HOME_MAX_HOMES=2;
        assertEquals(2, ClanHomePolicy.limit(party));
    }
    @Test void shrinkPreservesHomesAndRegrowthUnlocksThem() {
        PartyHomeImpl base=HomeCapacityTest.home("default",1), mine=HomeCapacityTest.home("mine",2), farm=HomeCapacityTest.home("farm",3);
        party.getHomes().addAll(Arrays.asList(base,mine,farm));
        members(1);
        assertTrue(ClanHomePolicy.canUse(party,base));
        assertFalse(ClanHomePolicy.canUse(party,mine));
        assertFalse(ClanHomePolicy.canUse(party,farm));
        assertEquals(3,party.getHomes().size());
        members(5); assertTrue(ClanHomePolicy.canUse(party,farm)); assertFalse(ClanHomePolicy.canUse(party,mine));
        members(10); assertTrue(ClanHomePolicy.canUse(party,mine));
    }
    @Test void serializedReloadKeepsUnlockedSelection() {
        members(1);
        party.getHomes().add(HomeCapacityTest.home("zeta",1));
        party.getHomes().add(HomeCapacityTest.home("Alpha",2));
        String saved=PartyHomeImpl.serializeMultiple(party.getHomes());
        party.getHomes().clear(); party.getHomes().addAll(PartyHomeImpl.deserializeMultiple(saved));
        assertTrue(ClanHomePolicy.canUse(party,HomeCapacityTest.home("Alpha",2)));
        assertFalse(ClanHomePolicy.canUse(party,HomeCapacityTest.home("zeta",1)));
    }
    @Test void lockedHomeCannotBeMoved() {
        members(1); party.getHomes().add(HomeCapacityTest.home("default",1)); party.getHomes().add(HomeCapacityTest.home("mine",2));
        assertFalse(CommandSetHome.trySavePartyHome(party,HomeCapacityTest.home("mine",99)));
        assertTrue(party.getHomes().stream().anyMatch(h->h.getX()==2));
        assertTrue(CommandSetHome.trySavePartyHome(party,HomeCapacityTest.home("default",99)));
    }
    @Test void simultaneousSavesCannotExceedAllowance() throws Exception {
        members(5);
        ExecutorService executor=Executors.newFixedThreadPool(8);
        CountDownLatch gate=new CountDownLatch(1);
        try {
            List<Future<?>> results=new ArrayList<>();
            for(int i=0;i<12;i++){final int n=i; results.add(executor.submit(()->{gate.await(); return CommandSetHome.trySavePartyHome(party,HomeCapacityTest.home("home"+n,n));}));}
            gate.countDown(); for(Future<?> result:results) result.get(5,TimeUnit.SECONDS);
            assertEquals(2,party.getHomes().size());
        } finally {executor.shutdownNow();}
    }
    @Test void rejectNamesThatWouldCorruptSerializedHomes() {
        members(10);
        for(String name:Arrays.asList("a,b","a;b","a\nb","", "a b"))
            assertFalse(CommandSetHome.trySavePartyHome(party,HomeCapacityTest.home(name,1)));
        assertTrue(party.getHomes().isEmpty());
    }
    @Test void removedOrReplacedDestinationIsNoLongerUsable() {
        members(10); PartyHomeImpl old=HomeCapacityTest.home("base",1);
        party.getHomes().add(HomeCapacityTest.home("base",2));
        assertFalse(ClanHomePolicy.canUse(party,old));
    }
}
