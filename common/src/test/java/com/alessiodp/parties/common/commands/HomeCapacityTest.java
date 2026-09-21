package com.alessiodp.parties.common.commands;

import com.alessiodp.parties.api.interfaces.PartyPlayer;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.commands.sub.CommandSetHome;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyHomeImpl;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HomeCapacityTest {
    static PartyImpl party() {
        return new PartyImpl(mock(PartiesPlugin.class), UUID.randomUUID()) {
            public void sendPacketUpdate() { }
            public void sendPacketExperience(double exp, PartyPlayer killer, boolean gain) { }
            public void sendPacketLevelUp(int level) { }
            public CompletableFuture<Void> updateParty() { return CompletableFuture.completedFuture(null); }
        };
    }
    static PartyHomeImpl home(String name, double x) {
        return new PartyHomeImpl(name, "world", x, 64, 0, 0, 0, "");
    }
    @Test void finalSaveMustRecheckCapacityAfterAnEarlierCommandCheck() {
        ConfigParties.ADDITIONAL_HOME_MAX_HOMES = 3;
        PartyImpl party = party();
        for (int i = 0; i < 10; i++) party.getMembers().add(UUID.randomUUID());
        party.getHomes().add(home("a", 1));
        party.getHomes().add(home("b", 2));
        party.getHomes().add(home("c", 3));
        CommandSetHome.savePartyHome(party, home("d", 4));
        assertEquals(3, party.getHomes().size(), "a late location response must not exceed the limit");
        assertFalse(party.getHomes().stream().anyMatch(h -> "d".equals(h.getName())));
    }
}
