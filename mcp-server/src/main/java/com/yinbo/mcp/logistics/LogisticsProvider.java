package com.yinbo.mcp.logistics;

// 物流供应商适配器接口。后续换快递鸟或内部订单物流时，只新增实现。
public interface LogisticsProvider {

    LogisticsQueryResult query(LogisticsQueryRequest request);
}
