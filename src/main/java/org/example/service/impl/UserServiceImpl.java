package org.example.service.impl;

import org.example.dto.UserDTO;
import org.example.dto.UserLoginDTO;
import org.example.mapper.UserMapper;
import org.example.service.UserService;
import org.example.util.JwtUtil;
import org.example.vo.UserLoginVO;
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

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 创建用户
     */
    @Override
    public UserDTO createUser(UserDTO userDTO) {
        // 检查用户名是否已存在
        if (userMapper.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查手机号是否已存在
        if (userDTO.getPhone() != null && userMapper.existsByPhone(userDTO.getPhone())) {
            throw new RuntimeException("手机号已被使用");
        }

        // 设置默认密码为123456
        userDTO.setPassword("123456");

        // 插入用户
        int result = userMapper.insert(userDTO);
        if (result > 0) {
            return userDTO;
        }
        throw new RuntimeException("创建用户失败");
    }

    /**
     * 用户登录
     */
    @Override
    public UserLoginVO login(UserLoginDTO loginDTO) {
        // 根据用户名查询用户
        UserDTO user = userMapper.selectByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 验证密码（简单字符串比较，实际生产环境应使用加密密码）
        if (!loginDTO.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成JWT令牌
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        // 构建并返回登录响应
        return new UserLoginVO(token, user.getId(), user.getUsername());
    }
}

