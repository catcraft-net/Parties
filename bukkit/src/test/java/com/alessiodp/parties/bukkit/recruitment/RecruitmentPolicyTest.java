package com.alessiodp.parties.bukkit.recruitment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecruitmentPolicyTest {
    @Test void browseGateUsesTicksWithExactMinuteBoundary() {
        assertEquals(1,RecruitmentPolicy.remainingMinutes(143999,120));
        assertEquals(0,RecruitmentPolicy.remainingMinutes(144000,120));
        assertEquals(0,RecruitmentPolicy.remainingMinutes(0,0));
        assertEquals(120,RecruitmentPolicy.remainingMinutes(-1,120));
        assertTrue(RecruitmentPolicy.remainingMinutes(0,Integer.MAX_VALUE)>0);
    }
    @Test void pagesClampAfterListingsDisappear() {
        assertEquals(0,RecruitmentPolicy.page(3,0,28));
        assertEquals(1,RecruitmentPolicy.page(3,29,28));
        assertEquals(0,RecruitmentPolicy.page(-1,29,28));
        assertEquals(28,RecruitmentPolicy.pageSize(50));
        assertEquals(1,RecruitmentPolicy.pageSize(0));
    }
}
