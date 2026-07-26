package com.yinbo.mcp.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.logistics")
// 物流供应商配置。密钥只从 local-secrets.yml 或环境变量读取，不写入代码。
public class LogisticsProperties {

    private String provider = "kuaidi100";
    private Duration requestTimeout = Duration.ofSeconds(10);
    private final Kuaidi100 kuaidi100 = new Kuaidi100();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Kuaidi100 getKuaidi100() {
        return kuaidi100;
    }

    public static class Kuaidi100 {

        private String key = "";
        private String customer = "";
        private URI queryUrl = URI.create("https://poll.kuaidi100.com/poll/query.do");
        private URI autoNumberUrl = URI.create("http://www.kuaidi100.com/autonumber/auto");
        private String resultV2 = "4";
        private String order = "desc";
        private String lang = "zh";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getCustomer() {
            return customer;
        }

        public void setCustomer(String customer) {
            this.customer = customer;
        }

        public URI getQueryUrl() {
            return queryUrl;
        }

        public void setQueryUrl(URI queryUrl) {
            this.queryUrl = queryUrl;
        }

        public URI getAutoNumberUrl() {
            return autoNumberUrl;
        }

        public void setAutoNumberUrl(URI autoNumberUrl) {
            this.autoNumberUrl = autoNumberUrl;
        }

        public String getResultV2() {
            return resultV2;
        }

        public void setResultV2(String resultV2) {
            this.resultV2 = resultV2;
        }

        public String getOrder() {
            return order;
        }

        public void setOrder(String order) {
            this.order = order;
        }

        public String getLang() {
            return lang;
        }

        public void setLang(String lang) {
            this.lang = lang;
        }
    }
}
