package com.cache.client.ui;

import com.cache.client.CacheClientApp;
import com.cache.client.net.CacheServerClient;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * 多客户端标签页控制器。
 *
 * 管理多个客户端标签页，每个标签页拥有独立的 CacheServerClient 实例和 MainController。
 * 通过顶部的 TabPane 实现标签切换，每个标签页内部加载 MainView.fxml。
 */
public class TabPaneController {

    @FXML
    private TabPane clientTabPane;

    private int tabCounter = 0;

    @FXML
    public void initialize() {
        // 创建第一个默认标签页，复用 CacheClientApp 已创建的默认客户端
        CacheServerClient defaultClient = CacheClientApp.getDefaultClient();
        if (defaultClient != null) {
            addTabWithClient("default", defaultClient, "Client 1");
        }
    }

    /**
     * "＋新客户端"按钮 — 创建新标签页。
     */
    @FXML
    private void onNewTab() {
        String tabId = "client-" + (++tabCounter);
        CacheServerClient client = CacheClientApp.createClient(tabId);
        addTabWithClient(tabId, client, "Client " + (tabCounter + 1));
    }

    /**
     * 将指定 client 封装为一个标签页添加到 TabPane。
     */
    private void addTabWithClient(String tabId, CacheServerClient client, String tabTitle) {
        try {
            // 先创建 MainController 并注入 client，再加载 FXML
            MainController controller = new MainController();
            controller.setClient(tabId, client);

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/cache/client/ui/MainView.fxml"));
            loader.setController(controller);

            Tab tab = new Tab(tabTitle);
            tab.setContent(loader.load());
            tab.setClosable(true);

            // 关闭标签页时释放客户端资源
            tab.setOnClosed(event -> CacheClientApp.removeClient(tabId));

            clientTabPane.getTabs().add(tab);
            clientTabPane.getSelectionModel().select(tab);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tab: " + tabId, e);
        }
    }
}
