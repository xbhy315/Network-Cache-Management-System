package com.cache.client.ui;

import com.cache.client.model.CacheEntry;
import com.cache.client.net.CacheServerClient;
import com.cache.client.net.MockCacheClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class MainController {

    private final CacheServerClient client = new MockCacheClient();
    private final ObservableList<CacheEntry> tableData = FXCollections.observableArrayList();

    // ================================================================
    // [组员B] FXML 注入 — 连接管理区域
    // ================================================================
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private Label connectionStatusLabel;
    // TODO [组员B]: 新增 FXML 控件 — 多客户端面板（标签页，每个标签页一个独立客户端实例）

    // ================================================================
    // [组员A] FXML 注入 — CRUD 输入区域
    // ================================================================
    @FXML private TextField keyField;
    @FXML private TextField valueField;
    @FXML private TextField ttlField;

    // ================================================================
    // [组员A] 新增 — Key 详情/操作区域
    // ================================================================
    @FXML private TextField detailKeyField;
    @FXML private Label detailTypeLabel;
    @FXML private Label detailTtlLabel;
    @FXML private TextField expireField;
    @FXML private Button checkBtn;
    @FXML private Button expireBtn;

    // ================================================================
    // [组员C] FXML 注入 — 搜索区域
    // ================================================================
    @FXML private TextField searchField;

    // ================================================================
    // [组员A] FXML 注入 — 表格
    // ================================================================
    @FXML private TableView<CacheEntry> tableView;
    @FXML private TableColumn<CacheEntry, String> keyColumn;
    @FXML private TableColumn<CacheEntry, String> valueColumn;
    @FXML private TableColumn<CacheEntry, Long> ttlColumn;
    @FXML private TableColumn<CacheEntry, Instant> createTimeColumn;
    @FXML private TableColumn<CacheEntry, String> statusColumn;

    // ================================================================
    // [组员B] FXML 注入 — 统计面板
    // ================================================================
    @FXML private Label statEntriesLabel;
    @FXML private Label statHitRateLabel;
    @FXML private Label statMemoryLabel;
    @FXML private Label statUptimeLabel;

    // ================================================================
    //  状态栏
    // ================================================================
    @FXML private Label statusLabel;

    // ================================================================
    // 初始化
    // ================================================================
    @FXML
    public void initialize() {
        // [组员A] 绑定表格列
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        ttlColumn.setCellValueFactory(new PropertyValueFactory<>("ttlSeconds"));
        createTimeColumn.setCellValueFactory(new PropertyValueFactory<>("createTime"));
        // statusColumn 需要自定义 cellFactory — 见组员A TODO
        // TODO [组员A]: 表格列排序支持（点击列头排序）
        // TODO [组员A]: TTL 列显示增强 — 显示剩余秒数而非原始值
        // TODO [组员A]: statusColumn 用自定义 cellFactory 显示 "正常/已过期/即将过期"

        refreshTable();
        updateStats();
    }

    // ================================================================
    // [组员B] 连接管理
    // ================================================================

    @FXML
    private void onConnect() {
        // TODO 组员B: 读取 serverHostField / serverPortField
        // TODO 组员B: 调用 client.connect(host, port)
        // TODO 组员B: 更新 connectionStatusLabel 文字和颜色
        // TODO 组员B: 异常时弹出 Alert
    }

    @FXML
    private void onDisconnect() {
        // TODO 组员B: 调用 client.disconnect()
        // TODO 组员B: 更新 connectionStatusLabel
    }

    // ================================================================
    // [组员B] 统计面板
    // ================================================================

    @FXML
    private void onShowStats() {
        // TODO 组员B: 弹出详细统计对话框
        // TODO 组员B: 调用 client.stats() 并展示
    }

    private void updateStats() {
        // TODO 组员B: 从 client.stats() 获取数据
        // TODO 组员B: 更新 statEntriesLabel / statHitRateLabel / statMemoryLabel / statUptimeLabel
        Map<String, String> stats = client.stats();
        statEntriesLabel.setText("Entries: " + stats.getOrDefault("entries", "-"));
        statHitRateLabel.setText("Hit Rate: " + stats.getOrDefault("hit_rate", "-"));
        statMemoryLabel.setText("Memory: " + stats.getOrDefault("memory", "-"));
        statUptimeLabel.setText("Uptime: " + stats.getOrDefault("uptime", "-"));
    }

    // ================================================================
    // [组员A] CRUD 操作
    // ================================================================

    @FXML
    private void onAdd() {
        // TODO 组员A: 改为弹出 CacheEntryDialog 进行输入
        // TODO 组员A: 当前简化版本直接从输入框读取
        String key = keyField.getText().trim();
        String value = valueField.getText().trim();
        long ttl = 0;
        try {
            ttl = Long.parseLong(ttlField.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        if (key.isEmpty() || value.isEmpty()) return;

        client.set(key, value, ttl);
        refreshTable();
        updateStats();
        keyField.clear();
        valueField.clear();
        ttlField.clear();
    }

    @FXML
    private void onDelete() {
        // TODO 组员A: 确认对话框后再删除
        CacheEntry selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            client.delete(selected.getKey());
            refreshTable();
            updateStats();
        }
    }

    @FXML
    private void onClearAll() {
        // TODO 组员A: 弹出确认对话框后调用 client.clear()
        client.clear();
        refreshTable();
        updateStats();
    }

    // ================================================================
    // [组员A] 新增 — Key 详情 / 类型 / TTL 操作
    // ================================================================

    @FXML
    private void onCheckKey() {
        // TODO [组员A]: 读取 detailKeyField 的 key
        // TODO [组员A]: 调用 client.exists(key) 和 client.type(key) 和 client.ttl(key)
        // TODO [组员A]: 更新 detailTypeLabel / detailTtlLabel 显示结果
        // TODO [组员A]: 若 key 不存在，标签显示红色提示
    }

    @FXML
    private void onSetExpire() {
        // TODO [组员A]: 读取 detailKeyField 的 key 和 expireField 的秒数
        // TODO [组员A]: 调用 client.expire(key, seconds)
        // TODO [组员A]: 更新 detailTtlLabel 并刷新表格
        // TODO [组员A]: 弹出成功/失败提示
    }

    // ================================================================
    // [组员C] 搜索 + 批量操作 + 导出
    // ================================================================

    @FXML
    private void onSearch() {
        // TODO 组员C: 读取 searchField 的 pattern
        // TODO 组员C: 调用 client.keys(pattern) 获取匹配的 key 列表
        // TODO 组员C: 刷新表格只显示匹配的条目
        String pattern = searchField.getText().trim();
        if (pattern.isEmpty()) return;
        List<String> matchedKeys = client.keys(pattern);
        // 从 client.getAll() 过滤出匹配的条目
        Map<String, CacheEntry> all = client.getAll();
        tableData.setAll(all.values().stream()
                .filter(e -> matchedKeys.contains(e.getKey()))
                .toList());
        tableView.setItems(tableData);
        statusLabel.setText("Matched: " + tableData.size());
    }

    @FXML
    private void onShowAll() {
        // TODO 组员C: 清空搜索条件，显示所有条目
        searchField.clear();
        refreshTable();
    }

    @FXML
    private void onExportJson() {
        // TODO 组员C: 将当前缓存数据导出为 JSON 文件
        // TODO 组员C: 使用 FileChooser 选择保存路径
    }

    @FXML
    private void onExportCsv() {
        // TODO 组员C: 将当前缓存数据导出为 CSV 文件
        // TODO 组员C: 使用 FileChooser 选择保存路径
    }

    // ================================================================
    // 公用方法
    // ================================================================

    @FXML
    private void onRefresh() {
        refreshTable();
        updateStats();
    }

    private void refreshTable() {
        tableData.setAll(client.getAll().values());
        tableView.setItems(tableData);
        statusLabel.setText("Entries: " + tableData.size());
    }
}
