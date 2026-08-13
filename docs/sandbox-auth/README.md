# 沙箱调用鉴权（ECDSA 请求签名）接入指南

从「请求头带静态密钥」升级为 ECDSA 请求签名：后端持私钥签名，沙箱持公钥验签，
配合时间窗与 nonce 去重实现防伪造、防篡改、防重放。两端均无第三方依赖。

## 协议

- 请求头：`X-Sandbox-Key-Id` / `X-Sandbox-Timestamp` / `X-Sandbox-Nonce` / `X-Sandbox-Signature`
- 规范化字符串（被签名的内容）：`timestamp + "\n" + nonce + "\n" + sha256Hex(body)`
  - `timestamp`：epoch 毫秒；`nonce`：16 字节随机数（Base64 URL-safe 无 padding）
  - `sha256Hex(body)`：请求体按 UTF-8 字节算 SHA-256 的小写十六进制串
- 签名算法：`SHA256withECDSA`（secp256r1 曲线），签名 Base64 编码
- 验签顺序（任一失败返回 401）：头齐全 → key-id 匹配 → 时间窗 ±300s → nonce 未重复 → 验签

## 后端（oj-backend）

- 签名器：`judge.codesandbox.auth.SandboxAuthSigner`，装配：`config.SandboxAuthConfig`
- 配置：
  ```yaml
  codesandbox:
    auth:
      key-id: sandbox-key-dev-2026          # 与沙箱侧保持一致
      private-key: <Base64 PKCS#8>          # 生产用环境变量 CODESANDBOX_AUTH_PRIVATE_KEY 注入
  ```
- 开发环境已内置测试密钥对（见 `application-dev.yml`），生产密钥缺失时启动即失败（fail-fast）
- 生成新密钥：运行 `src/test/java/.../judge/codesandbox/auth/SandboxAuthKeygenTool.java` 的 main 方法

## 沙箱（oj-code-sandbox）

- 验签过滤器：`com.hjl.ojcodesandbox.auth.SandboxAuthFilter`（本目录 Java 文件为同步副本）
- 鉴权统一由 Filter 处理，Controller 不做静态密钥检查；`/health` 探活端点放行（install.sh 健康检查、监控探针依赖）
- Filter 初始化用 `InitializingBean#afterPropertiesSet`（不用 `@PostConstruct` 注解：Spring Boot 2.7 fat jar 下嵌入式 Tomcat 实例化 Filter 时会扫描该注解并报 Invalid annotation）
- 沙箱配置（`application.yml`）：
  ```yaml
  codesandbox:
    auth:
      enabled: false                 # 过渡开关：后端切换为签名调用后一起打开
      key-id: sandbox-key-dev-2026   # 与后端一致
      public-key: MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEujoSoLNpNzIcqf6diJhgPPccfqroPqBFTwxm51jt9cb+JmO8/LLsTgUqRkcLA7Xf3SEpl3TUrzYNdv7K0Nv07w==
      clock-skew: 300                # 秒，两端需 NTP 校时
  ```
  上面这份公钥与后端 `application-dev.yml` 里的开发私钥配套，生产公钥由后端提供后替换。
- 自测：`enabled: true` 时无鉴权头调用 `/executeCode` 返回 401，带签名头（由后端发起）正常执行。沙箱侧单测 `SandboxAuthFilterTest` 用后端开发私钥构造签名，覆盖验签通过、缺头、未知 key-id、时间戳超窗、nonce 重放、请求体/签名篡改、health 放行、开关关闭放行各分支。

## 部署顺序（重要）

后端与沙箱必须同批启用，否则旧沙箱不认识新头 / 新沙箱拒绝旧请求。
推荐步骤：沙箱以 `enabled: false` 部署（放行所有请求）→ 后端部署签名版本（新头发出，沙箱放行不校验）→ 约定时刻沙箱 `enabled: true` 并确认后端配置生效。

## 密钥轮换

1. 用 `SandboxAuthKeygenTool` 生成新密钥对
2. 沙箱配置里新旧 key-id 双持（key-id 用于区分）
3. 后端切到新私钥与新 key-id，观察无 401
4. 稳定后沙箱删除旧公钥

应急泄露：立即生成新密钥对，沙箱只配新公钥、后端切新私钥，旧私钥即刻作废。

## 边界说明

- 请求体仍为明文传输（内容不是秘密）；如需防嗅探，升级 HTTPS/TLS，与本方案正交
- 响应方向不验签，若需防判题结果被篡改可给沙箱也配一对密钥反向签名（二期加固）
- nonce 缓存为内存态，沙箱重启后 10 分钟窗口内存在极小重放窗口，判题场景可接受
