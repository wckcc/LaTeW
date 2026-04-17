package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置类
 */
@Configuration
public class PasswordConfig {

    /**
     * 配置BCrypt密码编码器
     * BCrypt是一种安全的密码哈希算法，具有以下特点：
     * 1. 内置盐值，防止彩虹表攻击
     * 2. 可调节的成本因子（默认为10）
     * 3. 单向不可逆
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
