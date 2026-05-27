package org.example.service.impl;

import org.apache.commons.io.FilenameUtils;
import org.example.dto.UserDTO;
import org.example.dto.UserLoginDTO;
import org.example.mapper.UserMapper;
import org.example.service.EmailService;
import org.example.service.UserService;
import org.example.util.JwtUtil;
import org.example.vo.UserLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EmailService emailService;

    @Value("${avatar.upload.path:./static/avatars}")
    private String avatarUploadPath;

    @Value("${avatar.upload.allowed-types:image/jpeg,image/png,image/gif,image/webp}")
    private String allowedTypes;

    // 邮箱验证码有效期（秒）
    private static final long EMAIL_CODE_TTL_SECONDS = 300;

    private final SecureRandom secureRandom = new SecureRandom();

    private String buildEmailCodeKey(String email) {
        return "email:code:" + email.toLowerCase();
    }

    private String generateEmailCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private void verifyEmailCode(String email, String code) {
        if (email == null || code == null || !code.matches("^\\d{6}$")) {
            throw new RuntimeException("验证码格式不正确");
        }
        String key = buildEmailCodeKey(email);
        key = Objects.requireNonNull(key, "redis key");
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached == null) {
            throw new RuntimeException("验证码已过期或不存在");
        }
        if (!cached.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        // 不再主动删除验证码，让其在过期后自动失效
    }

    /**
     * 创建用户
     */
    @Override
    public UserDTO createUser(UserDTO userDTO) {
        if (userDTO.getEmail() == null || userDTO.getEmail().isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (userDTO.getCode() == null || userDTO.getCode().isBlank()) {
            throw new RuntimeException("验证码不能为空");
        }

        String email = userDTO.getEmail().trim();
        String code = userDTO.getCode().trim();
        verifyEmailCode(email, code);

        // 检查用户名是否已存在
        String username = (userDTO.getUsername() == null || userDTO.getUsername().isBlank())
                ? email
                : userDTO.getUsername().trim();
        userDTO.setUsername(username);

        if (userMapper.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userMapper.existsByEmail(email)) {
            throw new RuntimeException("该邮箱已注册");
        }

        // 如果用户没有设置密码，则使用默认密码123456
        String rawPassword = userDTO.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) {
            rawPassword = "123456";
        }
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(rawPassword);
        userDTO.setPassword(encodedPassword);
        userDTO.setEmail(email);

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
        if (loginDTO.getEmail() == null || loginDTO.getEmail().isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (loginDTO.getPassword() == null || loginDTO.getPassword().isBlank()) {
            throw new RuntimeException("密码不能为空");
        }
        String email = loginDTO.getEmail().trim();
        String password = loginDTO.getPassword().trim();
        // 根据邮箱查询用户
        UserDTO user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new RuntimeException("请先注册");
        }
        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        // 生成JWT令牌
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        // 构建并返回登录响应
        return new UserLoginVO(token, user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 生成并写入 Redis 邮箱验证码，同时发送邮件
     */
    @Override
    public String sendEmailCode(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        String trimmed = email.trim();
        // 简单邮箱校验
        if (!trimmed.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new RuntimeException("请输入正确的邮箱地址");
        }

        // 检查邮箱是否已存在
        if (userMapper.existsByEmail(trimmed)) {
            throw new RuntimeException("该邮箱已注册");
        }

        String code = generateEmailCode();
        code = Objects.requireNonNull(code, "verification code");
        String key = buildEmailCodeKey(trimmed);
        key = Objects.requireNonNull(key, "redis key");
        stringRedisTemplate.opsForValue().set(key, code, EMAIL_CODE_TTL_SECONDS, TimeUnit.SECONDS);
        
        // 发送邮件验证码
        boolean sent = emailService.sendVerificationCode(trimmed, code);
        if (!sent) {
            // 发送失败，删除已生成的验证码
            stringRedisTemplate.delete(key);
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
        
        return "验证码已发送至您的邮箱，请查收";
    }

    /**
     * 上传用户头像
     */
    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        // 验证用户是否存在
        UserDTO user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证文件是否为空
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择要上传的头像文件");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        List<String> allowedTypeList = Arrays.asList(allowedTypes.split(","));
        if (contentType == null || !allowedTypeList.contains(contentType)) {
            throw new RuntimeException("不支持的文件类型，仅支持: " + allowedTypes);
        }

        // 验证文件大小（5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("文件大小不能超过5MB");
        }

        try {
            // 创建存储目录
            Path uploadDir = Paths.get(avatarUploadPath).toAbsolutePath();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = FilenameUtils.getExtension(originalFilename);
            String newFilename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

            // 保存文件
            Path filePath = uploadDir.resolve(newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 生成访问URL
            String avatarUrl = "/api/avatars/" + newFilename;

            // 删除旧头像文件（如果存在）
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String oldFilename = user.getAvatar().replace("/api/avatars/", "");
                Path oldFilePath = uploadDir.resolve(oldFilename);
                Files.deleteIfExists(oldFilePath);
            }

            // 更新数据库
            userMapper.updateAvatar(userId, avatarUrl);

            return avatarUrl;
        } catch (IOException e) {
            throw new RuntimeException("头像上传失败: " + e.getMessage());
        }
    }

    /**
     * 修改用户名
     */
    @Override
    public UserDTO updateUsername(Long userId, String username) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }

        String trimmed = username.trim();
        if (trimmed.length() < 2 || trimmed.length() > 32) {
            throw new RuntimeException("用户名长度需在2-32个字符之间");
        }

        UserDTO user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (trimmed.equals(user.getUsername())) {
            return user;
        }

        if (userMapper.existsByUsername(trimmed)) {
            throw new RuntimeException("用户名已存在");
        }

        int result = userMapper.updateUsername(userId, trimmed);
        if (result <= 0) {
            throw new RuntimeException("修改用户名失败");
        }

        return userMapper.selectById(userId);
    }

    /**
     * 获取用户信息
     */
    @Override
    public UserDTO getUserById(Long userId) {
        UserDTO user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    /**
     * 修改密码
     */
    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new RuntimeException("请输入当前密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("请输入新密码");
        }
        String trimmedNew = newPassword.trim();
        if (trimmedNew.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }
        if (trimmedNew.length() > 128) {
            throw new RuntimeException("新密码长度不能超过128位");
        }
        if (trimmedNew.equals(oldPassword)) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }

        UserDTO user = userMapper.selectByIdWithPassword(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(oldPassword.trim(), user.getPassword())) {
            throw new RuntimeException("当前密码错误");
        }

        String encoded = passwordEncoder.encode(trimmedNew);
        int updated = userMapper.updatePasswordHash(userId, encoded);
        if (updated <= 0) {
            throw new RuntimeException("修改密码失败");
        }
    }
}

