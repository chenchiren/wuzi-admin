package com.relex.wuzi.ai.config;

import com.relex.wuzi.ai.llm.LlmProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 智能助手模块装配：SSE 专用线程池（Tomcat worker 立即释放，长连接不占请求线程）。
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AiModuleConfig {

    @Bean(name = "aiSseExecutor")
    public ExecutorService aiSseExecutor() {
        return new ThreadPoolExecutor(
                4, 16, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32),
                r -> {
                    Thread t = new Thread(r, "ai-sse");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
