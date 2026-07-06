# 网络缓存服务器 —— RESP协议文档 & 命令说明

> **阅读对象**：题目4（Qt管理程序）、题目10（Python测试程序）  
> **服务器地址**：`127.0.0.1`  
> **端口**：`6379`

---

## 目录

1. [RESP协议概述](#1-resp协议概述)
2. [五种数据类型](#2-五种数据类型)
3. [请求格式](#3-请求格式)
4. [响应格式速查](#4-响应格式速查)
5. [8个命令详细说明](#5-8个命令详细说明)
   - [PING](#51-ping)
   - [SET](#52-set)
   - [GET](#53-get)
   - [DEL](#54-del)
   - [LPUSH](#55-lpush)
   - [RPUSH](#56-rpush)
   - [LPOP](#57-lpop)
   - [LRANGE](#58-lrange)
6. [客户端实现要点](#6-客户端实现要点)
7. [附录：完整请求/响应速查表](#附录完整请求响应速查表)

---

## 1. RESP协议概述

**RESP**（REdis Serialization Protocol）是一种简单的文本协议，纯字符串、明文传输、以 `\r\n` 分隔。

### 核心规则

```
1. 每条消息以 \r\n（CRLF）结尾
2. 第一个字符决定数据类型（+ - : $ *）
3. 全部使用 UTF-8 编码
4. 客户端发请求 → 服务器回响应（一问一答）
```

### 为什么用RESP？

1. **兼容 redis-cli**：可直接用 `redis-cli -p 6379` 连接测试
2. **调试方便**：`telnet 127.0.0.1 6379` 就能看到明文
3. **实现简单**：50行代码搞定编解码
4. **字符串协议**：不需要处理字节序、位操作

---

## 2. 五种数据类型

| 类型 | 首字节 | 格式 | 示例 |
|:----:|:------:|------|------|
| **简单字符串** | `+` | `+内容\r\n` | `+OK\r\n` |
| **错误** | `-` | `-错误信息\r\n` | `-ERR unknown command\r\n` |
| **整数** | `:` | `:数字\r\n` | `:3\r\n` |
| **批量字符串** | `$` | `$长度\r\n数据\r\n` | `$5\r\nhello\r\n` |
| **空值(NULL)** | `$` | `$-1\r\n` | `$-1\r\n` |
| **数组** | `*` | `*元素个数\r\n元素1元素2...` | `*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n` |

---

## 3. 请求格式

**客户端始终发送 RESP 数组**，格式如下：

```
*参数个数\r\n$第1个参数长度\r\n第1个参数\r\n$第2个参数长度\r\n第2个参数\r\n...
```

### 示例1：无参数命令

```
客户端请求：PING

编码后发送：
  *1\r\n              ← 1个参数
  $4\r\n              ← 参数长度=4
  PING\r\n            ← 参数内容
```

### 示例2：有参数命令

```
客户端请求：SET name Alice

编码后发送：
  *3\r\n              ← 3个参数
  $3\r\n              ← 第1个参数长度=3
  SET\r\n             ← "SET"
  $4\r\n              ← 第2个参数长度=4
  name\r\n            ← "name"
  $5\r\n              ← 第3个参数长度=5
  Alice\r\n           ← "Alice"
```

### 示例3：带可选参数的命令

```
客户端请求：SET token abc123 EX 60

编码后发送：
  *5\r\n
  $3\r\nSET\r\n
  $5\r\ntoken\r\n
  $6\r\nabc123\r\n
  $2\r\nEX\r\n
  $2\r\n60\r\n
```

### 伪代码实现

```python
# Python 编码函数
def encode(*args):
    parts = [f"*{len(args)}\r\n"]
    for arg in args:
        arg = str(arg)
        parts.append(f"${len(arg)}\r\n")
        parts.append(f"{arg}\r\n")
    return "".join(parts)

# 示例
encode("SET", "name", "Alice")
# → "*3\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAlice\r\n"
```

```cpp
// Qt C++ 编码函数
QByteArray encode(const QStringList &args) {
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
```

---

## 4. 响应格式速查

| 响应类型 | 格式 | 何时出现 |
|----------|------|----------|
| `+OK\r\n` | 简单字符串 | SET成功 / PING成功 |
| `+PONG\r\n` | 简单字符串 | PING无参数时 |
| `-ERR message\r\n` | 错误 | 命令不存在 / 参数错误 |
| `:3\r\n` | 整数 | DEL删除数量 / LPUSH后的长度 |
| `$5\r\nAlice\r\n` | 批量字符串 | GET存在的key |
| `$-1\r\n` | 空值 | GET不存在的key / LPOP空列表 |
| `*3\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n` | 数组 | LRANGE结果 |

---

## 5. 8个命令详细说明

### 5.1 PING

**功能**：测试连通性

| 项目 | 内容 |
|------|------|
| 命令格式 | `PING [message]` |
| 参数 | 0或1个参数 |
| 成功响应 | `+PONG\r\n`（无参数）/ `$len\r\nmessage\r\n`（有参数） |

**示例**：

```
请求: *1\r\n$4\r\nPING\r\n
响应: +PONG\r\n

请求: *2\r\n$4\r\nPING\r\n$5\r\nhello\r\n
响应: $5\r\nhello\r\n
```

**Python测试**：
```python
import socket
sock = socket.socket()
sock.connect(("127.0.0.1", 6379))
sock.sendall(b"*1\r\n$4\r\nPING\r\n")
print(sock.recv(1024))  # → b'+PONG\r\n'
```

---

### 5.2 SET

**功能**：设置键值对，可选过期时间

| 项目 | 内容 |
|------|------|
| 命令格式 | `SET key value [EX seconds]` |
| 参数 | 3个（基本）或 5个（带过期） |
| 成功响应 | `+OK\r\n` |
| 错误响应 | `-ERR wrong number of arguments\r\n` |

**示例1：基本设置**

```
请求: *3\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nAlice\r\n
响应: +OK\r\n
```

**示例2：带过期时间（60秒后过期）**

```
请求: *5\r\n$3\r\nSET\r\n$5\r\ntoken\r\n$6\r\nabc123\r\n$2\r\nEX\r\n$2\r\n60\r\n
响应: +OK\r\n
```

**说明**：
- `EX` 和秒数之间要有空格
- 过期后 `GET` 返回 `$-1\r\n`
- EX 参数位置固定：第4个参数必须是 `EX`，第5个是秒数

---

### 5.3 GET

**功能**：获取键的值

| 项目 | 内容 |
|------|------|
| 命令格式 | `GET key` |
| 参数 | 2个 |
| 键存在 | `$len\r\nvalue\r\n` |
| 键不存在 | `$-1\r\n` |

**示例1：键存在**

```
请求: *2\r\n$3\r\nGET\r\n$4\r\nname\r\n
响应: $5\r\nAlice\r\n
```

**示例2：键不存在或已过期**

```
请求: *2\r\n$3\r\nGET\r\n$8\r\nnobody\r\n
响应: $-1\r\n
```

---

### 5.4 DEL

**功能**：删除一个或多个键

| 项目 | 内容 |
|------|------|
| 命令格式 | `DEL key [key ...]` |
| 参数 | ≥2个 |
| 成功响应 | `:n\r\n`（n = 实际删除的键数量） |

**示例：删除3个键（只有2个存在）**

```
请求: *4\r\n$3\r\nDEL\r\n$4\r\nname\r\n$3\r\nage\r\n$8\r\nnotexist\r\n
响应: :2\r\n          ← 实际删除了2个
```

---

### 5.5 LPUSH

**功能**：从列表**左侧（头部）**插入一个或多个元素

| 项目 | 内容 |
|------|------|
| 命令格式 | `LPUSH key value [value ...]` |
| 参数 | ≥3个 |
| 成功响应 | `:len\r\n`（插入后的列表总长度） |

**示例**：

```
# 空列表插入3个元素
请求: *4\r\n$5\r\nLPUSH\r\n$5\r\nqueue\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n
响应: :3\r\n           ← 列表现在有3个元素：c, b, a

# 再插入2个元素
请求: *3\r\n$5\r\nLPUSH\r\n$5\r\nqueue\r\n$1\r\nd\r\n
响应: :4\r\n           ← 列表现在有4个元素：d, c, b, a
```

**注意**：`LPUSH a b c` 后列表为 `[c, b, a]`（后插入的在前面，堆栈语义）

---

### 5.6 RPUSH

**功能**：从列表**右侧（尾部）**插入一个或多个元素

| 项目 | 内容 |
|------|------|
| 命令格式 | `RPUSH key value [value ...]` |
| 参数 | ≥3个 |
| 成功响应 | `:len\r\n`（插入后的列表总长度） |

**示例**：

```
请求: *4\r\n$5\r\nRPUSH\r\n$5\r\nqueue\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n
响应: :3\r\n           ← 列表现在有3个元素：a, b, c
```

**注意**：`RPUSH a b c` 后列表为 `[a, b, c]`（保持插入顺序，队列语义）

---

### 5.7 LPOP

**功能**：从列表**左侧（头部）**弹出一个元素

| 项目 | 内容 |
|------|------|
| 命令格式 | `LPOP key` |
| 参数 | 2个 |
| 列表非空 | `$len\r\nvalue\r\n` |
| 列表为空/不存在 | `$-1\r\n` |

**示例**：

```
# 先 LPUSH 几个元素
LPUSH queue a b c  → :3   (列表: c, b, a)

LPOP queue
请求: *2\r\n$4\r\nLPOP\r\n$5\r\nqueue\r\n
响应: $1\r\nc\r\n         ← 弹出了 c

LPOP queue
响应: $1\r\nb\r\n         ← 弹出了 b

LPOP queue
响应: $1\r\na\r\n         ← 弹出了 a

LPOP queue
响应: $-1\r\n             ← 列表已空
```

---

### 5.8 LRANGE

**功能**：获取列表指定范围的元素

| 项目 | 内容 |
|------|------|
| 命令格式 | `LRANGE key start stop` |
| 参数 | 4个 |
| 列表存在 | `*count\r\n元素1元素2...` |
| 列表不存在 | `*0\r\n`（空数组） |

**索引规则**：
- `0` = 第一个元素
- `-1` = 最后一个元素
- `-2` = 倒数第二个元素
- 返回范围**包含 start 和 stop**（闭区间）

**示例**：

```
# 列表：a(索引0) b(索引1) c(索引2) d(索引3)

# 获取全部
请求: *4\r\n$6\r\nLRANGE\r\n$3\r\nlst\r\n$1\r\n0\r\n$2\r\n-1\r\n
响应: *4\r\n$1\r\na\r\n$1\r\nb\r\n$1\r\nc\r\n$1\r\nd\r\n

# 获取前2个
请求: *4\r\n$6\r\nLRANGE\r\n$3\r\nlst\r\n$1\r\n0\r\n$1\r\n1\r\n
响应: *2\r\n$1\r\na\r\n$1\r\nb\r\n

# 获取最后2个（用负索引）
请求: *4\r\n$6\r\nLRANGE\r\n$3\r\nlst\r\n$2\r\n-2\r\n$2\r\n-1\r\n
响应: *2\r\n$1\r\nc\r\n$1\r\nd\r\n

# 空列表
请求: *4\r\n$6\r\nLRANGE\r\n$5\r\nempty\r\n$1\r\n0\r\n$2\r\n-1\r\n
响应: *0\r\n
```

---

## 6. 客户端实现要点

### 6.1 发送请求（伪代码）

```
步骤1：将命令参数拼接成 RESP 数组格式的字符串
步骤2：编码为 UTF-8 字节
步骤3：通过 TCP Socket 发送
```

### 6.2 解析响应（伪代码）

```
步骤1：读取第一个字节，判断类型（+ - : $ *）
步骤2：根据类型读取后续内容直到 \r\n
步骤3：

  if 首字节 == '+': → 读一行，返回字符串
  if 首字节 == '-': → 读一行，返回/抛出错误
  if 首字节 == ':': → 读一行，转整数
  if 首字节 == '$': → 读一行得到长度L
      if L == -1: → 返回 NULL/None
      else:      → 读取 L 字节数据 + \r\n
  if 首字节 == '*': → 读一行得到元素数N
      for i in 0..N-1: 递归解析每个元素
```

### 6.3 注意事项

| 注意点 | 说明 |
|--------|------|
| **编码** | 全部使用 **UTF-8** |
| **分隔符** | 严格使用 `\r\n`（不是 `\n`） |
| **批量字符串** | 先读长度再读数据，数据后还有 `\r\n` |
| **连接复用** | 建议一个连接收发多个请求，不要每次重建 |
| **超时设置** | 建议 5 秒 |
| **服务器地址** | `127.0.0.1:6379` |

### 6.4 最简测试

```bash
# 方法1：用 redis-cli
redis-cli -p 6379 PING
# 预期输出: PONG

# 方法2：用 telnet
telnet 127.0.0.1 6379
# 输入（手动敲）:
*1
$4
PING
# 预期输出: +PONG

# 方法3：用 nc（netcat）
echo -e "*1\r\n\$4\r\nPING\r\n" | nc 127.0.0.1 6379
# 预期输出: +PONG
```

---

## 附录：完整请求/响应速查表

| 命令 | 请求（RESP编码） | 响应 | 注释 |
|------|-----------------|------|------|
| `PING` | `*1\r\n$4\r\nPING\r\n` | `+PONG\r\n` | 连通性测试 |
| `PING hello` | `*2\r\n$4\r\nPING\r\n$5\r\nhello\r\n` | `$5\r\nhello\r\n` | 返回消息 |
| `SET k v` | `*3\r\n$3\r\nSET\r\n$1\r\nk\r\n$1\r\nv\r\n` | `+OK\r\n` | 设置键值 |
| `SET k v EX 60` | `*5\r\n$3\r\nSET\r\n$1\r\nk\r\n$1\r\nv\r\n$2\r\nEX\r\n$2\r\n60\r\n` | `+OK\r\n` | 带60秒过期 |
| `GET k` | `*2\r\n$3\r\nGET\r\n$1\r\nk\r\n` | `$1\r\nv\r\n` | 键存在 |
| `GET bad` | `*2\r\n$3\r\nGET\r\n$3\r\nbad\r\n` | `$-1\r\n` | 键不存在 |
| `DEL k1 k2` | `*3\r\n$3\r\nDEL\r\n$2\r\nk1\r\n$2\r\nk2\r\n` | `:2\r\n` | 删除2个键 |
| `LPUSH q a` | `*3\r\n$5\r\nLPUSH\r\n$1\r\nq\r\n$1\r\na\r\n` | `:1\r\n` | 头插 |
| `RPUSH q b` | `*3\r\n$5\r\nRPUSH\r\n$1\r\nq\r\n$1\r\nb\r\n` | `:2\r\n` | 尾插 |
| `LPOP q` | `*2\r\n$4\r\nLPOP\r\n$1\r\nq\r\n` | `$1\r\na\r\n` | 弹出 |
| `LPOP q`（空） | `*2\r\n$4\r\nLPOP\r\n$1\r\nq\r\n` | `$-1\r\n` | 列表空 |
| `LRANGE q 0 -1` | `*4\r\n$6\r\nLRANGE\r\n$1\r\nq\r\n$1\r\n0\r\n$2\r\n-1\r\n` | `*2\r\n$1\r\na\r\n$1\r\nb\r\n` | 获取全部 |
| `LRANGE q 0 -1`（空） | `*4\r\n$6\r\nLRANGE\r\n$1\r\nq\r\n$1\r\n0\r\n$2\r\n-1\r\n` | `*0\r\n` | 空列表 |

---

> **文档版本**：v1.0  
> **最后更新**：2026-07-05  
> **适用范围**：题目4（Qt客户端）、题目10（Python客户端）接口对接
