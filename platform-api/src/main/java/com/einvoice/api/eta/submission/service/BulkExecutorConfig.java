package com.einvoice.api.eta.submission.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BulkExecutorConfig {

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService etaBulkStatusPool() {
        return Executors.newFixedThreadPool(8, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r,
                        "eta-bulk-status-pool-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }
}
