package org.example.controller;

import org.example.dto.ResponseResult;
import org.example.dto.UserDTO;
import org.example.service.UserService;
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
        // 检查用户名是否已存在
        if (userService.existsByUsername(userDTO.getUsername())) {
            return ResponseResult.error(400, "用户名已存在");
        }
        // 检查手机号是否已存在
        if (userDTO.getPhone() != null && userService.existsByPhone(userDTO.getPhone())) {
            return ResponseResult.error(400, "邮箱已被注册");
        }
        UserDTO created = userService.createUser(userDTO);
        return ResponseResult.success("用户创建成功", created);
    }

    /**
     * 根据ID获取用户
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseResult<String> getUserById(@PathVariable Long id) {
//        UserDTO user = userService.getUserById(id);
//        if (user == null) {
//            return ResponseResult.error(404, "用户不存在");
//        }
        return ResponseResult.success("user");
    }

    /**
     * 根据用户名获取用户
     * GET /api/users/username/{username}
     */
    @GetMapping("/username/{username}")
    public ResponseResult<UserDTO> getUserByUsername(@PathVariable String username) {
        UserDTO user = userService.getUserByUsername(username);
        if (user == null) {
            return ResponseResult.error(404, "用户不存在");
        }
        return ResponseResult.success(user);
    }

    /**
     * 获取所有用户
     * GET /api/users
     */
    @GetMapping
    public ResponseResult<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseResult.success(users);
    }

    /**
     * 更新用户信息
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseResult<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        UserDTO existing = userService.getUserById(id);
        if (existing == null) {
            return ResponseResult.error(404, "用户不存在");
        }
        userDTO.setId(id);
        UserDTO updated = userService.updateUser(id, userDTO);
        return ResponseResult.success("用户更新成功", updated);
    }

    /**
     * 删除用户
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseResult<Void> deleteUser(@PathVariable Long id) {
        UserDTO existing = userService.getUserById(id);
        if (existing == null) {
            return ResponseResult.error(404, "用户不存在");
        }
        userService.deleteUser(id);
        return ResponseResult.success("用户删除成功", null);
    }
}

