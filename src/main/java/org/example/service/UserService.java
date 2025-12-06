package org.example.service;

import org.example.dto.UserDTO;
import org.example.dto.UserLoginDTO;
import org.example.vo.UserLoginVO;

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
     * 用户登录
     *
     * @param loginDTO 登录信息
     * @return 登录响应（包含token）
     */
    UserLoginVO login(UserLoginDTO loginDTO);
}

