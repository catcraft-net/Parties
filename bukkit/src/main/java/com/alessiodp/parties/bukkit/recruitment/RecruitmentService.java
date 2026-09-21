package com.alessiodp.parties.bukkit.recruitment;

import com.alessiodp.parties.bukkit.BukkitPartiesPlugin;
import com.alessiodp.parties.bukkit.configuration.data.BukkitConfigParties;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.utils.RankPermission;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Owns local persistence and lightweight immutable listing snapshots. */
public final class RecruitmentService implements AutoCloseable {
    private final BukkitPartiesPlugin plugin;
    private final Path path;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Parties-recruitment"); thread.setDaemon(true); return thread;
    });
    private final ConcurrentMap<UUID, RecruitmentEntry> directory = new ConcurrentHashMap<>();
    private volatile RecruitmentRegistry registry;
    private volatile boolean ready, closed;
    public RecruitmentService(BukkitPartiesPlugin plugin, Path path) { this.plugin=plugin; this.path=path; }

    public CompletableFuture<Void> start() {
        return submit(() -> {
            registry=RecruitmentRegistry.load(path);
            for (UUID id : registry.recruitingClans()) {
                PartyImpl party=plugin.getPartyManager().getParty(id);
                if (party != null) onPartyChanged(party);
            }
            ready=true;
            return null;
        });
    }
    public CompletableFuture<Void> rebuild() {
        ready=false;
        return submit(() -> {
            directory.clear();
            if (registry == null) registry=RecruitmentRegistry.load(path);
            else registry.save(); // A failed write must succeed before recruitment resumes.
            for (UUID id : registry.recruitingClans()) {
                PartyImpl party=plugin.getPartyManager().getParty(id);
                if(party!=null)onPartyChanged(party);
            }
            ready=true;
            return null;
        });
    }
    public boolean isAvailable() {
        return ready && !closed && BukkitConfigParties.RECRUITMENT_ENABLE && !plugin.isBungeeCordEnabled()
                && ConfigParties.ADDITIONAL_JOIN_ENABLE && ConfigParties.ADDITIONAL_JOIN_OPENCLOSE_ENABLE;
    }
    public Collection<RecruitmentEntry> entries() { return new ArrayList<>(directory.values()); }
    public boolean isRecruiting(UUID id) { return registry != null && registry.isRecruiting(id); }
    public boolean isListed(PartyImpl party) {
        return registry != null && registry.isRecruiting(party.getId()) && party.isOpen()
                && !party.isFixed() && !party.isFull() && party.getName() != null && party.getPassword() == null;
    }
    public void onPartyChanged(PartyImpl party) {
        if (closed || registry == null) return;
        synchronized (party) {
            if (isListed(party)) directory.put(party.getId(),new RecruitmentEntry(party));
            else directory.remove(party.getId());
        }
    }
    /** No playtime check here: it gates browser entry only. Called under the party lock. */
    public boolean allowsPublicJoin(PartyImpl party, UUID player) {
        if (!BukkitConfigParties.RECRUITMENT_ENABLE || plugin.isBungeeCordEnabled()) return true;
        if (!isAvailable()) return false;
        if (registry.isBlocked(party.getId(),player,System.currentTimeMillis())) return false;
        return !registry.isManaged(party.getId()) || isListed(party);
    }
    public CompletableFuture<String> setRecruiting(UUID actor, boolean enabled) {
        return submit(() -> {
            if (!isAvailable()) return "Recruitment is unavailable. Ask staff to check its configuration.";
            PartyPlayerImpl member=plugin.getPlayerManager().getPlayer(actor);
            PartyImpl party=plugin.getPartyManager().getPartyOfPlayer(member);
            if (party == null) return "You are not in a clan.";
            synchronized (party) {
                if (closed) return "Recruitment is stopping. Please try again after the restart.";
                if (!party.getId().equals(member.getPartyId()) ||
                        !(actor.equals(party.getLeader()) || plugin.getRankManager().checkPlayerRank(member,RankPermission.EDIT_RECRUITMENT)))
                    return "Only your clan leader or an authorized clan rank can change recruitment.";
                if (enabled && (party.isFixed() || party.getPassword() != null))
                    return "Remove your clan password before recruiting. Fixed clans cannot recruit.";
                registry.setRecruiting(party.getId(),enabled);
                party.setOpen(enabled);
                onPartyChanged(party);
            }
            registry.save();
            plugin.getDatabaseManager().executeSafelyAsync(() -> { }).join();
            return enabled ? "Recruitment enabled. Eligible players can now join your clan without an invitation."
                    : "Recruitment disabled. Your clan is closed to public joining; invitations still work.";
        });
    }
    public void recordKick(PartyImpl party, UUID player) {
        if (registry == null || closed || !BukkitConfigParties.RECRUITMENT_ENABLE) return;
        long hours=Math.max(0,Math.min(8760,BukkitConfigParties.RECRUITMENT_KICK_HOURS));
        if (hours == 0) return;
        registry.block(party.getId(),player,System.currentTimeMillis()+hours*3600000L);
        submit(() -> { registry.save(); return null; });
    }
    public void remove(UUID id) {
        directory.remove(id);
        if (registry != null && !closed) {
            registry.remove(id);
            submit(() -> { registry.save(); return null; });
        }
    }
    public <T> CompletableFuture<T> submit(Callable<T> work) {
        CompletableFuture<T> result=new CompletableFuture<>();
        if (closed) { result.completeExceptionally(new IllegalStateException("Recruitment stopped")); return result; }
        try {
            worker.execute(() -> {
                try { result.complete(work.call()); }
                catch (Exception e) {
                    ready=false;
                    plugin.getLogger().error("Clan recruitment paused: " + e.getMessage());
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) { result.completeExceptionally(e); }
        return result;
    }
    @Override public void close() {
        closed=true;ready=false;directory.clear();worker.shutdown();
        // Flush the small registry at shutdown, without waiting on worker tasks that may
        // be waiting for a main-thread API event. All normal saves remain asynchronous.
        if (registry != null) {
            try { registry.save(); }
            catch (java.io.IOException e) { plugin.getLogger().error("Cannot save clan recruitment at shutdown",e); }
        }
    }
}
