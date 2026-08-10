# 结构化用例代码沙箱对接说明

## 背景

OJ 后端将题目用例升级为结构化数据，并负责将每个逻辑输入值编码为独立的进程参数。代码沙箱继续保持通用的编译、执行和资源隔离能力，不解析 OJ 的整数、数组、矩阵等业务类型，也不接收预期答案或比较输出。

现有 `inputList: List<String>` 无法同时表达“多条用例”和“每条用例的多个独立参数”，还可能在空格、空字符串和引号处丢失参数边界。本次直接替换为执行用例数组，不保留旧字段兼容逻辑。

## 请求协议

`POST /executeCode` 请求调整为：

```json
{
  "code": "用户提交的完整代码",
  "language": "java",
  "cases": [
    {
      "args": ["[2,7,11,15]", "9"]
    },
    {
      "args": ["[3,2,4]", "6"]
    }
  ]
}
```

对应模型：

```java
public class ExecuteCodeRequest {
    private String code;
    private String language;
    private List<ExecuteCaseRequest> cases;
}

public class ExecuteCaseRequest {
    private List<String> args;
}
```

每个 `args` 元素都是一个完整且独立的用户程序参数。沙箱不得再次按空格、逗号或引号拆分。

## 执行要求

1. 一次请求只编译一次代码。
2. 按 `cases` 顺序逐条启动程序并传入对应 `args`。
3. 使用进程参数列表传递参数，例如 `ProcessBuilder(List<String>)`；不得拼接 shell 命令。
4. 必须原样保留包含空格的字符串、空字符串、JSON 数组和 JSON 矩阵。
5. 每条用例收集一份原始标准输出，并按执行顺序写入 `outputList`。
6. 编译错误、运行错误、超时、内存限制和系统错误继续沿用现有状态与 `judgeInfo` 约定。

参数在各运行时中的可见位置由语言本身决定，例如 Java 的首个用户参数是 `args[0]`，C++、Python 和 Go 通常从索引 `1` 开始，Node.js 从索引 `2` 开始。沙箱只追加参数，不处理这些下标差异。

## 响应协议

响应结构保持不变：

```json
{
  "outputList": ["[0,1]", "[1,2]"],
  "message": "执行成功",
  "status": 1,
  "judgeInfo": {
    "message": null,
    "time": 12,
    "memory": 20480
  }
}
```

`outputList` 必须是用户程序的原始标准输出。合法 JSON、答案格式和语义比较由代码模板及 OJ 后端负责，沙箱不得修改空格、换行或数组格式。

## 必测场景

```json
{"args": ["hello world", ""]}
{"args": ["[1,2,3]", "4"]}
{"args": ["[[1,2],[3,4]]"]}
{"args": ["& calc", "$(command)"]}
```

前三组用于验证参数边界，最后一组必须被当作普通参数，不能触发任何 shell 解释。
