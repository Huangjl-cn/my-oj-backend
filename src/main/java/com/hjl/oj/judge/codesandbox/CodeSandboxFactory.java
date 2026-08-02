package com.hjl.oj.judge.codesandbox;

import com.hjl.oj.judge.codesandbox.impl.ExampleCodeSandbox;
import com.hjl.oj.judge.codesandbox.impl.RemoteCodeSandbox;
import com.hjl.oj.judge.codesandbox.impl.ThirdPartyCodeSandbox;
import com.hjl.oj.utils.SpringContextUtils;

/**
 * 代码沙箱工厂（根据字符串参数创建指定的代码沙箱实例）
 */
public class CodeSandboxFactory {

    /**
     * 创建代码沙箱示例
     *
     * @param type 代码沙箱类型
     * @return 具体代码沙箱
     */
    public static CodeSandbox newInstance(String type) {
        switch (type) {
            case "remote":
                return SpringContextUtils.getBean(RemoteCodeSandbox.class);
            case "thirdParty":
                return new ThirdPartyCodeSandbox();
            case "example":
            default:
                return new ExampleCodeSandbox();
        }
    }
}
