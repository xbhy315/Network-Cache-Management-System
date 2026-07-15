# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Network Cache Management System — 实习课题第二组客户端程序。

本项目为西安电子科技大学实习课题题目 2，与题目 1（Linux 缓存服务端组）通过自定义 TCP 协议配合完成。当前处于开发初期，尚未提交实质源码。

## Tech Stack

- **Language**: Java (JDK 17+)
- **UI Framework**: JavaFX + FXML + Scene Builder
- **Network**: Java Socket / NIO
- **Build Tool**: Maven 或 Gradle（待定，确认后更新此处）
- **Version Control**: GitHub

## Architecture

分层设计，通过接口解耦界面层与通信层：

```
FXML Controller (界面层)
       ↓ 调用
CacheServerClient (接口层)
       ↓ 实现
MockCacheClient  ── 开发调试用，返回硬编码模拟数据
TcpCacheClient   ── 真实实现，通过 TCP 与题目 1 服务端通信（协议待定）
```

### 核心类型

- `CacheServerClient` — 接口，定义缓存操作契约（get/set/delete/stats 等）
- `CacheEntry` — 数据模型（key, value, ttl, createTime 等）
- `MockCacheClient` — 界面先行开发时使用，不依赖服务端
- `TcpCacheClient` — 联调阶段接入（服务端协议确定后实现）

## Key Context

- 尚未确认通信协议，正在等待题目 1 组确定
- 已获导师确认：可用 Java+JavaFX 替代 Qt
- 四人小组协作，GitHub 管理代码
- Day 8（第七个开发日前后）有一次文档答辩，需在此之前产出概要设计、详细设计文档
