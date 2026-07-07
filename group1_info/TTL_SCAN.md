# SCAN / TTL 命令接口说明
> **阅读对象**：题目4（Qt管理程序）、题目10（Python测试程序）  
> **服务器地址**：`127.0.0.1`  
> **端口**：`6379`（或 `6380`）  
> **协议**：RESP 文本协议（与 redis-cli 兼容）

---

## 目录

1. [SCAN —— 查找 key](#1-scan--查找-key)
2. [TTL —— 查询 key 过期信息](#2-ttl--查询-key-过期信息)
3. [Python 客户端调用示例](#3-python-客户端调用示例)
4. [Qt C++ 客户端调用示例](#4-qt-c-客户端调用示例)

---

## 1. SCAN —— 查找 key

### 1.1 功能

遍历服务器中所有未过期的 key，支持简单通配符匹配（仅 `*`）。

### 1.2 命令格式

```
SCAN [pattern]
```

| 参数 | 说明 |
|------|------|
| 无参数 / `*` | 返回所有 key |
| `key` | 精确匹配单个 key，存在则返回该 key |
| `prefix*` | 返回以 `prefix` 开头的所有 key |
| `*suffix` | 返回以 `suffix` 结尾的所有 key |
| `*mid*`  | 返回包含 `mid` 的所有 key |

### 1.3 请求格式

```
SCAN              → *1\r\n$4\r\nSCAN\r\n
SCAN *            → *2\r\n$4\r\nSCAN\r\n$1\r\n*\r\n
SCAN name         → *2\r\n$4\r\nSCAN\r\n$4\r\nname\r\n
SCAN user*        → *2\r\n$4\r\nSCAN\r\n$5\r\nuser*\r\n
```

### 1.4 响应格式

**匹配到 key → RESP 数组**

```
*3\r\n              ← 3个key
$4\r\nname\r\n      ← "name"
$4\r\ncity\r\n      ← "city"
$5\r\nhobby\r\n     ← "hobby"
```

**无匹配 → 空数组**

```
*0\r\n
```

### 1.5 完整示例

```
# 先存入数据
SET name Alice    → +OK
SET city Beijing  → +OK
SET hobby coding  → +OK

# 查询所有 key
请求: *1\r\n$4\r\nSCAN\r\n
响应: *3\r\n$4\r\nname\r\n$4\r\ncity\r\n$5\r\nhobby\r\n

# 通配符匹配
请求: *2\r\n$4\r\nSCAN\r\n$2\r\nn*\r\n
响应: *1\r\n$4\r\nname\r\n     ← 只有 name

# 精确匹配存在的 key
请求: *2\r\n$4\r\nSCAN\r\n$4\r\nname\r\n
响应: *1\r\n$4\r\nname\r\n

# 精确匹配不存在的 key
请求: *2\r\n$4\r\nSCAN\r\n$8\r\nnobody\r\n
响应: *0\r\n

# key 过期后不再出现
SET temp val EX 2 → +OK
SCAN *
响应: *4\r\n... ← 包含 temp
# ... 等 2 秒后 ...
SCAN *
响应: *3\r\n... ← temp 已消失
```

### 1.6 返回值速查

| 场景 | 响应 |
|------|------|
| 有匹配的 key | `*N\r\n$len1\r\nkey1\r\n$len2\r\nkey2\r\n...` |
| 无匹配 key | `*0\r\n` |
| 参数错误 | `-ERR wrong number of arguments\r\n` |

---

## 2. TTL —— 查询 key 过期信息

### 2.1 功能

查询指定 key 的剩余生存时间（秒）。

### 2.2 命令格式

```
TTL key
```

| 参数 | 说明 |
|------|------|
| `key` | 要查询的键名 |

### 2.3 请求格式

```
TTL mykey         → *2\r\n$3\r\nTTL\r\n$5\r\nmykey\r\n
```

### 2.4 响应格式

```
:剩余秒数\r\n
```

### 2.5 返回值含义

| 返回值 | 含义 |
|:------:|------|
| `>= 0` | key 存在且有过期时间，值为剩余秒数 |
| `-1` | key 存在但没有设置过期时间（持久 key） |
| `-2` | key 不存在（从未设置或已过期被删除） |

### 2.6 完整示例

```
# 1. 持久 key（无过期）
SET name Alice
请求: *2\r\n$3\r\nTTL\r\n$4\r\nname\r\n
响应: :-1\r\n              ← 持久 key

# 2. 有过期时间的 key
SET token abc123 EX 60
请求: *2\r\n$3\r\nTTL\r\n$5\r\ntoken\r\n
响应: :60\r\n              ← 还剩 60 秒

# 等 10 秒后再查
请求: *2\r\n$3\r\nTTL\r\n$5\r\ntoken\r\n
响应: :50\r\n              ← 还剩 50 秒

# 3. 不存在的 key
请求: *2\r\n$3\r\nTTL\r\n$8\r\nnobody\r\n
响应: :-2\r\n              ← key 不存在

# 4. key 过期后
SET temp val EX 2          ← 2秒后过期
请求: *2\r\n$3\r\nTTL\r\n$4\r\ntemp\r\n
响应: :2\r\n
# ... 等 3 秒 ...
请求: *2\r\n$3\r\nTTL\r\n$4\r\ntemp\r\n
响应: :-2\r\n              ← 已过期自动删除
```

### 2.7 返回值速查

| 场景 | 响应 |
|------|------|
| key 存在，有 TTL | `:剩余秒数\r\n`（`>= 0`） |
| key 存在，无过期 | `:-1\r\n` |
| key 不存在 | `:-2\r\n` |
| 参数错误 | `-ERR wrong number of arguments\r\n` |

---

## 3. Python 客户端调用示例

```python
import socket

def resp_encode(*args):
    """编码 RESP 数组请求"""
    parts = [f"*{len(args)}\r\n"]
    for arg in args:
        arg = str(arg)
        parts.append(f"${len(arg)}\r\n{arg}\r\n")
    return "".join(parts).encode()

def resp_decode_line(sock):
    """读取一行 RESP 数据"""
    data = b""
    while not data.endswith(b"\r\n"):
        data += sock.recv(1)
    return data.decode().strip()

def resp_decode(sock):
    """解码 RESP 响应"""
    line = resp_decode_line(sock)
    prefix = line[0]
    if prefix == '+': return line[1:]           # 简单字符串
    if prefix == '-': raise Exception(line[1:])  # 错误
    if prefix == ':': return int(line[1:])       # 整数
    if prefix == '$':                            # 批量字符串
        length = int(line[1:])
        if length == -1: return None
        data = sock.recv(length)
        sock.recv(2)  # 吃掉 \r\n
        return data.decode()
    if prefix == '*':                            # 数组
        count = int(line[1:])
        return [resp_decode(sock) for _ in range(count)]

# === SCAN 示例 ===
sock = socket.socket()
sock.connect(("127.0.0.1", 6379))

# 获取所有 key
sock.sendall(resp_encode("SCAN", "*"))
keys = resp_decode(sock)
print(f"所有 key: {keys}")          # ['name', 'city', 'hobby']

# 通配符匹配
sock.sendall(resp_encode("SCAN", "user*"))
keys = resp_decode(sock)
print(f"匹配 user*: {keys}")

# === TTL 示例 ===
sock.sendall(resp_encode("TTL", "name"))
ttl = resp_decode(sock)
print(f"name TTL: {ttl}")

if ttl == -1:
    print("持久 key，无过期时间")
elif ttl == -2:
    print("key 不存在")
else:
    print(f"剩余 {ttl} 秒")

sock.close()
```

---

## 4. Qt C++ 客户端调用示例

```cpp
#include <QTcpSocket>
#include <QStringList>

// RESP 编码
QByteArray respEncode(const QStringList &args) {
    QByteArray data;
    data.append(QString("*%1\r\n").arg(args.size()).toUtf8());
    for (const QString &arg : args) {
        QByteArray bytes = arg.toUtf8();
        data.append(QString("$%1\r\n").arg(bytes.size()).toUtf8());
        data.append(bytes);
        data.append("\r\n");
    }
    return data;
}

// RESP 解码（简化版，返回 QVariant）
QVariant respDecode(QTcpSocket *sock) {
    QByteArray line = sock->readLine();  // 读到 \n
    char prefix = line.at(0);

    if (prefix == '+') return QString::fromUtf8(line.mid(1).trimmed());
    if (prefix == '-') return QVariant();  // 错误返回空
    if (prefix == ':') return line.mid(1).trimmed().toLongLong();
    if (prefix == '$') {
        int len = line.mid(1).trimmed().toInt();
        if (len == -1) return QVariant();
        QByteArray data = sock->read(len);
        sock->read(2);  // \r\n
        return QString::fromUtf8(data);
    }
    if (prefix == '*') {
        int count = line.mid(1).trimmed().toInt();
        QStringList list;
        for (int i = 0; i < count; i++)
            list.append(respDecode(sock).toString());
        return list;
    }
    return QVariant();
}

// === 使用示例 ===
void scanAndTtlExample() {
    QTcpSocket sock;
    sock.connectToHost("127.0.0.1", 6379);
    sock.waitForConnected(5000);

    // SCAN —— 获取所有 key
    sock.write(respEncode({"SCAN", "*"}));
    sock.waitForReadyRead(3000);
    QStringList keys = respDecode(&sock).toStringList();
    qDebug() << "所有 key:" << keys;   // ["name", "city", "hobby"]

    // SCAN —— 通配符
    sock.write(respEncode({"SCAN", "user*"}));
    sock.waitForReadyRead(3000);
    QStringList matched = respDecode(&sock).toStringList();
    qDebug() << "匹配:" << matched;

    // TTL —— 查询过期
    sock.write(respEncode({"TTL", "token"}));
    sock.waitForReadyRead(3000);
    long long ttl = respDecode(&sock).toLongLong();

    if (ttl == -1)      qDebug() << "持久 key";
    else if (ttl == -2) qDebug() << "key 不存在";
    else                qDebug() << "剩余" << ttl << "秒";

    sock.disconnectFromHost();
}
```

---

## 附录：请求/响应速查

| 命令 | 请求（RESP） | 响应 | 说明 |
|------|-------------|------|------|
| `SCAN` | `*1\r\n$4\r\nSCAN\r\n` | `*0\r\n` | 无 key 时空数组 |
| `SCAN *` | `*2\r\n$4\r\nSCAN\r\n$1\r\n*\r\n` | `*3\r\n$4\r\nname\r\n...` | 所有 key |
| `SCAN n*` | `*2\r\n$4\r\nSCAN\r\n$2\r\nn*\r\n` | `*1\r\n$4\r\nname\r\n` | 通配符 |
| `SCAN k` | `*2\r\n$4\r\nSCAN\r\n$1\r\nk\r\n` | `*1\r\n...` 或 `*0\r\n` | 精确匹配 |
| `TTL k` | `*2\r\n$3\r\nTTL\r\n$1\r\nk\r\n` | `:剩余秒数\r\n` | k 存在且有 TTL |
| `TTL k` | `*2\r\n$3\r\nTTL\r\n$1\r\nk\r\n` | `:-1\r\n` | k 存在，持久 |
| `TTL k` | `*2\r\n$3\r\nTTL\r\n$1\r\nk\r\n` | `:-2\r\n` | k 不存在 |

---

> **文档版本**：v1.0  
> **最后更新**：2026-07-07  
> **适用范围**：题目4（Qt客户端）、题目10（Python客户端）的 SCAN / TTL 接口对接  
