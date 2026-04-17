package org.example.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录数据传输对象
 */
@Data
public class UserLoginDTO {
    /**
     * 邮箱（作为登录标识）
     */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}

