package com.cache.client;

import com.cache.client.util.Config;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class CacheClientApp extends Application {

    /** Global config, accessible from any layer via CacheClientApp.getConfig(). */
    private static Config CONFIG;

    public static Config getConfig() {
        return CONFIG;
    }

    @Override
    public void start(Stage stage) throws IOException {
        CONFIG = Config.load();

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
