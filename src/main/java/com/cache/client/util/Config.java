package com.cache.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration loaded from config.properties.
 * Usage: Config config = Config.load();
 */
public class Config {

    private final Properties props;

    private Config(Properties props) {
        this.props = props;
    }

    /** Load configuration from the classpath. */
    public static Config load() {
        Properties props = new Properties();
        try (InputStream in = Config.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            // Ignore — use defaults
        }
        return new Config(props);
    }

    // ---- Server ----

    public String getDefaultHost() {
        return props.getProperty("server.default.host", "127.0.0.1");
    }

    public int getDefaultPort() {
        return parseInt("server.default.port", 6379);
    }

    public int getConnectTimeout() {
        return parseInt("server.connect.timeout", 5000);
    }

    public boolean isAutoReconnect() {
        return Boolean.parseBoolean(props.getProperty("server.auto.reconnect", "true"));
    }

    public int getReconnectInterval() {
        return parseInt("server.reconnect.interval", 3000);
    }

    // ---- Client Mode ----

    public boolean isClientMock() {
        return Boolean.parseBoolean(props.getProperty("client.mock", "true"));
    }

    // ---- Mock ----

    public int getMockMaxEntries() {
        return parseInt("mock.cache.max.entries", 10000);
    }

    // ---- UI ----

    public int getUiRefreshInterval() {
        return parseInt("ui.refresh.interval", 3000);
    }

    public int getUiMaxDisplayEntries() {
        return parseInt("ui.max.display.entries", 5000);
    }

    private int parseInt(String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
