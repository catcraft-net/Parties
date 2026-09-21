package com.alessiodp.parties.bukkit.recruitment;
import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.configuration.data.BukkitConfigParties;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RecruitmentMenuTest {
    @Test void browseBelowThresholdDoesNotOpenInventoryAndShowsRemainingMinutes() {
        BukkitConfigParties.RECRUITMENT_MINUTES=120;
        Player player=mock(Player.class);
        when(player.hasPermission("parties.user.browse")).thenReturn(true);
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(143999);
        RecruitmentService service=mock(RecruitmentService.class);when(service.isAvailable()).thenReturn(true);
        new RecruitmentMenu(mock(BukkitPartiesPlugin.class),service).open(player,false);
        verify(player,never()).openInventory(any(Inventory.class));
        verify(player).sendMessage(contains("1 more minute"));
    }
    @Test void exactThresholdOpensClanCardsWithoutAnyPlaytimeAndOnlyOptedInDirectoryEntries() {
        BukkitConfigParties.RECRUITMENT_MINUTES=120;BukkitConfigParties.RECRUITMENT_PAGE_SIZE=28;
        ConfigParties.GENERAL_MEMBERS_LIMIT=25;
        Player player=mock(Player.class);UUID id=UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("parties.user.browse")).thenReturn(true);
        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(144000);
        PartyImpl clan=mock(PartyImpl.class);when(clan.getId()).thenReturn(UUID.randomUUID());when(clan.getName()).thenReturn("CatKnights");
        when(clan.getMembers()).thenReturn(Collections.singleton(id));when(clan.getDescription()).thenReturn("Builders welcome");
        RecruitmentService service=mock(RecruitmentService.class);when(service.isAvailable()).thenReturn(true);
        RecruitmentEntry entry=new RecruitmentEntry(clan);
        when(service.entries()).thenReturn(Collections.singleton(entry));
        Map<Integer,ItemStack> items=new HashMap<>();AtomicReference<Inventory> opened=new AtomicReference<>();
        ItemFactory factory=mock(ItemFactory.class);
        when(factory.getItemMeta(any(Material.class))).thenAnswer(a->{ItemMeta m=mock(ItemMeta.class);when(m.clone()).thenReturn(m);return m;});
        when(factory.isApplicable(any(ItemMeta.class),any(Material.class))).thenReturn(true);
        when(factory.asMetaFor(any(ItemMeta.class),any(Material.class))).thenAnswer(a->a.getArgument(0));
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singleton(player));
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            bukkit.when(()->Bukkit.createInventory(any(InventoryHolder.class),eq(54),anyString())).thenAnswer(a->{
                Inventory inv=mock(Inventory.class);when(inv.getHolder()).thenReturn(a.getArgument(0));when(inv.getSize()).thenReturn(54);
                doAnswer(c->{items.put(c.getArgument(0),c.getArgument(1));return null;}).when(inv).setItem(anyInt(),any(ItemStack.class));opened.set(inv);return inv;
            });
            new RecruitmentMenu(mock(BukkitPartiesPlugin.class),service).open(player,false);
            verify(player).openInventory(opened.get());
            assertNotNull(items.get(10));
            ItemMeta meta=items.get(10).getItemMeta();
            org.mockito.ArgumentCaptor<List<String>> lore=org.mockito.ArgumentCaptor.forClass(List.class);
            verify(meta).setLore(lore.capture());
            assertTrue(lore.getValue().contains("§7Members: §f1/25"));
            assertTrue(lore.getValue().contains("§aClick to join!"));
            assertFalse(lore.getValue().toString().toLowerCase(Locale.ROOT).contains("playtime"));
            assertFalse(lore.getValue().toString().contains("120"));
        }
    }
    @Test void duplicateClicksScheduleOnceAndDisconnectCancelsDeferredAction() {
        BukkitPartiesPlugin plugin=mock(BukkitPartiesPlugin.class);
        when(plugin.getBootstrap()).thenReturn(mock(com.alessiodp.parties.bukkit.bootstrap.BukkitPartiesBootstrap.class));
        RecruitmentService service=mock(RecruitmentService.class);
        RecruitmentMenu menu=new RecruitmentMenu(plugin,service);
        Player player=mock(Player.class);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        Inventory inv=mock(Inventory.class);InventoryView view=mock(InventoryView.class);
        RecruitmentMenu.Session session=new RecruitmentMenu.Session(id,false,Collections.emptyList(),0);
        session.clans.put(10,UUID.randomUUID());when(inv.getHolder()).thenReturn(session);when(inv.getSize()).thenReturn(54);
        when(view.getTopInventory()).thenReturn(inv);
        InventoryClickEvent click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(view);
        when(click.getWhoClicked()).thenReturn(player);when(click.getRawSlot()).thenReturn(10);when(click.isLeftClick()).thenReturn(true);
        org.bukkit.scheduler.BukkitScheduler scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);
        AtomicReference<Runnable> action=new AtomicReference<>();
        when(scheduler.runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class))).thenAnswer(a->{action.set(a.getArgument(1));return null;});
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            menu.click(click);menu.click(click);
            verify(scheduler,times(1)).runTask(any(org.bukkit.plugin.Plugin.class),any(Runnable.class));
            when(player.isOnline()).thenReturn(false);action.get().run();
            verify(player,never()).closeInventory();verifyNoInteractions(service);
        }
    }
    @Test void inventoryDragAndBottomInventoryClicksCannotMoveMenuItems() {
        Player player=mock(Player.class);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        Inventory inv=mock(Inventory.class);InventoryView view=mock(InventoryView.class);
        when(inv.getHolder()).thenReturn(new RecruitmentMenu.Session(id,false,Collections.emptyList(),0));
        when(inv.getSize()).thenReturn(54);when(view.getTopInventory()).thenReturn(inv);
        RecruitmentMenu menu=new RecruitmentMenu(mock(BukkitPartiesPlugin.class),mock(RecruitmentService.class));
        InventoryClickEvent click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(view);when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(60);menu.click(click);verify(click).setCancelled(true);
        InventoryDragEvent drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);menu.drag(drag);verify(drag).setCancelled(true);
    }
}
