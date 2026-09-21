package com.alessiodp.parties.bukkit.recruitment;

import com.alessiodp.parties.common.parties.objects.PartyImpl;
import java.util.*;

/** Immutable menu data: no live Party or Bukkit object escapes into a menu snapshot. */
public final class RecruitmentEntry {
    public final UUID id;
    public final String name, description;
    public final Set<UUID> members;
    public RecruitmentEntry(PartyImpl party) {
        synchronized (party) {
            id = party.getId(); name = party.getName(); description = party.getDescription();
            members = Collections.unmodifiableSet(new HashSet<>(party.getMembers()));
        }
    }
}
