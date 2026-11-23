# 初始代码模板前端联调说明

后端新增了按题目、语言保存初始代码模板的能力。模板只用于进入题目时初始化编辑器，用户提交时仍提交编辑器中的最终代码，提交接口不新增字段。

## 语言值

当前支持：`java`、`cpp`、`go`、`python`、`javascript`。语言值必须直接使用这些字符串。

前端不要再单独维护语言选项，通过以下接口获取当前后端支持的语言及运行环境展示名称：

```http
GET /api/question/supported-languages
```

成功响应的 `data`：

```json
[
  {"text": "Java 25", "value": "java"},
  {"text": "C++ 17 (GCC 15)", "value": "cpp"},
  {"text": "Go 1.25", "value": "go"},
  {"text": "Python 3.14", "value": "python"},
  {"text": "JavaScript (Node.js 24)", "value": "javascript"}
]
```

选择器显示 `text`，创建模板、查询模板和提交代码时传递 `value`。语言列表由后端枚举统一维护，前端不应根据 `text` 推导提交值。

## 读取模板

题目详情页根据当前编辑器语言读取一份模板：

```http
GET /api/question/starter-code?questionId=题目ID&language=java
```

成功响应的 `data`：

```json
{
  "language": "java",
  "starterCode": "import java.util.*;\n..."
}
```

修改题目页面回填全部语言模板：

```http
GET /api/question/starter-code/list?questionId=题目ID
```

返回 `data` 数组，每项包含 `language` 和 `starterCode`。前端不要假定数组顺序，应按 `language` 建立映射。
题目不存在、单语言模板不存在或全量模板不完整时，接口返回 `code = 40400`，前端应提示模板配置异常，不要自行拼接其他语言模板。

## 创建和修改

创建题目请求增加可选字段：

```json
{
  "starterCodeList": [
    {"language": "java", "starterCode": "..."},
    {"language": "cpp", "starterCode": "..."},
    {"language": "go", "starterCode": "..."},
    {"language": "python", "starterCode": "..."},
    {"language": "javascript", "starterCode": "..."}
  ]
}
```

创建时省略 `starterCodeList`，后端会为全部语言生成默认模板。修改题目时，字段为 `null` 或未传表示不修改模板；一旦传入，必须包含全部五种语言。某项代码为空、空字符串或只包含空白字符时，后端会替换为该语言默认模板。

题目与模板在同一个后端事务中保存。前端只需根据接口最终响应提示成功或失败，不需要自行拆分保存请求。
修改请求可以只携带 `id` 和完整的 `starterCodeList`，用于仅修改模板而不变更题目其他字段。

## 前端行为

- 进入题目详情时加载当前语言模板；切换语言时加载对应模板，并可在前端缓存。
- 不要把模板内容拼接到提交请求之外的字段中。
- 编辑器应保留模板中的换行和缩进，并按纯文本处理。
- 题目删除后模板接口会返回题目不存在，不要继续使用缓存模板作为可编辑题目内容。
