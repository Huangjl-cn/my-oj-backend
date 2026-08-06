-- 优化“当前用户在某题的提交记录”查询；仅需执行一次。
use oj_db;

alter table question_submit
    add index idx_user_question_time (userId, questionId, createTime);
