package org.example.controller;

import org.example.dto.ResponseResult;
import org.example.dto.UserDTO;
import org.example.dto.UserLoginDTO;
import org.example.service.UserService;
import org.example.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 提供用户的增删改查功能
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 创建用户
     * POST /api/users
     */
    @PostMapping
    public ResponseResult<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        UserDTO created = userService.createUser(userDTO);
        return ResponseResult.success("用户创建成功", created);
    }

    /**
     * 用户登录
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseResult<UserLoginVO> login(@RequestBody UserLoginDTO loginDTO) {
        try {
            UserLoginVO result = userService.login(loginDTO);
            return ResponseResult.success("登录成功", result);
        } catch (RuntimeException e) {
            return ResponseResult.error(401, e.getMessage());
        }
    }

}

