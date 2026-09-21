package com.alessiodp.parties.bukkit.recruitment;

import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.configuration.data.BukkitConfigParties;
import com.alessiodp.parties.common.commands.sub.CommandJoin;
import com.alessiodp.parties.common.commands.utils.PartiesCommandData;
import com.alessiodp.parties.common.configuration.data.ConfigMain;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.stream.Collectors;

/** Main-thread inventory rendering and input; admission runs on the recruitment worker. */
public final class RecruitmentMenu implements Listener {
    private static final int[] CLAN_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final BukkitPartiesPlugin plugin;
    private final RecruitmentService service;
    private RecruitmentJoinCommand joinCommand;
    public void setJoinCommand(RecruitmentJoinCommand command) { joinCommand=command; }
    public RecruitmentMenu(BukkitPartiesPlugin plugin,RecruitmentService service){this.plugin=plugin;this.service=service;}

    public void open(Player player,boolean member) {
        if (!service.isAvailable()) { tell(player,"Recruitment is unavailable. Please try again later.");return; }
        if (!player.hasPermission("parties.user.browse")) { tell(player,"You do not have permission to browse clans.");return; }
        try {
            long remaining=RecruitmentPolicy.remainingMinutes(player.getStatistic(Statistic.PLAY_ONE_MINUTE),BukkitConfigParties.RECRUITMENT_MINUTES);
            if (remaining>0) { tell(player,"Play for " + remaining + " more minute(s) on this realm to browse clans.");return; }
        } catch (RuntimeException e) { tell(player,"Your playtime is unavailable. Please try again later.");return; }
        show(player,member,snapshot(),0);
    }
    private List<RecruitmentEntry> snapshot() {
        Set<UUID> online=Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet());
        List<RecruitmentEntry> entries=new ArrayList<>(service.entries());
        entries.sort(Comparator.<RecruitmentEntry>comparingInt(e -> onlineCount(e,online)).reversed()
                .thenComparing(e -> e.name,String.CASE_INSENSITIVE_ORDER).thenComparing(e -> e.id));
        return entries;
    }
    private int onlineCount(RecruitmentEntry entry,Set<UUID> online) {
        return (int)entry.members.stream().filter(online::contains).count();
    }
    private void show(Player player,boolean member,List<RecruitmentEntry> entries,int requestedPage) {
        if (!player.isOnline() || !service.isAvailable() || !player.hasPermission("parties.user.browse")) return;
        int size=RecruitmentPolicy.pageSize(BukkitConfigParties.RECRUITMENT_PAGE_SIZE);
        int page=RecruitmentPolicy.page(requestedPage,entries.size(),size);
        Session session=new Session(player.getUniqueId(),member,entries,page);
        Inventory inventory=Bukkit.createInventory(session,54,"Find a Clan | " + (page+1));
        session.inventory=inventory;
        Set<UUID> online=Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet());
        for(int i=0;i<size && page*size+i<entries.size();i++) {
            RecruitmentEntry entry=entries.get(page*size+i);
            session.clans.put(CLAN_SLOTS[i],entry.id);
            List<String> lore=new ArrayList<>();
            String description=clean(entry.description,160);
            if (!description.isEmpty()) {
                // Bound line length without trusting formatting in player-supplied descriptions.
                for(int offset=0;offset<description.length();offset+=40)
                    lore.add("§7"+description.substring(offset,Math.min(offset+40,description.length())));
                lore.add("");
            }
            lore.add("§7Members: §f"+entry.members.size()+(ConfigParties.GENERAL_MEMBERS_LIMIT<0?"":"/"+ConfigParties.GENERAL_MEMBERS_LIMIT));
            lore.add("§7Online: §f"+onlineCount(entry,online));
            lore.add("");
            lore.add(member?"§eLeave your current clan before joining.":"§aClick to join!");
            inventory.setItem(CLAN_SLOTS[i],item(Material.PAPER,"§b"+clean(entry.name,48),lore));
        }
        if(entries.isEmpty()) inventory.setItem(22,item(Material.BARRIER,"§eNo clans are recruiting",Collections.singletonList("§7Check back later.")));
        if(page>0) inventory.setItem(45,item(Material.ARROW,"§ePrevious",Collections.emptyList()));
        inventory.setItem(48,item(Material.SUNFLOWER,"§aRefresh",Collections.emptyList()));
        inventory.setItem(49,item(Material.BARRIER,"§cClose",Collections.emptyList()));
        if((page+1)*size<entries.size()) inventory.setItem(53,item(Material.ARROW,"§eNext",Collections.emptyList()));
        player.openInventory(inventory);
    }
    private static String clean(String text,int limit) {
        if(text==null)return "";
        String clean=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',text)).replaceAll("[\\p{Cntrl}]"," ");
        return clean.substring(0,Math.min(limit,clean.length()));
    }
    private static ItemStack item(Material material,String name,List<String> lore) {
        ItemStack item=new ItemStack(material);
        ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);item.setItemMeta(meta);
        return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        Inventory top=event.getView().getTopInventory();
        if(!(top.getHolder() instanceof Session))return;
        event.setCancelled(true);
        Session session=(Session)top.getHolder();
        if(!(event.getWhoClicked() instanceof Player) || !session.viewer.equals(event.getWhoClicked().getUniqueId()) || session.used)return;
        int slot=event.getRawSlot();
        if(slot<0 || slot>=top.getSize() || !event.isLeftClick() || event.isShiftClick())return;
        Player player=(Player)event.getWhoClicked();
        UUID clan=session.clans.get(slot);
        if(clan==null && slot!=45 && slot!=48 && slot!=49 && slot!=53)return;
        session.used=true;
        // Opening/closing inventories during InventoryClickEvent itself is unsafe.
        Bukkit.getScheduler().runTask((Plugin)plugin.getBootstrap(),()->{
            if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=top)return;
            if(slot==49){player.closeInventory();return;}
            if(!service.isAvailable() || !player.hasPermission("parties.user.browse")) {player.closeInventory();return;}
            if(clan==null) {
                if(slot==48)show(player,session.member,snapshot(),session.page);
                else show(player,session.member,session.entries,session.page+(slot==45?-1:1));
            } else {
                player.closeInventory();
                if(session.member) {tell(player,"Leave your current clan before joining another.");return;}
                join(player.getUniqueId(),clan);
            }
        });
    }
    public void join(UUID playerId,UUID clanId) {
        service.submit(()->{
            com.alessiodp.core.common.user.User user=plugin.getPlayer(playerId);
            if(user==null)return null;
            if(!service.isAvailable()) {user.sendMessage("&cRecruitment is unavailable. Please try again later.",true);return null;}
            if(joinCommand!=null)joinCommand.joinFromBrowser(user,clanId);
            return null;
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Session)event.setCancelled(true);
    }
    private static void tell(Player player,String text){player.sendMessage("§e"+text);}
    static final class Session implements InventoryHolder {
        final UUID viewer;final boolean member;final List<RecruitmentEntry> entries;final int page;
        final Map<Integer,UUID> clans=new HashMap<>();
        Inventory inventory;boolean used;
        Session(UUID viewer,boolean member,List<RecruitmentEntry> entries,int page) {
            this.viewer=viewer;this.member=member;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));this.page=page;
        }
        @Override public Inventory getInventory(){return inventory;}
    }
}
