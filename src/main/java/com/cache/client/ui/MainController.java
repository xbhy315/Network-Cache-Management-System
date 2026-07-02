package com.cache.client.ui;

import com.cache.client.model.CacheEntry;
import com.cache.client.net.CacheServerClient;
import com.cache.client.net.MockCacheClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class MainController {

    private final CacheServerClient client = new MockCacheClient();
    private final ObservableList<CacheEntry> tableData = FXCollections.observableArrayList();

    @FXML private TableView<CacheEntry> tableView;
    @FXML private TableColumn<CacheEntry, String> keyColumn;
    @FXML private TableColumn<CacheEntry, String> valueColumn;
    @FXML private TableColumn<CacheEntry, Long> ttlColumn;
    @FXML private Label statusLabel;
    @FXML private TextField keyField;
    @FXML private TextField valueField;
    @FXML private TextField ttlField;

    @FXML
    public void initialize() {
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        ttlColumn.setCellValueFactory(new PropertyValueFactory<>("ttlSeconds"));

        refreshTable();
    }

    @FXML
    private void onAdd() {
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
        keyField.clear();
        valueField.clear();
        ttlField.clear();
    }

    @FXML
    private void onDelete() {
        CacheEntry selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            client.delete(selected.getKey());
            refreshTable();
        }
    }

    @FXML
    private void onRefresh() {
        refreshTable();
    }

    private void refreshTable() {
        tableData.setAll(client.getAll().values());
        tableView.setItems(tableData);
        statusLabel.setText("Entries: " + tableData.size());
    }
}
