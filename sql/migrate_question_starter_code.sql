-- 为已有题目补齐初始代码模板。
-- 已存在的自定义模板不会被覆盖；执行前请确认 question_starter_code 已创建。
use oj_db;

insert ignore into question_starter_code (questionId, language, starterCode)
select q.id, template.language, template.starterCode
from question q
         join (
    select 'java' as language, 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        int a = Integer.parseInt(args[0]);\n        System.out.print("output the answer");\n    }\n}\n' as starterCode
    union all
    select 'cpp', '#include <iostream>\n#include <string>\nusing namespace std;\n\nint main(int argc, char *argv[]) {\n    int a = stoi(argv[1]);\n    cout << "output the answer";\n    return 0;\n}\n'
    union all
    select 'go', 'package main\n\nimport (\n    "fmt"\n    "os"\n    "strconv"\n)\n\nfunc main() {\n    a, _ := strconv.Atoi(os.Args[1])\n    fmt.Print("output the answer")\n    _ = a\n}\n'
    union all
    select 'python', 'import sys\n\na = int(sys.argv[1])\nprint("output the answer", end="")\n'
    union all
    select 'javascript', 'const a = Number(process.argv[2]);\n\nconsole.log("output the answer");\n'
) template on 1 = 1
where q.isDelete = 0;
