package com.hjl.oj.service;

import com.hjl.oj.model.dto.companion.CompanionNoteApplyRequest;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.model.vo.CompanionNoteApplyVO;
import com.hjl.oj.model.vo.CompanionNotePreviewVO;

/**
 * 鸭鸭的笔记提议：预览、应用。提议在聊天时由笔记工具记在消息上（noteEdits），这里负责真正写进笔记。
 */
public interface CompanionNoteService {

    /**
     * 按笔记现在的内容算出应用后的样子，不写任何东西
     */
    CompanionNotePreviewVO preview(User loginUser, long messageId, int index);

    /**
     * 写进笔记并把提议标记为已应用；已经应用过的直接返回那篇笔记
     */
    CompanionNoteApplyVO apply(User loginUser, CompanionNoteApplyRequest request);
}
