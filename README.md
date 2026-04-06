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
JForex is natively a Java SDK. Phiner Gateway exposes all market data via **Redis Stream** and order control via **Redis Pub/Sub**, meaning you can now write your strategies in **Python (NumPy/Pandas/PyTorch)**, **Go (High-performance execution)**, or **Node.js (Real-time dashboards)** without touching a single line of Java.

### 2. High-Density Market Data Streaming
- **Redis Stream Backplane**: Real-time ticks are pushed into Redis Streams. This allows for multi-consumer group distribution and sub-millisecond historical replay.
- **Efficient Normalization**: All market data is normalized into a consistent format (`t`, `a`, `b`, `av`, `bv`), reducing parsing overhead for strategy layers.
- **Reliable Buffering**: Redis acts as a high-speed buffer, preventing backpressure from slow strategy logic from affecting the broker connection.

### 3. Resilience & Lifecycle Management
- **Isolation**: A strategy crash does not impact the connection to the broker.
- **Startup Protection**: The Gateway implements strict readiness checks, only enabling the data flow after a stable connection is verified, preventing "dirty" data during initialization.
- **Scalability**: Subscribing to market data is as simple as reading from a Redis key, supporting massive fan-out to multiple internal consumers.

---

## 🛠️ Technology Stack

| Component | Technology | Role |
| :--- | :--- | :--- |
| **Runtime** | Java 21 (OpenJDK) | Utilizing Virtual Threads (Project Loom) for high-concurrency I/O. |
| **Framework** | Spring Boot 4.x | Core dependency injection, scheduling, and lifecycle management. |
| **Connectivity** | JForex SDK 3.6.x | Native binary connection to Dukascopy servers. |
| **Messaging** | Redis (RESP2/Stream) | Backplane for market data distribution and command routing. |
| **Serialization** | Native / MsgPack | Hybrid serialization optimized for speed and payload size. |
| **Containerization** | Google Jib | Minimalist distroless container builds (Multi-arch). |

---

## 📡 Data Schema & Protocol

### Market Data (Output Streams)
| Channel | Mechanism | Data Structure |
| :--- | :--- | :--- |
| `gateway:ticks:stream:{Symbol}` | **Stream** | `t(Time), a(Ask), b(Bid), av(AskVol), bv(BidVol)` |
| `gateway:kline:{Symbol}:{Period}` | Pub/Sub | `Open, High, Low, Close, Volume` (MsgPack) |
| `gateway:account:status` | Pub/Sub | `Equity, Margin, Unrealized P/L` (MsgPack) |

### Command Interface (Input Control)
Trigger trades by publishing data to these channels:
- `gateway:order:submit`: Submit Market/Limit/Stop orders.
- `gateway:order:modify`: Updates SL/TP on the fly.
- `gateway:order:cancel`: Terminate pending orders.
- `gateway:order:close`: Full or partial position liquidation.

See [Gateway API Documentation](./docs/gateway_api.md) for details.

---

## 🚀 Quick Start

1. **Configure Environment**: Set your credentials and **Redis connection** in `.env`:
   ```bash
   REDIS_HOST=your-redis-host
   REDIS_PORT=6379
   REDIS_PASSWORD=optional-password
   ```
2. **Launch Gateway**:
   ```bash
   ./mvnw spring-boot:run
   ```
3. **Connect your Strategy**: Read from the Stream for ticks and subscribe to channels for events.

**Example: Read EUR/USD ticks using Node.js (Streams)**
```javascript
const Redis = require('ioredis');
const redis = new Redis(); // Connects to localhost:6379 by default

async function trackTicks() {
  let lastId = '$'; // Start reading from the newest entry
  console.log("Listening to EUR/USD Stream...");
  
  while (true) {
    // Blocking read from Redis Stream
    const result = await redis.xread('BLOCK', 5000, 'STREAMS', 'gateway:ticks:stream:EUR/USD', lastId);
    
    if (result) {
      const [stream, messages] = result[0];
      for (const [id, data] of messages) {
        lastId = id;
        // Data is returned as a flat array [field, value, field, value...]
        console.log(`New Tick [${id}]:`, data);
      }
    }
  }
}

trackTicks().catch(console.error);
```

---
Built with ❤️ for the Quant community by **Phiner**.
