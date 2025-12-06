package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户数据传输对象
 */
@Data
public class UserDTO {
    private Long id;

    private String username;

    private String phone;

    private String password;
}

