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

public class CacheClientApp extends Application {

    /** Global config, accessible from any layer via CacheClientApp.getConfig(). */
    private static Config CONFIG;

    /** Global cache client, initialized on startup based on config. */
    private static CacheServerClient CLIENT;

    public static Config getConfig() {
        return CONFIG;
    }

    public static CacheServerClient getClient() {
        return CLIENT;
    }

    @Override
    public void start(Stage stage) throws IOException {
        CONFIG = Config.load();

        // 根据配置选择 Mock 或 RESP 模式
        if (CONFIG.isClientMock()) {
            CLIENT = new MockCacheClient();
        } else {
            CLIENT = new RespCacheClient();
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cache/client/ui/MainView.fxml"));
        Scene scene = new Scene(loader.load(), 1000, 700);
        stage.setTitle("Network Cache Management System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
