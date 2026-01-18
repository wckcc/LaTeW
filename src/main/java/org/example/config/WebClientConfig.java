package org.example.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient配置类
 * 用于HTTP客户端调用（如AI服务）
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // 配置 HttpClient 使用系统默认的 DNS 解析器
        // 而不是 Netty 默认的 DNS 解析器（会使用 1.1.1.1 等公共 DNS，在国内可能超时）
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)  // 使用 JVM/系统的 DNS 解析
                .responseTimeout(Duration.ofSeconds(120));       // 设置响应超时时间
        
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}

