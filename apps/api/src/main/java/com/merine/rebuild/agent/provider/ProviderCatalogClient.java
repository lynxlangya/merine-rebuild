package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.DiscoveredModel;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import com.merine.rebuild.agent.provider.dto.ModelCatalog;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 读取供应商的模型清单：OpenAI 兼容的 {@code GET {baseUrl}/models}。
 *
 * 只解析模型标识与归属方，不探测能力（是否支持思考、哪些推理强度都由管理员在使用侧决定，
 * 探测式猜测既慢又容易得出错的结论）。出站约束：仅 HTTPS（由服务层校验地址）、禁止跟随重定向、
 * 连接与请求都有超时，并拒绝本机与内网 IP 字面量目标；上游响应原文只用于判定，不回传前端。
 */
@Component
final class ProviderCatalogClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /** 上游模型清单的条数上限，避免异常响应把页面撑爆。 */
    private static final int MAX_MODELS = 300;

    private final ObjectMapper json;
    private final HttpClient http;

    ProviderCatalogClient(ObjectMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    ModelCatalog fetch(Vendor vendor, String baseUrl, String apiKey) {
        requirePublicTarget(URI.create(baseUrl));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timeout) {
            throw unavailable("模型服务响应超时，请稍后重试");
        } catch (IOException error) {
            throw unavailable("无法连接模型服务，请检查请求地址与网络");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable("获取模型列表已中断，请重试");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            String message = "模型服务拒绝了这次请求，请检查 API Key 是否正确";
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_LIST_AUTH_FAILED", message,
                    List.of(new ApiResponse.FieldError("apiKey", message)));
        }
        if (response.statusCode() == 404) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_LIST_UNAVAILABLE",
                    "该地址没有 /models 接口，请核对请求地址，或手动维护模型列表");
        }
        if (response.statusCode() == 429) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MODEL_LIST_RATE_LIMITED",
                    "模型服务限流，请稍后再试");
        }
        if (response.statusCode() >= 400) {
            throw unavailable("模型服务返回错误（HTTP " + response.statusCode() + "），请稍后重试");
        }
        return parse(vendor, json, response.body());
    }

    /**
     * 解析 OpenAI 兼容的 /models 响应；无法识别的结构按上游协议错误处理。
     * 能力信息（推理强度）不在上游响应里，按供应商目录补齐，界面据此展示可选档位。
     */
    static ModelCatalog parse(Vendor vendor, ObjectMapper json, String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (RuntimeException error) {
            throw protocolError();
        }
        JsonNode data = root == null ? null : root.path("data");
        if (data == null || !data.isArray()) throw protocolError();
        Set<String> seen = new LinkedHashSet<>();
        List<DiscoveredModel> models = new ArrayList<>();
        for (JsonNode item : data) {
            String modelId = item.path("id").asString("").trim();
            if (modelId.isEmpty() || !seen.add(modelId)) continue;
            String ownedBy = item.path("owned_by").asString("").trim();
            models.add(new DiscoveredModel(modelId, ownedBy.isEmpty() ? null : ownedBy,
                    ReasoningEffortCatalog.effortsFor(vendor, modelId)));
            if (models.size() >= MAX_MODELS) break;
        }
        if (models.isEmpty()) throw protocolError();
        return new ModelCatalog(List.copyOf(models));
    }

    /** 只拦明显的本机与内网 IP 字面量；域名不做解析，自建网关走域名属于合法用法。 */
    private static void requirePublicTarget(URI uri) {
        String host = uri.getHost();
        if (host == null) throw blocked();
        if (host.equalsIgnoreCase("localhost") || host.endsWith(".localhost")) throw blocked();
        if (!isIpLiteral(host)) return;
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();
            boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || uniqueLocal) {
                throw blocked();
            }
        } catch (IOException error) {
            throw blocked();
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    }

    private static ApiException blocked() {
        String message = "请求地址不能是本机或内网地址";
        return new ApiException(HttpStatus.BAD_REQUEST, "MODEL_LIST_BLOCKED_TARGET", message,
                List.of(new ApiResponse.FieldError("baseUrl", message)));
    }

    private static ApiException protocolError() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_LIST_PROTOCOL_ERROR",
                "模型服务返回了无法识别的模型清单");
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_LIST_UNAVAILABLE", message);
    }
}
