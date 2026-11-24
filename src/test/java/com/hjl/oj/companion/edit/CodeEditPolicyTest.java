package com.hjl.oj.companion.edit;

import com.hjl.oj.companion.hint.EditPermission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeEditPolicyTest {

    private static final String JAVA = "java";

    @Test
    void syntaxOnlyAllowsTinySingleLineFix() {
        assertTrue(CodeEditPolicy.check("int a = 1", "int a = 1;", EditPermission.SYNTAX_ONLY, JAVA).isEmpty());
        assertTrue(CodeEditPolicy.check("Sytem.out.println(a);", "System.out.println(a);",
                EditPermission.SYNTAX_ONLY, JAVA).isEmpty());
    }

    @Test
    void syntaxOnlyAllowsAddingMissingImport() {
        assertTrue(CodeEditPolicy.check("public class Main {", "import java.util.*;\npublic class Main {",
                EditPermission.SYNTAX_ONLY, JAVA).isEmpty());
    }

    @Test
    void syntaxOnlyRejectsNewLogic() {
        String old = "return -1;";
        String rewritten = "Map<Integer, Integer> seen = new HashMap<>();\nfor (int i = 0; i < n; i++) {\n"
                + "    if (seen.containsKey(target - nums[i])) return i;\n}\nreturn -1;";

        assertTrue(CodeEditPolicy.check(old, rewritten, EditPermission.SYNTAX_ONLY, JAVA).isPresent());
        assertTrue(CodeEditPolicy.check("if (a > b) {", "if (a >= b && seen.contains(a)) {",
                EditPermission.SYNTAX_ONLY, JAVA).isPresent());
    }

    @Test
    void annotationsNeedAnnotateLevel() {
        String old = "for (int i = 1; i < n; i++) {";
        String annotated = "// 这里从 1 开始，会漏掉第一个元素\nfor (int i = 1; i < n; i++) {";

        assertTrue(CodeEditPolicy.check(old, annotated, EditPermission.SYNTAX_ONLY, JAVA).isPresent());
        assertTrue(CodeEditPolicy.check(old, annotated, EditPermission.ANNOTATE, JAVA).isEmpty());
    }

    @Test
    void trailingCommentOnUnchangedCodeCountsAsAnnotation() {
        String old = "int mid = (left + right) / 2;";
        String annotated = "int mid = (left + right) / 2; // left + right 可能溢出";

        assertTrue(CodeEditPolicy.check(old, annotated, EditPermission.ANNOTATE, JAVA).isEmpty());
    }

    @Test
    void pythonHashIsCommentButCppIncludeIsCode() {
        assertTrue(CodeEditPolicy.check("x = 1", "# 先想想 x 的含义\nx = 1", EditPermission.ANNOTATE, "python").isEmpty());
        assertTrue(CodeEditPolicy.isComment("# note", "python"));
        assertTrue(!CodeEditPolicy.isComment("#include <vector>", "cpp"));
    }

    @Test
    void smallChangeAllowsUpToThreeLines() {
        String old = "a\nb\nc";
        String threeChanged = "a1\nb1\nc1";
        String fourAdded = "a\nb\nc\nd\ne\nf\ng";

        assertTrue(CodeEditPolicy.check(old, threeChanged, EditPermission.SMALL_CHANGE, JAVA).isEmpty());
        assertTrue(CodeEditPolicy.check(old, fourAdded, EditPermission.SMALL_CHANGE, JAVA).isPresent());
    }

    @Test
    void indentationOnlyChangesAreIgnored() {
        assertTrue(CodeEditPolicy.check("  return x;", "    return x;", EditPermission.SYNTAX_ONLY, JAVA).isEmpty());
    }

    @Test
    void unrestrictedAllowsAnything() {
        assertTrue(CodeEditPolicy.check("a", "completely\nnew\ncode\nhere\nwith\nmany\nlines",
                EditPermission.UNRESTRICTED, JAVA).isEmpty());
    }
}
