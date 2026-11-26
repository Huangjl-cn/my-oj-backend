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
-- 只在 user 表为空（全新的库）时插入：每次上线部署脚本都会重新执行本文件，
-- 线上把 admin 改名或删掉后，不能因此又冒出一个默认密码的 admin
insert into user (userAccount, userPassword, userName, userRole)
select 'admin', '8c03c9839cb10c023d7ea362e4b42f69', 'admin', 'admin'
from dual
where not exists (select 1 from user);


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
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    index idx_user_question_id (userId, questionId, id),
    index idx_conversation_id (conversationId, id),
    index idx_submissionId (submissionId)
) comment 'AI 陪练消息' collate = utf8mb4_unicode_ci;
