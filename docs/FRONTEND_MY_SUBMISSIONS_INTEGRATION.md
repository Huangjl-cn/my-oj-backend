# 我的题目提交记录前端联调说明

题目详情页使用两个接口实现“查看已提交”：列表只返回摘要，用户点击某条记录后再读取包含代码的详情。两个接口都从登录会话确定用户身份，前端不要传递或缓存 `userId` 作为权限条件。

## 提交列表

```http
POST /api/question/question_submit/my/list/page
Content-Type: application/json

{
  "questionId": 2013621883695575042,
  "current": 1,
  "pageSize": 10
}
```

`questionId` 必填，`current` 必须大于 0，`pageSize` 范围为 1-20。后端固定按 `createTime` 倒序，并强制使用当前登录用户 ID。响应 `data.records` 的每项包含：

- `id`、`questionId`、`language`
- `status`、`judgeInfo`
- `createTime`、`updateTime`

列表摘要不返回 `code`。`status = 2` 只表示判题流程结束，最终结果必须使用 `judgeInfo.message`。

## 提交详情

```http
GET /api/question/question_submit/my/get?id=提交ID
```

成功时 `data` 为 `QuestionSubmitVO`，包含提交代码、语言、状态、时间以及对象形式的 `judgeInfo`。提交不属于当前用户时统一返回 `40400`，不要区分记录不存在与无权访问。

## 前端交互

1. 进入题目详情后请求第一页提交摘要。
2. 分页或刷新时继续调用列表接口，不需要先请求当前用户 ID。
3. 点击某条摘要后，用该条 `id` 请求详情，再展示代码和完整判题诊断。
4. `status` 为 `0` 或 `1` 的记录可沿用现有轮询逻辑；达到 `2` 或 `3` 后停止轮询并刷新列表。

原 `POST /api/question/question_submit/list/page` 继续作为公开的脱敏提交列表使用，其他用户的提交代码不会返回。原提交详情接口保留用于现有兼容流程，新页面查看自己的提交时优先使用严格当前用户范围的 `my/get`。
