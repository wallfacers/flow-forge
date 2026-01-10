package com.workflow.node.http;

import com.workflow.context.VariableResolver;
import com.workflow.model.WorkflowException;
import com.workflow.model.ExecutionContext;
import com.workflow.model.Node;
import com.workflow.model.NodeResult;
import com.workflow.model.NodeType;
import com.workflow.node.AbstractNodeExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP node executor.
 * <p>
 * Executes HTTP requests and returns the response status, headers, and body.
 * Supports GET, POST, PUT, DELETE methods with custom headers and request body.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "url": "https://api.example.com/users/{{input.userId}}",
 *   "method": "GET",
 *   "headers": {"Authorization": "Bearer {{global.apiKey}}"},
 *   "body": "{\"name\": \"{{input.userName}}\"}"
 * }
 * </pre>
 * <p>
 * Output format:
 * <pre>
 * {
 *   "status": 200,
 *   "headers": {"Content-Type": "application/json"},
 *   "body": "{\"id\": 123, \"name\": \"John\"}"
 * }
 * </pre>
 */
@Component
public class HttpNodeExecutor extends AbstractNodeExecutor {

    private static final String DEFAULT_METHOD = "GET";
    private static final String DEFAULT_URL = "";
    private static final String DEFAULT_BODY = "";

    private final WebClient webClient;

    public HttpNodeExecutor(VariableResolver variableResolver, WebClient webClient) {
        super(variableResolver);
        this.webClient = webClient;
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.HTTP;
    }

    @Override
    protected NodeResult doExecute(Node node,
                                   ExecutionContext context,
                                   Map<String, Object> resolvedConfig) {
        String nodeId = node.getId();

        // Extract config values
        String url = getConfigString(resolvedConfig, "url", DEFAULT_URL);
        String method = getConfigString(resolvedConfig, "method", DEFAULT_METHOD).toUpperCase();
        String body = getConfigString(resolvedConfig, "body", DEFAULT_BODY);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) resolvedConfig.get("headers");

        // Validate required config
        if (url == null || url.isEmpty()) {
            return NodeResult.failure(nodeId, "URL is required for HTTP node");
        }

        logger.debug("Executing HTTP request: {} {} (node: {})", method, url, nodeId);

        try {
            // Build request
            WebClient.RequestHeadersSpec<?> request = buildRequest(method, url, body, headers);

            // Execute request and capture full response (status, headers, body)
            // Use toEntity() to get ResponseEntity with all metadata
            ResponseEntity<String> response = request.retrieve()
                    .toEntity(String.class)
                    .block(Duration.ofSeconds(node.getTimeout() / 1000 + 10));

            // Build output with actual response details
            Map<String, Object> output = new HashMap<>();
            output.put("status", response.getStatusCode().value());
            output.put("headers", response.getHeaders().toSingleValueMap());
            output.put("body", response.getBody() != null ? response.getBody() : "");

            // Return success for 2xx, failure for others
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("HTTP request completed: {} {} -> {} (node: {})",
                        method, url, response.getStatusCode(), nodeId);
                return NodeResult.success(nodeId, output);
            } else {
                logger.warn("HTTP request returned non-2xx status: {} {} -> {} (node: {})",
                        method, url, response.getStatusCode(), nodeId);
                return NodeResult.failure(nodeId,
                        "HTTP request failed with status " + response.getStatusCode().value());
            }

        } catch (WebClientResponseException e) {
            // Handle HTTP errors with response details
            Map<String, Object> output = new HashMap<>();
            output.put("status", e.getStatusCode().value());
            output.put("headers", e.getHeaders().toSingleValueMap());
            output.put("body", e.getResponseBodyAsString());

            // Return failure with error details
            logger.warn("HTTP request failed with status {}: {} (node: {})",
                    e.getStatusCode().value(), url, nodeId);
            return NodeResult.failure(nodeId,
                    "HTTP request failed with status " + e.getStatusCode().value());

        } catch (Exception e) {
            logger.error("HTTP request execution failed: {} {} (node: {})", method, url, nodeId, e);
            return NodeResult.failure(nodeId, "HTTP request failed: " + e.getMessage());
        }
    }

    /**
     * Build the WebClient request based on method, URL, body, and headers.
     *
     * @param method  HTTP method (GET, POST, PUT, DELETE)
     * @param url     request URL
     * @param body    request body (for POST/PUT)
     * @param headers request headers
     * @return configured WebClient request
     */
    private WebClient.RequestHeadersSpec<?> buildRequest(String method,
                                                          String url,
                                                          String body,
                                                          Map<String, String> headers) {
        // Check if we need a body
        boolean hasBody = (method.equals("POST") || method.equals("PUT")) &&
                body != null && !body.isEmpty();

        // Start building request
        WebClient.RequestBodyUriSpec uriSpec = webClient.method(
                org.springframework.http.HttpMethod.valueOf(method)
        );

        // Build URI
        WebClient.RequestBodySpec bodySpec = uriSpec.uri(url);

        // Build headers
        WebClient.RequestHeadersSpec<?> headersSpec = bodySpec;
        if (headers != null && !headers.isEmpty()) {
            headersSpec = bodySpec.headers(h -> headers.forEach(h::add));
        }

        // Build body
        if (hasBody) {
            return bodySpec.body(BodyInserters.fromValue(body));
        }

        return headersSpec;
    }

    @Override
    protected Map<String, Object> resolveConfig(Node node, ExecutionContext context) {
        Map<String, Object> resolved = super.resolveConfig(node, context);

        // Special handling for headers - need to resolve values within the map
        Object headersObj = resolved.get("headers");
        if (headersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = new HashMap<>((Map<String, Object>) headersObj);
            // Resolve each header value
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (entry.getValue() instanceof String) {
                    String resolvedValue = variableResolver.resolve(
                            (String) entry.getValue(), context);
                    entry.setValue(resolvedValue);
                }
            }
            resolved.put("headers", headers);
        }

        // Resolve body string separately for better control
        Object bodyObj = resolved.get("body");
        if (bodyObj instanceof String bodyStr) {
            String resolvedBody = variableResolver.resolve(bodyStr, context);
            resolved.put("body", resolvedBody);
        }

        return resolved;
    }
}
