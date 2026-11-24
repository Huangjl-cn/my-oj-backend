# AI 陪练前端对接说明

做题页上的 AI 陪练：提交后会说话，可以聊天、分级要提示、看答案，还能提出代码修改（用户预览后再应用）。模型调用的费用走用户自己的 DeepSeek API Key。

所有接口都要求登录，返回格式与其他接口一致（`{code, data, message}`，`code === 0` 为成功），只有 `chat`、`solution` 两个流式接口返回 SSE。

## 一、进入题目

```http
GET /api/companion/session?questionId=题目ID
```

`data` 主要字段：

| 字段 | 说明 |
|---|---|
| `hintLevel` / `effectiveLevel` | 已解锁等级 / 实际生效等级（有问必答模式下为 5）。**只用于程序判断，不要把数字展示给用户** |
| `hintTrack` | 当前方向：`idea` 找思路 / `bug` 找 bug |
| `hintSteps` | 两条线各自的 6 个步骤 `{level, title, description}`，key 为 `idea` / `bug`。界面上展示 `title`、`description` |
| `canCallModel` | 为 `false` 时没配 Key，引导用户去设置（提交后的反应仍可用，会返回兜底话术） |
| `mockMode` | 开发环境的模拟模式，回复是固定话术 |
| `personaName` | 陪练的名字 |
| `contextUsage` | `{usedTokens, limitTokens, ratio, compactRatio, estimated}`，显示成「3.2% · 32K / 1M」 |
| `messages` / `hasMoreMessages` | 最近的消息（按时间正序）；往前翻用 `GET /api/companion/messages?questionId=&beforeId=最早一条的id` |

消息 `type`：`chat` 聊天、`hint` 提示、`reaction` 提交后反应、`solution` 参考解法、`compaction` 记忆整理（`content` 是整理出的笔记，可以折叠展示）。

## 二、提示进度怎么展示

用户看到的是名字，不是"第几级"：

- 用一排步骤展示当前线的 `hintSteps`，已解锁的高亮，鼠标悬停显示 `description`。
- "再给点提示"按钮上写明下一步会得到什么，例如：`再给点提示：说出解法`（取下一级的 `title`）。
- "看答案"单独一个按钮，点击时二次确认。
- "从头自己做"调用 `PUT /api/companion/level`，`{questionId, level: 0}`；也可以直接选 1-4 中的任意一级。
- 陪练判断方向变化时（`hintTrack` 从 `idea` 变成 `bug`），切换展示另一条线的步骤名。

```http
POST /api/companion/hint
{ "questionId": "...", "targetLevel": 3, "snapshot": { ...见下文 } }
```

`targetLevel` 可省略，默认在当前等级上加一（最多到 4）。返回 `{messageId, hintLevel, hintTrack, stepTitle, message, codeEdits, contextUsage}`。

## 三、提交后的反应

判题轮询拿到终态（不再是判题中）后调用：

```http
POST /api/companion/react
{ "submissionId": "..." }
```

返回 `{messageId, submissionId, mood, moodIntensity, hintTrack, message, fallback}`。同一次提交重复调用返回同一条结果，可以放心重试。

建议：判题结果一到，先立刻按结果播一个小动作（零延迟），等接口返回后再换成 `mood` 对应的动画并弹出气泡。`mood` 的取值：

| mood | 心情 | mood | 心情 |
|---|---|---|---|
| `excited` | 兴奋 | `encouraging` | 打气 |
| `proud` | 骄傲 | `playful` | 调皮 |
| `surprised` | 惊喜 | `nervous` | 紧张 |
| `thoughtful` | 若有所思 | `confused` | 困惑 |
| `sympathetic` | 心疼 | | |

`moodIntensity` 为 1-3，可以用来调动画幅度。

## 四、聊天与看答案（SSE）

```http
POST /api/companion/chat
Accept: text/event-stream, application/json
{ "questionId": "...", "message": "这里为什么超时？", "snapshot": { ... } }

POST /api/companion/solution
Accept: text/event-stream, application/json
{ "questionId": "...", "snapshot": { ... } }
```

**用 `fetch` 读取流**（`EventSource` 只能发 GET）。开始推流前就出错（没配 Key、上一条还在处理、参数错误等）时，响应是普通 JSON：先看响应头 `Content-Type`，是 `application/json` 就按普通错误处理。

事件（每个事件的 `data` 都是 JSON）：

| event | data | 说明 |
|---|---|---|
| `thinking` | `{text}` | 思考过程片段，只有看答案时有；可以显示"思考中"动画或折叠展示 |
| `delta` | `{text}` | 回答正文片段，依次拼接 |
| `edits` | 代码修改建议，见下文 | 回答里提出了修改时才有，在 `done` 之前 |
| `done` | `{messageId, hintLevel, hintTrack, contextUsage, compacted}` | 结束；`compacted` 为 true 表示这次回答前自动整理过记忆 |
| `error` | `{code, message}` | 出错，之后流结束 |

同一个用户同一时间只能有一个进行中的 AI 请求，返回 `42900` 时提示"上一条还在处理"。

## 五、代码快照与修改建议

每次 `chat` / `hint` / `solution` 都带上编辑器当前状态：

```json
{
  "code": "编辑器里的完整代码（原样）",
  "language": "java",
  "cursorLine": 12,
  "selectionStartLine": 10,
  "selectionEndLine": 14
}
```

行号从 1 开始，没有选中时 `selectionStartLine` / `selectionEndLine` 不传。

修改建议（`edits` 事件、`hint` 返回的 `codeEdits`、消息里的 `codeEdits`）：

```json
{
  "baseHash": "请求里那份 code 的 SHA-256（十六进制）",
  "edits": [
    { "kind": "replace", "oldText": "...", "newText": "...", "startLine": 8, "reason": "这里少了分号" }
  ],
  "resultCode": "依次应用全部修改后的完整代码（换行为 \\n）"
}
```

应用方式：

1. 用 Monaco 的对比编辑器展示 `当前代码` 与 `resultCode` 的差异，顶部放「应用 / 拒绝」，每处的 `reason` 可以作为说明展示。
2. 点「应用」前，对编辑器当前内容算 SHA-256 与 `baseHash` 比较：
   - 一致：用 `executeEdits` 把整段替换为 `resultCode`（进入撤销栈，Ctrl+Z 可以撤销）。
   - 不一致：说明用户在模型思考期间改过代码。逐处在当前代码里查找 `oldText`（唯一时替换为 `newText`），找不到的提示用户重新问一次。`kind` 为 `rewrite` 时是整体替换，直接提示用户确认是否覆盖。
3. 应用与否不需要通知后端：下一次请求带上的代码就反映了结果。

## 六、记忆与上下文占用

- 上下文占用达到 `compactRatio`（默认 80%）时，后端在下一次回答前自动整理记忆。
- 手动整理：`POST /api/companion/compact`，`{questionId}`，返回 `{compacted, messageId, note, contextUsage}`；对话太短时 `compacted` 为 false。

## 七、AI 设置

```http
GET    /api/ai/setting
PUT    /api/ai/setting      { "apiKey": "sk-...", "model": "deepseek-flash", "hintMode": "guided" }
DELETE /api/ai/setting/key
```

- 三个字段都可以只传一部分；带 `apiKey` 时后端先校验再保存，失败返回具体原因。
- 返回 `{provider, keyConfigured, keyHint, model, models, hintMode, mockMode}`，`keyHint` 形如 `****a1b2`，任何接口都不会返回完整 Key。
- `hintMode`：`guided` 循序渐进 / `direct` 有问必答（不分级，可以直接改代码）。

## 八、错误码

| code | 含义 | 建议处理 |
|---|---|---|
| 40020 | 没配 API Key | 打开 AI 设置 |
| 40021 | Key 无效 | 提示重新填写 |
| 40022 | DeepSeek 余额不足 | 提示去 DeepSeek 充值 |
| 42900 | 上一条还在处理 | 稍候再发 |
| 42901 | DeepSeek 限流 | 稍后再试 |
| 50020 | AI 服务调用失败（含超时） | 稍后再试 |
