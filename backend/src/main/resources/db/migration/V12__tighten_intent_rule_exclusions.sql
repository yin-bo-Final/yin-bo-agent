UPDATE chat_intent_rule
SET exclude_keywords_json = '["运费","清关","配送规则","快递公司","什么意思","是什么意思","含义","概念","定义","解释一下","指什么","这句话"]'
WHERE rule_code = 'logistics-tracking-strong'
  AND (
      exclude_keywords_json IS NULL
      OR exclude_keywords_json = '["运费","清关","配送规则","快递公司"]'
  );

UPDATE chat_intent_rule
SET exclude_keywords_json = '["退货政策","退款规则","订单规则","什么意思","是什么意思","含义","概念","定义","解释一下","指什么","这句话"]'
WHERE rule_code = 'order-query-strong'
  AND (
      exclude_keywords_json IS NULL
      OR exclude_keywords_json = '["退货政策","退款规则","订单规则"]'
  );
