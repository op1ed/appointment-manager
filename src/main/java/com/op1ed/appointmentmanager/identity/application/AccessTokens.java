package com.op1ed.appointmentmanager.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 登录令牌工具：生成原始令牌、检查字符串格式、计算用于数据库查找的散列。
 *
 * <p>沿着 IdentityService.login 来读 generate 和 hash，再回到登录服务看令牌如何保存。
 * 当前令牌是随机字符串；对应的用户和过期时间保存在数据库中，字符串自身没有这些业务字段。
 */
public final class AccessTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    // 只有静态方法，调用 AccessTokens.generate() 即可，私有构造方法防止创建工具类实例。
    private AccessTokens() {
    }

    /** 创建交给客户端保存的原始令牌。 */
    public static String generate() {
        byte[] bytes = new byte[32];
        // 使用适合生成安全随机值的 SecureRandom 填满 32 字节。
        RANDOM.nextBytes(bytes);
        // 转为 URL 安全的 Base64 字符串并去掉填充符，得到 43 个字符。
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 3B 会用到：这里只检查形状；是否存在、是否过期还要查询数据库。 */
    public static boolean isValidFormat(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    /** 同一个原始令牌得到同一个散列，用于保存和查找令牌记录。 */
    public static String hash(String token) {
        try {
            // SHA-256 结果为 32 字节，转十六进制后为 64 个字符，与数据库 token_hash 字段对应。
            // 这是令牌查找用的散列；账号密码的散列由 PasswordEncoder 单独处理。
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
