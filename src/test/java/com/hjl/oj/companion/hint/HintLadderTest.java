package com.hjl.oj.companion.hint;

import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintLadderTest {

    @Test
    void sixNamedStepsWithRulesForBothTracks() {
        List<HintStep> steps = HintLadder.steps();
        assertEquals(6, steps.size());
        for (int level = 0; level < steps.size(); level++) {
            HintStep step = steps.get(level);
            assertEquals(level, step.level());
            assertFalse(step.title().isBlank() || step.description().isBlank());
            for (HintTrackEnum track : HintTrackEnum.values()) {
                assertFalse(step.rule(track).isBlank());
            }
        }
        assertTrue(HintLadder.step(3).rule(HintTrackEnum.BUG).contains("反例"));
        assertTrue(HintLadder.step(3).rule(HintTrackEnum.IDEA).contains("算法"));
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
        assertEquals("看答案", HintLadder.step(99).title());
    }

    @Test
    void promptDescribesBothTracksUnderEveryLevel() {
        String prompt = HintLadder.describeForPrompt();

        assertTrue(prompt.contains("找思路（IDEA）") && prompt.contains("找 bug（BUG）"));
        assertTrue(prompt.contains("「只陪伴」") && prompt.contains("「给步骤」"));
        assertTrue(prompt.contains("改代码的权限"));
    }

    @Test
    void stateDescriptionUsesTheRuleOfTheJudgedTrack() {
        String idea = new HintState(HintTrackEnum.IDEA, 2, HintModeEnum.GUIDED).describe();
        String bug = new HintState(HintTrackEnum.BUG, 2, HintModeEnum.GUIDED).describe();

        assertTrue(idea.contains("「点关键」") && idea.contains("关键观察"));
        assertTrue(bug.contains("「点关键」") && bug.contains("大致区域"));
        assertTrue(idea.contains("只能修语法"));
    }
}
