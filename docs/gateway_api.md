# Phiner Gateway API 文档 (Redis Pub/Sub & Storage)

本文档详细描述了 Gateway 与外部应用（策略、前端、监控等）之间通过 Redis 进行交互的 API 规范。所有键名和频道均已统一至 `gateway:` 命名空间。

## ⚠️ 重要规范说明
1. **序列化协议**: 所有业务对象（DTO/Request）必须使用 **MessagePack** (msgpack) 进行二进制序列化与反序列化。纯文本信息（如错误、部分通知）使用普通 UTF-8 字符串。
2. **命名无损转换**: 
   - **交易品种 (Instrument)**: 严格保留原始斜杠（如 `EUR/USD`），网关不再做自动补全。
   - **时间周期 (Period)**: 严格使用缩写短格式（如 `1m`, `5m`, `15m`, `1h`, `1d`, `1w`, `1M`）。网关会自动将 JForex 原始名称映射为这些短代码。
   - **订单标签 (Label)**: 必须符合 JForex 规范（仅字母、数字、下划线，且不能以数字开头）。网关不再做静默替换，若出现非法字符只会产生错误日志。
3. **编程示例**: 本文档所有订阅与交互示例一律使用 **Rust** 编写。

---

## 1. 存储类键 (Storage Keys)

Gateway 会将全量状态或快照写入以下 Redis 键中，客户端可主动读取 (GET, HGETALL, LRANGE 等)。

| 键名 (Key) | 数据结构 (Redis Type) | 值格式 (Value) | 描述与使用场景 |
| :--- | :--- | :--- | :--- |
| `gateway:kline:{Instrument}:{Period}` | `Sorted Set (ZSET)` | Msgpack `BarDTO` | **K线缓存**。使用时间戳作为 Score。使用 `ZREVRANGE 0 -1` 获取限定长度的历史K线（由新到旧）。 |
| `gateway:positions:active` | `Hash` | Msgpack `PositionDTO` | **活跃持仓**。Field=`dealId`。客户端可通过获取这个 Hash 的所有值来构建当前全量持仓视图。 |
| `gateway:orders:history` | `Hash` | Msgpack `OrderHistoryDTO` | **订单历史**。Field=`dealId`。客户端拉取历史记录的持久化存储点。 |
| `gateway:history:coverage` | `Hash` | String (`"min,max"`) | **历史覆盖区间**。Field=`Instrument`。记录本地某品种拥有的历史数据时间戳范围。 |
| `gateway:config:instruments` | `Set` | String | **已订阅的外汇品种列表**。通过 `SMEMBERS` 可获取系统当前支持的产品。 |
| `gateway:config:periods` | `Set` | String | **配置的K线周期列表**。 |
| `gateway:config:instrument_info` | `Hash` | Msgpack `InstrumentInfoDTO`| **产品元数据**。Field=`Instrument`。包含合约乘数、点值、最小交易量等。 |

---

## 2. 收听频道 (Gateway -> Client Pub/Sub)

外部应用通过 `SUBSCRIBE` 监听这些频道，以接收 Gateway 实时推送的市场数据和事件。

### 行情与状态推送
| 频道名 (Topic) | 消息体格式 | 触发机制与用途 |
| :--- | :--- | :--- |
| `gateway:tick:{Instrument}` | Msgpack `TickDTO` | L1 逐笔报价（Tick）更新推流。 |
| `gateway:kline:{Instrument}:{Period}` | Msgpack `BarDTO` | K 线（Bar）走完后（或者每个Tick更新中）的实时推流。 |
| `gateway:account:status` | Msgpack `AccountStatusDTO`| 账户余额、净值、已用保证金等状态推送（随心跳发送）。 |
| `gateway:status` | Msgpack `GatewayStatusDTO`| 网关自身健康及连接状态（如 `CONNECTED` / `DISCONNECTED`）。 |
| `gateway:order:event` | Msgpack `OrderEventDTO` | 订单生命周期事件（如 开仓成功、平仓、修改报单、遭拒等）。 |
| `gateway:error` | String | 系统级异常与错误日志下发机制。 |
| `gateway:info` | String | 系统一般性通知（如“成功订阅产品”）。 |
| `gateway:error:structured` | Msgpack `ErrorDTO` | **结构化错误通知**。当 Dukascopy 服务端拒绝订单或发生业务错误时发布。详见 [错误通知](#5-错误通知) 章节。 |

### 事件通知（提示客户端去 Storage Keys 拉取最新数据）
| 频道名 (Topic) | 消息体格式 | 触发机制与用途 |
| :--- | :--- | :--- |
| `gateway:positions:updated` | String (Timestamp) | 当网关完成完整的持仓同步并更新了 `gateway:positions:active` 后触发。收到该通知后，客户端应去拉取最新的 Hash。 |
| `gateway:orders:history:updated`| String (Instrument 或是 `ALL`) | 历史订单请求（History Sync）完成并写入数据库后的广播。 |

### 响应通知
| 频道名 (Topic) | 消息体格式 | 触发机制与用途 |
| :--- | :--- | :--- |
| `gateway:info:instrument:response:{reqId}` | Msgpack `InstrumentInfoDTO`| 回送由客户端发起的请求 `gateway:system:request:instrument_info` 的结果。 |

---

## 3. 指令频道 (Client -> Gateway Pub/Sub)

外部应用向这些频道发布指令，来控制 Gateway （下单、平仓等）。

### 交易指令 (Trading Commands)
发送到以下频道的消息将被路由到 Dukascopy 服务端。必须发送预定义 Request 对象的 Msgpack 字节流。

| 频道名 (Topic) | 请求类型 (Msgpack) | 描述 |
| :--- | :--- | :--- |
| `gateway:order:open` | `OpenMarketOrderRequest` | **快捷市价开仓**。 |
| `gateway:order:submit` | `SubmitOrderRequest` | **万能下单**。支持市价、限价、止损等所有类型。 |
| `gateway:order:close` | `CloseMarketOrderRequest` | **主动平仓**。根据 `orderId` 全平或部分平仓。 |
| `gateway:order:modify` | `ModifyOrderRequest` | **修改订单**。主要用于更新 SL (止损) 和 TP (止盈)。 |
| `gateway:order:cancel` | `CancelOrderRequest` | **取消挂单**。 |

### 系统指令 (System Requests)
| 频道名 (Topic) | 请求类型 (Msgpack) | 描述 |
| :--- | :--- | :--- |
| `gateway:system:request:instrument_info` | `InstrumentInfoRequest` | 主动请求指定产品的详细信息。网关将返回至专属响应频道。 |
| `gateway:system:request:orders_history` | `OrdersHistoryRequest` | 请求拉取特定品种的历史订单快照并同步到 Redis。 |
| `gateway:system:request:positions` | `PositionsRequest` | 强制网关执行一次持仓防抖同步，并将结果写入活跃持仓 Hash。 |

---

## 4. 核心数据结构示意 (DTO)

为便于使用其他语言（如 Python、Go、Node.js）接入，以下仅列出关键字段属性。具体序列化请严格按照字段名构造 MessagePack 字典。

**TickDTO (行情实时报价)**
```json
{
  "instrument": "EUR/USD",
  "time": 1700000000000,
  "ask": 1.05012,
  "bid": 1.05008
}
```

**BarDTO (K线)**
```json
{
  "instrument": "EUR/USD",
  "period": "5m",
  "time": 1700000000000,
  "open": 1.05010,
  "high": 1.05025,
  "low": 1.04990,
  "close": 1.05020,
  "volume": 1500.2
}
```

**SubmitOrderRequest (万能下单参数)**
```json
{
  "instrument": "GBP/USD",
  "orderCommand": "BUY", // BUY, SELL, BUYLIMIT, SELLLIMIT, BUYSTOP, SELLSTOP
  "amount": 0.1, // 10,000 单位
  "label": "MyStrategy_001", // 必须仅包含 a-zA-Z0-9_ 且不能数字开头
  "comments": "5m", // 订单备注，记录周期信息（如 "5m", "15m"）
  "price": 0, // 限价/止损单需指定
  "slippage": 5.0, 
  "stopLossPrice": 0, 
  "takeProfitPrice": 0 
}
```

## 5. 代码与接入示例 (Examples)

下面提供了常见的命令行（Redis CLI）与 Python 代码示例，展示如何读取数据、监听行情和发送指令。

### 5.1 纯命令行 (Redis CLI) 示例

**读取最新持仓 (Storage Hash)**
```bash
# 获取所有活跃持仓的 dealId 和 Msgpack 序列化数据（终端可能显示乱码）
redis-cli HGETALL gateway:positions:active

# 查看支持的产品列表
redis-cli SMEMBERS gateway:config:instruments
```

**订阅实时 K 线与 Tick (Pub/Sub)**
```bash
# 订阅 EUR/USD 的 5分钟 K线推送（实时）
redis-cli SUBSCRIBE gateway:kline:EUR/USD:5m

# 读取存储的离线数据：获取 EUR/USD 5分钟周期的最近 10 条 K 线
# 使用 ZREVRANGE 从最高分数（最新）向最低分数（最旧）拉取
redis-cli ZREVRANGE gateway:kline:EUR/USD:5m 0 9

# 订阅系统错误日志
redis-cli SUBSCRIBE gateway:error
```

### 5.2 Rust 接入示例 (需 `redis` 和 `rmp-serde` 库)

**示例 A：监听实时行情 (Tick Subscription)**
```rust
use redis::PubSubCommands;
use serde_json::Value;

fn main() -> redis::RedisResult<()> {
    let client = redis::Client::open("redis://127.0.0.1/")?;
    let mut con = client.get_connection()?;
    let mut pubsub = con.as_pubsub();

    // 订阅 EUR/USD 的实时 Tick 推送
    pubsub.subscribe("gateway:tick:EUR/USD")?;

    println!("正在监听 Tick 数据...");
    loop {
        let msg = pubsub.get_message()?;
        let payload: Vec<u8> = msg.get_payload()?;
        
        // 使用 rmp-serde 解码 MessagePack 数据
        let tick: Value = rmp_serde::from_slice(&payload).map_err(|e| {
            redis::RedisError::from((redis::ErrorKind::TypeError, "Msgpack decode error", e.to_string()))
        })?;
        
        println!("[{}] Bid: {} Ask: {}", tick["instrument"], tick["bid"], tick["ask"]);
    }
}
```

**示例 B：发送万能下单指令 (Submit Order)**
```rust
use redis::Commands;
use serde_json::json;

fn main() -> redis::RedisResult<()> {
    let client = redis::Client::open("redis://127.0.0.1/")?;
    let mut con = client.get_connection()?;

    // 构造请求对象
    let order_request = json!({
        "instrument": "GBP/USD",
        "orderCommand": "BUY",
        "amount": 0.1,
        "label": "Rust_Strategy_01",
        "comments": "5m",
        "price": 0.0,
        "slippage": 5.0,
        "stopLossPrice": 0.0,
        "takeProfitPrice": 1.3000
    });

    // 序列化为 MessagePack 字节流
    let packed = rmp_serde::to_vec(&order_request).map_err(|e| redis::RedisError::from((redis::ErrorKind::TypeError, "Msgpack encode error", e.to_string())))?;

    // 发布到网格的指令频道
    con.publish("gateway:order:submit", packed)?;
    println!("已通过 Rust 发送下单指令。");
    Ok(())
}
```

**示例 C：读取 K 线历史缓存 (ZSET Storage)**
```rust
use redis::Commands;
use serde_json::Value;

fn main() -> redis::RedisResult<()> {
    let client = redis::Client::open("redis://127.0.0.1/")?;
    let mut con = client.get_connection()?;

    let kline_key = "gateway:kline:EUR/USD:15m";
    
    // 使用 ZREVRANGE 获取按时间戳降序（从新到旧）排列的 Bar 字节流列表
    let raw_bars: Vec<Vec<u8>> = con.zrevrange(kline_key, 0, -1)?;

    println!("从 Sorted Set 加载了 {} 条 K 线。", raw_bars.len());
    for raw_bar in raw_bars {
        let bar: Value = rmp_serde::from_slice(&raw_bar).unwrap();
        println!("时间: {}, 收盘价: {}, 成交量: {}", bar["time"], bar["close"], bar["volume"]);
    }
    Ok(())
}
```

---

## 5. 错误通知

当 Dukascopy 服务端拒绝订单或发生业务错误时，网关会发布结构化错误通知。

### ErrorDTO 结构
```json
{
  "code": "LABEL_NOT_UNIQUE",
  "message": "Order label already exists. Each order label must be unique.",
  "type": "ORDER_ERROR",
  "timestamp": 1700000000000,
  "orderId": null,
  "instrument": "EUR/USD",
  "label": "my_order_label",
  "context": "Open Market Order [EUR/USD]"
}
```

### 错误码参考

| 错误码 | 说明 | 推荐处理 |
| :--- | :--- | :--- |
| `LABEL_NOT_UNIQUE` | 订单标签已存在 | 使用唯一标签或生成新标签 |
| `LABEL_INCONSISTENT` | 标签与现有订单冲突 | 检查订单状态 |
| `INVALID_AMOUNT` | 无效订单数量 | 验证数量在限制范围内 |
| `ORDER_INCORRECT` | 订单参数不正确 | 检查价格、止损、止盈值 |
| `ORDER_STATE_IMMUTABLE` | 当前状态无法修改 | 等待订单成交 |
| `ORDER_CANCEL_INCORRECT` | 当前状态无法取消 | 检查订单状态 |
| `ORDERS_UNAVAILABLE` | 交易不可用 | 稍后重试 |
| `QUEUE_OVERLOADED` | 订单队列已满 | 等待后重试 |
| `ZERO_PRICE_NOT_ALLOWED` | 不允许零价格 | 设置有效价格 |
| `INVALID_GTT` | 止损有效期设置无效 | 检查时间参数 |
| `NO_ACCOUNT_SETTINGS_RECEIVED` | 账户未就绪 | 等待连接 |
| `CALL_INCORRECT` | API 在错误线程调用 | 使用 context.executeTask() |
| `COMMAND_IS_NULL` | 订单命令为空 | 提供有效命令 |
| `VALIDATION_ERROR` | 输入验证失败 | 检查请求参数 |
| `NULL_POINTER` | 空引用错误 | 检查必填字段 |
| `CONTEXT_NOT_INITIALIZED` | 网关未完全启动 | 等待连接 |

### 订阅示例 (Rust)
```rust
use redis::PubSubCommands;
use serde_json::Value;

fn main() -> redis::RedisResult<()> {
    let client = redis::Client::open("redis://127.0.0.1/")?;
    let mut con = client.get_connection()?;
    let mut pubsub = con.as_pubsub();
    
    pubsub.subscribe("gateway:error:structured")?;

    println!("正在等待结构化错误通知...");
    loop {
        let msg = pubsub.get_message()?;
        let payload: Vec<u8> = msg.get_payload()?;
        let error: Value = rmp_serde::from_slice(&payload).map_err(|e| redis::RedisError::from((redis::ErrorKind::TypeError, "Msgpack decode error", e.to_string())))?;
        println!("错误代码: {} - 消息: {}", error["code"], error["message"]);
    }
}
```
