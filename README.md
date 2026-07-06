# Network Cache Management System

网络缓存管理客户端程序 — 实习课题第二组。

## 项目背景

本项目为西安电子科技大学实习课题，实现**网络缓存管理程序设计**（题目 2），与题目 1（基于 Linux 系统的网络缓存结构设计）配合完成：

- **题目 1** — Linux 服务端：实现缓存核心逻辑（Hash/链表/队列存储、过期策略、epoll 并发访问），类似 redis
- **题目 2（本组）** — 客户端：提供可视化图形界面，通过 **RESP 协议**（Redis 序列化协议）连接并管理服务端缓存

## 功能模块

| 模块 | 说明 |
|------|------|
| 连接管理 | 配置服务端 IP/端口，连接/断开，连接状态显示 |
| PING 连通性测试 | 检测服务端是否在线 |
| 缓存 CRUD | 添加、查询、删除缓存条目，支持 TTL 过期时间 |
| String 操作 | SET/GET 标准的键值对读写 |
| List 操作 | LPUSH/RPUSH/LPOP/LRANGE 列表类型数据操作 |
| TTL 查询 | 查看指定 key 的剩余生存时间 |
| 本地搜索过滤 | 对已加载的缓存数据进行 key 模式匹配 |
| 数据导出 | 将缓存数据导出为 JSON / CSV 格式 |

## 技术栈

- **语言**: Java 17+
- **UI 框架**: JavaFX 21 + FXML
- **通信协议**: RESP（REdis Serialization Protocol，文本协议，兼容 redis-cli）
- **网络**: TCP Socket
- **构建**: Maven

## 架构设计

本系统采用 **分层架构 + 接口隔离** 设计，界面层通过 `CacheServerClient` 接口与实现层解耦，支持 Mock 和 RESP 双模式切换。

```
┌──────────────────────────────────────┐     RESP 文本协议     ┌──────────────────────┐
│      JavaFX 客户端（本组）           │ ◄──────────────────► │  Linux 缓存服务端     │
│                                      │     TCP :6379        │   （题目 1 组）      │
│  ┌──────────────────────────────┐    │                      │                      │
│  │   界面层（JavaFX + FXML）      │    │                      │  String / List 存储  │
│  │   MainController              │    │                      │  过期管理            │
│  ├──────────────────────────────┤    │                      │  epoll 并发          │
│  │   接口层（CacheServerClient）  │    │                      │                      │
│  │   get/set/del/ping/lpush/...  │    │                      │                      │
│  ├──────────────────────────────┤    │                      │                      │
│  │   实现层                       │    │                      │                      │
│  │   MockCacheClient（开发用）    │    │                      │                      │
│  │   RespCacheClient（联调用）    │    │                      │                      │
│  └──────────────────────────────┘    │                      └──────────────────────┘
└──────────────────────────────────────┘
```

- **CacheServerClient**（接口层）：定义 12 个操作方法，界面层不直接依赖具体实现
- **MockCacheClient**：内存模拟（ConcurrentHashMap），用于界面层独立开发调试
- **RespCacheClient**：RESP-TCP 真实通信，委托 `RespCodec` 进行编解码
- **RespCodec + RespResponse**：RESP 协议编解码工具，位于 `net/protocol/` 子包

### 工作模式切换

通过 `config.properties` 中的 `client.mock` 配置项切换：

```properties
client.mock=true   # Mock 模式（开发/演示，零外部依赖，默认）
client.mock=false  # RESP 模式（联调/生产，需要第一组服务端运行）
```

## 快速开始

### 开发环境

- **JDK**: 17+
- **IDE**: IntelliJ IDEA
- **构建工具**: Maven 3.8+
- **UI**: JavaFX 21（Maven 依赖自动管理，不需额外安装 SDK）

### 运行

```bash
# Mock 模式（默认，无需服务端）
# 修改 config.properties 中 client.mock=true
# 双击 Maven javafx:run 或运行 CacheClientApp.main()

# RESP 模式（需要第一组服务端运行在 127.0.0.1:6379）
# 修改 config.properties 中 client.mock=false
# 启动服务端后，运行 CacheClientApp.main()
```

## 项目结构

```
src/main/java/com/cache/client/
├── CacheClientApp.java              ← 启动入口 + 全局工厂
├── model/
│   └── CacheEntry.java              ← 数据模型（String + List 双类型）
├── net/
│   ├── CacheServerClient.java       ← 接口（12 方法契约）
│   ├── MockCacheClient.java         ← Mock 实现
│   ├── RespCacheClient.java         ← RESP-TCP 实现
│   ├── TcpCacheClient.java          ← [已废弃] TLV 归档
│   └── protocol/
│       ├── RespCodec.java           ← RESP 编解码器
│       └── RespResponse.java        ← 响应类型封装
├── ui/
│   ├── MainController.java          ← 主界面控制器
│   ├── CacheEntryController.java    ← 条目编辑弹窗
│   └── ConnectController.java       ← 连接配置弹窗
└── util/
    ├── Config.java                  ← 配置管理
    └── ExportUtil.java              ← JSON/CSV 导出

src/test/java/com/cache/client/
├── net/
│   ├── MockCacheClientTest.java     ← Mock 单元测试（18用例）
│   └── protocol/
│       └── RespCodecTest.java       ← RESP 编解码测试（16用例）
```

## 开发阶段

| 阶段 | 时间 | 任务 |
|:----:|:----:|------|
| P0 | 第1批 | 接口重写 + MockCacheClient 重写 + 单元测试 |
| P1 | 第2批 | RespCodec + RespCacheClient 实现 |
| P2 | 第3批 | UI 适配（CRUD 重构 + List 面板 + TTL 面板 + PING） |
| P3 | 第4批 | 联调测试 + SCAN/TTL 格式确认后补全 |
| P4 | 第5批 | 多客户端标签页（可选） |

## 团队成员

Group 2 — 四人小组

## 许可证

本项目为实习课题作业，仅供学习参考。
