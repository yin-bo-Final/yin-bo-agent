package com.yinbo.agent.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// 意图识别专用线程池配置，隔离 LLM 分类的阻塞调用。
public class ChatIntentExecutorConfig {

    @Bean(name = "intentClassifyExecutor", destroyMethod = "shutdown")
    // 提供子问题并行分类线程池。
    public ExecutorService intentClassifyExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, processors);
        int maxPoolSize = Math.max(corePoolSize, processors * 2);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                new NamedThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "intent-classify-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
