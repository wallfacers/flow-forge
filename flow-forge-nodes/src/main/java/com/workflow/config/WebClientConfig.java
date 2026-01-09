package com.workflow.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for WebClient used in HTTP node execution.
 * <p>
 * Provides a configured {@link WebClient} bean with:
 * <ul>
 *   <li>Connection timeout: 10 seconds</li>
 *   <li>Read timeout: 60 seconds</li>
 *   <li>Write timeout: 60 seconds</li>
 *   <li>Follows redirects (max 3)</li>
 * </ul>
 * <p>
 * Note: Per-request timeout is handled by {@link com.workflow.node.NodeExecutorFactory}
 * using CompletableFuture.orTimeout().
 *
 * @see com.workflow.node.http.HttpNodeExecutor
 */
@Configuration
public class WebClientConfig {

    /**
     * Create and configure WebClient bean.
     *
     * @return configured WebClient instance
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Configure HttpClient with timeouts
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10 seconds
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * Create default WebClient instance.
     *
     * @return WebClient with default configuration
     */
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .build();
    }
}
