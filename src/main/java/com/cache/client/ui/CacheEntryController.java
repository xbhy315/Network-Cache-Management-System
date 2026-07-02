package com.cache.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * [组员A] 添加/编辑缓存条目对话框控制器。
 *
 * 负责：
 * - 读取用户输入的 key、value、TTL
 * - 支持编辑已有条目时回显数据
 * - 将结果返回给 MainController
 */
public class CacheEntryController {

    @FXML private TextField keyField;
    @FXML private TextArea valueField;
    @FXML private TextField ttlField;

    public String getKey() {
        return keyField.getText().trim();
    }

    public String getValue() {
        return valueField.getText().trim();
    }

    public long getTtl() {
        try {
            return Long.parseLong(ttlField.getText().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 编辑时回显已有数据 */
    public void setKey(String key) {
        keyField.setText(key);
    }

    public void setValue(String value) {
        valueField.setText(value);
    }

    public void setTtl(long ttl) {
        ttlField.setText(String.valueOf(ttl));
    }
}
