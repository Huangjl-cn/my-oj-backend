package com.hjl.oj.companion.hint;

import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;

/**
 * 用户在某道题上的提示状态。
 *
 * @param track         陪练判断的方向（只给模型用）
 * @param unlockedLevel 用户设的档位（会话里保存的值）
 * @param mode          用户的提示模式
 */
public record HintState(HintTrackEnum track, int unlockedLevel, HintModeEnum mode) {

    /**
     * 实际生效的档位：有问必答模式下视为第 5 级
     */
    public int effectiveLevel() {
        return HintLadder.effectiveLevel(unlockedLevel, mode);
    }

    public EditPermission editPermission() {
        return HintLadder.editPermission(effectiveLevel());
    }

    public HintState withLevel(int level) {
        return new HintState(track, HintLadder.clamp(level), mode);
    }

    /**
     * 写进用户消息的「当前提示状态」
     */
    public String describe() {
        int level = effectiveLevel();
        HintStep step = HintLadder.step(level);
        StringBuilder text = new StringBuilder("【当前提示状态】\n");
        text.append("- 方向：").append(track.getText()).append("（").append(track.name()).append("）\n");
        if (mode == HintModeEnum.DIRECT) {
            text.append("- 用户开启了「有问必答」，按第 5 级处理，不必循序渐进\n");
        } else {
            text.append("- 档位：第 ").append(level).append(" 级「").append(step.title()).append("」\n");
        }
        text.append("- 本级最多透露：").append(step.rule(track)).append("\n");
        text.append("- 改代码权限：").append(editPermission().getDescription()).append("\n");
        return text.toString();
    }
}
