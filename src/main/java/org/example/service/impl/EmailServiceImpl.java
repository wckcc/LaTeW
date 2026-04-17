package org.example.service.impl;

import org.example.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 */
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${spring.mail.properties.mail.smtp.nickname:LaTeW}")
    private String nickname;

    /**
     * 发送验证码邮件
     */
    @Override
    public boolean sendVerificationCode(String email, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(nickname + " <" + from + ">");
            message.setTo(email);
            message.setSubject("LaTeW 验证码");
            message.setText("您的验证码是: " + code + "\n\n" +
                    "该验证码有效期为5分钟，请尽快使用。\n\n" +
                    "如果您没有申请验证码，请忽略此邮件。");
            
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            // 记录日志，返回发送失败
            e.printStackTrace();
            return false;
        }
    }
}
