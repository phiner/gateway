# 🚀 Phiner Gateway

[English](./README.md) | **中文版本**

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.2-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Messaging-red.svg?style=flat-square&logo=redis)](https://redis.io/)
[![Serialization](https://img.shields.io/badge/MsgPack-High--Performance-blue.svg?style=flat-square)](https://msgpack.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

**Phiner Gateway** 是专为工业级量化交易设计的核心中间件。它通过将复杂的 [Dukascopy JForex](https://www.dukascopy.com/) 原生环境转化为语言无关、反应式且高度可扩展的交易基础设施，彻底解决了传统交易平台集成度差、开发限制多等痛点。

---

## 🎯 技术定位

在现代量化交易技术栈中，Phiner Gateway 置于 **接入与规范化层 (Connectivity & Normalization Layer)**。它负责底层 SDK 的极其复杂的生命周期管理（包括链接维持、身份认证、高性能编解码），使策略开发者能够完全脱离底层细节，专注于核心算法。

---

## 💡 为什么使用 Phiner Gateway？(核心价值)

### 1. 彻底打破 "Java 语言限制"
JForex 官方仅支持 Java SDK。通过本网关，所有的行情和交易功能都通过 **Redis Pub/Sub** 暴露。这意味着您可以使用：
- **Python**: 直接利用 Pandas/PyTorch 进行 AI 决策。
- **Go**: 进行高并发指令执行。
- **Node.js**: 开发实时可视化面板。
*无需编写任何一行 Java 代码即可接入 JForex。*

### 2. 极致的数据便利性
- **高性能序列化**: 全面采用 **MessagePack** 二进制协议，相比传统 JSON 减少了 50% 的带宽开销，且编解码速度提升数倍。
- **数据解耦**: 所有的 Tick、K线 (Bar) 都被标准化为一致的消息格式，方便策略层进行多品种聚合分析。
- **天然的缓冲区**: Redis 作为高速缓冲区，可以避免因为策略计算过慢导致的 Broker 连接阻塞。

### 3. 分布式可靠性与热更新
- **架构隔离**: 策略程序的崩溃不会影响网关与券商的链接稳定性。
- **热更新能力**: 您可以随时重启或升级策略逻辑，而无需重新初始化昂贵的行情订阅连接。
- **多端订阅**: 单个网关实例可支持多个策略订阅者同时监听行情。

---

## 🛠️ 技术栈详情

| 组件 | 选用技术 | 说明 |
| :--- | :--- | :--- |
| **运行时 (Runtime)** | Java 21 | 使用虚拟线程 (Project Loom) 技术优雅处理高并发 I/O。 |
| **框架 (Framework)** | Spring Boot 4.0.2 | 构建稳定、可观测、易扩展的微服务基座。 |
| **接入 (SDK)** | JForex SDK 3.6.51 | 与 Dukascopy 服务器建立原生加密二进制链接。 |
| **消息中间件 (Messaging)** | Redis (RESP2) | 负责全量行情的实时分发与交易指令路由。 |
| **序列化 (Serialization)** | MessagePack | 工业级二进制编解码，确保极低的时延表现。 |
| **构建 (DevOps)** | Google Jib | 打造轻量化 Distroless 容器容器，支持跨平台 (x86/ARM) 部署。 |

---

## 📡 数据架构与协议规范

### 行情输出流 (Output)
| 频道 | 含义 | 关键数据项 |
| :--- | :--- | :--- |
| `tick:{Symbol}` | L1 实时盘口 | `时间戳, 卖出价, 买入价` |
| `kline:{Symbol}:{Period}` | OHLC 柱状图 | `开、高、低、收、成交量` |
| `account:status` | 账户实时汇总 | `净值, 保证金, 未实现盈亏` |

### 指令控制口 (Input Control)
通过向以下 Redis 频道发布消息来驱动交易：
- `order:submit`: 提交市价单/限价单/止损报单。
- `order:modify`: 实时动态调整订单的止损止盈。
- `order:cancel`: 撤销尚未成交的挂单。
- `order:close`: 全额或部分平掉现有持仓。

---

## 🚀 快速开始

1. **环境配置**: 在 `.env` 文档中填入您的 JForex 账号信息。
2. **启动网关**:
   ```bash
   ./mvnw spring-boot:run
   ```
3. **策略接入**: 使用任何语言的 Redis 客户端订阅 `tick:EUR/USD`，即可开始接收经过 MessagePack 压缩的实时行情。

---
由 **Phiner** 团队倾力打造，为量化社群提供高效交易动力 ❤️
