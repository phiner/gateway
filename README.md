# 🚀 Phiner Gateway

**English** | [中文版本](./README_CN.md)

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.2-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Messaging-red.svg?style=flat-square&logo=redis)](https://redis.io/)
[![Serialization](https://img.shields.io/badge/MsgPack-High--Performance-blue.svg?style=flat-square)](https://msgpack.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

**Phiner Gateway** is a high-performance, industrial-grade middleware designed to bridge the gap between [Dukascopy JForex](https://www.dukascopy.com/) and modern distributed trading systems. It transforms the legacy, Java-restricted JForex environment into a language-agnostic, reactive, and highly scalable trading infrastructure.

---

## 🎯 Technical Positioning

In a modern quantitative trading stack, Phiner Gateway sits as the **Connectivity & Normalization Layer**. It abstracts away the complexities of the JForex SDK, handling connection persistence, authentication, and heavy-duty data serialization, allowing developers to focus purely on strategy logic.

---

## 💡 Why Phiner Gateway? (Value Proposition)

### 1. Break the "Java-Only" Barrier
JForex is natively a Java SDK. Phiner Gateway exposes all functionality via **Redis Pub/Sub and Hashes**, meaning you can now write your strategies in **Python (NumPy/Pandas/PyTorch)**, **Go (High-performance execution)**, or **Node.js (Real-time dashboards)** without touching a single line of Java.

### 2. High-Density Market Data Streaming
Standard JSON streaming is slow and heavy. We use **MessagePack (binary)** to stream ticks and bars. This provides:
- **Lower Latency**: Faster serialization/deserialization.
- **Smaller Footprint**: Up to 50% reduction in bandwidth compared to JSON.
- **Backpressure Handling**: Redis acting as a high-speed buffer between the broker and your strategy.

### 3. Distributed & Resilient Architecture
Unlike running a strategy directly inside JForex:
- **Isolation**: A crash in your strategy doesn't kill the connection to the broker.
- **Scalability**: Multiple strategy instances can subscribe to the same market data stream from a single Gateway instance.
- **Hot Updates**: Restart or update your strategy logic while the Gateway maintains the market data connection and session state.

---

## 🛠️ Technology Stack

| Component | Technology | Role |
| :--- | :--- | :--- |
| **Runtime** | Java 21 (OpenJDK) | Utilizing Virtual Threads (Project Loom) for high-concurrency I/O. |
| **Framework** | Spring Boot 4.0.2 | Core dependency injection, scheduling, and observability. |
| **Connectivity** | JForex SDK 3.6.51 | Native binary connection to Dukascopy servers. |
| **Messaging** | Redis (RESP2) | Backplane for market data distribution and command routing. |
| **Serialization** | MessagePack | Production-grade binary serialization for all DTOs. |
| **Containerization** | Google Jib | Minimalist distroless container builds (Multi-arch). |

---

## 📡 Data Schema & Protocol

### Market Data (Output Streams)
| Channel | Description | Data Structure |
| :--- | :--- | :--- |
| `tick:{Symbol}` | L1 Market Data | `Time, Ask, Bid` |
| `kline:{Symbol}:{Period}` | OHLC Bar Data | `Time, Open, High, Low, Close, Volume` |
| `account:status` | Real-time balance | `Equity, Margin, Unrealized P/L` |

### Command Interface (Input Control)
Trigger trades by publishing to these channels:
- `order:submit`: Submit Market/Limit/Stop orders.
- `order:modify`: Updates SL/TP on the fly.
- `order:cancel`: Terminate pending orders.
- `order:close`: Full or partial position liquidation.

---

## 🚀 Quick Start

1. **Configure Environment**: Set your JForex credentials in `.env`.
2. **Launch Gateway**:
   ```bash
   ./mvnw spring-boot:run
   ```
3. **Connect your Strategy**: Use any Redis client to subscribe to `tick:EUR/USD` and start receiving binary MessagePack data.

---
Built with ❤️ for the Quant community by **Phiner**.
