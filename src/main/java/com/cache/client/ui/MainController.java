package com.cache.client.ui;

import com.cache.client.CacheClientApp;
import com.cache.client.model.CacheEntry;
import com.cache.client.net.CacheServerClient;
import com.cache.client.net.MockCacheClient;
import com.cache.client.util.ExportUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 主界面控制器。
 *
 * 通过 CacheServerClient 接口与缓存服务端通信，
 * 不依赖具体实现（Mock / RESP 均可）。
 */
public class MainController {

    // 客户端实例 — 从 CacheClientApp 全局获取
    private final CacheServerClient client = CacheClientApp.getClient();
    private final ObservableList<CacheEntry> tableData = FXCollections.observableArrayList();

    // ================================================================
    // [组员B] FXML 注入 — 连接管理区域
    // ================================================================
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private Label connectionStatusLabel;
    // 多客户端面板（标签页）— 由组员B后续实现
    // @FXML private TabPane multiClientTabPane;

    // ================================================================
    // [组员A] FXML 注入 — CRUD 输入区域
    // ================================================================
    @FXML private TextField keyField;
    @FXML private TextField valueField;
    @FXML private TextField ttlField;

    // ================================================================
    // [组员C] FXML 注入 — 数据管理区域
    // ================================================================
    @FXML private TextField searchField;  // 改为本地过滤，不再依赖 KEYS 命令
    @FXML private TableView<CacheEntry> tableView;
    @FXML private TableColumn<CacheEntry, String> keyColumn;
    @FXML private TableColumn<CacheEntry, String> valueColumn;
    @FXML private TableColumn<CacheEntry, Long> ttlColumn;
    @FXML private TableColumn<CacheEntry, Instant> createTimeColumn;
    @FXML private TableColumn<CacheEntry, String> statusColumn; // "类型"列：STRING / LIST
    @FXML private TableColumn<CacheEntry, String> typeColumn;   // 显示数据类型

    // ================================================================
    // [新增 - 组员A] List 操作面板
    // ================================================================
    @FXML private TextField listKeyField;
    @FXML private TextField listValueField;
    @FXML private ListView<String> listResultView;
    @FXML private Label listLengthLabel;

    // ================================================================
    // [新增 - 组员B] TTL 查询面板
    // ================================================================
    @FXML private TextField ttlKeyField;
    @FXML private Label ttlResultLabel;

    // ================================================================
    // [新增 - 组员B] PING 按钮
    // ================================================================
    @FXML private Button pingButton;
    @FXML private Label pingResultLabel;

    // ================================================================
    //  状态栏
    // ================================================================
    @FXML private Label statusLabel;

    // ================================================================
    // 初始化
    // ================================================================

    @FXML
    public void initialize() {
        // 绑定表格列
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        ttlColumn.setCellValueFactory(new PropertyValueFactory<>("ttlSeconds"));
        createTimeColumn.setCellValueFactory(new PropertyValueFactory<>("createTime"));

        // typeColumn — 显示数据类型（STRING / LIST）
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));

        // statusColumn — 用于显示"正常/已过期/即将过期"状态
        // TODO [组员A]: 用自定义 cellFactory 显示条目状态
        // TTL > 60s → "正常"
        // TTL 0~60s → "即将过期"
        // TTL = 0  → "已过期"
        // LIST 类型 → "[N items]"

        refreshTable();
        updateStatusBar();
    }

    // ================================================================
    // [组员B] 连接管理
    // ================================================================

    @FXML
    private void onConnect() {
        String host = serverHostField.getText().trim();
        if (host.isEmpty()) host = CacheClientApp.getConfig().getDefaultHost();
        int port = CacheClientApp.getConfig().getDefaultPort();
        try {
            port = Integer.parseInt(serverPortField.getText().trim());
        } catch (NumberFormatException ignored) {}

        try {
            client.connect(host, port);
            connectionStatusLabel.setText("Connected to " + host + ":" + port);
            connectionStatusLabel.setStyle("-fx-text-fill: green;");
        } catch (Exception e) {
            connectionStatusLabel.setText("Connection failed: " + e.getMessage());
            connectionStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void onDisconnect() {
        client.disconnect();
        connectionStatusLabel.setText("Disconnected");
        connectionStatusLabel.setStyle("-fx-text-fill: gray;");
    }

    // ================================================================
    // [新增 - 组员B] PING
    // ================================================================

    @FXML
    private void onPing() {
        try {
            String result = client.ping();
            pingResultLabel.setText(result);
            pingResultLabel.setStyle("-fx-text-fill: green;");
        } catch (Exception e) {
            pingResultLabel.setText("Error: " + e.getMessage());
            pingResultLabel.setStyle("-fx-text-fill: red;");
        }
    }

    // ================================================================
    // [组员A] CRUD 操作
    // ================================================================

    @FXML
    private void onAdd() {
        // TODO [组员A]: 改为弹出 CacheEntryDialog 进行输入
        String key = keyField.getText().trim();
        String value = valueField.getText().trim();
        long ttl = 0;
        try {
            ttl = Long.parseLong(ttlField.getText().trim());
        } catch (NumberFormatException ignored) {}
        if (key.isEmpty() || value.isEmpty()) return;

        client.set(key, value, ttl);
        refreshTable();
        updateStatusBar();
        keyField.clear();
        valueField.clear();
        ttlField.clear();
    }

    @FXML
    private void onDelete() {
        // TODO [组员A]: 确认对话框后再删除
        CacheEntry selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            client.del(selected.getKey());
            refreshTable();
            updateStatusBar();
        }
    }

    @FXML
    private void onClearAll() {
        // TODO [组员A]: 弹出确认对话框
        // 由于服务端不支持 FLUSHDB，从本地缓存逐条删除
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Clear all entries? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // 从表格中获取所有 key 并逐个删除
                for (CacheEntry entry : tableData) {
                    client.del(entry.getKey());
                }
                refreshTable();
                updateStatusBar();
            }
        });
    }

    // ================================================================
    // [新增 - 组员A] List 操作
    // ================================================================

    @FXML
    private void onLpush() {
        String key = listKeyField.getText().trim();
        String value = listValueField.getText().trim();
        if (key.isEmpty() || value.isEmpty()) return;
        int len = client.lpush(key, value);
        refreshListDisplay(key);
        listLengthLabel.setText("Length: " + len);
        refreshTable();
    }

    @FXML
    private void onRpush() {
        String key = listKeyField.getText().trim();
        String value = listValueField.getText().trim();
        if (key.isEmpty() || value.isEmpty()) return;
        int len = client.rpush(key, value);
        refreshListDisplay(key);
        listLengthLabel.setText("Length: " + len);
        refreshTable();
    }

    @FXML
    private void onLpop() {
        String key = listKeyField.getText().trim();
        if (key.isEmpty()) return;
        String value = client.lpop(key);
        if (value != null) {
            listResultView.getItems().add(0, "POP: " + value);
            refreshListDisplay(key);
        } else {
            listResultView.getItems().add(0, "POP: (empty)");
        }
        refreshTable();
    }

    @FXML
    private void onLrange() {
        String key = listKeyField.getText().trim();
        if (key.isEmpty()) return;
        List<String> items = client.lrange(key, 0, -1);
        listResultView.setItems(FXCollections.observableArrayList(items));
        listLengthLabel.setText("Length: " + items.size());
    }

    /** 刷新 List 面板的显示。 */
    private void refreshListDisplay(String key) {
        List<String> items = client.lrange(key, 0, -1);
        listResultView.setItems(FXCollections.observableArrayList(items));
    }

    // ================================================================
    // [新增 - 组员B] TTL 查询
    // ================================================================

    @FXML
    private void onTtlQuery() {
        String key = ttlKeyField.getText().trim();
        if (key.isEmpty()) return;
        long ttl = client.ttl(key);
        if (ttl == -2) {
            ttlResultLabel.setText("Key does not exist");
            ttlResultLabel.setStyle("-fx-text-fill: red;");
        } else if (ttl == -1) {
            ttlResultLabel.setText("No expiry (persistent)");
            ttlResultLabel.setStyle("-fx-text-fill: blue;");
        } else {
            ttlResultLabel.setText("TTL: " + ttl + " seconds");
            ttlResultLabel.setStyle("-fx-text-fill: green;");
        }
    }

    // ================================================================
    // [组员C] 本地搜索过滤
    // ================================================================

    @FXML
    private void onSearch() {
        // 改为本地过滤 — 不再依赖服务端的 KEYS 命令
        String pattern = searchField.getText().trim().toLowerCase();
        if (pattern.isEmpty()) return;

        // 获取全部本地条目
        List<CacheEntry> all = getAllEntries();
        if (pattern.equals("*")) {
            tableData.setAll(all);
        } else {
            // 简单通配符匹配：* 表示任意字符
            String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
            tableData.setAll(all.stream()
                    .filter(e -> e.getKey().matches(regex))
                    .toList());
        }
        tableView.setItems(tableData);
        statusLabel.setText("Filtered: " + tableData.size() + " / " + all.size());
    }

    @FXML
    private void onShowAll() {
        searchField.clear();
        refreshTable();
    }

    // ================================================================
    // [组员C] 导出
    // ================================================================

    @FXML
    private void onExportJson() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showSaveDialog(tableView.getScene().getWindow());
        if (file != null) {
            try {
                List<CacheEntry> entries = getAllEntries();
                ExportUtil.exportJson(entries, file.toPath());
                statusLabel.setText("Exported to " + file.getName());
            } catch (IOException e) {
                statusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onExportCsv() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = fc.showSaveDialog(tableView.getScene().getWindow());
        if (file != null) {
            try {
                List<CacheEntry> entries = getAllEntries();
                ExportUtil.exportCsv(entries, file.toPath());
                statusLabel.setText("Exported to " + file.getName());
            } catch (IOException e) {
                statusLabel.setText("Export failed: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // 公用方法
    // ================================================================

    @FXML
    private void onRefresh() {
        refreshTable();
        updateStatusBar();
    }

    private void refreshTable() {
        List<CacheEntry> all = getAllEntries();
        tableData.setAll(all);
        tableView.setItems(tableData);
    }

    private void updateStatusBar() {
        int count = tableData.size();
        statusLabel.setText("Entries: " + count
                + " | Mode: " + (CacheClientApp.getConfig().isClientMock() ? "Mock" : "RESP"));
    }

    /**
     * 获取全部缓存条目。
     *
     * Mock 模式：直接从 MockCacheClient 的本地存储获取。
     * RESP 模式：通过 SCAN + GET 逐条获取（暂未实现，待 SCAN 格式确认）。
     */
    private List<CacheEntry> getAllEntries() {
        if (client instanceof MockCacheClient mock) {
            // Mock 模式：直接读本地存储
            return mock.getAllLocalEntries();
        }
        // RESP 模式：待 SCAN 命令格式确认后实现
        // TODO [组员C]: SCAN 格式确认后改为:
        //   List<String> keys = client.scan("0", "*");
        //   for (key : keys) { 逐个 GET 组装 CacheEntry }
        return tableData.stream().toList();
    }
}
