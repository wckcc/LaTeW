package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送邮箱验证码请求
 */
@Data
public class EmailSendCodeDTO {
    @NotBlank(message = "邮箱不能为空")
    private String email;
}

