package com.javalearning.frameworks;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 知识点 7-7：Spring Security / Shiro 安全框架。
 *
 * <p>迷你实现安全框架的四个支柱：过滤器链（请求依次经过认证、授权过滤器）、
 * 密码加盐哈希（PBKDF2，对应 BCryptPasswordEncoder）、无状态 Token（HMAC 签名的简化 JWT）、
 * 基于角色/权限的访问控制（对应 @PreAuthorize / authorizeHttpRequests）。
 */
public class SecurityFilterChainDemo {

    record Request(String path, Map<String, String> headers) { }
    record Response(int status, String body) { }
    record Authentication(String username, Set<String> roles) { }

    /** SecurityContextHolder：线程绑定的当前认证信息。 */
    static final ThreadLocal<Authentication> SECURITY_CONTEXT = new ThreadLocal<>();

    interface Filter {
        Response doFilter(Request req, FilterChain chain);
    }

    static class FilterChain {
        private final List<Filter> filters;
        private final java.util.function.Function<Request, Response> servlet;
        private int index = 0;

        FilterChain(List<Filter> filters, java.util.function.Function<Request, Response> servlet) {
            this.filters = filters;
            this.servlet = servlet;
        }

        Response proceed(Request req) {
            if (index < filters.size()) return filters.get(index++).doFilter(req, this);
            return servlet.apply(req);
        }
    }

    // ---------- 密码编码器：PasswordEncoder ----------
    static class Pbkdf2PasswordEncoder {
        String encode(String raw) throws Exception {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return Base64.getEncoder().encodeToString(salt) + "$" + hash(raw, salt);
        }

        boolean matches(String raw, String encoded) throws Exception {
            String[] parts = encoded.split("\\$");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            return java.security.MessageDigest.isEqual(hash(raw, salt).getBytes(), parts[1].getBytes()); // 常量时间比较防时序攻击
        }

        private static String hash(String raw, byte[] salt) throws Exception {
            KeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, 65_536, 256);
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        }
    }

    // ---------- 简化 JWT：header.payload.signature ----------
    static class TokenService {
        private final byte[] secret = "a-very-long-server-side-secret-key".getBytes(StandardCharsets.UTF_8);

        String issue(Authentication auth) throws Exception {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (auth.username() + "|" + String.join(",", auth.roles()) + "|" + (System.currentTimeMillis() + 60_000)).getBytes(StandardCharsets.UTF_8));
            return payload + "." + sign(payload);
        }

        Authentication verify(String token) throws Exception {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) return null; // 签名不匹配 -> 被篡改
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|");
            if (Long.parseLong(fields[2]) < System.currentTimeMillis()) return null; // 过期
            return new Authentication(fields[0], Set.of(fields[1].split(",")));
        }

        private String sign(String data) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        }
    }

    public static void main(String[] args) throws Exception {
        Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder();
        TokenService tokens = new TokenService();

        System.out.println("== 用户存储（UserDetailsService）==");
        Map<String, String> passwordTable = new HashMap<>();
        Map<String, Set<String>> roleTable = Map.of("alice", Set.of("ADMIN", "USER"), "bob", Set.of("USER"));
        passwordTable.put("alice", encoder.encode("alice123"));
        passwordTable.put("bob", encoder.encode("bob123"));
        System.out.println("  alice 的密码存储为: " + passwordTable.get("alice").substring(0, 30) + "...（盐 + 哈希，无法反推明文，相同密码哈希也不同）");
        System.out.println("  校验 alice123: " + encoder.matches("alice123", passwordTable.get("alice")) + "，校验 wrong: " + encoder.matches("wrong", passwordTable.get("alice")));

        System.out.println("\n== 登录签发 Token ==");
        String aliceToken = tokens.issue(new Authentication("alice", roleTable.get("alice")));
        String bobToken = tokens.issue(new Authentication("bob", roleTable.get("bob")));
        System.out.println("  alice token: " + aliceToken.substring(0, 40) + "...");
        // 改动 payload 的第一个字符（相当于改了用户名的编码），签名随即失效
        String tampered = (aliceToken.charAt(0) == 'A' ? 'B' : 'A') + aliceToken.substring(1);
        System.out.println("  篡改 payload 后验证结果: " + tokens.verify(tampered) + "（签名失效）");

        // ---------- 过滤器链 ----------
        Filter authenticationFilter = (req, chain) -> {
            String header = req.headers().get("Authorization");
            try {
                if (header != null && header.startsWith("Bearer ")) {
                    Authentication auth = tokens.verify(header.substring(7));
                    if (auth == null) return new Response(401, "token 无效或过期");
                    SECURITY_CONTEXT.set(auth);
                    System.out.println("  [认证过滤器] 已认证 " + auth.username() + " 角色 " + auth.roles());
                }
                return chain.proceed(req);
            } catch (Exception e) {
                return new Response(401, e.getMessage());
            } finally {
                SECURITY_CONTEXT.remove(); // 请求结束清理 ThreadLocal
            }
        };

        Map<String, String> requiredRole = Map.of("/admin", "ADMIN", "/orders", "USER"); // authorizeHttpRequests
        Filter authorizationFilter = (req, chain) -> {
            String role = requiredRole.entrySet().stream().filter(e -> req.path().startsWith(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
            if (role == null) return chain.proceed(req);            // permitAll
            Authentication auth = SECURITY_CONTEXT.get();
            if (auth == null) return new Response(401, "需要登录");  // 未认证 -> 401
            if (!auth.roles().contains(role)) return new Response(403, "需要角色 " + role); // 已认证但无权限 -> 403
            return chain.proceed(req);
        };

        List<Filter> filters = List.of(authenticationFilter, authorizationFilter);
        java.util.function.Function<Request, Response> controller = req -> new Response(200, "OK " + req.path() + " by " + (SECURITY_CONTEXT.get() == null ? "匿名" : SECURITY_CONTEXT.get().username()));

        System.out.println("\n== 请求通过过滤器链 ==");
        Request[] requests = {
                new Request("/public", Map.of()),
                new Request("/orders/1", Map.of()),
                new Request("/orders/1", Map.of("Authorization", "Bearer " + bobToken)),
                new Request("/admin/users", Map.of("Authorization", "Bearer " + bobToken)),
                new Request("/admin/users", Map.of("Authorization", "Bearer " + aliceToken)),
                new Request("/admin/users", Map.of("Authorization", "Bearer " + tampered)),
        };
        for (Request req : requests) {
            Response resp = new FilterChain(filters, controller).proceed(req);
            System.out.println("  " + req.path() + (req.headers().isEmpty() ? "" : " (带 token)") + " -> " + resp.status() + " " + resp.body());
        }

        System.out.println("""

                Spring Security 架构：
                  DelegatingFilterProxy -> FilterChainProxy -> SecurityFilterChain（十余个 Filter 有序排列）
                  关键过滤器：SecurityContextHolderFilter、UsernamePasswordAuthenticationFilter / BearerTokenAuthenticationFilter、
                             ExceptionTranslationFilter（401/403 转换）、AuthorizationFilter（最后做授权）
                  认证：AuthenticationManager -> ProviderManager -> AuthenticationProvider（DaoAuthenticationProvider 用 UserDetailsService + PasswordEncoder）
                  授权：authorizeHttpRequests 的 URL 规则 + 方法级 @PreAuthorize("hasRole('ADMIN')") / @Secured
                  SecurityContextHolder 默认 ThreadLocal 策略（虚拟线程下可切换 InheritableThreadLocal 或显式传递）
                密码：永远使用自适应慢哈希（BCrypt / SCrypt / Argon2 / PBKDF2），DelegatingPasswordEncoder 前缀 {bcrypt} 支持算法升级
                会话 vs Token：传统 Session（服务端状态，CSRF 防护必要）；JWT 无状态（易水平扩展，但无法主动失效，需短期 + Refresh Token 或黑名单）
                OAuth2 / OIDC：授权码模式 + PKCE 是现代标准，Spring Authorization Server / Keycloak 作授权服务器
                Shiro：更轻量，Subject / SecurityManager / Realm 三件套，适合非 Spring 或简单权限场景
                """);
    }
}
