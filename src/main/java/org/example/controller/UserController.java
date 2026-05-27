package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.ChangePasswordDTO;
import org.example.dto.EmailSendCodeDTO;
import org.example.dto.ResponseResult;
import org.example.dto.UserDTO;
import org.example.dto.UserLoginDTO;
import org.example.service.UserService;
import org.example.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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
     * 发送邮箱验证码
     * POST /api/users/email/send-code
     */
    @PostMapping("/email/send-code")
    public ResponseResult<String> sendEmailCode(@RequestBody EmailSendCodeDTO dto) {
        try {
            String code = userService.sendEmailCode(dto.getEmail());
            // 前端调试场景：返回验证码明文，正式环境建议改为仅发邮件/不回传
            return ResponseResult.success("验证码已生成", code);
        } catch (RuntimeException e) {
            return ResponseResult.error(400, e.getMessage());
        }
    }

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

    /**
     * 上传用户头像
     * POST /api/users/{userId}/avatar
     */
    @PostMapping("/{userId}/avatar")
    public ResponseResult<String> uploadAvatar(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {
        try {
            String avatarUrl = userService.uploadAvatar(userId, file);
            return ResponseResult.success("头像上传成功", avatarUrl);
        } catch (RuntimeException e) {
            return ResponseResult.error(400, e.getMessage());
        }
    }

    /**
     * 修改用户名
     * PUT /api/users/{userId}/username
     */
    @PutMapping("/{userId}/username")
    public ResponseResult<UserDTO> updateUsername(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        try {
            String username = body == null ? null : body.get("username");
            UserDTO user = userService.updateUsername(userId, username);
            user.setPassword(null);
            return ResponseResult.success("用户名修改成功", user);
        } catch (RuntimeException e) {
            return ResponseResult.error(400, e.getMessage());
        }
    }

    /**
     * 获取用户信息
     * GET /api/users/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseResult<UserDTO> getUserById(@PathVariable Long userId) {
        try {
            UserDTO user = userService.getUserById(userId);
            // 不返回密码
            user.setPassword(null);
            return ResponseResult.success(user);
        } catch (RuntimeException e) {
            return ResponseResult.error(404, e.getMessage());
        }
    }

    /**
     * 修改密码
     * PUT /api/users/{userId}/password
     */
    @PutMapping("/{userId}/password")
    public ResponseResult<Void> changePassword(
            @PathVariable Long userId,
            @RequestBody ChangePasswordDTO dto,
            HttpServletRequest request) {
        try {
            Object attr = request.getAttribute("userId");
            if (!(attr instanceof Long tokenUserId) || !tokenUserId.equals(userId)) {
                return ResponseResult.error(403, "无权修改该账号密码");
            }
            if (dto == null) {
                return ResponseResult.error(400, "请求体不能为空");
            }
            userService.changePassword(userId, dto.getOldPassword(), dto.getNewPassword());
            return ResponseResult.success("密码修改成功", null);
        } catch (RuntimeException e) {
            return ResponseResult.error(400, e.getMessage());
        }
    }

}

