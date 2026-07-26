package com.yinbo.mcp.logistics;

// 统一物流查询请求，屏蔽具体供应商入参差异。
public record LogisticsQueryRequest(
        String trackingNo,
        String carrierCode,
        String carrierName,
        String phone,
        String from,
        String to
) {
}
