package com.policyinsight.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(WorkerConfig.class);

    @Value("${policyinsight.worker.enabled:false}")
    private boolean workerEnabled;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Worker enabled = {}", workerEnabled);
    }
}

