# 用户管理与个人中心联调说明

## 一、管理员用户管理

以下接口都需要管理员登录：

### 获取角色选项

```http
GET /api/user/supported-roles
```

返回角色的展示文本和值：

```json
[
  { "text": "用户", "value": "user" },
  { "text": "管理员", "value": "admin" },
  { "text": "被封号", "value": "ban" }
]
```

前端用 `value` 提交，后端会再次校验角色是否合法，不能依赖前端选项限制权限。

### 分页查询用户

```http
POST /api/user/list/page
```

请求示例：

```json
{
  "current": 1,
  "pageSize": 10,
  "userAccount": "nelson",
  "userName": "管理员",
  "userRole": "admin"
}
```

返回 `Page<AdminUserVO>`。记录包含 `id`、`userAccount`、`userName`、`userAvatar`、`userProfile`、`userRole`、`createTime`、`updateTime`，不会包含密码、`unionId`、`mpOpenId` 或删除标记。

### 获取管理员用户详情

```http
GET /api/user/get?id=用户ID
```

返回单个 `AdminUserVO`。管理员编辑页面可以直接使用列表记录，也可以在点击编辑时再次请求详情。

### 新增、修改、删除

```http
POST /api/user/add
POST /api/user/update
POST /api/user/delete
```

管理员新增和修改用户时都可以提交 `userName`、`userAvatar`、`userProfile` 和 `userRole`。管理员接口不提供密码编辑字段。新增用户时后端会设置默认密码 `12345678`，用户登录后应立即在个人中心修改密码。不能删除当前登录管理员，也不能把当前登录管理员降级或封禁。

`ban` 用户登录或读取当前登录信息时会返回无权限错误（`40101`），前端应清理登录状态并回到登录页。

## 二、个人中心

### 获取当前用户

```http
GET /api/user/get/login
```

返回 `LoginUserVO`，包含 `userAccount`、昵称、头像标识、简介、角色和时间字段。密码不会返回。

### 修改个人资料

```http
POST /api/user/update/my
```

请求体只提交 `userName`、`userAvatar`、`userProfile`。`userAvatar` 保存前端约定的本地头像标识，例如 `avatar_01`，不是图片 URL。

### 修改密码

```http
POST /api/user/update/password
```

```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码",
  "checkPassword": "确认新密码"
}
```

旧密码错误、新密码少于 8 位或两次新密码不一致时返回 `40000`。管理员页面不要调用此接口替用户修改密码。

## 三、接口区别

`/api/user/list/page/vo` 和 `/api/user/get/vo` 仍是普通脱敏用户信息接口，不用于管理员用户管理；管理员页面使用上文的 `/list/page` 和 `/get`。
