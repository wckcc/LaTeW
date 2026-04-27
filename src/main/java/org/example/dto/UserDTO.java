package org.example.dto;

import lombok.Data;

/**
 * 用户数据传输对象
 */
@Data
public class UserDTO {
    private Long id;

    private String username;

    /**
     * 这里沿用数据库字段 phone 存储邮箱（当前项目未做表结构迁移）。
     * 前端/接口对外统一叫 email。
     */
    private String email;

    /**
     * 注册接口请求用：邮箱验证码
     * 注意：用户信息查询时该字段一般为 null。
     */
    private String code;

    private String password;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户角色（如 user/admin）
     */
    private String role;
}

