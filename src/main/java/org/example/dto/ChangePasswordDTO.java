package org.example.dto;

import lombok.Data;

/**
 * 修改密码请求
 */
@Data
public class ChangePasswordDTO {
    private String oldPassword;
    private String newPassword;
}
