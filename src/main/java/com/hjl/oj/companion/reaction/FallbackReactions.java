package com.hjl.oj.companion.reaction;

import com.hjl.oj.companion.assistant.ReactionAnswer;
import com.hjl.oj.companion.context.Verdict;
import com.hjl.oj.model.enums.CompanionMoodEnum;
import com.hjl.oj.model.enums.HintTrackEnum;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 兜底话术：没配 Key 或模型调用失败时的降级反应，每种结果一组随机挑选。
 * 只表达情绪、说明结果是什么意思，不给任何解题信息，所以不会越级。
 */
public final class FallbackReactions {

    private record Line(CompanionMoodEnum mood, int intensity, String message) {
    }

    private static final Map<Verdict, List<Line>> POOL = Map.of(
            Verdict.ACCEPTED, List.of(
                    new Line(CompanionMoodEnum.EXCITED, 3, "过了！这一下干净利落。"),
                    new Line(CompanionMoodEnum.PROUD, 2, "漂亮，通过了。"),
                    new Line(CompanionMoodEnum.SURPRISED, 2, "通过了！可以啊。")),
            Verdict.WRONG_ANSWER, List.of(
                    new Line(CompanionMoodEnum.THOUGHTFUL, 1, "差一点，有个用例没对上，诊断里能看到是哪一组。"),
                    new Line(CompanionMoodEnum.ENCOURAGING, 2, "答案没对上，先别急，拿失败的那组输入手算一遍试试。"),
                    new Line(CompanionMoodEnum.SYMPATHETIC, 1, "又没对上……喝口水，我们再看一遍。")),
            Verdict.TIME_LIMIT_EXCEEDED, List.of(
                    new Line(CompanionMoodEnum.NERVOUS, 2, "跑超时了，结果也许是对的，只是慢了点。"),
                    new Line(CompanionMoodEnum.THOUGHTFUL, 2, "超时了，看看题目的数据规模？")),
            Verdict.MEMORY_LIMIT_EXCEEDED, List.of(
                    new Line(CompanionMoodEnum.NERVOUS, 2, "内存超了，程序用的空间比限制多了一些。"),
                    new Line(CompanionMoodEnum.THOUGHTFUL, 1, "内存超限了，我们看看空间都花在哪了。")),
            Verdict.COMPILE_ERROR, List.of(
                    new Line(CompanionMoodEnum.CONFUSED, 1, "编译没过，诊断里标出了出错的位置。"),
                    new Line(CompanionMoodEnum.PLAYFUL, 1, "编译器有意见了，按它说的位置瞅一眼。")),
            Verdict.RUNTIME_ERROR, List.of(
                    new Line(CompanionMoodEnum.NERVOUS, 2, "运行时出错了，诊断里有报错信息，先看看。"),
                    new Line(CompanionMoodEnum.THOUGHTFUL, 1, "程序跑到一半出错了，从报错信息查起。")),
            Verdict.SYSTEM_ERROR, List.of(
                    new Line(CompanionMoodEnum.SYMPATHETIC, 1, "判题这边出了点状况，不是你的问题，稍后再交一次试试。")));

    private static final List<Line> DEFAULT_LINES = List.of(
            new Line(CompanionMoodEnum.THOUGHTFUL, 1, "结果出来了，我们一起看看。"));

    private FallbackReactions() {
    }

    /**
     * @param recentLines 最近说过的话，尽量避开
     */
    public static ReactionAnswer pick(Verdict verdict, HintTrackEnum track, List<String> recentLines,
                                      RandomGenerator random) {
        List<Line> candidates = POOL.getOrDefault(verdict, DEFAULT_LINES);
        List<Line> fresh = candidates.stream().filter(line -> !recentLines.contains(line.message())).toList();
        List<Line> pool = fresh.isEmpty() ? candidates : fresh;
        Line line = pool.get(random.nextInt(pool.size()));
        ReactionAnswer answer = new ReactionAnswer();
        answer.setMood(line.mood());
        answer.setIntensity(line.intensity());
        answer.setTrack(track);
        answer.setMessage(line.message());
        return answer;
    }
}
