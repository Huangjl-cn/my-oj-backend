package com.hjl.ojcodesandbox.auth;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 沙箱调用鉴权过滤器，与 oj-backend 的 {@code SandboxAuthSigner} 配套的验签端。
 *
 * <p>校验顺序：
 * <ol>
 *     <li>四个请求头齐全</li>
 *     <li>key-id 与配置一致</li>
 *     <li>timestamp 落在 ±clock-skew 时间窗内</li>
 *     <li>nonce 在本服务未被使用过（内存缓存，超窗自动清理）</li>
 *     <li>重算请求体 SHA-256、重建规范化字符串，ECDSA 公钥验签</li>
 * </ol>
 * 任何一步失败返回 401。规范化字符串协议为 {@code timestamp \n nonce \n sha256Hex(body)}，
 * 与后端 {@code SandboxAuthSigner.buildCanonicalString} 严格一致。
 *
 * <p>Java 8 / Spring Boot 2.7（javax.servlet）兼容，无第三方依赖，
 * 由 Spring Boot 自动注册 Filter Bean。
 */
@Component
public class SandboxAuthFilter implements Filter, InitializingBean {

    private static final String HEADER_KEY_ID = "X-Sandbox-Key-Id";
    private static final String HEADER_TIMESTAMP = "X-Sandbox-Timestamp";
    private static final String HEADER_NONCE = "X-Sandbox-Nonce";
    private static final String HEADER_SIGNATURE = "X-Sandbox-Signature";

    /** 探活端点，无需鉴权。 */
    private static final String HEALTH_PATH = "/health";

    /** 过渡开关：后端切换为签名调用后置为 true（两侧同批切换），false 期间放行所有请求。 */
    @Value("${codesandbox.auth.enabled:true}")
    private boolean enabled;

    @Value("${codesandbox.auth.key-id}")
    private String keyId;

    @Value("${codesandbox.auth.public-key}")
    private String publicKeyConfig;

    /** 时间窗（秒），两端需要 NTP 校时，默认 ±5 分钟。 */
    @Value("${codesandbox.auth.clock-skew:300}")
    private long clockSkewSeconds;

    private PublicKey publicKey;

    /** nonce → 首次出现时间，仅缓存时间窗内的条目，窗口外自动清理。 */
    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    /** 用 InitializingBean 而非 @PostConstruct：嵌入式 Tomcat 实例化 Filter 时会对注解做扫描，javax.annotation 在 fat jar 类加载下会报错。 */
    @Override
    public void afterPropertiesSet() throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(stripPemArmor(publicKeyConfig).replaceAll("\\s+", ""));
        this.publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        // 探活端点放行（install.sh 健康检查、监控探针依赖）
        if (HEALTH_PATH.equals(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // 请求体流只能消费一次，包一层缓存住原始字节，下游 @RequestBody 从缓存流中读取
        BodyCachingRequestWrapper wrappedRequest = new BodyCachingRequestWrapper(httpRequest);
        String body = new String(wrappedRequest.getBody(), StandardCharsets.UTF_8);

        long now = System.currentTimeMillis();
        long windowMillis = clockSkewSeconds * 1000L;

        String requestKeyId = httpRequest.getHeader(HEADER_KEY_ID);
        String timestampHeader = httpRequest.getHeader(HEADER_TIMESTAMP);
        String nonce = httpRequest.getHeader(HEADER_NONCE);
        String signatureHeader = httpRequest.getHeader(HEADER_SIGNATURE);

        if (requestKeyId == null || timestampHeader == null || nonce == null || signatureHeader == null) {
            reject(httpResponse, "missing auth header");
            return;
        }
        if (!keyId.equals(requestKeyId)) {
            reject(httpResponse, "unknown key id");
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            reject(httpResponse, "invalid timestamp");
            return;
        }
        // 用一对比较避免极端值下 Math.abs 溢出；时间窗只是廉价前置过滤，最终防线是验签
        if (timestamp < now - windowMillis || timestamp > now + windowMillis) {
            reject(httpResponse, "expired request");
            return;
        }
        pruneExpiredNonces(now, windowMillis);
        if (nonceCache.putIfAbsent(nonce, now) != null) {
            reject(httpResponse, "replayed request");
            return;
        }

        String canonical = timestamp + "\n" + nonce + "\n" + sha256Hex(body);
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(canonical.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getDecoder().decode(signatureHeader))) {
                reject(httpResponse, "invalid signature");
                return;
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            reject(httpResponse, "invalid signature");
            return;
        }

        chain.doFilter(wrappedRequest, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"unauthorized: " + message + "\"}");
    }

    private void pruneExpiredNonces(long now, long windowMillis) {
        Iterator<Map.Entry<String, Long>> iterator = nonceCache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() > windowMillis) {
                iterator.remove();
            }
        }
    }

    private static String sha256Hex(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static String stripPemArmor(String encodedKey) {
        String trimmed = encodedKey == null ? "" : encodedKey.trim();
        if (trimmed.startsWith("-----BEGIN")) {
            int headerEnd = trimmed.indexOf('\n');
            int footerStart = trimmed.indexOf("-----END");
            if (headerEnd < 0 || footerStart < 0) {
                throw new IllegalArgumentException("PEM 格式不完整");
            }
            return trimmed.substring(headerEnd + 1, footerStart);
        }
        return trimmed;
    }

    /** 缓存请求体字节，使 Filter 与下游 @RequestBody 都能读取。 */
    private static class BodyCachingRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        BodyCachingRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = readAll(request.getInputStream());
        }

        byte[] getBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 同步读取，无需监听器
                }

                @Override
                public int read() {
                    return byteStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private static byte[] readAll(ServletInputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
