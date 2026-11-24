package com.hjl.oj.companion.edit;

import com.hjl.oj.companion.hint.EditPermission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeEditSessionTest {

    private static final String CODE = "public class Main {\r\n    int a = 1\r\n    int b = 2;\r\n}\r\n";

    @Test
    void replacesUniqueTextAndProducesProposal() {
        CodeEditSession session = new CodeEditSession(CODE, "java", EditPermission.SYNTAX_ONLY);

        String result = session.replace("int a = 1", "int a = 1;", "补上分号");

        assertTrue(result.startsWith("已记录"), result);
        CodeEditProposal proposal = session.proposal().orElseThrow();
        assertEquals(CodeEditSession.sha256(CODE), proposal.baseHash());
        assertEquals("public class Main {\n    int a = 1;\n    int b = 2;\n}\n", proposal.resultCode());
        assertEquals(2, proposal.edits().get(0).startLine());
        assertEquals("补上分号", proposal.edits().get(0).reason());
    }

    @Test
    void rejectsMissingAndAmbiguousText() {
        CodeEditSession session = new CodeEditSession("x = 1\nx = 1\n", "python", EditPermission.UNRESTRICTED);

        assertTrue(session.replace("y = 2", "y = 3", "").startsWith("修改失败"));
        assertTrue(session.replace("x = 1", "x = 2", "").contains("出现了 2 次"));
        assertTrue(session.proposal().isEmpty());
    }

    @Test
    void stripsLineNumberPrefixesCopiedFromPrompt() {
        CodeEditSession session = new CodeEditSession(CODE, "java", EditPermission.SYNTAX_ONLY);

        String result = session.replace("2|     int a = 1", "2|     int a = 1;", "补分号");

        assertTrue(result.startsWith("已记录"), result);
        assertTrue(session.proposal().orElseThrow().resultCode().contains("int a = 1;"));
    }

    @Test
    void enforcesPermissionForEachEdit() {
        CodeEditSession session = new CodeEditSession(CODE, "java", EditPermission.SYNTAX_ONLY);

        String result = session.replace("    int b = 2;", "    int b = 2;\n    for (int i = 0; i < b; i++) a += i;\n    a *= 2;", "");

        assertTrue(result.startsWith("修改被拒绝"), result);
        assertTrue(session.proposal().isEmpty());
    }

    @Test
    void rewriteOnlyWhenUnrestricted() {
        assertTrue(new CodeEditSession(CODE, "java", EditPermission.SMALL_CHANGE)
                .rewrite("new code", "").startsWith("修改被拒绝"));

        CodeEditSession unrestricted = new CodeEditSession(CODE, "java", EditPermission.UNRESTRICTED);
        assertTrue(unrestricted.rewrite("new code\r\n", "参考代码").startsWith("已记录"));
        CodeEditProposal proposal = unrestricted.proposal().orElseThrow();
        assertEquals("new code\n", proposal.resultCode());
        assertEquals(CodeEdit.KIND_REWRITE, proposal.edits().get(0).kind());
    }

    @Test
    void limitsEditsPerTurn() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CodeEditSession.MAX_EDITS_PER_TURN + 2; i++) {
            code.append("v").append(i).append(" = 0\n");
        }
        CodeEditSession session = new CodeEditSession(code.toString(), "python", EditPermission.UNRESTRICTED);
        for (int i = 0; i < CodeEditSession.MAX_EDITS_PER_TURN; i++) {
            assertTrue(session.replace("v" + i + " = 0", "v" + i + " = 1", "").startsWith("已记录"));
        }

        assertTrue(session.replace("v11 = 0", "v11 = 1", "").contains("上限"));
    }
}
