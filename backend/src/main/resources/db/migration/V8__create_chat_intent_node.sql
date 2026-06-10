CREATE TABLE IF NOT EXISTS chat_intent_node (
    id BIGINT PRIMARY KEY,
    node_code VARCHAR(128) NOT NULL UNIQUE,
    parent_code VARCHAR(128) NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    level VARCHAR(32) NOT NULL,
    kind VARCHAR(32) NOT NULL DEFAULT 'KB',
    examples_json TEXT NULL,
    knowledge_base_no VARCHAR(64) NULL,
    collection_name VARCHAR(128) NULL,
    mcp_tool_id VARCHAR(128) NULL,
    prompt_snippet TEXT NULL,
    prompt_template TEXT NULL,
    param_prompt_template TEXT NULL,
    top_k INTEGER NULL,
    min_score NUMERIC(4, 3) NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_intent_node_parent
ON chat_intent_node (parent_code, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_chat_intent_node_enabled
ON chat_intent_node (enabled, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_chat_intent_node_kind
ON chat_intent_node (kind, enabled);

INSERT INTO chat_intent_node (
    id, node_code, parent_code, name, description, level, kind, examples_json,
    knowledge_base_no, collection_name, mcp_tool_id, top_k, min_score, sort_order, enabled
) VALUES
    (810000000000000001, 'system', NULL, '系统交互', '欢迎问候、助手介绍、能力说明等系统交互', 'DOMAIN', 'SYSTEM', '[]', NULL, NULL, NULL, NULL, NULL, 10, TRUE),
    (810000000000000002, 'system-greeting', 'system', '欢迎与问候', '用户打招呼、问好、感谢、礼貌性寒暄', 'CATEGORY', 'SYSTEM', '["你好","谢谢你","在吗"]', NULL, NULL, NULL, NULL, 0.350, 10, TRUE),
    (810000000000000003, 'system-about', 'system', '关于助手', '询问助手身份、能力范围、使用方式', 'CATEGORY', 'SYSTEM', '["你是谁？","你能做什么？"]', NULL, NULL, NULL, NULL, 0.350, 20, TRUE),

    (810000000000000010, 'product', NULL, '商品服务', '商品信息、售后维修、退换货政策等商品服务问题', 'DOMAIN', 'KB', '[]', NULL, NULL, NULL, NULL, NULL, 20, TRUE),
    (810000000000000011, 'product-info', 'product', '商品信息', '商品规格、参数、功能介绍、适用场景等产品信息', 'CATEGORY', 'KB', '["iPhone 16 Pro 的摄像头参数是什么？","这款耳机支持降噪吗？"]', NULL, 'kb_product_info', NULL, 5, 0.350, 10, TRUE),
    (810000000000000012, 'product-repair', 'product', '售后维修', '商品维修政策、保修期限、维修流程、维修网点等信息', 'CATEGORY', 'KB', '["手机屏幕碎了怎么保修？","保修期内维修要收费吗？"]', NULL, 'kb_product_repair', NULL, 5, 0.350, 20, TRUE),
    (810000000000000013, 'product-return-exchange', 'product', '退换货政策', '退货条件、换货流程、退款时效、七天无理由退货规则等', 'CATEGORY', 'KB', '["买了一周的东西还能退吗？","退货运费谁出？"]', NULL, 'kb_product_return_exchange', NULL, 5, 0.350, 30, TRUE),

    (810000000000000020, 'logistics', NULL, '物流与配送', '国内物流、跨境物流、配送方式、运费、清关、轨迹查询等问题', 'DOMAIN', 'KB', '[]', NULL, NULL, NULL, NULL, NULL, 30, TRUE),
    (810000000000000021, 'logistics-domestic', 'logistics', '国内物流', '国内配送方式、配送时效、国内运费规则', 'CATEGORY', 'KB', '[]', NULL, NULL, NULL, NULL, NULL, 10, TRUE),
    (810000000000000022, 'logistics-domestic-delivery', 'logistics-domestic', '配送方式', '国内快递公司选择、配送时效、同城配送、预约配送等说明', 'TOPIC', 'KB', '["你们用什么快递发货？","能选顺丰吗？"]', NULL, 'kb_logistics_domestic_delivery', NULL, 5, 0.350, 10, TRUE),
    (810000000000000023, 'logistics-domestic-fee', 'logistics-domestic', '运费规则', '国内物流运费计算规则、包邮条件、偏远地区加收等', 'TOPIC', 'KB', '["满多少包邮？","新疆发货运费多少？"]', NULL, 'kb_logistics_domestic_fee', NULL, 5, 0.350, 20, TRUE),
    (810000000000000024, 'logistics-overseas', 'logistics', '跨境物流', '海外仓、跨境运费、清关流程、禁运规则等问题', 'CATEGORY', 'KB', '[]', NULL, NULL, NULL, NULL, NULL, 20, TRUE),
    (810000000000000025, 'logistics-overseas-warehouse', 'logistics-overseas', '海外仓', '海外仓备货、海外仓发货时效、海外仓退货流程等说明', 'TOPIC', 'KB', '["海外仓发货一般几天到？","你们有美国仓吗？"]', NULL, 'kb_logistics_overseas_warehouse', NULL, 5, 0.350, 10, TRUE),
    (810000000000000026, 'logistics-overseas-customs', 'logistics-overseas', '清关流程', '跨境物流的清关申报、关税计算、禁运品规则等相关说明', 'TOPIC', 'KB', '["海淘包裹清关一般要多久？","清关需要身份证吗？"]', NULL, 'kb_logistics_overseas_customs', NULL, 5, 0.350, 20, TRUE),
    (810000000000000027, 'logistics-overseas-fee', 'logistics-overseas', '运费计算', '跨境物流运费计算方式、不同国家和地区的费率对比等', 'TOPIC', 'KB', '["寄到日本运费怎么算？","跨境运费比国内贵多少？"]', NULL, 'kb_logistics_overseas_fee', NULL, 5, 0.350, 30, TRUE),
    (810000000000000028, 'logistics-tracking', 'logistics', '物流轨迹查询', '查询用户包裹位置、快递轨迹、物流状态等实时信息', 'CATEGORY', 'MCP', '["我的快递到哪了？","帮我查一下包裹物流进度"]', NULL, NULL, 'logistics-tracking-tool', NULL, 0.350, 30, TRUE),

    (810000000000000030, 'order', NULL, '订单管理', '订单状态、订单明细、支付记录、物流关联查询等问题', 'DOMAIN', 'MCP', '[]', NULL, NULL, NULL, NULL, NULL, 40, TRUE),
    (810000000000000031, 'order-query', 'order', '订单查询', '查询用户订单状态、订单详情、支付记录等实时信息', 'CATEGORY', 'MCP', '["帮我查一下订单 2024112801 的状态","我的订单到哪了？"]', NULL, NULL, 'order-query-tool', NULL, 0.350, 10, TRUE)
ON CONFLICT (node_code) DO NOTHING;
