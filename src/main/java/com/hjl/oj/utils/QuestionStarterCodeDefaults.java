package com.hjl.oj.utils;

import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeParameterDefinition;
import com.hjl.oj.model.enums.JudgeValueTypeEnum;
import com.hjl.oj.model.enums.QuestionSubmitLanguageEnum;

import java.util.List;

/**
 * 根据题目输入、输出定义生成各语言初始代码模板。
 */
public final class QuestionStarterCodeDefaults {

    private QuestionStarterCodeDefaults() {
    }

    /**
     * 保留一个无配置入口，供历史调用和测试使用。
     */
    public static String getDefaultStarterCode(QuestionSubmitLanguageEnum language) {
        return getDefaultStarterCode(language, null);
    }

    /**
     * 根据题目的结构化判题配置生成可直接运行的模板。
     */
    public static String getDefaultStarterCode(QuestionSubmitLanguageEnum language,
                                               JudgeCaseConfig config) {
        List<JudgeParameterDefinition> definitions = config == null
                ? List.of(defaultInputDefinition())
                : config.getInputDefinitions() == null ? List.of() : config.getInputDefinitions();
        JudgeValueTypeEnum outputType = getType(config == null || config.getOutputDefinition() == null
                ? "INTEGER" : config.getOutputDefinition().getType());
        return switch (language) {
            case JAVA -> javaTemplate(definitions, outputType);
            case CPLUSPLUS -> cppTemplate(definitions, outputType);
            case GOLANG -> goTemplate(definitions, outputType);
            case PYTHON -> pythonTemplate(definitions, outputType);
            case JAVASCRIPT -> javascriptTemplate(definitions, outputType);
        };
    }

    private static JudgeParameterDefinition defaultInputDefinition() {
        JudgeParameterDefinition definition = new JudgeParameterDefinition();
        definition.setName("input");
        definition.setType("INTEGER");
        return definition;
    }

    private static JudgeValueTypeEnum getType(String type) {
        JudgeValueTypeEnum valueType = JudgeValueTypeEnum.getEnumByValue(type);
        return valueType == null ? JudgeValueTypeEnum.INTEGER : valueType;
    }

    private static String javaTemplate(List<JudgeParameterDefinition> definitions,
                                       JudgeValueTypeEnum outputType) {
        StringBuilder code = new StringBuilder();
        code.append("import com.google.gson.Gson;\n");
        code.append("import java.util.*;\n\n");
        code.append("public class Main {\n");
        code.append("    private static final Gson JSON = new Gson();\n\n");
        code.append("    public static void main(String[] args) {\n");
        for (int i = 0; i < definitions.size(); i++) {
            code.append("        // args[").append(i).append("] -> ")
                    .append(variableName(definitions.get(i), i)).append("\n");
            code.append("        ").append(javaInputLine(definitions.get(i), i)).append("\n");
        }
        code.append("\n        // Write your solution here.\n");
        code.append("        ").append(javaType(outputType)).append(" answer = ")
                .append(javaDefault(outputType)).append(";\n");
        code.append("        System.out.print(JSON.toJson(answer));\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    private static String javaInputLine(JudgeParameterDefinition definition, int index) {
        String variable = variableName(definition, index);
        String argument = "args[" + index + "]";
        return switch (getType(definition.getType())) {
            case INTEGER -> "int " + variable + " = Integer.parseInt(" + argument + ");";
            case LONG -> "long " + variable + " = Long.parseLong(" + argument + ");";
            case DOUBLE -> "double " + variable + " = Double.parseDouble(" + argument + ");";
            case BOOLEAN -> "boolean " + variable + " = Boolean.parseBoolean(" + argument + ");";
            case STRING -> "String " + variable + " = " + argument + ";";
            case INTEGER_ARRAY -> "int[] " + variable + " = JSON.fromJson(" + argument + ", int[].class);";
            case INTEGER_MATRIX -> "int[][] " + variable + " = JSON.fromJson(" + argument + ", int[][].class);";
            case STRING_ARRAY -> "String[] " + variable + " = JSON.fromJson(" + argument + ", String[].class);";
        };
    }

    private static String javaType(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER -> "int";
            case LONG -> "long";
            case DOUBLE -> "double";
            case BOOLEAN -> "boolean";
            case STRING -> "String";
            case INTEGER_ARRAY -> "List<Integer>";
            case INTEGER_MATRIX -> "List<List<Integer>>";
            case STRING_ARRAY -> "List<String>";
        };
    }

    private static String javaDefault(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER -> "0";
            case LONG -> "0L";
            case DOUBLE -> "0.0d";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> "new ArrayList<>()";
        };
    }

    private static String cppTemplate(List<JudgeParameterDefinition> definitions,
                                      JudgeValueTypeEnum outputType) {
        StringBuilder code = new StringBuilder();
        code.append("#include <iostream>\n");
        code.append("#include <string>\n");
        code.append("#include <vector>\n");
        code.append("#include <nlohmann/json.hpp>\n\n");
        code.append("using json = nlohmann::json;\n\n");
        code.append("int main(int argc, char* argv[]) {\n");
        for (int i = 0; i < definitions.size(); i++) {
            code.append("    // argv[").append(i + 1).append("] -> ")
                    .append(variableName(definitions.get(i), i)).append("\n");
            code.append("    ").append(cppInputLine(definitions.get(i), i)).append("\n");
        }
        code.append("\n    // Write your solution here.\n");
        code.append("    ").append(cppType(outputType)).append(" answer = ")
                .append(cppDefault(outputType)).append(";\n");
        code.append("    std::cout << json(answer).dump();\n");
        code.append("    return 0;\n");
        code.append("}\n");
        return code.toString();
    }

    private static String cppInputLine(JudgeParameterDefinition definition, int index) {
        String variable = variableName(definition, index);
        String argument = "argv[" + (index + 1) + "]";
        return switch (getType(definition.getType())) {
            case INTEGER -> "int " + variable + " = std::stoi(" + argument + ");";
            case LONG -> "long long " + variable + " = std::stoll(" + argument + ");";
            case DOUBLE -> "double " + variable + " = std::stod(" + argument + ");";
            case BOOLEAN -> "bool " + variable + " = std::string(" + argument + ") == \"true\";";
            case STRING -> "std::string " + variable + " = " + argument + ";";
            case INTEGER_ARRAY -> "std::vector<int> " + variable
                    + " = json::parse(" + argument + ").get<std::vector<int>>();";
            case INTEGER_MATRIX -> "std::vector<std::vector<int>> " + variable
                    + " = json::parse(" + argument + ").get<std::vector<std::vector<int>>>();";
            case STRING_ARRAY -> "std::vector<std::string> " + variable
                    + " = json::parse(" + argument + ").get<std::vector<std::string>>();";
        };
    }

    private static String cppType(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER -> "int";
            case LONG -> "long long";
            case DOUBLE -> "double";
            case BOOLEAN -> "bool";
            case STRING -> "std::string";
            case INTEGER_ARRAY -> "std::vector<int>";
            case INTEGER_MATRIX -> "std::vector<std::vector<int>>";
            case STRING_ARRAY -> "std::vector<std::string>";
        };
    }

    private static String cppDefault(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER -> "0";
            case LONG -> "0LL";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> "{}";
        };
    }

    private static String goTemplate(List<JudgeParameterDefinition> definitions,
                                     JudgeValueTypeEnum outputType) {
        StringBuilder code = new StringBuilder();
        code.append("package main\n\n");
        code.append("import (\n");
        code.append("    \"encoding/json\"\n");
        code.append("    \"fmt\"\n");
        if (!definitions.isEmpty()) {
            code.append("    \"os\"\n");
        }
        if (definitions.stream().map(JudgeParameterDefinition::getType)
                .map(QuestionStarterCodeDefaults::getType)
                .anyMatch(QuestionStarterCodeDefaults::usesGoStrconv)) {
            code.append("    \"strconv\"\n");
        }
        code.append(")\n\n");
        code.append("func main() {\n");
        for (int i = 0; i < definitions.size(); i++) {
            String variable = variableName(definitions.get(i), i);
            code.append("    // os.Args[").append(i + 1).append("] -> ").append(variable).append("\n");
            code.append("    ").append(goInputLine(definitions.get(i), i)).append("\n");
            code.append("    _ = ").append(variable).append("\n");
        }
        code.append("\n    // Write your solution here.\n");
        code.append("    var answer ").append(goType(outputType)).append(" = ")
                .append(goDefault(outputType)).append("\n");
        code.append("    output, _ := json.Marshal(answer)\n");
        code.append("    fmt.Print(string(output))\n");
        code.append("}\n");
        return code.toString();
    }

    private static boolean usesGoStrconv(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER, LONG, DOUBLE, BOOLEAN -> true;
            case STRING, INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> false;
        };
    }

    private static String goInputLine(JudgeParameterDefinition definition, int index) {
        String variable = variableName(definition, index);
        String argument = "os.Args[" + (index + 1) + "]";
        return switch (getType(definition.getType())) {
            case INTEGER -> variable + ", _ := strconv.Atoi(" + argument + ")";
            case LONG -> variable + ", _ := strconv.ParseInt(" + argument + ", 10, 64)";
            case DOUBLE -> variable + ", _ := strconv.ParseFloat(" + argument + ", 64)";
            case BOOLEAN -> variable + ", _ := strconv.ParseBool(" + argument + ")";
            case STRING -> variable + " := " + argument;
            case INTEGER_ARRAY ->
                    "var " + variable + " []int; _ = json.Unmarshal([]byte(" + argument + "), &" + variable + ")";
            case INTEGER_MATRIX ->
                    "var " + variable + " [][]int; _ = json.Unmarshal([]byte(" + argument + "), &" + variable + ")";
            case STRING_ARRAY ->
                    "var " + variable + " []string; _ = json.Unmarshal([]byte(" + argument + "), &" + variable + ")";
        };
    }

    private static String goType(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER -> "int";
            case LONG -> "int64";
            case DOUBLE -> "float64";
            case BOOLEAN -> "bool";
            case STRING -> "string";
            case INTEGER_ARRAY -> "[]int";
            case INTEGER_MATRIX -> "[][]int";
            case STRING_ARRAY -> "[]string";
        };
    }

    private static String goDefault(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER, LONG -> "0";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> goType(type) + "{}";
        };
    }

    private static String pythonTemplate(List<JudgeParameterDefinition> definitions,
                                         JudgeValueTypeEnum outputType) {
        StringBuilder code = new StringBuilder();
        code.append("import json\n");
        code.append("import sys\n\n");
        for (int i = 0; i < definitions.size(); i++) {
            code.append("# sys.argv[").append(i + 1).append("] -> ")
                    .append(variableName(definitions.get(i), i)).append("\n");
            code.append(pythonInputLine(definitions.get(i), i)).append("\n");
        }
        code.append("\n# Write your solution here.\n");
        code.append("answer = ").append(pythonDefault(outputType)).append("\n");
        code.append("print(json.dumps(answer, separators=(\",\", \":\")), end=\"\")\n");
        return code.toString();
    }

    private static String pythonInputLine(JudgeParameterDefinition definition, int index) {
        String variable = variableName(definition, index);
        String argument = "sys.argv[" + (index + 1) + "]";
        return switch (getType(definition.getType())) {
            case INTEGER, LONG -> variable + " = int(" + argument + ")";
            case DOUBLE -> variable + " = float(" + argument + ")";
            case BOOLEAN -> variable + " = " + argument + ".lower() == \"true\"";
            case STRING -> variable + " = " + argument;
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> variable + " = json.loads(" + argument + ")";
        };
    }

    private static String pythonDefault(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER, LONG -> "0";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "False";
            case STRING -> "\"\"";
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> "[]";
        };
    }

    private static String javascriptTemplate(List<JudgeParameterDefinition> definitions,
                                             JudgeValueTypeEnum outputType) {
        StringBuilder code = new StringBuilder();
        code.append("function toJson(value) {\n");
        code.append("    return JSON.stringify(value, (_, item) =>\n");
        code.append("        typeof item === \"bigint\" ? item.toString() : item);\n");
        code.append("}\n\n");
        for (int i = 0; i < definitions.size(); i++) {
            code.append("// process.argv[").append(i + 2).append("] -> ")
                    .append(variableName(definitions.get(i), i)).append("\n");
            code.append(javascriptInputLine(definitions.get(i), i)).append("\n");
        }
        code.append("\n// Write your solution here.\n");
        code.append("let answer = ").append(javascriptDefault(outputType)).append(";\n");
        code.append("process.stdout.write(toJson(answer));\n");
        return code.toString();
    }

    private static String javascriptInputLine(JudgeParameterDefinition definition, int index) {
        String variable = variableName(definition, index);
        String argument = "process.argv[" + (index + 2) + "]";
        return switch (getType(definition.getType())) {
            case INTEGER, DOUBLE -> "const " + variable + " = Number(" + argument + ");";
            case LONG -> "const " + variable + " = BigInt(" + argument + ");";
            case BOOLEAN -> "const " + variable + " = " + argument + " === \"true\";";
            case STRING -> "const " + variable + " = " + argument + ";";
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY ->
                    "const " + variable + " = JSON.parse(" + argument + ");";
        };
    }

    private static String javascriptDefault(JudgeValueTypeEnum type) {
        return switch (type) {
            case INTEGER -> "0";
            case LONG -> "0n";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case STRING -> "\"\"";
            case INTEGER_ARRAY, INTEGER_MATRIX, STRING_ARRAY -> "[]";
        };
    }

    private static String variableName(JudgeParameterDefinition definition, int index) {
        String name = definition == null || definition.getName() == null
                ? "input" : definition.getName().replaceAll("[^A-Za-z0-9_]", "_");
        if (name.isBlank()) {
            name = "input";
        }
        if (Character.isDigit(name.charAt(0))) {
            name = "_" + name;
        }
        return name + "Input" + index;
    }
}
