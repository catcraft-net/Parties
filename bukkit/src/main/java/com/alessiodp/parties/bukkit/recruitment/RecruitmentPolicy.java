package com.alessiodp.parties.bukkit.recruitment;

/** Pure browser rules; playtime is intentionally absent from membership admission. */
public final class RecruitmentPolicy {
    private RecruitmentPolicy() { }
    public static long remainingMinutes(int ticks, int requiredMinutes) {
        long remaining = Math.max(0L, (long) requiredMinutes) * 1200L - Math.max(0, ticks);
        return remaining <= 0 ? 0 : (remaining + 1199L) / 1200L;
    }
    public static int pageSize(int configured) { return Math.max(1, Math.min(28, configured)); }
    public static int page(int requested, int count, int size) {
        return Math.max(0, Math.min(requested, Math.max(0, count - 1) / pageSize(size)));
    }
}
