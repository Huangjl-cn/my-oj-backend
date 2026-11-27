# AI 陪练前端对接说明

做题页上的 AI 陪练：提交后会说话，可以聊天、选中代码提问、按档位要提示、看答案，还能提出代码修改（用户预览后再应用）。同一道题可以开多段对话。模型调用的费用走用户自己的 DeepSeek API Key。

所有接口都要求登录，返回格式与其他接口一致（`{code, data, message}`，`code === 0` 为成功），只有 `chat`、`solution` 两个流式接口返回 SSE。

## 一、进入题目

```http
GET /api/companion/session?questionId=题目ID
```

`data` 主要字段：

| 字段 | 说明 |
|---|---|
| `conversationId` | 当前对话。`chat` / `hint` / `solution` / `compact` 都带上它 |
| `hintLevel` / `effectiveLevel` | 提示档位 0-5（5 表示看过答案）/ 实际生效档位（有问必答模式下为 5） |
| `hintMode` | `guided` 按档位 / `direct` 有问必答 |
| `hintSteps` | 6 档的 `{level, title, description}`，下标即档位；界面展示 `title`，悬停展示 `description` |
| `canCallModel` | 为 `false` 时没配 Key，引导用户去设置（提交后的反应仍可用，会返回兜底话术） |
| `mockMode` | 开发环境的模拟模式，回复是固定话术 |
| `personaName` | 陪练的名字 |
| `contextUsage` | 当前对话的 `{usedTokens, limitTokens, ratio, compactRatio, estimated}` |
| `messages` / `hasMoreMessages` | 当前对话最近的消息（按时间正序）；往前翻用 `GET /api/companion/messages?conversationId=&beforeId=最早一条的id` |

消息 `type`：`chat` 聊天、`hint` 提示、`reaction` 提交后反应、`solution` 参考解法、`compaction` 记忆整理（`content` 是整理出的笔记，可以折叠展示）。用户选中代码提问时，消息带 `quote: {startLine, endLine, code}`。

## 二、对话

每道题可以有多段对话，各有各的记忆和上下文占用；提示档位按题目算，不随对话变化。

```http
GET  /api/companion/conversations?questionId=      # 对话列表：{id, title, messageCount, active, createTime, updateTime}，最近活跃的在前
POST /api/companion/conversation         { "questionId": "..." }       # 新对话；当前对话还没有消息时沿用它
PUT  /api/companion/conversation/active  { "conversationId": "..." }   # 切回之前的对话
```

- 后两个接口都返回切换后的会话（字段同「进入题目」），直接替换前端状态。
- `title` 是第一次提问的开头，还没提问时为空，可以显示成创建时间。
- 切走时，没有任何消息的当前对话会被删掉；在某段对话里聊天、要提示也会把它设为当前对话。
- 提交后的反应记在当前对话里。

## 三、提示档位

档位表示陪练最多透露多少，由用户自己调；陪练判断用户是没思路还是代码有 bug，用户看不到这个方向。

- 档位选择器展示 0-4 档的 `title`，悬停显示 `description`；第 5 档「看答案」单独一个入口，点击时二次确认，走 `solution`。
- 调档：`PUT /api/companion/level`，`{questionId, level: 0-4}`，只改档位，不生成内容。
- 要一条提示：

```http
POST /api/companion/hint
{ "conversationId": "...", "snapshot": { ...见下文 } }
```

按当前档位给一条提示，不改变档位。返回 `{messageId, hintLevel, stepTitle, message, codeEdits, contextUsage}`。

## 四、提交后的反应

判题轮询拿到终态（不再是判题中）后调用：

```http
POST /api/companion/react
{ "submissionId": "..." }
```

返回 `{messageId, submissionId, mood, moodIntensity, conversationId, message, fallback}`。同一次提交重复调用返回同一条结果，可以放心重试；`conversationId` 不是前端正在展示的对话时（比如另一个标签页切过对话），不要插进当前列表。

建议：判题结果一到，先立刻按结果播一个小动作（零延迟），等接口返回后再换成 `mood` 对应的动画并弹出气泡。`mood` 的取值：

| mood | 心情 | mood | 心情 |
|---|---|---|---|
| `excited` | 兴奋 | `encouraging` | 打气 |
| `proud` | 骄傲 | `playful` | 调皮 |
| `surprised` | 惊喜 | `nervous` | 紧张 |
| `thoughtful` | 若有所思 | `confused` | 困惑 |
| `sympathetic` | 心疼 | | |

`moodIntensity` 为 1-3，可以用来调动画幅度。

## 五、聊天与看答案（SSE）

```http
POST /api/companion/chat
Accept: text/event-stream, application/json
{ "conversationId": "...", "message": "这里为什么超时？", "quote": { "startLine": 8, "endLine": 10, "code": "..." }, "snapshot": { ... } }

POST /api/companion/solution
Accept: text/event-stream, application/json
{ "conversationId": "...", "snapshot": { ... } }
```

`quote` 是用户选中代码后提问时引用的那一段（可省略），`code` 最多 4000 个字符。

**用 `fetch` 读取流**（`EventSource` 只能发 GET）。开始推流前就出错（没配 Key、上一条还在处理、参数错误等）时，响应是普通 JSON：先看响应头 `Content-Type`，是 `application/json` 就按普通错误处理。

事件（每个事件的 `data` 都是 JSON）：

| event | data | 说明 |
|---|---|---|
| `thinking` | `{text}` | 思考过程片段，只有看答案时有；可以显示"思考中"动画或折叠展示 |
| `delta` | `{text}` | 回答正文片段，依次拼接 |
| `edits` | 代码修改建议，见下文 | 回答里提出了修改时才有，在 `done` 之前 |
| `notes` | 笔记提议（`NoteProposal` 数组），见「笔记」一节 | 只有聊天时，提议了新建或修改笔记才有，在 `done` 之前 |
| `done` | `{messageId, hintLevel, contextUsage, compacted}` | 结束；`compacted` 为 true 表示这次回答前自动整理过记忆 |
| `error` | `{code, message}` | 出错，之后流结束 |

同一个用户同一时间只能有一个进行中的 AI 请求，返回 `42900` 时提示"上一条还在处理"。

## 六、代码快照与修改建议

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

### 笔记

聊天时鸭鸭可以查看用户的笔记（本题的、按关键词搜全部的、读某一篇），也可以提议新建或修改。提议不会直接写进笔记：随回答一起推送（`notes` 事件），也保存在消息的 `noteEdits` 里，用户预览后点「应用」才写入。

```json
{
  "kind": "update",
  "noteId": "...",
  "baseVersion": 3,
  "title": "应用后的标题",
  "previousTitle": "修改前的标题",
  "questionId": "...",
  "questionTitle": "两数之和",
  "changes": [{ "kind": "replace", "oldText": "...", "newText": "...", "reason": "..." }],
  "content": "提议时算好的全文",
  "reason": "一句话说明",
  "applied": false
}
```

- `kind` 为 `create` 时是新建：没有 `noteId`（应用后填上）、`baseVersion` 和 `changes`，`questionId` 为空表示自由笔记。
- `changes[].kind`：`replace` 替换一段原文、`append` 追加到末尾、`rewrite` 整篇改写。

预览和应用（下标 `index` 是这条提议在 `noteEdits` 里的位置）：

```http
GET  /api/companion/note/preview?messageId=...&index=0
POST /api/companion/note/apply   { "messageId": "...", "index": 0, "overwrite": false }
```

预览按笔记现在的内容算出应用后的样子，返回 `{status, proposal, title, content, currentContent, skipped, noteId}`。笔记在提议之后又被改过时（版本号对不上），后端按 `changes` 在新内容上重新应用：

| status | 含义 | 应用 |
|---|---|---|
| `ready` | 可以直接应用 | 直接应用 |
| `rebased` | 笔记在这之后改过，修改都重新对上了 | 直接应用 |
| `partial` | 有 `skipped` 处原文对不上了 | 跳过对不上的，应用其余的 |
| `overwrite` | 笔记在这之后改过，而提议要整篇改写 | 要用户确认，带 `overwrite: true` |
| `stale` | 修改都对不上了 | 不能应用（40900），让鸭鸭重新改 |
| `missing` | 笔记已经删了 | 不能应用（40400） |
| `applied` | 已经应用过 | 重复应用直接返回那篇笔记，不会再建一篇 |

应用返回 `{note, proposal, skipped}`：`note` 是写好的笔记（`NoteVO`），`proposal` 已标记 `applied`。还没到「看答案」档位时，后端会拒绝把解法代码写进提议（照抄用户自己的代码不算）。

## 七、记忆与上下文占用

- 上下文占用达到 `compactRatio`（默认 80%）时，后端在下一次回答前自动整理记忆。
- 记忆和上下文占用按对话算，新对话从零开始。
- 手动整理：`POST /api/companion/compact`，`{conversationId}`，返回 `{compacted, messageId, note, contextUsage}`；对话太短时 `compacted` 为 false。

## 八、AI 设置

```http
GET    /api/ai/setting
PUT    /api/ai/setting      { "apiKey": "sk-...", "model": "deepseek-flash", "hintMode": "guided" }
DELETE /api/ai/setting/key
```

- 三个字段都可以只传一部分；带 `apiKey` 时后端先校验再保存，失败返回具体原因。
- 返回 `{provider, keyConfigured, keyHint, model, models, hintMode, mockMode}`，`keyHint` 形如 `****a1b2`，任何接口都不会返回完整 Key。
- `hintMode`：`guided` 按档位 / `direct` 有问必答（不限档位，可以直接改代码）。

## 九、错误码

| code | 含义 | 建议处理 |
|---|---|---|
| 40020 | 没配 API Key | 打开 AI 设置 |
| 40021 | Key 无效 | 提示重新填写 |
| 40022 | DeepSeek 余额不足 | 提示去 DeepSeek 充值 |
| 42900 | 上一条还在处理 | 稍候再发 |
| 42901 | DeepSeek 限流 | 稍后再试 |
| 50020 | AI 服务调用失败（含超时） | 稍后再试 |
