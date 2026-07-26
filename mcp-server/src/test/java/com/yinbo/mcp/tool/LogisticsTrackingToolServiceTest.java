package com.yinbo.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.yinbo.mcp.logistics.LogisticsEvent;
import com.yinbo.mcp.logistics.LogisticsProvider;
import com.yinbo.mcp.logistics.LogisticsProviderException;
import com.yinbo.mcp.logistics.LogisticsQueryRequest;
import com.yinbo.mcp.logistics.LogisticsQueryResult;
import com.yinbo.mcp.tool.dto.McpToolCallRequest;
import com.yinbo.mcp.tool.dto.McpToolCallResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogisticsTrackingToolServiceTest {

    @Test
    void asksForTrackingNoWhenQueryDoesNotContainOne() {
        LogisticsTrackingToolService service = new LogisticsTrackingToolService(request -> {
            throw new AssertionError("provider should not be called");
        });

        McpToolCallResponse response = service.call(new McpToolCallRequest(
                "快递到哪了",
                "conv-1",
                1L,
                Map.of()
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.needClarification()).isTrue();
        assertThat(response.message()).contains("快递单号");
    }

    @Test
    void returnsProviderTrackingDataWhenTrackingNoExists() {
        LogisticsTrackingToolService service = new LogisticsTrackingToolService(request -> new LogisticsQueryResult(
                request.trackingNo(),
                "yuantong",
                "圆通速递",
                "0",
                "在途",
                false,
                "郑州转运中心",
                "2026-06-13 12:05:00",
                "2026-06-14 18",
                List.of(
                        new LogisticsEvent("2026-06-13 12:05:00", "正在发往收件城市", "郑州转运中心", "在途"),
                        new LogisticsEvent("2026-06-13 10:20:00", "到达郑州转运中心", "郑州转运中心", "在途")
                )
        ));

        McpToolCallResponse response = service.call(new McpToolCallRequest(
                "帮我查一下 YT1234567890 的快递",
                "conv-1",
                1L,
                Map.of()
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.needClarification()).isFalse();
        assertThat(response.message()).contains("YT1234567890", "郑州转运中心", "圆通速递");
        assertThat(response.data()).containsEntry("status", "在途");
        assertThat(response.data()).containsEntry("carrierCode", "yuantong");
    }

    @Test
    void asksForPhoneWhenProviderNeedsClarification() {
        LogisticsTrackingToolService service = new LogisticsTrackingToolService(request -> {
            throw LogisticsProviderException.clarification("该快递公司查询需要收/寄件手机号或后四位，请补充手机号后再查。");
        });

        McpToolCallResponse response = service.call(new McpToolCallRequest(
                "帮我查一下 SF1234567890 的快递",
                "conv-1",
                1L,
                Map.of()
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.needClarification()).isTrue();
        assertThat(response.message()).contains("手机号");
    }

    @Test
    void doesNotTreatPhoneAsTrackingNo() {
        LogisticsTrackingToolService service = new LogisticsTrackingToolService(request -> {
            throw new AssertionError("provider should not be called");
        });

        McpToolCallResponse response = service.call(new McpToolCallRequest(
                "手机号 13800138000，帮我查快递",
                "conv-1",
                1L,
                Map.of()
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.needClarification()).isTrue();
        assertThat(response.message()).contains("快递单号");
    }

    @Test
    void passesArgumentsToProvider() {
        CapturingProvider provider = new CapturingProvider();
        LogisticsTrackingToolService service = new LogisticsTrackingToolService(provider);

        service.call(new McpToolCallRequest(
                "帮我查一下包裹",
                "conv-1",
                1L,
                Map.of(
                        "trackingNo", "yt1234567890",
                        "carrierCode", "yuantong",
                        "phone", "13800138000"
                )
        ));

        assertThat(provider.request.trackingNo()).isEqualTo("YT1234567890");
        assertThat(provider.request.carrierCode()).isEqualTo("yuantong");
        assertThat(provider.request.phone()).isEqualTo("13800138000");
    }

    private static class CapturingProvider implements LogisticsProvider {

        private LogisticsQueryRequest request;

        @Override
        public LogisticsQueryResult query(LogisticsQueryRequest request) {
            this.request = request;
            return new LogisticsQueryResult(
                    request.trackingNo(),
                    request.carrierCode(),
                    "圆通速递",
                    "0",
                    "在途",
                    false,
                    "郑州转运中心",
                    "2026-06-13 12:05:00",
                    "",
                    List.of(new LogisticsEvent("2026-06-13 12:05:00", "正在运输", "郑州转运中心", "在途"))
            );
        }
    }
}
