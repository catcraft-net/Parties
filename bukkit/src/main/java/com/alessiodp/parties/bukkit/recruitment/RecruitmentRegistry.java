package com.alessiodp.parties.bukkit.recruitment;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Realm-local opt-ins. Disk access is performed only by the recruitment worker. */
public final class RecruitmentRegistry {
    private final Path file;
    private final Map<UUID, Boolean> clans = new HashMap<>();
    private final Map<String, Long> blocks = new HashMap<>();
    private final Object diskLock = new Object();

    private RecruitmentRegistry(Path file) { this.file = file; }

    public static RecruitmentRegistry load(Path file) throws IOException {
        RecruitmentRegistry registry = new RecruitmentRegistry(file);
        if (!Files.exists(file)) return registry;
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(file)) { data.load(input); }
        try {
            for (String key : data.stringPropertyNames()) {
                String value = data.getProperty(key);
                if (key.startsWith("clan.")) {
                    UUID id = UUID.fromString(key.substring(5));
                    if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid recruitment value");
                    registry.clans.put(id, Boolean.parseBoolean(value));
                } else if (key.startsWith("block.")) {
                    String[] ids = key.substring(6).split("/", -1);
                    if (ids.length != 2) throw new IllegalArgumentException("Invalid block key");
                    String normalized = UUID.fromString(ids[0]) + "/" + UUID.fromString(ids[1]);
                    long expiry = Long.parseLong(value);
                    if (expiry < 0) throw new IllegalArgumentException("Negative expiry");
                    registry.blocks.put(normalized, expiry);
                } else throw new IllegalArgumentException("Unknown recruitment key: " + key);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid recruitment data; original file preserved", e); }
        return registry;
    }

    public synchronized boolean isManaged(UUID clan) { return clans.containsKey(clan); }
    public synchronized boolean isRecruiting(UUID clan) { return Boolean.TRUE.equals(clans.get(clan)); }
    public synchronized void setRecruiting(UUID clan, boolean enabled) { clans.put(clan, enabled); }
    public synchronized Set<UUID> recruitingClans() {
        Set<UUID> ids = new HashSet<>();
        clans.forEach((id, enabled) -> { if (enabled) ids.add(id); });
        return ids;
    }
    public synchronized void block(UUID clan, UUID player, long expiry) { blocks.put(clan + "/" + player, expiry); }
    public synchronized boolean isBlocked(UUID clan, UUID player, long now) {
        return blocks.getOrDefault(clan + "/" + player, 0L) > now;
    }
    public synchronized void remove(UUID clan) {
        clans.remove(clan);
        blocks.keySet().removeIf(key -> key.startsWith(clan + "/"));
    }
    public void save() throws IOException {
        synchronized (diskLock) {
            Properties data = new Properties();
            synchronized (this) {
                clans.forEach((id, enabled) -> data.setProperty("clan." + id, enabled.toString()));
                blocks.forEach((key, expiry) -> data.setProperty("block." + key, expiry.toString()));
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "recruitment-", ".tmp");
            try {
                try (OutputStream out = Files.newOutputStream(temp)) { data.store(out, "Parties clan recruitment - local realm"); }
                try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        }
    }
}
