-- 判题队列轮询与排队计数查询的支撑索引（按 status 取最老记录 / 按 status+id 计数）；仅需执行一次。
use oj_db;

alter table question_submit
    add index idx_status_id (status, id);
