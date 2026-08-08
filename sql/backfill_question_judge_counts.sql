use oj_db;

-- 根据已有已完成判题记录回填题目提交数和通过数。
-- 系统错误在旧版本中可能也曾写入成功状态，历史数据只能按现有 judgeInfo 尽量回填。
update question q
left join (
    select questionId,
           count(*) as submitNum,
           sum(case
                   when json_valid(judgeInfo)
                       and json_unquote(json_extract(judgeInfo, '$.message')) = 'Accepted'
                       then 1
                   else 0
               end) as acceptedNum
    from question_submit
    where isDelete = 0
      and status = 2
    group by questionId
) stats on stats.questionId = q.id
set q.submitNum = coalesce(stats.submitNum, 0),
    q.acceptedNum = coalesce(stats.acceptedNum, 0)
where q.isDelete = 0;
