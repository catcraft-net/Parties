package com.alessiodp.parties.common.commands;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.tasks.HomeDelayTask;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HomeDelayAccessTest {
    @Test void lostAccessCancelsPendingTeleport() {
        PartiesPlugin plugin=mock(PartiesPlugin.class,RETURNS_DEEP_STUBS);
        PartyPlayerImpl player=mock(PartyPlayerImpl.class);
        when(player.getPlayerUUID()).thenReturn(UUID.randomUUID());
        AtomicBoolean teleported=new AtomicBoolean(), cancelled=new AtomicBoolean();
        HomeDelayTask task=new HomeDelayTask(plugin,player,0,HomeCapacityTest.home("mine",1)) {
            protected void performTeleport(){teleported.set(true);}
            public void cancel(){cancelled.set(true);}
        };
        task.setAccessCheck(()->false);
        task.run();
        assertTrue(cancelled.get()); assertFalse(teleported.get());
    }
}
