# 题库页前端对接说明

题库页顶部的进度和标签分类用到两个接口，题目列表另外支持按难度筛选，返回格式与其他接口一致（`{code, data, message}`）。

## 标签列表

```http
GET /api/question/tags
```

不需要登录。返回题库里用到的标签和带这个标签的题目数，难度标签（easy / medium / hard，或 简单 / 中等 / 困难）不在其中，用得多的在前：

```json
[{ "tag": "字符串", "count": 2 }, { "tag": "数学", "count": 1 }]
```

## 我的做题进度

```http
GET /api/question/progress/my
```

需要登录。

| 字段 | 说明 |
|---|---|
| `total` | 各难度的题目总数 `{all, easy, medium, hard}`；没有难度标签的题只计入 `all` |
| `solved` | 各难度已通过的题目数，结构同上 |
| `statuses` | 做过的题目的状态，key 为题目 id：`passed` 已通过 / `failed` 没通过 / `judging` 判题中；没做过的题不在其中 |

通过过一次就算已通过，之后再提交错误也不影响。题库列表直接用 `statuses` 显示每题的状态，不用逐题请求提交记录。

## 按难度、是否通过筛选题目

`POST /api/question/list/page/vo` 的请求体多了 `difficulty`：`easy` / `medium` / `hard`（也认 `简单` / `中等` / `困难`）。题目标签里的难度可能写成英文也可能写成中文，两种都会匹配；不认识的值不筛选。

请求体里 `unsolvedOnly: true` 只返回当前用户还没通过的题，需要登录（没登录返回未登录错误）。

## 我的做题日历

```http
GET /api/question/progress/calendar?days=365
```

需要登录。`days` 默认 365，范围 7～366，超出按边界算。

| 字段 | 说明 |
|---|---|
| `days` | 最近 `rangeDays` 天（含今天）里有提交的日子，按日期从早到晚：`{date: "2026-11-25", submitCount, acceptedCount}`；没提交的日子不在其中 |
| `rangeDays` | 实际统计的天数 |
| `totalSubmits` / `acceptedSubmits` | 全部的提交次数和其中通过的次数（不限时间） |

日期按数据库里的提交时间算。连续做题天数等由前端根据 `days` 计算。
