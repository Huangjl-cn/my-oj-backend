package com.hjl.oj.judge.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次程序运行使用的独立参数列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCaseRequest {

    private List<String> args;
}
