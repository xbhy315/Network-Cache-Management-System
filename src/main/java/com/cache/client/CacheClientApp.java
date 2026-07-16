package com.cache.client;

import com.cache.client.net.CacheServerClient;
import com.cache.client.net.MockCacheClient;
import com.cache.client.net.RespCacheClient;
import com.cache.client.util.Config;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheClientApp extends Application {

    /** Global config, accessible from any layer via CacheClientApp.getConfig(). */
    private static Config CONFIG;

    /**
     * 客户端注册表（Client Registry）。
     * 单客户端模式：使用 "default" 作为 tabId，通过 getDefaultClient() 访问。
     * 多客户端模式：TabPaneController 为每个标签页分配独立 tabId，
     *              通过 createClient(tabId) / getClient(tabId) 管理多实例。
     */
    private static final Map<String, CacheServerClient> CLIENTS = new ConcurrentHashMap<>();
    private static final String DEFAULT_TAB_ID = "default";

    public static Config getConfig() {
        return CONFIG;
    }

    /**
     * 获取默认客户端（单客户端模式向后兼容）。
     */
    public static CacheServerClient getDefaultClient() {
        return CLIENTS.get(DEFAULT_TAB_ID);
    }

    /**
     * 按 tabId 获取客户端实例。
     */
    public static CacheServerClient getClient(String tabId) {
        return CLIENTS.get(tabId);
    }

    /**
     * 创建新的客户端实例并注册到注册表。
     */
    public static CacheServerClient createClient(String tabId) {
        CacheServerClient client = CONFIG.isClientMock()
                ? new MockCacheClient()
                : new RespCacheClient();
        CLIENTS.put(tabId, client);
        return client;
    }

    /**
     * 移除并断开客户端。
     */
    public static void removeClient(String tabId) {
        CacheServerClient c = CLIENTS.remove(tabId);
        if (c != null) {
            c.disconnect();
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        CONFIG = Config.load();

        // 创建默认客户端实例（向后兼容单客户端模式）
        createClient(DEFAULT_TAB_ID);

        // 加载主容器（含 TabPane），由 TabPaneController 创建各标签页
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cache/client/ui/MainContainer.fxml"));
        Scene scene = new Scene(loader.load(), 1000, 700);
        stage.setTitle("Network Cache Management System");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // 窗口关闭时：断开所有客户端连接，释放 Socket 资源
        for (String tabId : CLIENTS.keySet()) {
            CacheServerClient client = CLIENTS.remove(tabId);
            if (client != null) {
                client.disconnect();
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
