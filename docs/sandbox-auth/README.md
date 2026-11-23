# 沙箱调用鉴权（ECDSA 请求签名）

后端用私钥给每个请求签名，沙箱用公钥验签，再配合时间窗和 nonce 去重，防止伪造、篡改和重放。沙箱只持有公钥，不持有任何秘密。

## 协议

- 请求头：`X-Sandbox-Key-Id`、`X-Sandbox-Timestamp`、`X-Sandbox-Nonce`、`X-Sandbox-Signature`。
- 被签名的字符串：`timestamp + "\n" + nonce + "\n" + sha256Hex(body)`。
  - `timestamp`：epoch **毫秒**。
  - `nonce`：16 字节随机数，Base64 URL-safe、无 padding。
  - `sha256Hex(body)`：请求体 UTF-8 字节的 SHA-256，小写十六进制。
- 签名算法：`SHA256withECDSA`（secp256r1 曲线），签名做 Base64 编码。
- 沙箱验签顺序，任何一步失败都返回 401：请求头齐全 → key-id 匹配 → 时间戳在 ±`clock-skew` 秒内 → nonce 没用过 → 验签通过。
- `/health` 不需要鉴权。

实现：后端 `judge/codesandbox/auth/SandboxAuthSigner`（装配见 `config/SandboxAuthConfig`）；沙箱 `auth/SandboxAuthFilter`。协议常量两边必须一致。

## 密钥配置

| 环境 | 后端（私钥） | 沙箱（公钥） |
|---|---|---|
| 开发 | `application-dev.yml` 内置开发专用密钥对，key-id `sandbox-key-dev-2026` | `application-dev.yml` 内置对应公钥 |
| 生产 | 环境变量 `CODESANDBOX_AUTH_KEY_ID`、`CODESANDBOX_AUTH_PRIVATE_KEY`（见 `deploy/.env.example`），缺失时启动失败 | `application-prod.yml` 里的生产公钥，key-id `sandbox-key-prod-2026` |

开发私钥是公开的，只能用于本机开发；生产沙箱只信任生产公钥，开发私钥在生产无效。两边的 `clock-skew` 默认 300 秒，服务器需要校准时间（NTP）。

## 生成密钥对

运行后端测试目录下的 `SandboxAuthKeygenTool`（IDE 里直接运行 main 方法），或在项目根目录执行：

```bash
java src/test/java/com/hjl/oj/judge/codesandbox/auth/SandboxAuthKeygenTool.java
```

输出 Base64 编码的私钥（PKCS#8）和公钥（X.509）：私钥给后端，公钥给沙箱，两边的 key-id 要一致。

## 密钥轮换

沙箱同一时间只信任一个 key-id，所以两端需要一起切换，切换期间的判题请求会失败。请在空闲时段操作：

1. 生成新密钥对和新 key-id。
2. 沙箱：更新 `application-prod.yml` 的 key-id 和公钥，重新打包部署。
3. 后端：更新服务器 `deploy/.env` 的 `CODESANDBOX_AUTH_KEY_ID` 和 `CODESANDBOX_AUTH_PRIVATE_KEY`，重启后端容器。
4. 提交一次代码，确认判题正常、沙箱日志没有 401。

私钥泄露时立即按上面的步骤换新密钥，旧私钥随即失效。

## 边界

- 请求体是明文传输（内容不是秘密）；需要防嗅探时改用 HTTPS，与签名方案互不影响。
- 响应方向没有签名。
- nonce 缓存在沙箱内存中，沙箱重启后的时间窗内理论上可以重放一次已发出的请求，判题场景可以接受。
