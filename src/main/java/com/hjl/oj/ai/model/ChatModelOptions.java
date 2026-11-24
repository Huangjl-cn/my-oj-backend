package com.hjl.oj.ai.model;

/**
 * 创建一次调用所用模型的选项。
 *
 * @param apiKey     用户自己的 API Key（模拟模式下可为空）
 * @param modelName  模型名
 * @param purpose    调用用途，用于日志和模拟回复，如 companion.chat
 * @param thinking   是否开启思考模式
 * @param jsonOutput 是否要求输出 JSON（DeepSeek 的 json_object 模式）
 */
public record ChatModelOptions(String apiKey, String modelName, String purpose, boolean thinking, boolean jsonOutput) {
}
