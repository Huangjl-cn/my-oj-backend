package com.hjl.oj.companion.hint;

import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;

import java.util.List;
import java.util.Map;

/**
 * 分级提示的规则：两条线（找思路 / 找 bug）各 6 级，以及每级的代码修改权限。
 *
 * <p>等级表示"最多透露多少"，两条线共用同一个等级。给用户展示 title 和 description，
 * 给模型的是 rule；用户看到的永远是名字，不是数字。
 */
public final class HintLadder {

    public static final int MIN_LEVEL = 0;

    /**
     * 分级提示接口能到的最高等级；第 5 级只能通过"看答案"进入
     */
    public static final int MAX_HINT_LEVEL = 4;

    public static final int SOLUTION_LEVEL = 5;

    private static final Map<HintTrackEnum, List<HintStep>> STEPS = Map.of(
            HintTrackEnum.IDEA, List.of(
                    new HintStep(0, "自己想", "只陪伴：鼓励、复述题意，用反问帮你自己想",
                            "不给任何解题信息：不提思路、观察、数据结构、算法和复杂度。可以复述题意、澄清输入输出，用提问引导用户思考，解释报错和判题结果的字面含义"),
                    new HintStep(1, "指个方向", "提醒你该注意什么，比如数据规模意味着什么",
                            "只指出思考方向或需要注意的约束（比如数据规模对复杂度的要求、题目里容易忽略的条件），不说出关键观察、数据结构或算法名"),
                    new HintStep(2, "点出关键", "点出解题的关键观察，但不说用什么算法",
                            "可以点出解题的关键观察或性质，不说出算法、数据结构的名字，不给步骤"),
                    new HintStep(3, "说出解法", "告诉你用什么算法或数据结构，以及复杂度",
                            "可以说出算法或数据结构的名称、整体思路和时间空间复杂度，不给具体步骤和代码"),
                    new HintStep(4, "给出步骤", "给出完整步骤或伪代码，不给能直接运行的代码",
                            "可以给出完整步骤或伪代码以及边界处理要点，不给任何编程语言写成的可运行代码"),
                    new HintStep(5, "看答案", "参考代码和讲解",
                            "不限制：可以给出完整参考代码并讲解")),
            HintTrackEnum.BUG, List.of(
                    new HintStep(0, "自己查", "只陪伴：解释报错和判题结果是什么意思",
                            "不指出 bug 的位置和类型，只解释报错、判题结果的字面含义，可以建议通用的排查方法（比如打印中间值、手算小样例）"),
                    new HintStep(1, "问题类型", "告诉你是哪一类问题，比如边界、溢出、超时",
                            "只说出问题属于哪一类（边界条件、整数溢出、下标越界、初始化、复杂度过高等），不说位置，不说怎么改"),
                    new HintStep(2, "大概位置", "告诉你问题大概出在哪段代码",
                            "可以指出问题所在的大致区域（哪个函数、哪个循环、哪个分支），不精确到行，不说怎么改"),
                    new HintStep(3, "给个反例", "给一个能让你的代码出错的输入",
                            "可以给出一个会让当前代码出错的具体输入（反例）和期望输出，不精确指出出错的行"),
                    new HintStep(4, "精确到行", "指出出错的行和原因",
                            "可以精确指出出错的行和原因，并说明修改方向"),
                    new HintStep(5, "帮我改好", "直接帮你改好",
                            "不限制：可以直接给出修改后的代码")));

    private HintLadder() {
    }

    public static List<HintStep> steps(HintTrackEnum track) {
        return STEPS.get(track);
    }

    public static HintStep step(HintTrackEnum track, int level) {
        return STEPS.get(track).get(clamp(level));
    }

    /**
     * 有问必答模式下相当于一直处在最高等级
     */
    public static int effectiveLevel(int sessionLevel, HintModeEnum hintMode) {
        return hintMode == HintModeEnum.DIRECT ? SOLUTION_LEVEL : clamp(sessionLevel);
    }

    public static EditPermission editPermission(int level) {
        int clamped = clamp(level);
        if (clamped >= SOLUTION_LEVEL) {
            return EditPermission.UNRESTRICTED;
        }
        if (clamped == 4) {
            return EditPermission.SMALL_CHANGE;
        }
        if (clamped == 3) {
            return EditPermission.ANNOTATE;
        }
        return EditPermission.SYNTAX_ONLY;
    }

    public static int clamp(int level) {
        return Math.max(MIN_LEVEL, Math.min(SOLUTION_LEVEL, level));
    }

    /**
     * 完整阶梯的文字说明，放进系统提示词（对每个用户、每道题都相同，利于缓存）
     */
    public static String describeForPrompt() {
        StringBuilder text = new StringBuilder();
        for (HintTrackEnum track : HintTrackEnum.values()) {
            text.append("【").append(track.getText()).append("（").append(track.name()).append("）】\n");
            for (HintStep step : STEPS.get(track)) {
                text.append("- 第 ").append(step.level()).append(" 级「").append(step.title()).append("」：")
                        .append(step.rule()).append("\n");
            }
        }
        text.append("【改代码的权限】\n");
        for (int level = MIN_LEVEL; level <= SOLUTION_LEVEL; level++) {
            text.append("- 第 ").append(level).append(" 级：").append(editPermission(level).getDescription()).append("\n");
        }
        return text.toString();
    }
}
