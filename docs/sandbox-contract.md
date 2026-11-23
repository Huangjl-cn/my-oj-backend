# 代码沙箱接口契约

本文是后端调用 [my-oj-sandbox](https://github.com/Huangjl-cn/my-oj-sandbox) 的接口约定。沙箱只负责编译、执行和收集原始输出，不理解 OJ 的值类型，也不接收预期答案；判题（类型化比较、时间和内存判定）全部在后端完成。

改动接口时两个仓库要同时修改本文和沙箱的对应实现。

## 调用方式

| 项 | 值 |
|---|---|
| 地址 | `POST {codesandbox.url}/executeCode`，`codesandbox.url` 不带 `/executeCode` 后缀 |
| 请求体 | JSON，UTF-8 |
| 鉴权 | 四个签名请求头，见 [沙箱调用鉴权](./sandbox-auth/README.md) |
| 超时 | 后端 `codesandbox.timeout`（毫秒，同时用作连接和读取超时） |
| 探活 | `GET /health`，不需要鉴权 |

后端实现：`judge/codesandbox/impl/RemoteCodeSandbox`；沙箱实现：`controller/MainController` → `sandbox/dispatch/DockerCodeSandboxDispatcher`。

## 请求

```json
{
  "code": "用户提交的完整代码",
  "language": "java",
  "cases": [
    { "args": ["[2,7,11,15]", "9"] },
    { "args": ["[3,2,4]", "6"] }
  ]
}
```

- `language`：`java`、`cpp`、`go`、`python`、`javascript`。两边各有一份语言枚举，增减语言时要同时修改。
- `cases`：每个元素是一次程序运行，`args` 的每个元素是一个完整、独立的进程参数。后端用 `JudgeInputEncoder` 把结构化用例编码成 `args`（数组、矩阵用 JSON 文本）。

沙箱必须遵守：

1. 一次请求只编译一次，按 `cases` 顺序逐条运行。
2. 用进程参数列表传参，不拼接 shell 命令；包含空格的字符串、空字符串、JSON 数组和 `$(command)` 这类文本都要原样传给程序。
3. 参数只追加在程序参数末尾。各语言读取参数的下标由语言本身决定（Java 从 `args[0]` 开始，C++、Python、Go 从 1 开始，Node.js 从 2 开始），由后端生成的初始代码模板处理。

## 响应

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

| 字段 | 含义 |
|---|---|
| `status` | `1` 执行成功、`2` 编译错误、`3` 运行错误（含超时和输出超限）、`4` 系统错误 |
| `outputList` | 每条用例的原始标准输出，顺序与 `cases` 一致；沙箱不得修改空格、换行或格式 |
| `message` | 执行流程的简短说明，不用于判题 |
| `judgeInfo.message` | 失败时的诊断信息（编译错误、运行错误输出等，已清洗掉宿主机路径） |
| `judgeInfo.time` | 所有用例中的最大耗时，单位毫秒 |
| `judgeInfo.memory` | 所有用例中的最大内存，单位 KB |

请求参数不合法（如语言不支持、`cases` 为空）时，沙箱同样返回 HTTP 200 和 `status = 4`。

## 错误响应

鉴权失败时沙箱返回 HTTP 401：

```json
{"code":401,"message":"unauthorized: <原因>"}
```

已知问题：后端目前不检查 HTTP 状态码，会把 401、5xx 的响应体当成执行结果解析，这时的判题结果不可信；排查时看两端日志。

## 执行环境中的 JSON 库

初始代码模板用 JSON 解析参数、输出答案。Python、JavaScript、Go 使用标准库；Java 和 C++ 依赖沙箱镜像内置的库：

| 语言 | 库 | 位置 |
|---|---|---|
| Java | Gson | `/opt/libs/gson.jar`，编译和运行时都在 classpath 中 |
| C++ | nlohmann/json | `/usr/local/include/nlohmann/json.hpp`，`#include <nlohmann/json.hpp>` |

库的版本和镜像构建方式以沙箱仓库的 Dockerfile 为准。镜像在构建时安装依赖，运行用户代码时禁止联网。

## 资源限制

内存、CPU、编译和运行超时、输出大小等限制由沙箱统一设定，具体数值见沙箱仓库。后端的 `codesandbox.timeout` 必须大于沙箱处理一次请求的最长时间（编译超时加上用例数乘以单用例运行超时），否则沙箱还在执行时后端就已判为失败。
