package org.example.service;

import org.example.dto.UserDTO;
import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 创建用户
     */
    UserDTO createUser(UserDTO userDTO);

    /**
     * 更新用户信息
     */
    UserDTO updateUser(Long id, UserDTO userDTO);

    /**
     * 删除用户
     */
    void deleteUser(Long id);

    /**
     * 根据ID获取用户
     */
    UserDTO getUserById(Long id);

    /**
     * 根据用户名获取用户
     */
    UserDTO getUserByUsername(String username);

    /**
     * 获取所有用户
     */
    List<UserDTO> getAllUsers();

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByPhone(String email);
}

