package org.example.service;

import org.example.dto.UserDTO;
import org.example.dto.UserLoginDTO;
import org.example.vo.UserLoginVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 创建用户
     */
    UserDTO createUser(UserDTO userDTO);

    /**
     * 生成并发送邮箱验证码（验证码会写入 Redis，用于后续注册/登录校验）
     *
     * @param email 邮箱
     * @return 成功消息
     */
    String sendEmailCode(String email);

    /**
     * 用户登录
     *
     * @param loginDTO 登录信息
     * @return 登录响应（包含token）
     */
    UserLoginVO login(UserLoginDTO loginDTO);

    /**
     * 上传用户头像
     *
     * @param userId 用户ID
     * @param file   头像文件
     * @return 头像访问URL
     */
    String uploadAvatar(Long userId, MultipartFile file);

    /**
     * 修改用户名
     *
     * @param userId 用户ID
     * @param username 新用户名
     * @return 更新后的用户信息
     */
    UserDTO updateUsername(Long userId, String username);

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserDTO getUserById(Long userId);

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 当前密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
}

