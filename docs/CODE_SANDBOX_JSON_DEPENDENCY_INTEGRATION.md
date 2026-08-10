# 代码沙箱 JSON 依赖对接说明

## 背景

OJ 后端会根据题目的 `inputDefinitions` 和 `outputDefinition` 自动生成各语言 starter code。数组、矩阵和字符串数组通过 JSON 文本传入 `cases[].args[]`，starter code 负责解析参数，并将答案序列化为一个 JSON 值写入标准输出。

Python、JavaScript 和 Go 分别使用标准库 `json`、内置 `JSON` 和 `encoding/json`。Java 与 C++ 标准库没有对应的通用 JSON API，因此沙箱镜像需要提供固定依赖：

- Java：Gson `2.14.0`
- C++：nlohmann/json `3.12.0`

沙箱仍然只负责编译、执行和收集原始标准输出，不解析 OJ 类型，也不修改输出内容。

## Java 镜像

后端生成的 Java 代码包含：

```java
import com.google.gson.Gson;
```

请将 Gson 固定放在镜像内：

```text
/opt/libs/gson.jar
```

Gson 的官方 Maven 坐标为：

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.14.0</version>
</dependency>
```

可使用 Maven 构建阶段获取，不要在处理用户请求时联网下载：

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS gson-dependency
ARG GSON_VERSION=2.14.0
RUN mvn -q dependency:get \
    -Dartifact=com.google.code.gson:gson:${GSON_VERSION}
RUN mkdir -p /deps && \
    cp /root/.m2/repository/com/google/code/gson/gson/${GSON_VERSION}/gson-${GSON_VERSION}.jar \
       /deps/gson.jar

# 在现有 Java 沙箱镜像阶段中加入
COPY --from=gson-dependency /deps/gson.jar /opt/libs/gson.jar
```

编译和运行都必须加入 classpath：

```bash
javac -encoding UTF-8 -cp /opt/libs/gson.jar Main.java
java -cp "/workspace:/opt/libs/gson.jar" Main '[1,2,3]'
```

`/workspace` 请替换为沙箱实际的编译产物目录。Gson JAR 位于镜像只读层，不需要宿主机目录挂载。

官方来源：

- https://github.com/google/gson
- https://repo1.maven.org/maven2/com/google/code/gson/gson/

## C++ 镜像

后端生成的 C++ 代码包含：

```cpp
#include <nlohmann/json.hpp>
```

nlohmann/json 是单头文件库。请从官方 release 固定下载 `json.hpp`，放入编译镜像：

```text
/usr/local/include/nlohmann/json.hpp
```

示例构建步骤：

```dockerfile
FROM alpine:3.22 AS nlohmann-json-dependency
ARG NLOHMANN_JSON_VERSION=3.12.0
RUN apk add --no-cache curl && \
    mkdir -p /deps/nlohmann && \
    curl -fsSL \
      "https://github.com/nlohmann/json/releases/download/v${NLOHMANN_JSON_VERSION}/json.hpp" \
      -o /deps/nlohmann/json.hpp

# 在现有 C++ 编译镜像阶段中加入
COPY --from=nlohmann-json-dependency /deps/nlohmann \
     /usr/local/include/nlohmann
```

生产构建应固定版本并校验官方 release 文件的 SHA-256。`/usr/local/include` 是 GCC 默认头文件搜索路径，因此现有命令通常无需增加参数：

```bash
g++ -O2 -std=c++17 Main.cpp -o Main
```

该库在编译期展开到可执行文件中，运行容器不需要额外动态库或 classpath。

官方来源：

- https://github.com/nlohmann/json
- https://github.com/nlohmann/json/releases

## 验收要求

1. 不改变现有 `/executeCode` 请求、响应结构。
2. 依赖必须在构建镜像时安装，运行用户代码时禁止联网下载。
3. Java 编译和运行阶段都能找到 `/opt/libs/gson.jar`。
4. C++ 能直接编译 `#include <nlohmann/json.hpp>`。
5. 镜像仍保持网络禁用、只读运行挂载、资源限制和用例隔离。
6. 对 Java、C++ 分别执行真实 Docker 测试，覆盖整数数组、二维整数数组、字符串数组和字符串输出。

最小 Java 验收代码：

```java
import com.google.gson.Gson;

public class Main {
    public static void main(String[] args) {
        Gson gson = new Gson();
        int[] value = gson.fromJson(args[0], int[].class);
        System.out.print(gson.toJson(value));
    }
}
```

最小 C++ 验收代码：

```cpp
#include <iostream>
#include <vector>
#include <nlohmann/json.hpp>

int main(int argc, char* argv[]) {
    auto value = nlohmann::json::parse(argv[1]).get<std::vector<int>>();
    std::cout << nlohmann::json(value).dump();
}
```

请求用例：

```json
{
  "code": "对应语言的验收代码",
  "language": "java 或 cpp",
  "cases": [
    {"args": ["[1,2,3]"]}
  ]
}
```

期望 `outputList`：

```json
["[1,2,3]"]
```
