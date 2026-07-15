package com.cache.client.ui;

import com.cache.client.CacheClientApp;
import com.cache.client.model.CacheEntry;
import com.cache.client.net.CacheServerClient;
import com.cache.client.net.MockCacheClient;
import com.cache.client.util.ExportUtil;
import com.cache.client.util.KeyPatternMatcher;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * 主界面控制器。
 *
 * 通过 CacheServerClient 接口与缓存服务端通信，
 * 不依赖具体实现（Mock / RESP 均可）。
 */
public class MainController {

    // 客户端实例 — 通过 setClient() 注入（支持多客户端）
    // 单客户端模式下，initialize() 会自动从 CacheClientApp 获取默认实例
    private CacheServerClient client;
    private String tabId = "default";
    private final ObservableList<CacheEntry> tableData = FXCollections.observableArrayList();
    private String connectedHost;
    private int connectedPort;
    private Timeline heartbeat;

    /**
     * 设置当前标签页的客户端实例。
     * TabPaneController 新建标签页时调用此方法注入独立的 client。
     *
     * @param tabId  标签页唯一标识，用于从注册表获取/释放资源
     * @param client 该标签页绑定的独立客户端实例
     */
    public void setClient(String tabId, CacheServerClient client) {
        this.tabId = tabId;
        this.client = client;
    }

    // ================================================================
    // FXML 注入 — 连接管理区域
    // ================================================================
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private Label connectionStatusLabel;

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
    @FXML private TextField listIndexField;
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
        // 向后兼容：如果没有通过 setClient() 注入，使用默认客户端
        if (client == null) {
            this.client = CacheClientApp.getDefaultClient();
        }

        // 绑定表格列
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        ttlColumn.setCellValueFactory(new PropertyValueFactory<>("ttlSeconds"));
        createTimeColumn.setCellValueFactory(new PropertyValueFactory<>("createTime"));

        // typeColumn — 显示数据类型（STRING / LIST）
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));

        // statusColumn — 用自定义 cellFactory 显示条目状态
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        statusColumn.setCellFactory(col -> new TableCell<CacheEntry, String>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                CacheEntry entry = getTableRow().getItem();
                if (empty || entry == null) {
                    setText(null);
                    return;
                }
                if (entry.getType() == CacheEntry.EntryType.LIST) {
                    setText("[" + entry.getListLength() + " items]");
                } else {
                    long remaining = entry.getRemainingTtl();
                    if (remaining < 0) {
                        setText("永不过期");
                    } else if (remaining == 0) {
                        setText("已过期");
                    } else if (remaining <= 60) {
                        setText("即将过期");
                    } else {
                        setText("正常");
                    }
                }
            }
        });

        // 选中表格中的 LIST 行时自动填充 List Key 输入框
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && selected.getType() == CacheEntry.EntryType.LIST) {
                listKeyField.setText(selected.getKey());
            }
        });

        refreshTable();
        updateStatusBar();
    }

    // ================================================================
    // 异步执行辅助
    // ================================================================

    /**
     * 在后台线程执行网络操作，操作完成后在 JavaFX 线程回调更新 UI。
     * 网络操作失败时自动更新连接状态并弹出错误提示。
     */
    private <T> void runAsync(Callable<T> callable, Consumer<T> onSuccess, String errorTitle) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return callable.call();
            }
        };
        task.setOnSucceeded(e -> {
            if (onSuccess != null) onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            if (!client.isConnected()) {
                connectionStatusLabel.setText("Connection lost: " + msg);
                connectionStatusLabel.setStyle("-fx-text-fill: red;");
            }
            if (errorTitle != null) {
                showError(errorTitle, msg);
            }
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ================================================================
    // 心跳检测 + 自动重连
    // ================================================================

    /** 启动定时心跳（每 5 秒检测连接状态 + 自动重连）。 */
    private void startHeartbeat() {
        if (heartbeat != null) heartbeat.stop();
        heartbeat = new Timeline(new KeyFrame(Duration.seconds(5), e -> heartbeatTick()));
        heartbeat.setCycleCount(Timeline.INDEFINITE);
        heartbeat.play();
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.stop();
            heartbeat = null;
        }
    }

    /** 单次心跳：ping 检测 → 断开时自动重连。 */
    private void heartbeatTick() {
        if (client == null || connectedHost == null) return;

        runAsync(() -> {
            if (client.isConnected()) {
                client.ping();
                return true; // 连接正常
            }
            return false;   // 已断开
        }, ok -> {
            if (ok) {
                connectionStatusLabel.setText("Connected to " + connectedHost + ":" + connectedPort);
                connectionStatusLabel.setStyle("-fx-text-fill: green;");
            }
        }, null); // 心跳失败不弹错误弹窗

        // 断线自动重连（运行在后台线程，不弹任何对话框）
        if (!client.isConnected() && connectedHost != null) {
            runAsync(() -> client.reconnect(), ok -> {
                if (ok) {
                    connectionStatusLabel.setText("Reconnected to " + connectedHost + ":" + connectedPort);
                    connectionStatusLabel.setStyle("-fx-text-fill: green;");
                }
            }, null);
        }
    }

    // ================================================================
    // 异步刷新方法
    // ================================================================

    /** 后台刷新条目列表并更新表格。 */
    private void refreshTableAsync() {
        runAsync(this::getAllEntries, entries -> {
            tableData.setAll(entries);
            tableView.setItems(tableData);
            updateStatusBar();
        }, "Refresh failed");
    }

    /** 后台刷新 List 面板展示。 */
    private void refreshListDisplayAsync(String key) {
        runAsync(() -> client.lrange(key, 0, -1), items -> {
            listResultView.setItems(FXCollections.observableArrayList(items));
        }, "Refresh list failed");
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

        final String connectHost = host;
        final int connectPort = port;

        runAsync(() -> {
            client.connect(connectHost, connectPort);
            return true;
        }, ok -> {
            connectedHost = connectHost;
            connectedPort = connectPort;
            connectionStatusLabel.setText("Connected to " + connectHost + ":" + connectPort);
            connectionStatusLabel.setStyle("-fx-text-fill: green;");
            startHeartbeat();
        }, "Connection failed");
    }

    @FXML
    private void onDisconnect() {
        stopHeartbeat();
        client.disconnect();
        connectionStatusLabel.setText("Disconnected");
        connectionStatusLabel.setStyle("-fx-text-fill: gray;");
    }

    // ================================================================
    // [新增 - 组员B] PING
    // ================================================================

    @FXML
    private void onPing() {
        runAsync(() -> client.ping(), result -> {
            pingResultLabel.setText(result);
            pingResultLabel.setStyle("-fx-text-fill: green;");
        }, "PING failed");
    }

    // ================================================================
    // [组员A] CRUD 操作
    // ================================================================

    @FXML
    private void onAdd() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("CacheEntryDialog.fxml"));
            DialogPane dialogPane = loader.load();
            CacheEntryController controller = loader.getController();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                String key = controller.getKey();
                String value = controller.getValue();
                long ttl = controller.getTtl();
                if (key.isEmpty() || value.isEmpty()) return;

                // 检查 key 是否已是 LIST 类型，防止覆盖
                if (isExistingListKey(key)) return;

                runAsync(() -> {
                    client.set(key, value, ttl);
                    return true;
                }, ok -> refreshTableAsync(), "Add failed");
            }
        } catch (IOException e) {
            statusLabel.setText("Failed to open dialog: " + e.getMessage());
        }
    }

    /**
     * 检查 key 在本地表格中是否已标记为 LIST 类型。
     * 如果是，弹出警告并返回 true，阻止覆盖；否则返回 false。
     */
    private boolean isExistingListKey(String key) {
        for (CacheEntry entry : tableData) {
            if (entry.getKey().equals(key) && entry.getType() == CacheEntry.EntryType.LIST) {
                Alert warn = new Alert(Alert.AlertType.WARNING,
                        "Key \"" + key + "\" already exists as a LIST.\n"
                                + "Setting a string value will overwrite the list data.\n\n"
                                + "Delete the key first, or use a different key name.",
                        ButtonType.OK);
                warn.showAndWait();
                return true;
            }
        }
        return false;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @FXML
    private void onDelete() {
        // TODO [组员A]: 确认对话框后再删除
        CacheEntry selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String deleteKey = selected.getKey();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete entry \"" + deleteKey + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                runAsync(() -> {
                    client.del(deleteKey);
                    return true;
                }, ok -> refreshTableAsync(), "Delete failed");
            }
        });
    }

    @FXML
    private void onClearAll() {
        // 弹出确认对话框后再逐条删除（服务端不支持 FLUSHDB）
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Clear all entries? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                runAsync(() -> {
                    for (CacheEntry entry : tableData) {
                        client.del(entry.getKey());
                    }
                    return true;
                }, ok -> refreshTableAsync(), "Clear all failed");
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
        runAsync(() -> {
            int len = client.lpush(key, value);
            List<String> items = client.lrange(key, 0, -1);
            return new Object[]{len, items};
        }, result -> {
            listResultView.setItems(FXCollections.observableArrayList((List<String>) result[1]));
            listLengthLabel.setText("Length: " + (int) result[0]);
            refreshTableAsync();
        }, "LPUSH failed");
    }

    @FXML
    private void onRpush() {
        String key = listKeyField.getText().trim();
        String value = listValueField.getText().trim();
        if (key.isEmpty() || value.isEmpty()) return;
        runAsync(() -> {
            int len = client.rpush(key, value);
            List<String> items = client.lrange(key, 0, -1);
            return new Object[]{len, items};
        }, result -> {
            listResultView.setItems(FXCollections.observableArrayList((List<String>) result[1]));
            listLengthLabel.setText("Length: " + (int) result[0]);
            refreshTableAsync();
        }, "RPUSH failed");
    }

    @FXML
    private void onLpop() {
        String key = listKeyField.getText().trim();
        if (key.isEmpty()) return;
        runAsync(() -> {
            String value = client.lpop(key);
            List<String> items = (value != null) ? client.lrange(key, 0, -1) : List.of();
            return new Object[]{value, items};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) result[1];
            listResultView.setItems(FXCollections.observableArrayList(items));
            if (result[0] == null) {
                listResultView.getItems().add(0, "POP: (empty)");
            }
            refreshTableAsync();
        }, "LPOP failed");
    }

    @FXML
    private void onLrange() {
        String key = listKeyField.getText().trim();
        if (key.isEmpty()) return;
        runAsync(() -> client.lrange(key, 0, -1), items -> {
            if (items.isEmpty()) {
                listResultView.setItems(FXCollections.observableArrayList("[empty]"));
            } else {
                listResultView.setItems(FXCollections.observableArrayList(items));
            }
            listLengthLabel.setText("Length: " + items.size());
        }, "LRANGE failed");
    }

    /**
     * 按值删除 list 中的元素（客户端侧实现）。
     * LRANGE 取出全部 → 过滤掉匹配值 → DEL → RPUSH 重建。
     */
    @FXML
    private void onRemoveValue() {
        String key = listKeyField.getText().trim();
        String value = listValueField.getText().trim();
        if (key.isEmpty() || value.isEmpty()) return;
        runAsync(() -> {
            List<String> all = client.lrange(key, 0, -1);
            List<String> remaining = all.stream()
                    .filter(v -> !v.equals(value))
                    .toList();
            int removed = all.size() - remaining.size();
            if (removed > 0) {
                client.del(key);
                if (!remaining.isEmpty()) {
                    client.rpush(key, remaining.toArray(new String[0]));
                }
            }
            return removed;
        }, removed -> {
            if (removed == 0) {
                showError("Remove by value", "\"" + value + "\" not found in list");
            } else {
                refreshListDisplayAsync(key);
                refreshTableAsync();
            }
        }, "Remove by value failed");
    }

    /**
     * 按序号删除 list 中的元素（1-based，从头算起）。
     * LRANGE 取出全部 → 移除指定索引元素 → DEL → RPUSH 重建。
     */
    @FXML
    private void onRemoveIndex() {
        String key = listKeyField.getText().trim();
        String idxText = listIndexField.getText().trim();
        if (key.isEmpty() || idxText.isEmpty()) return;
        try {
            int idx = Integer.parseInt(idxText);
            if (idx < 1) {
                showError("Remove by index", "Index must be >= 1");
                return;
            }
            final int removeIdx = idx;
            runAsync(() -> {
                List<String> all = client.lrange(key, 0, -1);
                if (removeIdx > all.size()) {
                    return -1; // 索引越界
                }
                all.remove(removeIdx - 1);
                client.del(key);
                if (!all.isEmpty()) {
                    client.rpush(key, all.toArray(new String[0]));
                }
                return removeIdx;
            }, removed -> {
                if (removed == -1) {
                    showError("Remove by index",
                            "Index out of range (list has fewer elements)");
                } else {
                    refreshListDisplayAsync(key);
                    refreshTableAsync();
                }
            }, "Remove by index failed");
        } catch (NumberFormatException e) {
            showError("Remove by index", "Invalid index: \"" + idxText + "\"");
        }
    }

    // ================================================================
    // [新增 - 组员B] TTL 查询
    // ================================================================

    @FXML
    private void onTtlQuery() {
        String key = ttlKeyField.getText().trim();
        if (key.isEmpty()) return;
        runAsync(() -> client.ttl(key), ttl -> {
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
        }, "TTL query failed");
    }

    // ================================================================
    // [组员C] 本地搜索过滤
    // ================================================================

    @FXML
    private void onSearch() {
        String pattern = searchField.getText();
        runAsync(this::getAllEntries, all -> {
            List<CacheEntry> filtered = KeyPatternMatcher.filter(all, pattern);
            tableData.setAll(filtered);
            tableView.setItems(tableData);
            statusLabel.setText("Filtered: " + filtered.size() + " / " + all.size());
        }, "Search failed");
    }

    @FXML
    private void onShowAll() {
        searchField.clear();
        refreshTableAsync();
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
            java.nio.file.Path path = file.toPath();
            String fileName = file.getName();
            runAsync(() -> {
                List<CacheEntry> entries = getAllEntries();
                ExportUtil.exportJson(entries, path);
                return entries.size();
            }, count -> statusLabel.setText("Exported " + count + " entries to " + fileName),
            "Export JSON failed");
        }
    }

    @FXML
    private void onExportCsv() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = fc.showSaveDialog(tableView.getScene().getWindow());
        if (file != null) {
            java.nio.file.Path path = file.toPath();
            String fileName = file.getName();
            runAsync(() -> {
                List<CacheEntry> entries = getAllEntries();
                ExportUtil.exportCsv(entries, path);
                return entries.size();
            }, count -> statusLabel.setText("Exported " + count + " entries to " + fileName),
            "Export CSV failed");
        }
    }

    // ================================================================
    // 公用方法
    // ================================================================

    @FXML
    private void onRefresh() {
        refreshTableAsync();
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
