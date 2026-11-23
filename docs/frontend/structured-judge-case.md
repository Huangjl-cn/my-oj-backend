# 结构化判题用例前端对接说明

题目用例是结构化数据：每道题声明输入参数和输出的类型，用例按类型填写值。前端负责编辑体验，后端负责类型校验、存储、执行参数编码和语义比较（例如 `[1, 2]` 与 `[1,2]` 视为相同）。

## 获取支持的值类型

```http
GET /api/question/supported-judge-types
```

成功响应的 `data` 为数组：

```json
[
  {
    "value": "INTEGER",
    "text": "整数",
    "category": "SCALAR",
    "dimensions": 0,
    "elementType": null
  },
  {
    "value": "INTEGER_ARRAY",
    "text": "整数数组",
    "category": "ARRAY",
    "dimensions": 1,
    "elementType": "INTEGER"
  }
]
```

前端必须使用 `value` 作为提交值，使用 `text` 展示。不要在前端维护一份独立的类型选项。首批类型为 `INTEGER`、`LONG`、`DOUBLE`、`BOOLEAN`、`STRING`、`INTEGER_ARRAY`、`INTEGER_MATRIX`、`STRING_ARRAY`。

`LONG` 为避免浏览器整数精度丢失，建议以十进制字符串提交，例如 `"9223372036854775807"`；后端同时接受 Long 范围内的 JSON 整数。`DOUBLE` 使用 JSON 数字，判题采用 `1e-6` 的绝对误差。

## 新建和编辑题目

`POST /api/question/add`、`POST /api/question/edit` 和 `POST /api/question/update` 的 `judgeCase` 是以下对象：

```json
{
  "judgeCase": {
    "inputDefinitions": [
      {"name": "nums", "type": "INTEGER_ARRAY"},
      {"name": "target", "type": "INTEGER"}
    ],
    "outputDefinition": {"type": "INTEGER_ARRAY"},
    "cases": [
      {
        "inputs": [[2, 7, 11, 15], 9],
        "expectedOutput": [0, 1]
      },
      {
        "inputs": [[3, 2, 4], 6],
        "expectedOutput": [1, 2]
      }
    ]
  }
}
```

每道题只定义一次输入参数和输出类型，所有用例共用该定义。`inputs` 的顺序必须与 `inputDefinitions` 一致。

## 页面要求

1. 新建、编辑页面加载后请求支持类型接口。
2. 增加输入参数定义编辑器：参数名称、参数类型、增删和排序。
3. 增加一个输出类型选择器。
4. 根据类型渲染用例控件：标量输入框、一维数组编辑器或二维矩阵编辑器。
5. 数组长度、矩阵行列数只用于生成控件，提交时仅发送最终数组值。
6. 修改参数定义后，清空或重新校验不再匹配的用例值。
7. 编辑回填使用结构化对象，不再把 `input`、`output` 当作自由文本。
8. 各语言初始代码模板仍按现有接口维护；模板需要完成参数解析并将答案输出为合法 JSON。

本人或管理员编辑回填继续调用：

```http
GET /api/question/get?id=题目ID
```

该接口的 `data` 是 `QuestionManageVO`，其中 `tags`、`judgeConfig` 和 `judgeCase` 均为结构化值，不再要求前端手动解析数据库中的 JSON 字符串。权限仍为题目创建者或管理员。

后端会再次校验参数数量、名称、类型和值。未知类型、类型不匹配、空用例或不规则二维数组会返回参数错误。

## 展示与安全

公开题目详情继续使用现有 `QuestionVO`，不得返回隐藏的 `cases` 和 `expectedOutput`。本人或管理员的编辑详情才能回填完整判题用例。
