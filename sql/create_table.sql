-- 创建库
create database if not exists oj_db;

-- 切换库
use oj_db;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    unionId      varchar(256)                           null comment '微信开放平台id',
    mpOpenId     varchar(256)                           null comment '公众号openId',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_unionId (unionId)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 添加默认admin用户，密码为12345678（仅用于开发和首次部署，上线后必须立即修改）
-- admin 不存在时才插入，脚本可重复执行（userAccount 没有唯一索引，on duplicate key 不会生效）
insert into user (userAccount, userPassword, userName, userRole)
select 'admin', '8c03c9839cb10c023d7ea362e4b42f69', 'admin', 'admin'
from dual
where not exists (select 1 from user where userAccount = 'admin');


-- 题目表
create table if not exists question
(
    id          bigint auto_increment comment 'id' primary key,
    userId      bigint                             not null comment '创建用户 id',
    title       varchar(512)                       null comment '标题',
    content     text                               null comment '内容',
    tags        varchar(1024)                      null comment '标签列表（json 数组）',
    answer      text                               null comment '题目答案',
    submitNum   int      default 0                 not null comment '题目提交数',
    acceptedNum int      default 0                 not null comment '题目通过数',
    judgeConfig text                               null comment '判题配置（json 数组）',
    judgeCase   text                               null comment '判题用例（json 对象）',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId)
) comment '题目' collate = utf8mb4_unicode_ci;

-- 题目多语言初始代码模板表
create table if not exists question_starter_code
(
    id          bigint auto_increment comment 'id' primary key,
    questionId  bigint                             not null comment '题目 id',
    language    varchar(32)                        not null comment '编程语言',
    starterCode text                               not null comment '编辑器初始代码模板',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_question_language (questionId, language)
) comment '题目多语言初始代码模板' collate = utf8mb4_unicode_ci;

-- 题目提交表
create table if not exists question_submit
(
    id         bigint auto_increment comment 'id' primary key,
    questionId bigint                             not null comment '题目 id',
    userId     bigint                             not null comment '创建用户 id',
    language   varchar(128)                       not null comment '编程语言',
    code       text                               null comment '用户提交代码',
    judgeInfo  text                               null comment '判题信息（json 对象）',
    status     int                                not null default 0 comment '判题状态（0-待判题，1-判题中，2-成功，3-失败）',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_postId (questionId),
    index idx_userId (userId),
    index idx_user_question_time (userId, questionId, createTime),
    index idx_status_id (status, id)
) comment '题目提交';

-- 用户 AI 设置（每人一行；API Key 加密存储，删除 Key 时清空 Key 相关列）
create table if not exists user_ai_setting
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                                 not null comment '用户 id',
    provider     varchar(32)  default 'deepseek'        not null comment 'AI 厂商',
    model        varchar(64)                            null comment '所选模型，为空时用默认模型',
    encryptedKey varchar(1024)                          null comment '加密后的 API Key（AES-GCM）',
    keyHint      varchar(16)                            null comment 'API Key 后 4 位，用于页面显示',
    hintMode     varchar(16)  default 'guided'          not null comment '提示模式：guided 循序渐进 / direct 有问必答',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_userId (userId)
) comment '用户 AI 设置' collate = utf8mb4_unicode_ci;

-- AI 陪练会话（每人每题一行：提示档位、陪练判断的方向、当前对话）
create table if not exists companion_session
(
    id                   bigint auto_increment comment 'id' primary key,
    userId               bigint                                not null comment '用户 id',
    questionId           bigint                                not null comment '题目 id',
    hintLevel            tinyint     default 0                 not null comment '提示档位 0-5（5 为看过答案）',
    hintTrack            varchar(16) default 'idea'            not null comment '陪练判断的方向：idea 找思路 / bug 找 bug（只给模型用）',
    activeConversationId bigint                                null comment '当前对话 id',
    createTime           datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime           datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_user_question (userId, questionId)
) comment 'AI 陪练会话' collate = utf8mb4_unicode_ci;

-- AI 陪练对话（同一道题可以有多段对话，各有各的模型记忆和上下文占用）
create table if not exists companion_conversation
(
    id               bigint auto_increment comment 'id' primary key,
    userId           bigint                             not null comment '用户 id',
    questionId       bigint                             not null comment '题目 id',
    title            varchar(64)                        null comment '标题：第一次提问的开头，还没提问时为空',
    memory           mediumtext                         null comment '模型记忆（LangChain4j 消息序列化后的 JSON）',
    contextTokens    int      default 0                 not null comment '最近一次调用的上下文 token 数',
    memoryUpdateTime datetime                           null comment '模型记忆最后更新时间',
    createTime       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_user_question (userId, questionId)
) comment 'AI 陪练对话' collate = utf8mb4_unicode_ci;

-- AI 陪练消息（给前端展示的对话历史，按雪花 id 排序分页）
create table if not exists companion_message
(
    id             bigint auto_increment comment 'id' primary key,
    userId         bigint                             not null comment '用户 id',
    questionId     bigint                             not null comment '题目 id',
    conversationId bigint                             not null comment '所属对话 id',
    role           varchar(16)                        not null comment 'user 用户 / assistant 陪练',
    type           varchar(16)                        not null comment 'chat 聊天 / hint 提示 / reaction 提交后反应 / solution 参考解法 / compaction 记忆整理',
    hintLevel      tinyint                            null comment '提示档位（提示、解法）',
    hintTrack      varchar(16)                        null comment '陪练判断的方向（提示、反应）',
    mood           varchar(16)                        null comment '陪练心情（提交后反应）',
    moodIntensity  tinyint                            null comment '心情强度 1-3',
    submissionId   bigint                             null comment '触发反应的提交 id',
    content        mediumtext                         not null comment '消息内容',
    quote          text                               null comment '用户提问时引用的代码（json）',
    codeEdits      mediumtext                         null comment '代码修改建议（json）',
    noteEdits      mediumtext                         null comment '笔记的新建、修改提议（json）',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    index idx_user_question_id (userId, questionId, id),
    index idx_conversation_id (conversationId, id),
    index idx_submissionId (submissionId)
) comment 'AI 陪练消息' collate = utf8mb4_unicode_ci;

-- 鸭鸭的轨迹：每一轮（一次聊天、提示、看答案、提交后反应、整理记忆）一行。
-- 系统提示词和工具清单和这段对话上一轮一样时不重复存（为空，沿用上一轮的）；删对话时一起删
create table if not exists companion_trace_turn
(
    id             bigint auto_increment comment 'id' primary key,
    userId         bigint                                not null comment '用户 id',
    questionId     bigint                                not null comment '题目 id',
    conversationId bigint                                not null comment '所属对话 id',
    type           varchar(16)                           not null comment 'chat 聊天 / hint 提示 / solution 看答案 / reaction 提交后反应 / compaction 整理记忆',
    status         varchar(16)                           not null comment 'running 进行中 / ok 完成 / error 出错 / fallback 用了兜底话术',
    userText       text                                  null comment '这一轮用户做了什么（聊天时是用户说的话）',
    userPrompt     mediumtext                            null comment '发给模型的用户消息（场景、档位、代码、用户说的话）',
    systemPrompt   mediumtext                            null comment '系统提示词；和上一轮相同时为空',
    tools          mediumtext                            null comment '提供给模型的工具（json）；和上一轮相同时为空',
    messageId      bigint                                null comment '这一轮产生的陪练消息 id',
    error          varchar(512)                          null comment '出错原因',
    startTime      datetime(3) default CURRENT_TIMESTAMP(3) not null comment '开始时间',
    endTime        datetime(3)                           null comment '结束时间',
    index idx_conversation_id (conversationId, id)
) comment '鸭鸭的轨迹：每一轮' collate = utf8mb4_unicode_ci;

-- 鸭鸭的轨迹：每一轮里的每一步，一次模型调用或一次工具调用
create table if not exists companion_trace_step
(
    id             bigint auto_increment comment 'id' primary key,
    turnId         bigint                                not null comment '所属的轮',
    conversationId bigint                                not null comment '所属对话 id',
    kind           varchar(16)                           not null comment 'model 模型调用 / tool 工具调用',
    name           varchar(64)                           null comment '模型名或工具名',
    input          mediumtext                            null comment '工具参数（json）',
    output         mediumtext                            null comment '模型回答的正文 / 工具结果',
    thinking       mediumtext                            null comment '模型的思考过程',
    toolCalls      mediumtext                            null comment '模型发起的工具调用（json）',
    inputTokens    int                                   null comment '输入 token',
    cachedTokens   int                                   null comment '输入里命中缓存的 token',
    outputTokens   int                                   null comment '输出 token',
    finishReason   varchar(32)                           null comment '结束原因',
    error          varchar(512)                          null comment '出错原因',
    startTime      datetime(3)                           not null comment '开始时间',
    durationMs     int                                   not null comment '耗时（毫秒）',
    index idx_turn_id (turnId, id),
    index idx_conversation_id (conversationId, id)
) comment '鸭鸭的轨迹：每一步' collate = utf8mb4_unicode_ci;

-- 题单：自建的、官方的（管理员建，所有人可见），以及每人一个「我的收藏」
-- 收藏也是题单，第一次用到时自动建；uniqueKey = 'favorite:<userId>' 保证每人只有一个
create table if not exists question_list
(
    id          bigint auto_increment comment 'id' primary key,
    userId      bigint                             not null comment '创建人 id',
    title       varchar(64)                        not null comment '标题',
    description varchar(512)                       null comment '简介',
    type        varchar(16)                        not null comment 'favorite 收藏 / custom 自建 / official 官方',
    uniqueKey   varchar(64)                        null comment '收藏题单为 favorite:<userId>，其他为空',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    unique key uk_unique_key (uniqueKey),
    index idx_user_type (userId, type),
    index idx_type (type)
) comment '题单' collate = utf8mb4_unicode_ci;

-- 题单里的题；同一道题在一个题单里只出现一次，删除题单里的题直接删行
create table if not exists question_list_item
(
    id         bigint auto_increment comment 'id' primary key,
    listId     bigint                             not null comment '题单 id',
    questionId bigint                             not null comment '题目 id',
    sortOrder  int      default 0                 not null comment '在题单里的顺序，小的在前',
    createTime datetime default CURRENT_TIMESTAMP not null comment '加入时间',
    unique key uk_list_question (listId, questionId),
    index idx_question (questionId)
) comment '题单里的题' collate = utf8mb4_unicode_ci;

-- 笔记：可以挂在某道题上（questionId），也可以是不挂题的自由笔记；一道题可以有多篇
-- version 每次保存加一，保存时带上读到的版本号，对不上说明在别处改过了
create table if not exists note
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '作者 id',
    questionId bigint                             null comment '关联的题目 id，自由笔记为空',
    title      varchar(128)                       not null comment '标题',
    content    mediumtext                         null comment '正文（Markdown）',
    version    int      default 0                 not null comment '版本号，每次保存加一',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_user_update (userId, updateTime),
    index idx_user_question (userId, questionId)
) comment '笔记' collate = utf8mb4_unicode_ci;
