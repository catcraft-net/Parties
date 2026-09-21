package com.alessiodp.parties.bukkit.recruitment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RecruitmentRegistryTest {
    @TempDir Path directory;
    @Test void optInAndKickBlockSurviveRestartButNewClansStayPrivate() throws Exception {
        Path file = directory.resolve("recruitment.properties");
        UUID clan = UUID.randomUUID(), player = UUID.randomUUID();
        RecruitmentRegistry registry = RecruitmentRegistry.load(file);
        assertFalse(registry.isRecruiting(clan));
        assertFalse(registry.isManaged(clan));
        registry.setRecruiting(clan, true);
        registry.block(clan, player, 10000);
        registry.save();
        RecruitmentRegistry restored = RecruitmentRegistry.load(file);
        assertTrue(restored.isRecruiting(clan));
        assertTrue(restored.isBlocked(clan, player, 9999));
        assertFalse(restored.isBlocked(clan, player, 10000));
        restored.setRecruiting(clan, false);
        restored.save();
        restored = RecruitmentRegistry.load(file);
        assertTrue(restored.isManaged(clan));
        assertFalse(restored.isRecruiting(clan));
    }
    @Test void malformedDataFailsClosedAndPreservesOriginal() throws Exception {
        Path file = directory.resolve("recruitment.properties");
        Files.write(file, "clan.bad-uuid=true\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> RecruitmentRegistry.load(file));
        assertTrue(Files.readString(file).contains("bad-uuid"));
    }
    @Test void deletingClanRemovesItsRecruitmentAndBlocksOnly() throws Exception {
        RecruitmentRegistry registry = RecruitmentRegistry.load(directory.resolve("r.properties"));
        UUID first=UUID.randomUUID(), second=UUID.randomUUID(), player=UUID.randomUUID();
        registry.setRecruiting(first,true); registry.setRecruiting(second,true);
        registry.block(first,player,100); registry.block(second,player,100);
        registry.remove(first);
        assertFalse(registry.isManaged(first));
        assertFalse(registry.isBlocked(first,player,1));
        assertTrue(registry.isRecruiting(second));
        assertTrue(registry.isBlocked(second,player,1));
    }
}
