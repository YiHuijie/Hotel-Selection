package edu.polyu.comp5511.hotelselection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;
import edu.polyu.comp5511.hotelselection.domain.Hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AmapMcpDistanceProvider implements DistanceProvider {
    private static final Logger logger = LoggerFactory.getLogger(AmapMcpDistanceProvider.class);
    private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";
    private static final Map<String, String> ROUTE_TOOLS = Map.of(
            "walking", "maps_direction_walking",
            "driving", "maps_direction_driving",
            "transit", "maps_direction_transit_integrated",
            "cycling", "maps_bicycling");

    private final String apiKey;
    private final URI endpoint;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AmapMcpDistanceProvider(
            @Value("${hotel.maps.amap.api-key:}") String apiKey,
            @Value("${hotel.maps.amap.endpoint:https://mcp.amap.com/mcp}") String endpoint,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.endpoint = URI.create(endpoint);
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DistanceEstimate> getRoutes(
            String origin,
            String city,
            List<String> transportModes,
            List<Hotel> hotels) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new DistanceProviderException("未配置高德 MCP API key。");
        }
        URI authenticatedEndpoint = UriComponentsBuilder.fromUri(endpoint)
                .queryParam("key", apiKey)
                .build()
                .encode()
                .toUri();
        McpSession session = new McpSession(authenticatedEndpoint);
        try {
            session.initialize();
            String originLocation = geocode(session, origin, city);
            List<DistanceEstimate> routes = new ArrayList<>();
            for (Hotel hotel : hotels) {
                if (hotel.longitude() == null || hotel.latitude() == null) {
                    throw new DistanceProviderException(
                            "候选酒店 " + hotel.id() + " 缺少经纬度，无法准确规划路线。");
                }
                String destination = hotel.longitude().toPlainString() + "," + hotel.latitude().toPlainString();
                for (String mode : transportModes) {
                    String tool = ROUTE_TOOLS.get(mode);
                    if (tool == null) {
                        throw new DistanceProviderException("不支持的路线交通方式：" + mode);
                    }
                    Map<String, Object> arguments = new LinkedHashMap<>();
                    arguments.put("origin", originLocation);
                    arguments.put("destination", destination);
                    if ("transit".equals(mode)) {
                        arguments.put("city", city);
                        arguments.put("cityd", city);
                    }
                    routes.add(parseRoute(hotel.id(), mode, session.callTool(tool, arguments)));
                }
            }
            return List.copyOf(routes);
        } catch (DistanceProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DistanceProviderException("高德 MCP 请求或响应处理失败。", exception);
        } finally {
            session.close();
        }
    }

    private String geocode(McpSession session, String address, String city) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("address", address);
        if (city != null && !city.isBlank()) {
            arguments.put("city", city);
        }
        JsonNode response = session.callTool("maps_geo", arguments);
        JsonNode location = response.findValue("location");
        if (location == null || !location.isTextual() || !location.textValue().contains(",")) {
            throw new DistanceProviderException("高德未能将用户起点解析为经纬度。");
        }
        return location.textValue();
    }

    private DistanceEstimate parseRoute(long hotelId, String mode, JsonNode response) {
        JsonNode route = findRoute(response);
        if (route == null) {
            throw new DistanceProviderException("高德未返回 " + mode + " 路线数据。");
        }
        double distanceMeters = numericValue(route.get("distance"), "distance", mode);
        double durationSeconds = numericValue(route.get("duration"), "duration", mode);
        List<String> steps = new ArrayList<>();
        collectInstructions(route, steps);
        return new DistanceEstimate(hotelId, mode, distanceMeters, durationSeconds, steps);
    }

    private JsonNode findRoute(JsonNode node) {
        if (node == null) {
            return null;
        }
        for (String collection : List.of("paths", "transits")) {
            JsonNode choices = node.get(collection);
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                if (first.has("distance") && first.has("duration")) {
                    return first;
                }
            }
        }
        if (node.has("distance") && node.has("duration")) {
            return node;
        }
        for (JsonNode child : node) {
            JsonNode result = findRoute(child);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private double numericValue(JsonNode value, String field, String mode) {
        if (value == null || value.isNull()) {
            throw new DistanceProviderException("高德 " + mode + " 路线缺少 " + field + "。");
        }
        try {
            double result = value.isNumber() ? value.doubleValue() : Double.parseDouble(value.asText());
            if (!Double.isFinite(result) || result < 0) {
                throw new NumberFormatException("not a non-negative finite number");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new DistanceProviderException("高德 " + mode + " 路线的 " + field + " 无效。", exception);
        }
    }

    private void collectInstructions(JsonNode node, List<String> instructions) {
        if (instructions.size() >= 30 || node == null) {
            return;
        }
        JsonNode instruction = node.get("instruction");
        if (instruction != null && instruction.isTextual() && !instruction.asText().isBlank()) {
            instructions.add(instruction.asText());
        }
        node.elements().forEachRemaining(child -> collectInstructions(child, instructions));
    }

    private JsonNode parseMcpBody(String body) {
        try {
            String jsonBody = body;
            if (!body.stripLeading().startsWith("{")) {
                List<String> dataLines = body.lines()
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring("data:".length()).strip())
                        .toList();
                if (dataLines.isEmpty()) {
                    throw new DistanceProviderException("高德 MCP 返回了无法读取的响应。");
                }
                jsonBody = String.join("\n", dataLines);
            }
            return objectMapper.readTree(jsonBody);
        } catch (DistanceProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DistanceProviderException("高德 MCP 返回了无效 JSON。", exception);
        }
    }

    private String resultText(JsonNode result) {
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                if (item.path("text").isTextual()) {
                    return item.path("text").asText();
                }
            }
        }
        return null;
    }

    private String redact(String message) {
        if (message == null) {
            return "未知错误";
        }
        return apiKey == null || apiKey.isBlank()
                ? message
                : message.replace(apiKey, "[redacted]");
    }

    private final class McpSession {
        private final URI authenticatedEndpoint;
        private final AtomicLong requestIds = new AtomicLong();
        private String sessionId;
        private String protocolVersion = DEFAULT_PROTOCOL_VERSION;

        private McpSession(URI authenticatedEndpoint) {
            this.authenticatedEndpoint = authenticatedEndpoint;
        }

        private void initialize() {
            JsonNode response = request("initialize", Map.of(
                    "protocolVersion", DEFAULT_PROTOCOL_VERSION,
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "hotel-selection-agent", "version", "1.0.0")));
            JsonNode version = response.path("result").path("protocolVersion");
            if (version.isTextual()) {
                protocolVersion = version.asText();
            }
            notifyServer("notifications/initialized", Map.of());
        }

        private JsonNode callTool(String name, Map<String, Object> arguments) {
            JsonNode response = request("tools/call", Map.of("name", name, "arguments", arguments));
            JsonNode result = response.path("result");
            if (result.path("isError").asBoolean(false)) {
                throw new DistanceProviderException(
                        "高德 MCP 工具 " + name + " 返回错误：" + redact(resultText(result)));
            }
            JsonNode structured = result.get("structuredContent");
            if (structured != null && !structured.isNull()) {
                return structured;
            }
            String text = resultText(result);
            if (text == null) {
                throw new DistanceProviderException("高德 MCP 工具 " + name + " 未返回结构化数据。");
            }
            try {
                return objectMapper.readTree(text);
            } catch (Exception exception) {
                throw new DistanceProviderException(
                        "高德 MCP 工具 " + name + " 返回了无法解析的 JSON 文本。", exception);
            }
        }

        private void notifyServer(String method, Map<String, Object> params) {
            exchange(Map.of("jsonrpc", "2.0", "method", method, "params", params), false);
        }

        private JsonNode request(String method, Map<String, Object> params) {
            JsonNode response = exchange(Map.of(
                    "jsonrpc", "2.0",
                    "id", requestIds.incrementAndGet(),
                    "method", method,
                    "params", params), true);
            JsonNode error = response.get("error");
            if (error != null && !error.isNull()) {
                throw new DistanceProviderException(
                        "高德 MCP " + method + " 调用失败：" + redact(error.path("message").asText()));
            }
            return response;
        }

        private JsonNode exchange(Map<String, Object> message, boolean expectsResponse) {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(authenticatedEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .body(message);
            if (sessionId != null) {
                request.header("Mcp-Session-Id", sessionId);
                request.header("MCP-Protocol-Version", protocolVersion);
            }
            McpHttpResponse response = request.exchange((httpRequest, clientResponse) -> {
                HttpStatusCode status = clientResponse.getStatusCode();
                String body = new String(clientResponse.getBody().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String responseSessionId = clientResponse.getHeaders().getFirst("Mcp-Session-Id");
                if (!status.is2xxSuccessful()) {
                    throw new DistanceProviderException("高德 MCP 返回 HTTP " + status.value() + ".");
                }
                return new McpHttpResponse(body, responseSessionId);
            });
            if (response.sessionId() != null) {
                sessionId = response.sessionId();
            }
            if (!expectsResponse) {
                return null;
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new DistanceProviderException("高德 MCP 未返回请求结果。");
            }
            JsonNode json = parseMcpBody(response.body());
            if (json.path("error").isObject()) {
                throw new DistanceProviderException(
                        "高德 MCP 调用失败：" + redact(json.path("error").path("message").asText()));
            }
            return json;
        }

        private void close() {
            if (sessionId == null) {
                return;
            }
            try {
                restClient.delete()
                        .uri(authenticatedEndpoint)
                        .header("Mcp-Session-Id", sessionId)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException exception) {
                logger.warn("Unable to close the AMap MCP session cleanly.");
            }
        }
    }

    private record McpHttpResponse(String body, String sessionId) {
    }
}
