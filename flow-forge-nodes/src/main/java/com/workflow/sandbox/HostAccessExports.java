package com.workflow.sandbox;

import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 安全的主机访问导出方法。
 * <p>
 * 这些方法可以被沙箱中的脚本安全地调用。
 * 只导出经过验证的安全功能，不暴露任何危险操作。
 * <p>
 * 可用方法：
 * <ul>
 *   <li>{@code log(message)} - 记录日志</li>
 *   <li>{@code error(message)} - 记录错误</li>
 *   <li>{@code sleep(ms)} - 休眠指定毫秒数</li>
 *   <li>{@code jsonStringify(obj)} - 序列化为JSON</li>
 *   <li>{@code jsonParse(str)} - 解析JSON字符串</li>
 *   <li>{@code base64Encode(str)} - Base64编码</li>
 *   <li>{@code base64Decode(str)} - Base64解码</li>
 * </ul>
 */
public class HostAccessExports {

    private static final Logger logger = LoggerFactory.getLogger(HostAccessExports.class);

    private final StringBuilder outputBuffer = new StringBuilder();

    /**
     * 记录日志消息。
     * <p>
     * 脚本中调用: {@code __host.log("message")}
     *
     * @param message 日志消息
     */
    @HostAccess.Export
    public void log(String message) {
        if (message != null) {
            logger.info("[Script] {}", message);
            outputBuffer.append("[LOG] ").append(message).append("\n");
        }
    }

    /**
     * 记录错误消息。
     * <p>
     * 脚本中调用: {@code __host.error("error message")}
     *
     * @param message 错误消息
     */
    @HostAccess.Export
    public void error(String message) {
        if (message != null) {
            logger.error("[Script] {}", message);
            outputBuffer.append("[ERROR] ").append(message).append("\n");
        }
    }

    /**
     * 休眠指定毫秒数。
     * <p>
     * 脚本中调用: {@code __host.sleep(1000)}
     *
     * @param ms 休眠毫秒数
     */
    @HostAccess.Export
    public void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取当前时间戳（毫秒）。
     * <p>
     * 脚本中调用: {@code __host.now()}
     *
     * @return 当前时间戳
     */
    @HostAccess.Export
    public long now() {
        return System.currentTimeMillis();
    }

    /**
     * JSON序列化。
     * <p>
     * 脚本中调用: {@code __host.jsonStringify(obj)}
     *
     * @param obj 要序列化的对象
     * @return JSON字符串
     */
    @HostAccess.Export
    public String jsonStringify(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        // 简化处理：其他类型返回toString
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    /**
     * JSON解析。
     * <p>
     * 脚本中调用: {@code __host.jsonParse(str)}
     *
     * @param str JSON字符串
     * @return 解析后的对象
     */
    @HostAccess.Export
    public Object jsonParse(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        str = str.trim();
        if ("null".equals(str)) {
            return null;
        }
        if ("true".equals(str)) {
            return true;
        }
        if ("false".equals(str)) {
            return false;
        }
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return unescapeJson(str.substring(1, str.length() - 1));
        }
        // 尝试解析数字
        try {
            if (str.contains(".")) {
                return Double.parseDouble(str);
            }
            long value = Long.parseLong(str);
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        } catch (NumberFormatException e) {
            // 忽略，返回原始字符串
        }
        return str;
    }

    /**
     * Base64编码。
     * <p>
     * 脚本中调用: {@code __host.base64Encode(str)}
     *
     * @param str 要编码的字符串
     * @return Base64编码后的字符串
     */
    @HostAccess.Export
    public String base64Encode(String str) {
        if (str == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64解码。
     * <p>
     * 脚本中调用: {@code __host.base64Decode(str)}
     *
     * @param str Base64编码的字符串
     * @return 解码后的原始字符串
     */
    @HostAccess.Export
    public String base64Decode(String str) {
        if (str == null) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(str);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * 获取捕获的输出。
     *
     * @return 捕获的输出字符串
     */
    public String getOutput() {
        return outputBuffer.toString();
    }

    /**
     * 清空输出缓冲区。
     */
    public void clearOutput() {
        outputBuffer.setLength(0);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
