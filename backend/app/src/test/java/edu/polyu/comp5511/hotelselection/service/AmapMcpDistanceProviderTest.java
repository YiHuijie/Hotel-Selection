package edu.polyu.comp5511.hotelselection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;
import edu.polyu.comp5511.hotelselection.domain.Hotel;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AmapMcpDistanceProviderTest {
    private static final String ENDPOINT = "https://mcp.amap.com/mcp?key=test-key";

    @Test
    void initializesMcpAndReturnsOnlyProviderSuppliedRouteValues() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2024-11-05","capabilities":{},
                          "clientInfo":{"name":"hotel-selection-agent","version":"1.0.0"}
                        }}
                        """))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}
                        """, MediaType.APPLICATION_JSON).header("Mcp-Session-Id", "test-session"));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "test-session"))
                .andExpect(content().json("""
                        {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "test-session"))
                .andExpect(content().json("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/call",
                         "params":{"name":"maps_geo","arguments":{"address":"West Lake","city":"Hangzhou"}}}
                        """))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text",
                         "text":"{\\"geocodes\\":[{\\"location\\":\\"120.1,30.2\\"}]}"}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "test-session"))
                .andExpect(content().json("""
                        {"jsonrpc":"2.0","id":3,"method":"tools/call",
                         "params":{"name":"maps_direction_walking",
                           "arguments":{"origin":"120.1,30.2","destination":"120.3,30.4"}}}
                        """))
                .andRespond(withSuccess("""
                        event: message
                        data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text",
                        data: "text":"{\\"route\\":{\\"paths\\":[{\\"distance\\":\\"1234\\",\\"duration\\":\\"600\\",\\"steps\\":[{\\"instruction\\":\\"Walk north\\"}]}]}}"}]}}

                        """, MediaType.TEXT_EVENT_STREAM));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Mcp-Session-Id", "test-session"))
                .andRespond(withSuccess());

        AmapMcpDistanceProvider provider = new AmapMcpDistanceProvider(
                "test-key", "https://mcp.amap.com/mcp", builder, new ObjectMapper());
        List<DistanceEstimate> routes = provider.getRoutes(
                "West Lake", "Hangzhou", List.of("walking"), List.of(hotelWithCoordinates()));

        assertEquals(1, routes.size());
        assertEquals("walking", routes.get(0).transportMode());
        assertEquals(1234, routes.get(0).distanceMeters());
        assertEquals(600, routes.get(0).durationSeconds());
        assertEquals(List.of("Walk north"), routes.get(0).routeSteps());
        server.verify();
    }

    @Test
    void failsClearlyWhenApiKeyIsNotConfigured() {
        AmapMcpDistanceProvider provider = new AmapMcpDistanceProvider(
                "", "https://mcp.amap.com/mcp", RestClient.builder(), new ObjectMapper());

        DistanceProviderException exception = assertThrows(DistanceProviderException.class,
                () -> provider.getRoutes("West Lake", "Hangzhou",
                        List.of("walking"), List.of(hotelWithCoordinates())));

        assertEquals("未配置高德 MCP API key。", exception.getMessage());
    }

    private Hotel hotelWithCoordinates() {
        return new Hotel(1, "Hotel", "Hangzhou", "Hotel Road", 4.5,
                new BigDecimal("200"), false, "", null,
                new BigDecimal("120.3"), new BigDecimal("30.4"), null,
                "Zhejiang", "Xihu", 2010, 2020, null,
                false, false, false, false, false, false, false, false, false);
    }
}
