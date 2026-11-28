package com.hjl.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjl.oj.model.entity.QuestionListItem;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface QuestionListItemMapper extends BaseMapper<QuestionListItem> {

    /**
     * 几个题单里的题（已删除的题不算），按题单、顺序排好
     */
    List<QuestionListItem> selectActiveItems(@Param("listIds") Collection<Long> listIds);
}
