package com.hjl.oj.companion.edit;

import java.util.List;

/**
 * 一轮回复里的全部代码修改建议，前端用对比视图预览，用户点"应用"后才写入编辑器。
 *
 * @param baseHash   修改所基于的代码的 SHA-256（按请求中原样的代码计算）。前端应用前与编辑器当前内容比对，
 *                   不一致说明用户在此期间改过代码，需要按原文重新定位或提示重试
 * @param edits      每处修改的明细
 * @param resultCode 依次应用全部修改后的完整代码（换行统一为 \n）
 */
public record CodeEditProposal(String baseHash, List<CodeEdit> edits, String resultCode) {
}
