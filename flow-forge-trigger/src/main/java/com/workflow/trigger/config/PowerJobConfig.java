package com.workflow.trigger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PowerJob 配置属性。
 * <p>
 * 配置 PowerJob Server 连接信息和应用名称。
 */
@Configuration
@ConfigurationProperties(prefix = "powerjob.worker")
public class PowerJobConfig {

    /**
     * PowerJob Server 地址
     */
    private String serverAddress = "127.0.0.1:7700";

    /**
     * 应用名称
     */
    private String appName = "flow-forge";

    /**
     * 是否启用 PowerJob
     */
    private boolean enabled = false;

    /**
     * 端口（可选，默认随机）
     */
    private Integer port;

    /**
     * Worker 最大工作线程数
     */
    private int maxWorkerThread = 4;

    /**
     * 超时时间（秒）
     */
    private int timeout = 60;

    // Getters and Setters
    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public int getMaxWorkerThread() {
        return maxWorkerThread;
    }

    public void setMaxWorkerThread(int maxWorkerThread) {
        this.maxWorkerThread = maxWorkerThread;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
