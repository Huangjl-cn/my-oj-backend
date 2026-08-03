# 前端判题结果适配任务

请检查当前 OJ 前端对题目提交、判题轮询和结果展示的实现，并根据下面的后端契约完成必要调整。先阅读现有代码和类型定义；已经符合要求的部分不要重写，不做无关的 UI 或架构重构。

## 后端契约

- 提交接口仍为 `POST /api/question/question_submit/do`，返回提交 ID；本次没有修改前端可见的接口路径或字段名。
- 提交状态 `status` 表示异步任务状态：`0` 等待、`1` 判题中、`2` 判题流程完成、`3` 判题任务失败。
- `status = 2` 不等于答案通过。最终结果由 `judgeInfo.message` 表达，可能为：
  - `Accepted`
  - `内存溢出`、`超时`
  - 多行答案错误：`Wrong Answer\n用例: 1\n输入:\n...\n预期输出:\n...\n实际输出:\n...`
  - 编译错误、运行错误或系统错误的多行诊断/堆栈信息
- `judgeInfo.memory` 单位为 KB，`judgeInfo.time` 单位为 ms。
- 后端远程沙箱 HTTP 超时为 `60000 ms`；前端整体轮询等待不要按 30 秒截止，建议至少允许 75 秒。单次查询请求仍使用较短的请求超时。
- `GET /api/question/question_submit/get?id=...` 当前返回实体，`judgeInfo` 可能是 JSON 字符串；`POST /api/question/question_submit/list/page` 返回 `QuestionSubmitVO`，其中 `judgeInfo` 是对象。请在 API 适配层规范化，不要让页面组件重复解析。

## 状态边界

- 提交记录的 `status`（0/1/2/3）表示异步判题任务状态；沙箱内部的 `ExecuteCodeResponse.status`（1/2/3/4）不会直接返回给前端，不要混用两套状态码。
- 单个程序超过沙箱 8 秒硬上限时，后端会在 `judgeInfo.message` 中保留类似 `Runtime Error\nExecution timed out after 8000 ms` 的诊断。它表示沙箱执行熔断，不等同于题目判题结果中的 `超时`。
- 真正的题目超时由后端根据 `judgeInfo.time` 和题目限制判断，前端可能收到 `judgeInfo.message = "超时"`。未知消息按原始文本展示。

## 实现要求

1. 轮询只在 `status` 为 `0` 或 `1` 时继续，在 `2` 或 `3` 时停止，并在组件卸载或提交 ID 变化时清理定时器。
2. 不要根据 `status = 2` 显示“Accepted”。应根据规范化后的 `judgeInfo.message` 展示最终结果；无法识别的消息按执行诊断原样展示。
3. 多行消息必须保留换行并支持长单词/堆栈换行，可使用 `white-space: pre-wrap` 和 `overflow-wrap: anywhere`。
4. 消息按纯文本渲染，禁止 `v-html`、`dangerouslySetInnerHTML` 等未转义 HTML，避免提交代码或沙箱诊断造成 XSS。
5. 检查语言选择器是否支持后端现有值：`java`、`cpp`、`go`、`python`、`javascript`。不要自行增加其他语言。
6. 保持现有设计系统和交互方式，不修改后端生成的 API 文件，除非项目既有流程要求重新生成。
7. 轮询整体等待时间要覆盖后端 60 秒沙箱请求上限，但终态（提交 `status=2` 或 `status=3`）后立即停止；不要因为收到 Runtime Error 诊断就继续轮询。

## 验收标准

- 为 `status = 2 + Wrong Answer`、多行编译/运行错误、`status = 3`、Accepted 和轮询终止行为补充测试。
- 错误用例、预期输出、实际输出和堆栈能够完整、安全地显示。
- 页面不会把已完成但未通过的提交标记为 Accepted，也不会在终态继续轮询。
- 最后说明修改文件、兼容处理方式和运行过的测试命令。
