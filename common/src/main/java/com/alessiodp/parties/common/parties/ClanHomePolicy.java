package com.alessiodp.parties.common.parties;

import com.alessiodp.parties.api.interfaces.PartyHome;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import java.util.*;

/** Computes allowances from existing membership; no timer, cache, or storage migration. */
public final class ClanHomePolicy {
    private ClanHomePolicy() { }
    public static int limit(PartyImpl party) {
        synchronized (party) {
            int max = Math.max(1, ConfigParties.ADDITIONAL_HOME_MAX_HOMES);
            if (!ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED) return max;
            int second = Math.max(2, ConfigParties.ADDITIONAL_HOME_SIZE_SECOND);
            int third = Math.max(second + 1, ConfigParties.ADDITIONAL_HOME_SIZE_THIRD);
            int members = party.getMembers().size();
            return Math.min(max, members >= third ? 3 : members >= second ? 2 : 1);
        }
    }
    public static List<PartyHome> orderedHomes(PartyImpl party) {
        synchronized (party) {
            List<PartyHome> homes = new ArrayList<>(party.getHomes());
            homes.sort(Comparator.comparingInt((PartyHome h) -> "default".equalsIgnoreCase(h.getName()) ? 0 : 1)
                    .thenComparing(h -> Objects.toString(h.getName(), ""), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(h -> Objects.toString(h.getName(), ""))
                    .thenComparing(PartyHome::toString));
            return homes;
        }
    }
    public static boolean canUse(PartyImpl party, PartyHome home) {
        if (party == null || home == null) return false;
        synchronized (party) {
            List<PartyHome> homes = orderedHomes(party);
            int available = ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED ? limit(party) : homes.size();
            for (int i=0; i<Math.min(available, homes.size()); i++)
                if (sameDestination(homes.get(i), home)) return true;
            return false;
        }
    }
    private static boolean sameDestination(PartyHome a, PartyHome b) {
        return Objects.equals(a.getName(), b.getName()) && Objects.equals(a.getWorld(), b.getWorld())
                && Double.compare(a.getX(), b.getX()) == 0 && Double.compare(a.getY(), b.getY()) == 0
                && Double.compare(a.getZ(), b.getZ()) == 0 && Float.compare(a.getYaw(), b.getYaw()) == 0
                && Float.compare(a.getPitch(), b.getPitch()) == 0
                && Objects.toString(a.getServer(), "").equals(Objects.toString(b.getServer(), ""));
    }
    public static boolean validName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_-]{1,32}");
    }
    public static boolean canSet(PartyImpl party, String name) {
        synchronized (party) {
            for (PartyHome home : party.getHomes()) {
                if (name != null && name.equalsIgnoreCase(home.getName()))
                    return !ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED || canUse(party, home);
            }
            return party.getHomes().size() < limit(party);
        }
    }
    public static String allowance(PartyImpl party) {
        synchronized (party) {
            int allowed = limit(party);
            String result = "&eClan homes: " + party.getHomes().size() + " saved, " + allowed + " unlocked.";
            int maximum = Math.min(3, Math.max(1, ConfigParties.ADDITIONAL_HOME_MAX_HOMES));
            if (ConfigParties.ADDITIONAL_HOME_SIZE_ENABLED && allowed < maximum) {
                int second = Math.max(2, ConfigParties.ADDITIONAL_HOME_SIZE_SECOND);
                int threshold = allowed == 1 ? second : Math.max(second + 1, ConfigParties.ADDITIONAL_HOME_SIZE_THIRD);
                result += " Next home at " + threshold + " members.";
            }
            return result;
        }
    }
}
