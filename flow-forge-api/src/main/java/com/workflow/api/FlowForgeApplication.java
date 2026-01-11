package com.workflow.api;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.PrintStream;

/**
 * Flow-Forge DAG工作流引擎启动类
 * <p>
 * 企业级DAG工作流引擎，支持：
 * <ul>
 *   <li>HTTP、Log、Script、IF、Merge、Webhook、Wait节点类型</li>
 *   <li>变量解析 ({{}} 语法)</li>
 *   <li>检查点恢复与重试策略</li>
 *   <li>多租户隔离</li>
 *   <li>执行历史可视化</li>
 * </ul>
 * </p>
 *
 * <p>启动方式：</p>
 * <pre>
 * java -jar flow-forge-api.jar
 * </pre>
 *
 * <p>访问 Swagger 文档：</p>
 * <pre>
 * http://localhost:8080/swagger-ui.html
 * </pre>
 */
@SpringBootApplication(scanBasePackages = "com.workflow")
@EntityScan(basePackages = "com.workflow.infra.entity")
@EnableJpaRepositories(basePackages = "com.workflow.infra.repository")
public class FlowForgeApplication {

    private static final String BANNER =
            "\n" +
            "========================================\n" +
            "  _   _        _  _   \n" +
            " | \\ | | ___  \\| |/ ___\n" +
            " |  \\| |/ _ \\    | |  _  \\\n" +
            " | |\\  | (_) |   | | |_| |\n" +
            " |_| \\_|\\___/    |_|\\___/\n" +
            "       DAG Workflow Engine\n" +
            "========================================\n";

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FlowForgeApplication.class);
        app.setBanner(new Banner() {
            @Override
            public void printBanner(org.springframework.core.env.Environment environment,
                                    Class<?> sourceClass,
                                    PrintStream out) {
                out.print(BANNER);
            }
        });
        app.run(args);
    }
}
