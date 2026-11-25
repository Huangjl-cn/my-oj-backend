package com.hjl.oj.companion.hint;

import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;

import java.util.List;

/**
 * 提示档位：6 档（0-4 由用户调，5 是看答案），以及每档的代码修改权限。
 *
 * <p>档位表示"最多透露多少"。用户只看到每档的名字和说明；没思路（找思路）和代码有 bug（找 bug）
 * 两个方向的规则只给模型，方向由模型根据代码和提交情况判断。
 */
public final class HintLadder {

    public static final int MIN_LEVEL = 0;

    /**
     * 用户能直接调到的最高档位；第 5 档只能通过"看答案"进入
     */
    public static final int MAX_HINT_LEVEL = 4;

    public static final int SOLUTION_LEVEL = 5;

    private static final List<HintStep> STEPS = List.of(
            new HintStep(0, "只陪伴", "不透露解法，只帮你理清题意、解释报错，用提问引导你自己想",
                    "不给任何解题信息：不提思路、观察、数据结构、算法和复杂度。可以复述题意、澄清输入输出，用提问引导用户思考，解释报错和判题结果的字面含义",
                    "不指出 bug 的位置和类型，只解释报错、判题结果的字面含义，可以建议通用的排查方法（比如打印中间值、手算小样例）"),
            new HintStep(1, "指方向", "提醒你该往哪想、问题属于哪一类（比如数据规模、整数溢出），不说怎么做",
                    "只指出思考方向或需要注意的约束（比如数据规模对复杂度的要求、题目里容易忽略的条件），不说出关键观察、数据结构或算法名",
                    "只说出问题属于哪一类（边界条件、整数溢出、下标越界、初始化、复杂度过高等），不说位置，不说怎么改"),
            new HintStep(2, "点关键", "点出解题的关键观察，或指出问题大概在哪段代码",
                    "可以点出解题的关键观察或性质，不说出算法、数据结构的名字，不给步骤",
                    "可以指出问题所在的大致区域（哪个函数、哪个循环、哪个分支），不精确到行，不说怎么改"),
            new HintStep(3, "说方法", "说出用什么算法、数据结构和复杂度，或给一个让你的代码出错的输入",
                    "可以说出算法或数据结构的名称、整体思路和时间空间复杂度，不给具体步骤和代码",
                    "可以给出一个会让当前代码出错的具体输入（反例）和期望输出，不精确指出出错的行"),
            new HintStep(4, "给步骤", "给出完整步骤或伪代码，或精确指出出错的行和原因",
                    "可以给出完整步骤或伪代码以及边界处理要点，不给任何编程语言写成的可运行代码",
                    "可以精确指出出错的行和原因，并说明修改方向"),
            new HintStep(5, "看答案", "完整的参考代码和讲解，或直接帮你改好",
                    "不限制：可以给出完整参考代码并讲解",
                    "不限制：可以直接给出修改后的代码"));

    private HintLadder() {
    }

    public static List<HintStep> steps() {
        return STEPS;
    }

    public static HintStep step(int level) {
        return STEPS.get(clamp(level));
    }

    /**
     * 有问必答模式下相当于一直处在最高档位
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
     * 完整档位的文字说明，放进系统提示词（对每个用户、每道题都相同，利于缓存）
     */
    public static String describeForPrompt() {
        StringBuilder text = new StringBuilder("【提示档位】（用户只看到档位名；方向由你判断，按方向取对应的规则）\n");
        for (HintStep step : STEPS) {
            text.append("- 第 ").append(step.level()).append(" 级「").append(step.title()).append("」\n");
            for (HintTrackEnum track : HintTrackEnum.values()) {
                text.append("  - ").append(track.getText()).append("（").append(track.name()).append("）：")
                        .append(step.rule(track)).append("\n");
            }
        }
        text.append("【改代码的权限】\n");
        for (int level = MIN_LEVEL; level <= SOLUTION_LEVEL; level++) {
            text.append("- 第 ").append(level).append(" 级：").append(editPermission(level).getDescription()).append("\n");
        }
        return text.toString();
    }
}
