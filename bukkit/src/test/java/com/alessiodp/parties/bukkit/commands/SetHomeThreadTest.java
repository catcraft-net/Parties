package com.alessiodp.parties.bukkit.commands;
import com.alessiodp.core.common.commands.utils.ADPMainCommand;
import com.alessiodp.parties.bukkit.commands.sub.BukkitCommandSetHome;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.configuration.data.Messages;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SetHomeThreadTest {
    @Test void captureLocationSynchronouslyButBroadcastOnlyFromAsyncContinuation() {
        PartiesPlugin plugin=mock(PartiesPlugin.class,RETURNS_DEEP_STUBS);
        PartyPlayerImpl member=mock(PartyPlayerImpl.class);
        PartyImpl party=mock(PartyImpl.class);
        UUID playerId=UUID.randomUUID(),partyId=UUID.randomUUID();
        when(member.getPlayerUUID()).thenReturn(playerId);
        when(member.getPartyId()).thenReturn(partyId);
        when(party.getId()).thenReturn(partyId);
        when(party.getMembers()).thenReturn(Collections.singleton(playerId));
        when(party.getHomes()).thenReturn(new HashSet<>());
        com.alessiodp.parties.common.configuration.data.ConfigMain.COMMANDS_SUB_SETHOME="sethome";
        ConfigParties.ADDITIONAL_HOME_MAX_HOMES=3;
        ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED=true;
        Messages.ADDCMD_SETHOME_CHANGED="saved";
        Messages.ADDCMD_SETHOME_BROADCAST="broadcast";
        AtomicReference<Runnable> sync=new AtomicReference<>(),async=new AtomicReference<>();
        when(plugin.getScheduler().getSyncExecutor()).thenReturn(sync::set);
        when(plugin.getScheduler().runAsync(any(Runnable.class))).thenAnswer(a->{async.set(a.getArgument(0));return null;});
        when(plugin.getRankManager().checkPlayerRank(eq(member),any())).thenReturn(true);
        Player player=mock(Player.class); World world=mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.hasPermission("parties.user.sethome")).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world,1,64,1));
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(playerId)).thenReturn(player);
            new BukkitCommandSetHome(plugin,mock(ADPMainCommand.class)) {
                void execute(){ getLocationAndSave(member,party,"base"); }
            }.execute();
            assertNotNull(sync.get());
            sync.get().run();
            assertNotNull(async.get());
            verify(party,never()).broadcastMessage(anyString(),eq(member));
            async.get().run();
            verify(party).broadcastMessage("broadcast",member);
            verify(member).sendMessage("saved");
        }
    }
}
