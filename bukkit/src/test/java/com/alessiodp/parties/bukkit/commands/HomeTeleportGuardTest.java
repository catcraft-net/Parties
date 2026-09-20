package com.alessiodp.parties.bukkit.commands;
import com.alessiodp.core.bukkit.user.BukkitUser;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandHome;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.parties.objects.PartyHomeImpl;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class HomeTeleportGuardTest {
    @Test void homeRemovedAfterEnqueuePreventsFinalTeleport() {
        PartiesPlugin plugin=mock(PartiesPlugin.class,RETURNS_DEEP_STUBS);
        PartyPlayerImpl player=mock(PartyPlayerImpl.class);
        BukkitUser user=mock(BukkitUser.class);
        AtomicBoolean allowed=new AtomicBoolean(true);
        AtomicReference<Runnable> syncTask=new AtomicReference<>();
        when(plugin.getScheduler().getSyncExecutor()).thenReturn(syncTask::set);
        PartyHomeImpl home=new PartyHomeImpl("mine","world",1,64,1,0,0,"");
        BukkitCommandHome.teleportToPartyHome(plugin,player,user,home,new Location(null,1,64,1),"done",allowed::get);
        assertNotNull(syncTask.get());
        allowed.set(false); syncTask.get().run();
        verify(user,never()).teleportAsync(any(Location.class));
        verify(player).sendMessage(contains("cancelled"));
    }
}
