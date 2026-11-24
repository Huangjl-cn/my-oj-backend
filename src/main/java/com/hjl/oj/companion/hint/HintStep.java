package com.hjl.oj.companion.hint;

/**
 * 提示阶梯上的一级。
 *
 * @param level       等级 0-5
 * @param title       给用户看的名字，如"点出关键"
 * @param description 给用户看的一句说明，如"告诉你用什么算法和复杂度"
 * @param rule        给模型看的规则：这一级最多能透露什么
 */
public record HintStep(int level, String title, String description, String rule) {
}
