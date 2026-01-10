package com.workflow.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger配置
 * <p>
 * 配置API文档信息，访问 /swagger-ui.html 查看文档
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI基本信息配置
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flow-Forge DAG工作流引擎 API")
                        .description("企业级DAG工作流引擎REST API文档\n\n" +
                                "## 功能特性\n" +
                                "- **DAG工作流定义**: 支持JSON DSL定义复杂工作流\n" +
                                "- **节点类型**: HTTP、Log、Script、IF、Merge、Webhook、Wait\n" +
                                "- **执行历史**: 查询工作流执行记录和节点状态\n" +
                                "- **可视化**: 获取DAG图数据用于前端渲染\n" +
                                "- **多租户**: 支持租户隔离的数据访问\n\n" +
                                "## 认证\n" +
                                "所有API需要在请求头中提供租户ID: `X-Tenant-ID`\n\n" +
                                "## 状态码\n" +
                                "- `200`: 成功\n" +
                                "- `400`: 请求参数错误\n" +
                                "- `404`: 资源不存在\n" +
                                "- `500`: 服务器内部错误")
                        .version("1.0.0-SNAPSHOT")
                        .contact(new Contact()
                                .name("Flow-Forge Team")
                                .email("support@workflow.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发环境"),
                        new Server().url("/").description("当前环境")));
    }

    /**
     * 执行历史API分组
     */
    @Bean
    public GroupedOpenApi executionApi() {
        return GroupedOpenApi.builder()
                .group("execution-history")
                .pathsToMatch("/api/executions/**")
                .build();
    }

    /**
     * 工作流定义API分组（待实现）
     */
    @Bean
    public GroupedOpenApi workflowApi() {
        return GroupedOpenApi.builder()
                .group("workflow-definitions")
                .pathsToMatch("/api/workflows/**")
                .build();
    }

    /**
     * 触发器API分组（待实现）
     */
    @Bean
    public GroupedOpenApi triggerApi() {
        return GroupedOpenApi.builder()
                .group("triggers")
                .pathsToMatch("/api/triggers/**", "/api/webhook/**")
                .build();
    }

    /**
     * Webhook触发API分组（待实现）
     */
    @Bean
    public GroupedOpenApi webhookApi() {
        return GroupedOpenApi.builder()
                .group("webhooks")
                .pathsToMatch("/api/webhooks/**")
                .build();
    }
}
