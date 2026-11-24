package com.hjl.oj.companion.hint;

import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintLadderTest {

    @Test
    void bothTracksHaveSixNamedSteps() {
        for (HintTrackEnum track : HintTrackEnum.values()) {
            List<HintStep> steps = HintLadder.steps(track);
            assertEquals(6, steps.size());
            for (int level = 0; level < steps.size(); level++) {
                HintStep step = steps.get(level);
                assertEquals(level, step.level());
                assertTrue(!step.title().isBlank() && !step.description().isBlank() && !step.rule().isBlank());
            }
        }
    }

    @Test
    void editPermissionWidensWithLevel() {
        assertEquals(EditPermission.SYNTAX_ONLY, HintLadder.editPermission(0));
        assertEquals(EditPermission.SYNTAX_ONLY, HintLadder.editPermission(2));
        assertEquals(EditPermission.ANNOTATE, HintLadder.editPermission(3));
        assertEquals(EditPermission.SMALL_CHANGE, HintLadder.editPermission(4));
        assertEquals(EditPermission.UNRESTRICTED, HintLadder.editPermission(5));
    }

    @Test
    void directModeActsAsHighestLevel() {
        HintState state = new HintState(HintTrackEnum.BUG, 1, HintModeEnum.DIRECT);

        assertEquals(5, state.effectiveLevel());
        assertEquals(EditPermission.UNRESTRICTED, state.editPermission());
        assertEquals(1, new HintState(HintTrackEnum.BUG, 1, HintModeEnum.GUIDED).effectiveLevel());
    }

    @Test
    void levelsAreClamped() {
        assertEquals(0, HintLadder.clamp(-3));
        assertEquals(5, HintLadder.clamp(9));
        assertEquals("看答案", HintLadder.step(HintTrackEnum.IDEA, 99).title());
    }

    @Test
    void promptDescribesEveryLevelOfBothTracks() {
        String prompt = HintLadder.describeForPrompt();

        assertTrue(prompt.contains("找思路") && prompt.contains("找 bug"));
        assertTrue(prompt.contains("「给个反例」") && prompt.contains("「说出解法」"));
        assertTrue(prompt.contains("改代码的权限"));
    }

    @Test
    void stateDescriptionShowsNameNotOnlyNumber() {
        String text = new HintState(HintTrackEnum.IDEA, 2, HintModeEnum.GUIDED).describe();

        assertTrue(text.contains("「点出关键」"));
        assertTrue(text.contains("只能修语法"));
    }
}
