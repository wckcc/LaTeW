package org.example.service.impl;

import org.example.dto.UserDTO;
import org.example.mapper.UserMapper;
import org.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现类
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDTO createUser(UserDTO userDTO) {
        // 检查用户名是否已存在
        if (userMapper.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userDTO.getEmail() != null && userMapper.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("邮箱已被使用");
        }

        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        userDTO.setCreatedAt(now);
        userDTO.setUpdatedAt(now);

        // 设置默认角色
        if (userDTO.getRole() == null || userDTO.getRole().isEmpty()) {
            userDTO.setRole("USER");
        }

        // 插入用户
        int result = userMapper.insert(userDTO);
        if (result > 0) {
            return userDTO;
        }
        throw new RuntimeException("创建用户失败");
    }

    @Override
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        // 检查用户是否存在
        UserDTO existing = userMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }

        // 如果更新用户名，检查新用户名是否已被其他用户使用
        if (userDTO.getUsername() != null && !userDTO.getUsername().equals(existing.getUsername())) {
            if (userMapper.existsByUsername(userDTO.getUsername())) {
                throw new RuntimeException("用户名已被使用");
            }
        }

        // 如果更新邮箱，检查新邮箱是否已被其他用户使用
        if (userDTO.getEmail() != null && !userDTO.getEmail().equals(existing.getEmail())) {
            if (userMapper.existsByEmail(userDTO.getEmail())) {
                throw new RuntimeException("邮箱已被使用");
            }
        }

        // 设置ID和更新时间
        userDTO.setId(id);
        userDTO.setUpdatedAt(LocalDateTime.now());

        // 更新用户
        int result = userMapper.updateById(userDTO);
        if (result > 0) {
            return userMapper.selectById(id);
        }
        throw new RuntimeException("更新用户失败");
    }

    @Override
    public void deleteUser(Long id) {
        // 检查用户是否存在
        UserDTO existing = userMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("用户不存在");
        }

        // 删除用户
        int result = userMapper.deleteById(id);
        if (result <= 0) {
            throw new RuntimeException("删除用户失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userMapper.selectAll();
    }

    /**
     * 检查用户名是否存在
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }
}

