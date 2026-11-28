-- 一次性脚本：已有的库给 companion_message 加 noteEdits 列（鸭鸭的笔记提议）。
-- 新库直接执行 create_table.sql 即可，不需要这个脚本；线上执行过后删除本文件。
-- 不能重复执行：列已存在时会报 Duplicate column name。
use oj_db;

alter table companion_message
    add column noteEdits mediumtext null comment '笔记的新建、修改提议（json）' after codeEdits;
