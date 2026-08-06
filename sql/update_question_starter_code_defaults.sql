-- 将此前迁移脚本生成的旧默认模板更新为带参数读取和输出示例的新模板。
-- 只匹配旧默认内容，不会覆盖出题人已经修改过的模板。
use oj_db;

update question_starter_code
set starterCode = 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        int a = Integer.parseInt(args[0]);\n        System.out.print("output the answer");\n    }\n}\n'
where language = 'java'
  and starterCode = 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // Write your solution here.\n    }\n}\n';

update question_starter_code
set starterCode = '#include <iostream>\n#include <string>\nusing namespace std;\n\nint main(int argc, char *argv[]) {\n    int a = stoi(argv[1]);\n    cout << "output the answer";\n    return 0;\n}\n'
where language = 'cpp'
  and starterCode = '#include <iostream>\nusing namespace std;\n\nint main(int argc, char *argv[]) {\n    // Write your solution here.\n    return 0;\n}\n';

update question_starter_code
set starterCode = 'package main\n\nimport (\n    "fmt"\n    "os"\n    "strconv"\n)\n\nfunc main() {\n    a, _ := strconv.Atoi(os.Args[1])\n    fmt.Print("output the answer")\n    _ = a\n}\n'
where language = 'go'
  and starterCode = 'package main\n\nfunc main() {\n    // Write your solution here.\n}\n';

update question_starter_code
set starterCode = 'import sys\n\na = int(sys.argv[1])\nprint("output the answer", end="")\n'
where language = 'python'
  and starterCode = 'def main():\n    # Write your solution here.\n    pass\n\nif __name__ == "__main__":\n    main()\n';

update question_starter_code
set starterCode = 'const a = Number(process.argv[2]);\n\nconsole.log("output the answer");\n'
where language = 'javascript'
  and starterCode = 'function main() {\n    // Write your solution here.\n}\n\nmain();\n';
