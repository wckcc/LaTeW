package org.example.config;

import org.example.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web配置类
 * 配置拦截器、跨域和静态资源
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    @NonNull
    private JwtInterceptor jwtInterceptor;
    
    @Value("${latex.compile.output-dir:./static/pdf}")
    private String pdfOutputDir;
    
    @Value("${avatar.upload.path:./static/avatars}")
    private String avatarUploadPath;

    /**
     * 添加拦截器
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns(
                        "/api/users/login",      // 登录接口
                        "/api/users",            // 注册接口（创建用户）
                        "/api/users/email/**", // 邮箱验证码接口
                        "/api/pdf/**",           // PDF静态资源
                        "/api/avatars/**",       // 头像静态资源
                        "/error",                // 错误页面
                        "/swagger-ui/**",        // Swagger UI（如果使用）
                        "/v3/api-docs/**"        // Swagger文档（如果使用）
                );
    }

    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
    
    /**
     * 静态资源映射
     * 配置PDF文件和头像文件的访问路径
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // PDF文件映射
        String pdfPath = Paths.get(pdfOutputDir).toAbsolutePath().toString().replace("\\", "/");
        registry.addResourceHandler("/api/pdf/**")
                .addResourceLocations("file:" + pdfPath + "/");
        
        // 头像文件映射
        String avatarPath = Paths.get(avatarUploadPath).toAbsolutePath().toString().replace("\\", "/");
        registry.addResourceHandler("/api/avatars/**")
                .addResourceLocations("file:" + avatarPath + "/");
    }
}

