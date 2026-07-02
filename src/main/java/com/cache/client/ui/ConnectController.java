package com.cache.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

/**
 * [组员B] 连接对话框控制器。
 *
 * 负责：
 * - 读取用户输入的服务器地址、端口、超时时间
 * - 将结果返回给 MainController
 */
public class ConnectController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField timeoutField;

    public String getHost() {
        return hostField.getText().trim();
    }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return 6379;
        }
    }

    public int getTimeout() {
        try {
            return Integer.parseInt(timeoutField.getText().trim());
        } catch (NumberFormatException e) {
            return 5000;
        }
    }
}
