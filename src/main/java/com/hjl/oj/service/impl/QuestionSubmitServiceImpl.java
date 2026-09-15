package com.hjl.oj.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.constant.CommonConstant;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.exception.ThrowUtils;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.mapper.QuestionSubmitMapper;
import com.hjl.oj.model.dto.questionsubmit.*;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.entity.QuestionSubmit;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.enums.JudgeResultEnum;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;
import com.hjl.oj.model.enums.QuestionSubmitStatusEnum;
import com.hjl.oj.model.vo.QuestionSubmitGroupVO;
import com.hjl.oj.model.vo.QuestionSubmitQueueStatusVO;
import com.hjl.oj.model.vo.QuestionSubmitRankVO;
import com.hjl.oj.model.vo.QuestionSubmitSummaryVO;
import com.hjl.oj.model.vo.QuestionSubmitVO;
import com.hjl.oj.service.QuestionService;
import com.hjl.oj.service.QuestionSubmitService;
import com.hjl.oj.service.UserService;
import com.hjl.oj.utils.SqlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuestionSubmitServiceImpl extends ServiceImpl<QuestionSubmitMapper, QuestionSubmit>
        implements QuestionSubmitService {

    /**
     * 归档排序字段：提交时间
     */
    private static final String SORT_FIELD_CREATE_TIME = "createTime";
    /**
     * 归档排序字段：判题耗时（judgeInfo.time，ms）
     */
    private static final String SORT_FIELD_JUDGE_TIME = "judgeTime";
    /**
     * 归档排序字段：判题内存（judgeInfo.memory，KB）
     */
    private static final String SORT_FIELD_JUDGE_MEMORY = "judgeMemory";
    /**
     * 聚合排序字段：最近提交时间
     */
    private static final String SORT_FIELD_LATEST_SUBMIT_TIME = "latestSubmitTime";
    /**
     * 聚合排序字段：最短判题耗时
     */
    private static final String SORT_FIELD_BEST_TIME = "bestTime";
    /**
     * 聚合排序字段：最小判题内存
     */
    private static final String SORT_FIELD_BEST_MEMORY = "bestMemory";
    /**
     * 归档列表允许的排序字段白名单（缺省按提交时间）
     */
    private static final Set<String> ARCHIVE_SORT_FIELDS =
            Set.of(SORT_FIELD_CREATE_TIME, SORT_FIELD_JUDGE_TIME, SORT_FIELD_JUDGE_MEMORY);
    /**
     * 按题目聚合允许的排序字段白名单（缺省按最近提交时间）
     */
    private static final Set<String> GROUP_SORT_FIELDS =
            Set.of(SORT_FIELD_LATEST_SUBMIT_TIME, SORT_FIELD_BEST_TIME, SORT_FIELD_BEST_MEMORY);
    @Resource
    private QuestionService questionService;
    @Resource
    private UserService userService;

    /**
     * SUM 聚合在无匹配行时返回 null，对外统一为 0
     */
    private static Long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    // region 提交归档页（全部提交 + 按题目聚合）

    /**
     * 提交题目
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser) {
        //检验编程语言是否合法
        String language = questionSubmitAddRequest.getLanguage();
        QuestionSubmitLanguageEnum languageEnum = QuestionSubmitLanguageEnum.getEnumByValue(language);
        if (languageEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言错误");
        }
        // 判断实体是否存在，根据类别获取实体
        Question question = questionService.getById(questionSubmitAddRequest.getQuestionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已提交题目
        long userId = loginUser.getId();
        // 每个用户串行提交题目
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setUserId(userId);
        questionSubmit.setQuestionId(questionSubmitAddRequest.getQuestionId());
        questionSubmit.setCode(questionSubmitAddRequest.getCode());
        questionSubmit.setLanguage(language);
        //初始化题目提交状态
        questionSubmit.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
        questionSubmit.setJudgeInfo("{}");
        boolean save = this.save(questionSubmit);
        if (!save) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据插入失败");
        }
        // 提交记录即队列元素：落库后由 JudgeTaskDispatcher 轮询拾起判题，削峰填谷
        return questionSubmit.getId();
    }

    @Override
    public boolean updateStatusIfCurrent(long questionSubmitId,
                                         QuestionSubmitStatusEnum currentStatus,
                                         QuestionSubmitStatusEnum targetStatus,
                                         String judgeInfo) {
        LambdaUpdateWrapper<QuestionSubmit> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionSubmit::getId, questionSubmitId)
                .eq(QuestionSubmit::getStatus, currentStatus.getValue())
                .eq(QuestionSubmit::getIsDelete, 0)
                .set(QuestionSubmit::getStatus, targetStatus.getValue());
        if (judgeInfo != null) {
            updateWrapper.set(QuestionSubmit::getJudgeInfo, judgeInfo);
        }
        return this.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionSubmit completeSubmissionAndUpdateStats(long questionSubmitId,
                                                           long questionId,
                                                           String judgeInfo,
                                                           boolean accepted) {
        boolean completed = updateStatusIfCurrent(
                questionSubmitId,
                QuestionSubmitStatusEnum.RUNNING,
                QuestionSubmitStatusEnum.SUCCEED,
                judgeInfo);
        if (!completed) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }
        boolean counted = questionService.incrementJudgeCount(questionId, accepted);
        if (!counted) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目统计更新错误");
        }
        return getById(questionSubmitId);
    }

    @Override
    public QuestionSubmit getNextWaitingSubmission() {
        // 雪花 id 单调递增，等价于提交先后顺序；仅取 id，避免读出大字段 code
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id")
                .eq("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .orderByAsc("id")
                .last("LIMIT 1");
        return this.getBaseMapper().selectOne(queryWrapper);
    }

    @Override
    public boolean resetStaleRunningSubmissions(Date staleThreshold) {
        // 判题最长耗时受 codesandbox.timeout 约束，超时仍是 RUNNING 的记录只可能是进程中断遗留
        LambdaUpdateWrapper<QuestionSubmit> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue())
                .lt(QuestionSubmit::getUpdateTime, staleThreshold)
                .set(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.WAITING.getValue());
        return this.getBaseMapper().update(null, updateWrapper) > 0;
    }

    /**
     * 获取查询包装类：前端根据用户可能会用到哪些字段查询，传递一个请求对象，返回mybatis框架支持的查询QueryWrapper类
     */
    @Override
    public QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitSubmitQueryRequest) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        if (questionSubmitSubmitQueryRequest == null) {
            return queryWrapper;
        }
        //考虑用户会用哪些字段来查询
        Long questionId = questionSubmitSubmitQueryRequest.getQuestionId();
        Long userId = questionSubmitSubmitQueryRequest.getUserId();
        String language = questionSubmitSubmitQueryRequest.getLanguage();
        Integer status = questionSubmitSubmitQueryRequest.getStatus();
        String sortField = questionSubmitSubmitQueryRequest.getSortField();
        String sortOrder = questionSubmitSubmitQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(StringUtils.isNotEmpty(language), "language", language);
        queryWrapper.eq(QuestionSubmitStatusEnum.getEnumByValue(status) != null, "status", status);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionSubmit getByIdAndUserId(long questionSubmitId, long userId) {
        return this.lambdaQuery()
                .eq(QuestionSubmit::getId, questionSubmitId)
                .eq(QuestionSubmit::getUserId, userId)
                .one();
    }

    /**
     * 用于获取实体的封装类
     */
    @Override
    public QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser) {
        QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
        Long userId = loginUser.getId();
        //脱敏处理：除了当前用户和管理员，其余用户是不允许查看提交代码的
        if (!userId.equals(questionSubmit.getUserId()) && !userService.isAdmin(loginUser)) {
            questionSubmitVO.setCode(null);
        }
        return questionSubmitVO;
    }

    /**
     * 分页获取实体的封装类
     *
     * @param questionSubmitPage 分页实体
     * @param loginUser          当前用户
     * @return 分页封装类
     */
    @Override
    public Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<QuestionSubmitVO> questionSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(), questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        if (CollUtil.isEmpty(questionSubmitList)) {
            return questionSubmitVOPage;
        }
        //暂时只做了脱敏，没有关联用户和题目
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitList.stream()
                .map(questionSubmit -> getQuestionSubmitVO(questionSubmit, loginUser))
                .collect(Collectors.toList());
        questionSubmitVOPage.setRecords(questionSubmitVOList);
        return questionSubmitVOPage;
    }

    @Override
    public Page<QuestionSubmitSummaryVO> getQuestionSubmitSummaryVOPage(Page<QuestionSubmit> questionSubmitPage) {
        Page<QuestionSubmitSummaryVO> summaryPage = new Page<>(questionSubmitPage.getCurrent(),
                questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        List<QuestionSubmitSummaryVO> summaryList = questionSubmitPage.getRecords().stream()
                .map(QuestionSubmitSummaryVO::objToVo)
                .collect(Collectors.toList());
        summaryPage.setRecords(summaryList);
        return summaryPage;
    }

    /**
     * 提交归档分页查询（全部提交视图）：判题结果筛选 + 多字段排序，排序在分页前完成。
     */
    @Override
    public Page<QuestionSubmitVO> getQuestionSubmitArchiveVOPage(QuestionSubmitArchiveQueryRequest archiveQueryRequest,
                                                                 User loginUser) {
        ThrowUtils.throwIf(archiveQueryRequest == null || archiveQueryRequest.getCurrent() <= 0
                || archiveQueryRequest.getPageSize() <= 0, ErrorCode.PARAMS_ERROR);
        JudgeResultEnum judgeResult = resolveJudgeResult(archiveQueryRequest.getJudgeResult());
        String sortField = archiveQueryRequest.getSortField();
        ThrowUtils.throwIf(StringUtils.isNotBlank(sortField) && !ARCHIVE_SORT_FIELDS.contains(sortField),
                ErrorCode.PARAMS_ERROR, "排序字段参数错误");
        String actualSortField = StringUtils.isBlank(sortField) ? SORT_FIELD_CREATE_TIME : sortField;
        String sortOrder = resolveArchiveSortOrder(archiveQueryRequest.getSortOrder());
        // 按提交者昵称模糊搜索：先解析用户 id 集合，匹配不到用户直接返回空页
        List<Long> submitterIds = resolveSubmitterIds(archiveQueryRequest.getUserName());
        if (submitterIds != null && submitterIds.isEmpty()) {
            return new Page<>(archiveQueryRequest.getCurrent(), archiveQueryRequest.getPageSize(), 0);
        }
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtils.isNotEmpty(archiveQueryRequest.getQuestionId()), "questionId",
                archiveQueryRequest.getQuestionId());
        queryWrapper.eq(ObjectUtils.isNotEmpty(archiveQueryRequest.getUserId()), "userId",
                archiveQueryRequest.getUserId());
        queryWrapper.eq(StringUtils.isNotEmpty(archiveQueryRequest.getLanguage()), "language",
                archiveQueryRequest.getLanguage());
        queryWrapper.eq(QuestionSubmitStatusEnum.getEnumByValue(archiveQueryRequest.getStatus()) != null, "status",
                archiveQueryRequest.getStatus());
        queryWrapper.eq("isDelete", false);
        if (submitterIds != null) {
            queryWrapper.in("userId", submitterIds);
        }
        if (judgeResult != JudgeResultEnum.ALL) {
            // 筛选片段为服务端枚举常量，不含客户端输入
            queryWrapper.apply(judgeResult.getSqlFragment());
        }
        // 整体追加 ORDER BY：排序表达式含函数与引号，方向仅来自白名单
        queryWrapper.last(buildArchiveOrderBy(actualSortField, sortOrder));
        Page<QuestionSubmit> questionSubmitPage = this.page(
                new Page<>(archiveQueryRequest.getCurrent(), archiveQueryRequest.getPageSize()), queryWrapper);
        return assembleArchiveVOPage(questionSubmitPage);
    }

    /**
     * 组装提交归档分页：提交代码对所有登录用户可见（产品决策：公开代码促进学习），
     * 批量补充题目与用户摘要及派生判题结果。（包级可见，便于单元测试）
     */
    Page<QuestionSubmitVO> assembleArchiveVOPage(Page<QuestionSubmit> questionSubmitPage) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<QuestionSubmitVO> questionSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(),
                questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        if (CollUtil.isEmpty(questionSubmitList)) {
            return questionSubmitVOPage;
        }
        Set<Long> questionIds = questionSubmitList.stream()
                .map(QuestionSubmit::getQuestionId)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Set<Long> userIds = questionSubmitList.stream()
                .map(QuestionSubmit::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitList.stream().map(questionSubmit -> {
            QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
            JudgeInfo judgeInfo = questionSubmitVO.getJudgeInfo();
            questionSubmitVO.setJudgeResult(JudgeResultEnum.from(questionSubmit.getStatus(),
                    judgeInfo == null ? null : judgeInfo.getMessage()).getValue());
            Question question = questionMap.get(questionSubmit.getQuestionId());
            if (question != null) {
                questionSubmitVO.setQuestionTitle(question.getTitle());
                questionSubmitVO.setQuestionTags(JSONUtil.toList(question.getTags(), String.class));
            }
            User user = userMap.get(questionSubmit.getUserId());
            if (user != null) {
                questionSubmitVO.setUserName(user.getUserName());
                questionSubmitVO.setUserAvatar(user.getUserAvatar());
            }
            return questionSubmitVO;
        }).collect(Collectors.toList());
        questionSubmitVOPage.setRecords(questionSubmitVOList);
        return questionSubmitVOPage;
    }

    /**
     * 提交归档分页查询（按题目聚合视图）：先筛选提交，再按题目聚合排序分页，total 为题目数量。
     */
    @Override
    public Page<QuestionSubmitGroupVO> getQuestionSubmitGroupVOPage(QuestionSubmitGroupQueryRequest groupQueryRequest,
                                                                    User loginUser) {
        ThrowUtils.throwIf(groupQueryRequest == null || groupQueryRequest.getCurrent() <= 0
                        || groupQueryRequest.getPageSize() <= 0 || groupQueryRequest.getPageSize() > 20,
                ErrorCode.PARAMS_ERROR);
        JudgeResultEnum judgeResult = resolveJudgeResult(groupQueryRequest.getJudgeResult());
        String sortField = groupQueryRequest.getSortField();
        ThrowUtils.throwIf(StringUtils.isNotBlank(sortField) && !GROUP_SORT_FIELDS.contains(sortField),
                ErrorCode.PARAMS_ERROR, "排序字段参数错误");
        String actualSortField = StringUtils.isBlank(sortField) ? SORT_FIELD_LATEST_SUBMIT_TIME : sortField;
        String sortOrder = resolveArchiveSortOrder(groupQueryRequest.getSortOrder());
        // 按提交者昵称模糊搜索：先解析用户 id 集合，匹配不到用户直接返回空页
        List<Long> submitterIds = resolveSubmitterIds(groupQueryRequest.getUserName());
        if (submitterIds != null && submitterIds.isEmpty()) {
            return new Page<>(groupQueryRequest.getCurrent(), groupQueryRequest.getPageSize(), 0);
        }
        String judgeResultSql = buildJudgeResultSql(judgeResult);
        Page<QuestionSubmitGroupRow> rowPage = new Page<>(groupQueryRequest.getCurrent(),
                groupQueryRequest.getPageSize());
        // 关闭自动 count：聚合查询的 total 需按题目数统计，改用独立 count 查询
        rowPage.setSearchCount(false);
        this.baseMapper.selectQuestionGroupPage(rowPage, judgeResultSql, groupQueryRequest.getLanguage(),
                groupQueryRequest.getUserId(), submitterIds, buildGroupOrderBy(actualSortField, sortOrder));
        Long total = this.baseMapper.countQuestionGroup(judgeResultSql, groupQueryRequest.getLanguage(),
                groupQueryRequest.getUserId(), submitterIds);
        rowPage.setTotal(total == null ? 0L : total);
        List<QuestionSubmitGroupVO> groupVOList = rowPage.getRecords().stream()
                .map(this::getQuestionSubmitGroupVO)
                .collect(Collectors.toList());
        Page<QuestionSubmitGroupVO> groupVOPage = new Page<>(rowPage.getCurrent(), rowPage.getSize(), rowPage.getTotal());
        groupVOPage.setRecords(groupVOList);
        return groupVOPage;
    }

    /**
     * 校验并解析判题结果筛选（空视为 all）
     */
    private JudgeResultEnum resolveJudgeResult(String judgeResult) {
        JudgeResultEnum judgeResultEnum = JudgeResultEnum.getEnumByValue(judgeResult);
        ThrowUtils.throwIf(StringUtils.isNotBlank(judgeResult) && judgeResultEnum == null,
                ErrorCode.PARAMS_ERROR, "判题结果参数错误");
        return judgeResultEnum == null ? JudgeResultEnum.ALL : judgeResultEnum;
    }

    /**
     * 校验并解析排序顺序（空视为降序）
     */
    private String resolveArchiveSortOrder(String sortOrder) {
        ThrowUtils.throwIf(StringUtils.isNotBlank(sortOrder) && !CommonConstant.SORT_ORDER_ASC.equals(sortOrder)
                        && !CommonConstant.SORT_ORDER_DESC.equals(sortOrder),
                ErrorCode.PARAMS_ERROR, "排序顺序参数错误");
        return StringUtils.isBlank(sortOrder) ? CommonConstant.SORT_ORDER_DESC : sortOrder;
    }

    /**
     * 构建判题结果筛选片段（前导 AND，供聚合 SQL 的 ${} 占位使用）
     */
    private String buildJudgeResultSql(JudgeResultEnum judgeResult) {
        if (judgeResult == null || judgeResult == JudgeResultEnum.ALL) {
            return "";
        }
        return " AND " + judgeResult.getSqlFragment();
    }

    /**
     * 按提交者昵称模糊解析用户 id 集合：昵称为空返回 null（不过滤），未匹配到用户返回空集合
     */
    private List<Long> resolveSubmitterIds(String userName) {
        if (StringUtils.isBlank(userName)) {
            return null;
        }
        return userService.list(Wrappers.<User>lambdaQuery().like(User::getUserName, userName))
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }

    /**
     * 构建归档列表排序：缺失指标排在有值之后，同值按提交时间倒序稳定排序
     */
    private String buildArchiveOrderBy(String sortField, String sortOrder) {
        String dir = CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? "ASC" : "DESC";
        if (SORT_FIELD_JUDGE_TIME.equals(sortField)) {
            return buildMetricOrderBy("CAST(JSON_EXTRACT(judgeInfo, '$.time') AS SIGNED)", dir);
        }
        if (SORT_FIELD_JUDGE_MEMORY.equals(sortField)) {
            return buildMetricOrderBy("CAST(JSON_EXTRACT(judgeInfo, '$.memory') AS SIGNED)", dir);
        }
        return "ORDER BY createTime " + dir + ", id DESC";
    }

    private String buildMetricOrderBy(String metricExpr, String dir) {
        return "ORDER BY (" + metricExpr + " IS NULL) ASC, " + metricExpr + " " + dir + ", createTime DESC, id DESC";
    }

    /**
     * 构建按题目聚合排序：缺失指标排在有值之后，同值按最近提交时间倒序稳定排序
     */
    private String buildGroupOrderBy(String sortField, String sortOrder) {
        String dir = CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? "ASC" : "DESC";
        if (SORT_FIELD_BEST_TIME.equals(sortField)) {
            return buildGroupMetricOrderBy("t.bestTime", dir);
        }
        if (SORT_FIELD_BEST_MEMORY.equals(sortField)) {
            return buildGroupMetricOrderBy("t.bestMemory", dir);
        }
        return "ORDER BY t.createTime " + dir + ", t.id DESC";
    }

    private String buildGroupMetricOrderBy(String metricColumn, String dir) {
        return "ORDER BY (" + metricColumn + " IS NULL) ASC, " + metricColumn + " " + dir
                + ", t.createTime DESC, t.id DESC";
    }

    /**
     * 聚合行转封装类：解析题目标签与最近一次提交的判题结果
     */
    private QuestionSubmitGroupVO getQuestionSubmitGroupVO(QuestionSubmitGroupRow row) {
        QuestionSubmitGroupVO groupVO = new QuestionSubmitGroupVO();
        groupVO.setQuestionId(row.getQuestionId());
        groupVO.setQuestionTitle(row.getQuestionTitle());
        groupVO.setQuestionTags(JSONUtil.toList(row.getQuestionTags(), String.class));
        groupVO.setLatestResult(JudgeResultEnum.from(row.getLatestStatus(),
                parseJudgeInfoMessage(row.getLatestJudgeInfo())).getValue());
        groupVO.setLatestSubmissionId(row.getLatestSubmissionId());
        groupVO.setLatestLanguage(row.getLatestLanguage());
        groupVO.setLatestCode(row.getLatestCode());
        groupVO.setLatestSubmitTime(row.getLatestSubmitTime());
        groupVO.setBestTime(row.getBestTime());
        groupVO.setBestMemory(row.getBestMemory());
        return groupVO;
    }

    /**
     * 从判题信息 JSON 中提取 message，解析失败时返回 null
     */
    private String parseJudgeInfoMessage(String judgeInfoJson) {
        if (StringUtils.isBlank(judgeInfoJson)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(judgeInfoJson).getStr("message");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取提交的指标排名数据：实时统计同题同语言、通过（Accepted）且指标有效的提交（含已通过的本人的提交），
     * 返回人群总数与指标大于等于本次提交（含本人与持平）的数量，比例由前端计算展示。
     * "超过"按不快于本次提交计：首次提交为 1/1（超过 100%），只要本人有指标就必有数据。
     */
    @Override
    public QuestionSubmitRankVO getQuestionSubmitRank(long submissionId) {
        QuestionSubmit questionSubmit = this.getById(submissionId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        JudgeInfo judgeInfo = StringUtils.isBlank(questionSubmit.getJudgeInfo())
                ? null : JSONUtil.toBean(questionSubmit.getJudgeInfo(), JudgeInfo.class);
        Long time = judgeInfo == null ? null : judgeInfo.getTime();
        Long memory = judgeInfo == null ? null : judgeInfo.getMemory();
        QuestionSubmitRankVO rankVO = new QuestionSubmitRankVO();
        rankVO.setSubmissionId(questionSubmit.getId());
        rankVO.setQuestionId(questionSubmit.getQuestionId());
        rankVO.setLanguage(questionSubmit.getLanguage());
        rankVO.setTime(time);
        rankVO.setMemory(memory);
        if (time == null && memory == null) {
            // 无有效指标（待判题/编译失败等），无对比数据
            return rankVO;
        }
        QuestionSubmitRankStatsRow statsRow = this.baseMapper.selectQuestionLanguageRankStats(
                JudgeResultEnum.ACCEPTED.getSqlFragment(), questionSubmit.getQuestionId(),
                questionSubmit.getLanguage(), time, memory);
        if (statsRow == null) {
            return rankVO;
        }
        if (time != null) {
            rankVO.setTimeTotal(zeroIfNull(statsRow.getTimeTotal()));
            rankVO.setTimeBeaten(zeroIfNull(statsRow.getTimeBeaten()));
        }
        if (memory != null) {
            rankVO.setMemoryTotal(zeroIfNull(statsRow.getMemoryTotal()));
            rankVO.setMemoryBeaten(zeroIfNull(statsRow.getMemoryBeaten()));
        }
        return rankVO;
    }

    /**
     * 获取提交的排队状态：待判题时返回前方排队人数与队列总长。
     * 队列按 id 升序派发（见 JudgeTaskDispatcher），因此前方人数与实际派发顺序一致；
     * 非待判题状态不在队列中，前方人数为 0。
     */
    @Override
    public QuestionSubmitQueueStatusVO getQuestionSubmitQueueStatus(long submissionId) {
        QuestionSubmit questionSubmit = this.getById(submissionId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        QuestionSubmitQueueStatusVO queueStatusVO = new QuestionSubmitQueueStatusVO();
        queueStatusVO.setSubmissionId(questionSubmit.getId());
        queueStatusVO.setStatus(questionSubmit.getStatus());
        queueStatusVO.setQueueLength(zeroIfNull(this.getBaseMapper().selectCount(buildWaitingCountWrapper(null))));
        if (QuestionSubmitStatusEnum.WAITING.getValue().equals(questionSubmit.getStatus())) {
            queueStatusVO.setAheadCount(zeroIfNull(this.getBaseMapper()
                    .selectCount(buildWaitingCountWrapper(questionSubmit.getId()))));
        } else {
            queueStatusVO.setAheadCount(0L);
        }
        return queueStatusVO;
    }

    /**
     * 构建待判题计数查询：指定 beforeId 时仅统计排在其之前（更早提交）的 WAITING 记录
     */
    private QueryWrapper<QuestionSubmit> buildWaitingCountWrapper(Long beforeId) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", QuestionSubmitStatusEnum.WAITING.getValue());
        queryWrapper.lt(beforeId != null, "id", beforeId);
        return queryWrapper;
    }

    // endregion
}




