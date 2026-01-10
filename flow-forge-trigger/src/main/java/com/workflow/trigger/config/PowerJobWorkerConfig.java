package com.workflow.trigger.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.powerjob.worker.PowerJobWorker;

/**
 * PowerJob Worker 配置。
 * <p>
 * 配置 PowerJob Worker 连接到 Server，并注册处理器。
 */
@Configuration
@ConditionalOnProperty(prefix = "powerjob.worker", name = "enabled", havingValue = "true")
public class PowerJobWorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(PowerJobWorkerConfig.class);

    private final PowerJobConfig powerJobConfig;

    public PowerJobWorkerConfig(PowerJobConfig powerJobConfig) {
        this.powerJobConfig = powerJobConfig;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public PowerJobWorker powerJobWorker() {
        log.info("Initializing PowerJob Worker: appName={}, serverAddress={}",
                powerJobConfig.getAppName(), powerJobConfig.getServerAddress());

        tech.powerjob.worker.common.PowerJobWorkerConfig config = new tech.powerjob.worker.common.PowerJobWorkerConfig();
        config.setServerAddress(java.util.Collections.singletonList(powerJobConfig.getServerAddress()));
        config.setAppName(powerJobConfig.getAppName());
        // Port is optional, let PowerJob auto-assign
        // MaxWorkerThread and timeout are configured differently in PowerJob 5.x

        PowerJobWorker worker = new PowerJobWorker(config);
        log.info("PowerJob Worker initialized successfully");
        return worker;
    }
}
