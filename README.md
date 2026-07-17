# Network Cache Management System

网络缓存管理客户端程序 — 实习课题第四组。

## 项目背景

本项目为西安电子科技大学实习课题，实现**网络缓存管理程序设计**（题目 2），与题目 1（基于 Linux 系统的网络缓存结构设计）配合完成：

- **题目 1** — Linux 服务端：实现缓存核心逻辑（Hash/链表/队列存储、过期策略、epoll 并发访问），类似 redis
- **题目 2（本组）** — 客户端：提供可视化图形界面，通过 **RESP 协议**（Redis 序列化协议）连接并管理服务端缓存

## 功能模块

| 模块 | 说明 |
|------|------|
| 连接管理 | 配置服务端 IP/端口，连接/断开，连接状态显示 |
| 多客户端标签页 | 每个标签页独立连接，各页面独立操作互不干扰 |
| PING 连通性测试 | 检测服务端是否在线 |
| 缓存 CRUD | 添加、删除、清空缓存条目，支持 TTL 过期时间 |
| String 操作 | SET/GET 标准的键值对读写 |
| List 操作 | LPUSH/RPUSH/LPOP/LRANGE 列表类型数据操作，支持按值/按索引删除 |
| TTL 查询 | 查看指定 key 的剩余生存时间 |
| 本地搜索过滤 | 对已加载的缓存数据进行 key 通配符匹配（`*` / `?`） |
| 数据导出 | 将缓存数据导出为 JSON / CSV 格式 |
| 断线重连 | 5 秒心跳定时检测，断开后自动重连 |

## 技术栈

- **语言**: Java 17+
- **UI 框架**: JavaFX 21 + FXML + Scene Builder
- **通信协议**: RESP（REdis Serialization Protocol，文本协议，兼容 redis-cli）
- **网络**: TCP Socket（java.net.Socket）
- **构建**: Maven
- **测试**: JUnit 5 + JaCoCo 代码覆盖率

## 架构设计

本系统采用 **分层架构 + 接口隔离** 设计，界面层通过 `CacheServerClient` 接口与实现层解耦，支持 Mock 和 RESP 双模式切换。

```
┌──────────────────────────────────────┐     RESP 文本协议     ┌──────────────────────┐
│      JavaFX 客户端（本组）           │ ◄──────────────────► │  Linux 缓存服务端     │
│                                      │     TCP :6379        │   （题目 1 组）      │
│  ┌──────────────────────────────┐    │                      │                      │
│  │   界面层（JavaFX + FXML）      │    │                      │  String / List 存储  │
│  │   MainController              │    │                      │  过期管理            │
│  │   TabPaneController           │    │                      │  epoll 并发          │
│  ├──────────────────────────────┤    │                      │                      │
│  │   接口层（CacheServerClient）  │    │                      │                      │
│  │   connect/get/set/del/scan/  │    │                      │                      │
│  │   lpush/rpush/lpop/lrange/   │    │                      │                      │
│  │   ping/ttl/reconnect         │    │                      │                      │
│  ├──────────────────────────────┤    │                      │                      │
│  │   实现层                       │    │                      │                      │
│  │   MockCacheClient（开发用）    │    │                      │                      │
│  │   RespCacheClient（联调用）    │    │                      │                      │
│  │   RespCodec + RespResponse   │    │                      │                      │
│  └──────────────────────────────┘    │                      └──────────────────────┘
└──────────────────────────────────────┘
```

- **CacheServerClient**（接口层）：定义 16 个操作方法，界面层不直接依赖具体实现
- **MockCacheClient**：内存模拟（ConcurrentHashMap），用于界面层独立开发调试
- **RespCacheClient**：RESP-TCP 真实通信，委托 `RespCodec` 进行编解码
- **RespCodec + RespResponse**：RESP 协议编解码工具，位于 `net/protocol/` 子包
- **CacheEntryLoader**：RESP 模式下通过 SCAN+GET+LRANGE 编排加载服务端数据
- **KeyPatternMatcher**：本地通配符匹配工具，支持 `*` 和 `?` 模式

### 工作模式切换

通过 `config.properties` 中的 `client.mock` 配置项切换：

```properties
client.mock=true   # Mock 模式（开发/演示，零外部依赖，默认）
client.mock=false  # RESP 模式（联调/生产，需要第二组服务端运行）
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
mvn javafx:run
# 或直接运行 CacheClientApp.main()

# RESP 模式（需要第二组服务端运行在 127.0.0.1:6379）
# 1. 修改 config.properties 中 client.mock=false
# 2. 启动服务端
# 3. 运行 CacheClientApp.main()
```

## 项目结构

```
src/main/java/com/cache/client/
├── CacheClientApp.java              ← 启动入口 + 客户端注册表
├── model/
│   └── CacheEntry.java              ← 数据模型（String + List 双类型）
├── net/
│   ├── CacheServerClient.java       ← 接口（16 方法契约）
│   ├── MockCacheClient.java         ← Mock 实现（内存模拟）
│   ├── RespCacheClient.java         ← RESP-TCP 实现（真实通信）
│   ├── TcpCacheClient.java          ← [已废弃] TLV 归档
│   └── protocol/
│       ├── RespCodec.java           ← RESP 编解码器
│       └── RespResponse.java        ← 响应类型封装
├── ui/
│   ├── TabPaneController.java       ← 多客户端标签页容器
│   ├── MainController.java          ← 主界面控制器（CRUD/List/TTL/搜索/导出）
│   ├── CacheEntryController.java    ← 条目编辑弹窗
│   └── ConnectController.java       ← 连接设置弹窗
└── util/
    ├── CacheEntryLoader.java        ← RESP 模式数据加载（SCAN+GET+LRANGE）
    ├── Config.java                  ← 配置管理（config.properties）
    ├── ExportUtil.java              ← 数据导出（JSON/CSV）
    └── KeyPatternMatcher.java       ← Key 通配符匹配（* / ?）

src/main/resources/
├── config.properties                ← 应用配置
└── com/cache/client/ui/
    ├── MainContainer.fxml           ← 顶层布局（含 TabPane）
    ├── MainView.fxml                ← 客户端标签页布局
    ├── CacheEntryDialog.fxml        ← 条目编辑弹窗
    └── ConnectDialog.fxml           ← 连接设置弹窗

src/test/java/com/cache/client/
├── net/
│   ├── MockCacheClientTest.java     ← Mock 客户端单元测试（18 用例）
│   ├── RespCacheClientTest.java     ← RESP 客户端测试（12 用例）
│   └── protocol/
│       └── RespCodecTest.java       ← RESP 编解码测试（23 用例）
├── util/
│   ├── CacheEntryLoaderTest.java    ← 数据加载测试（2 用例）
│   ├── ExportUtilTest.java          ← 导出转义测试（3 用例）
│   └── KeyPatternMatcherTest.java   ← 通配符匹配测试（6 用例）
└── verify/
    └── FixVerificationDemo.java     ← 修复验证演示
```

## 开发完成状态

本项目所有开发任务已于 **2026-07-16** 完成，当前处于文档收尾和答辩准备阶段。

| 类别 | 内容 | 状态 |
|:----:|------|:----:|
| 接口层 | `CacheServerClient` 接口（16 方法） | ✅ 完成 |
| Mock 实现 | `MockCacheClient`（34 测试用例） | ✅ 完成 |
| RESP 协议 | `RespCodec` 编解码 + `RespResponse` | ✅ 完成 |
| RESP 客户端 | `RespCacheClient` TCP 通信 | ✅ 完成 |
| 主界面 | CRUD / List / TTL / PING / 搜索 / 导出 | ✅ 完成 |
| 多客户端标签页 | `TabPaneController` 独立实例 + 资源释放 | ✅ 完成 |
| 断线重连 | 5 秒心跳 + 自动 `reconnect()` | ✅ 完成 |
| 跨组联调 | Day13（2026-07-15）与第二组服务端实测通过 | ✅ 完成 |
| 概要设计 | `docs/概要设计.md` | ✅ 完成 |
| 接口规范 | `docs/接口规范.md` | ✅ 完成 |
| 模块结构总览 | `docs/模块结构总览.md` | ✅ 完成 |
| 数据通信详解 | `docs/数据通信全过程详解.md` | ✅ 完成 |
| 详细设计 | `docs/05_详细设计说明书_初版.md` V1.2 | ✅ 完成 |
| 问题记录 | `docs/问题记录.md` | ✅ 完成 |
| 测试文档 | 含 64 个单元测试用例 | ✅ 完成 |
| 答辩 PPT | 答辩准备中 | ⚡ 进行中 |

## 团队成员

第四组 — 四人小组

## 许可证

本项目为实习课题作业，仅供学习参考。
