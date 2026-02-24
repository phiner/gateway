# Redis API 对接文档
本文档定义了与 JForex 交易网关进行交互的 Redis Pub/Sub 接口。它旨在供其他服务（包括 AI 工具）使用。

**序列化**: 所有数据负载都使用 **MessagePack (MsgPack)** 进行序列化，而不是原始 JSON。下面的 JSON 示例仅代表数据的逻辑结构。

---

## 交互模式

与网关交互主要有三种方式：

1.  **广播频道**: 网关发布实时数据，如市场价格、订单事件和状态更新。客户端订阅这些频道以接收连续的信息流。
2.  **请求/响应频道**: 客户端在一个频道上发送请求，并在一个独立的、唯一的频道上监听响应。这用于获取如交易品种详情之类的数据。
3.  **指令频道**: 客户端向特定频道发送一个“即发即忘”的指令，以触发交易行为，如开仓或平仓。

---

## 1. 广播频道 (网关 -> 客户端)
网关在这些频道上广播信息。客户端应该 **SUBSCRIBE** (订阅) 它们。

### 市场数据

**重要提示**: 所有频道名称中的交易品种（instrument），例如 `EUR/USD`，在 Redis 的 Channel 和 Key 中都会**完整保留斜杠 (`/`)**。下文所有示例均已遵循此格式。

#### ▶️ `tick:{instrument}`
发布一个交易品种的最新买卖报价。
*   **频道示例**: `tick:EUR/USD`
*   **负载示例**:
    ```json
    {
      "instrument": "EUR/USD",
      "time": 1678886400000,
      "ask": 1.07501,
      "bid": 1.07500
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `instrument` | String | 是 | 交易品种名称 (带斜杠) |
    | `time` | Long | 是 | Unix 时间戳 (毫秒) |
    | `ask` | Double | 是 | 卖出价 |
    | `bid` | Double | 是 | 买入价 |

#### ▶️ `kline:{instrument}:{period}`
发布一个指定品种和周期的已闭合K线。
*   **频道示例**: `kline:EUR/USD:1Min`
*   **负载示例**:
    ```json
    {
      "instrument": "EUR/USD",
      "period": "1Min",
      "time": 1678886400000,
      "open": 1.07499,
      "close": 1.07509,
      "low": 1.07489,
      "high": 1.07519
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `instrument` | String | 是 | 交易品种名称 (带斜杠) |
    | `period` | String | 是 | K线周期, e.g., "1Min", "5Min", "1Hour", "Daily" |
    | `time` | Long | 是 | K线开盘时间 (Unix 毫秒) |
    | `open` | Double | 是 | 开盘价 |
    | `close` | Double | 是 | 收盘价 |
    | `low` | Double | 是 | 最低价 |
    | `high` | Double | 是 | 最高价 |

#### 📈 获取K线历史数据

除了通过 Pub/Sub 订阅实时的K线广播，客户端还可以直接从 Redis 中拉取最近的 K 线历史记录。网关将每个品种和周期的 K 线存储在一个 **Redis List** 中。

*   **操作**: 使用 Redis 的 `LRANGE` 命令。
*   **Key 格式**: `kline:{instrument}:{period}`
*   **Key 示例**: `kline:EUR/USD:1Min`
*   **命令示例**: `LRANGE kline:EUR/USD:1Min 0 -1`

**响应**:

该命令会返回一个 MessagePack 编码的 `BarDTO` 对象列表。列表中的第一个元素是**最新**的 K 线，最后一个元素是**最旧**的。客户端需要自行解码每个元素。

**存储限制**:

K 线的历史数据量是**有限的**。这个上限在网关的 `application.yml` 配置文件中通过 `gateway.kline.storage-limit` 属性进行定义。例如，如果设置为 `45`，则该列表最多只会保留最新的 45 根 K 线。

### 账户与订单事件

#### ▶️ `order:event`
实时发布与订单生命周期相关的事件。该数据结构直接反映了 JForex 平台的消息和订单对象。
*   **负载示例**:
    ```json
    {
      "messageId": "D3a-fGv-42s",
      "eventType": "ORDER_FILL_OK",
      "creationTime": 1678886410000,
      "reason": null,
      "orderLabel": "strategy-alpha-01",
      "instrument": "EUR/USD",
      "orderState": "FILLED",
      "orderCommand": "BUY",
      "amount": 0.01,
      "openPrice": 1.07515,
      "fillTime": 1678886410000,
      "closePrice": null,
      "closeTime": null
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `messageId`| String | 是 | 来自平台的唯一订单/消息ID |
    | `eventType`| String | 是 | JForex事件类型, e.g., "ORDER_SUBMIT_OK", "ORDER_FILL_OK", "ORDER_CLOSE_OK" |
    | `creationTime`| Long | 是 | 事件的Unix时间戳 (毫秒) |
    | `reason` | String | 否 | 消息附带的原因, 例如订单被拒绝的理由 |
    | `orderLabel` | String | 否 | 订单的自定义标签 |
    | `instrument`| String | 是 | 交易品种名称 (带斜杠) |
    | `orderState`| String | 是 | 订单状态, e.g., "CREATED", "OPENED", "FILLED", "CLOSED" |
    | `orderCommand`| String | 是 | 订单指令, e.g., "BUY", "SELL", "BUY_LIMIT" |
    | `amount` | Double | 是 | 订单数量 (手数) |
    | `openPrice`| Double | 是 | 开仓价格 |
    | `fillTime` | Long | 否 | 订单成交时间 (Unix 毫秒) |
    | `closePrice`| Double | 否 | 平仓价格 |
    | `closeTime` | Long | 否 | 订单关闭时间 (Unix 毫秒) |

#### ▶️ `account:status`
在任何导致账户资金变动的事件后发布，用于同步当前账户的余额和净值。
*   **负载示例**:
    ```json
    {
      "balance": 10000.00,
      "equity": 10050.55
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `balance` | Double | 是 | 账户余额 |
    | `equity` | Double | 是 | 账户净值 (余额 + 未实现盈亏) |

### 系统通知

#### ▶️ `gateway:status`
发布网关的连接状态。
*   **负载示例**:
    ```json
    {
      "service": "gateway",
      "status": "CONNECTED",
      "timestamp": 1678886401000,
      "message": "交易策略已启动并连接到Dukascopy。"
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `service` | String | 是 | 表明消息来源的服务, 固定为 "gateway" |
    | `status` | String | 是 | "CONNECTED" (已连接) 或 "DISCONNECTED" (已断开) |
    | `timestamp` | Long | 是 | 事件的Unix时间戳 (毫秒) |
    | `message` | String | 是 | 描述性信息 |

#### ▶️ `gateway:error`
发布来自网关的错误消息。负载为 **原始字符串 (Raw String)**。
*   **负载示例**: `找不到交易品种: EUR/USD`

#### ▶️ `gateway:info`
发布来自网关的通用信息性消息。负载为 **原始字符串 (Raw String)**。
*   **负载示例**: `交易策略已启动。`

---

## 2. 请求/响应频道
客户端 **PUBLISH** (发布) 一个请求，并在指定的频道上监听响应。**注意**: 即使是动态请求的信息，网关在响应的同时也会更新 `gateway:config:instrument_info` 静态键。

### 获取交易品种信息

1.  **客户端发布请求**:
    *   **频道**: `system:request:instrument_info`
    *   **负载示例**:
        ```json
        {
          "requestId": "ai-tool-req-12345",
          "instrument": "EUR/USD"
        }
        ```
    *   **字段说明**:
        | 字段名 | 类型 | 是否必需 | 描述 |
        | :--- | :--- | :--- | :--- |
        | `requestId`| String | 是 | 由客户端生成的唯一请求ID |
        | `instrument`| String | 是 | 需要查询的交易品种 (带斜杠) |

2.  **网关发布响应**:
    *   **频道**: `info:instrument:response:{requestId}` (`requestId` 是请求中包含的ID)。
    *   **负载示例**:
        ```json
        {
          "name": "EUR/USD",
          "currency": "EUR/USD",
          "pip": 0.0001,
          "point": 1e-05,
          "description": "EUR/USD"
        }
        ```
    *   **字段说明**:
        | 字段名 | 类型 | 是否必需 | 描述 |
        | :--- | :--- | :--- | :--- |
        | `name` | String | 是 | 交易品种名称 (带斜杠) |
        | `currency` | String | 是 | 基础/报价货币对 |
        | `pip` | Double | 是 | **[MANDATORY]** 一个点的价值 (e.g., 0.0001) |
        | `point`| Double | 是 | **[MANDATORY]** 以微点为单位的点值 (由 SDK 的 TickScale 自动换算) |
        | `description`| String | 是 | 交易品种描述 |
        | `minTradeAmount`| Double | 是 | **[MANDATORY]** 最小交易数量 (手数) |

### 获取所有当前持仓

1.  **客户端发布请求**:
    *   **频道**: `system:request:positions`
    *   **负载示例**:
        ```json
        {
          "requestId": "sync-req-999"
        }
        ```

2.  **网关发布响应**:
    *   **频道**: `info:positions:response:{requestId}`
    *   **负载示例**:
        ```json
        {
          "positions": [
            {
              "dealId": "D123",
              "dealReference": "PivotSniper-1Min-EUR/USD",
              "instrument": "EUR/USD",
              "direction": "BUY",
              "amount": 0.01,
              "openPrice": 1.075,
              "profitLoss": 15.2
            }
          ]
        }
        ```

---

## 3. 指令频道 (客户端 -> 网关)
客户端向这些频道 **PUBLISH** (发布) 指令以执行交易操作。

### 市价开仓
*   **频道**: `order:open`
*   **负载示例**:
    ```json
    {
      "instrument": "EUR/USD",
      "orderType": "BUY",
      "amount": 0.01,
      "label": "strategy-alpha-01",
      "slippage": 3,
      "stopLossPrice": 1.07000,
      "takeProfitPrice": 1.08000
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `instrument` | String | 是 | 交易品种名称 (带斜杠) |
    | `orderType` | String | 是 | "BUY" 或 "SELL" |
    | `amount` | Double | 是 | 交易量 (手数) |
    | `label` | String | 否 | 自定义订单标签或备注 |
    | `slippage` | Double | 否 | 允许的最大滑点 (点) |
    | `stopLossPrice` | Double | 否 | 止损价格 |
    | `takeProfitPrice` | Double | 否 | 止盈价格 |

### 市价平仓
*   **频道**: `order:close`
*   **负载示例**:
    ```json
    {
      "orderId": "D3a-fGv-42s"
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `orderId` | String | 是 | 要平仓的订单的唯一ID |

### 提交挂单
*   **频道**: `order:submit`
*   **负载示例 (限价买单)**:
    ```json
    {
      "instrument": "EUR/USD",
      "orderCommand": "BUY_LIMIT",
      "amount": 0.01,
      "price": 1.07200,
      "label": "pending-buy-eurusd",
      "stopLossPrice": 1.07000,
      "takeProfitPrice": 1.08000
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `instrument` | String | 是 | 交易品种名称 (带斜杠) |
    | `orderCommand` | String | 是 | 挂单指令, e.g., "BUY_LIMIT", "SELL_STOP" |
    | `amount` | Double | 是 | 交易量 (手数) |
    | `price` | Double | 是 | 挂单价格 |
    | `label` | String | 否 | 自定义订单标签或备注 |
    | `stopLossPrice` | Double | 否 | 止损价格 |
    | `takeProfitPrice` | Double | 否 | 止盈价格 |

### 修改订单
*   **频道**: `order:modify`
*   **负载示例**:
    ```json
    {
      "orderId": "D3a-fGv-42s",
      "stopLossPrice": 1.07100,
      "takeProfitPrice": 1.08100
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `orderId` | String | 是 | 要修改的订单的唯一ID |
    | `stopLossPrice` | Double | 否 | 新的止损价格 |
    | `takeProfitPrice` | Double | 否 | 新的止盈价格 |

### 取消订单
*   **频道**: `order:cancel`
*   **负载示例**:
    ```json
    {
      "orderId": "D3a-fGv-42s"
    }
    ```
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `orderId` | String | 是 | 要取消的挂单的唯一ID |

---

## 4. 系统配置 (静态 Keys)
这些 Key 存储了网关的当前配置，供其他应用在启动或运行时读取。

#### ⚙️ `gateway:config:instruments` (Set)
存储网关当前订阅的所有交易品种。
*   **Key**: `gateway:config:instruments`
*   **类型**: Redis Set
*   **命令示例**: `SMEMBERS gateway:config:instruments`
*   **内容示例**: `EUR/USD`, `GBP/USD`, `USD/JPY`

#### ⚙️ `gateway:config:periods` (Set)
存储网关当前处理的所有 K 线周期。
*   **Key**: `gateway:config:periods`
*   **类型**: Redis Set
*   **命令示例**: `SMEMBERS gateway:config:periods`
*   **内容示例**: `FIVE_MINS`, `FIFTEEN_MINS`, `DAILY`

* ### 引擎全局配置

- **名称**: `gateway:config:trade_multiplier`
- **类型**: String (Key-Value)
- **描述**: 存储全局开仓手数的放大倍数。引擎在每次计算最终开仓量时 (minTradeAmount * multiplier) 会实时读取此值，默认值为 `1.0`。此值由引擎根据配置或环境变量在启动时写入，也可支持网关动态修改。
*   **说明**: 该值基于品种的 `minTradeAmount` 乘以全局倍数 `TRADE_LOTS_MULTIPLIER` 计算得出。

#### ⚙️ `gateway:config:instrument_info` (Hash)
存储每个交易品种的详细属性（Pip值、最小报价单位等）。
*   **Key**: `gateway:config:instrument_info`
*   **类型**: Redis Hash
*   **Field**: 交易品种名称 (e.g., `EUR/USD`)
*   **Value**: MessagePack 编码的 `InstrumentInfoDTO` 对象
*   **命令示例**: `HGET gateway:config:instrument_info "EUR/USD"`
*   **字段说明**:
    | 字段名 | 类型 | 是否必需 | 描述 |
    | :--- | :--- | :--- | :--- |
    | `name` | String | 是 | 交易品种名称 (带斜杠) |
    | `currency` | String | 是 | 基础/报价货币对 |
    | `pip` | Double | 是 | **[MANDATORY]** 一个点的价值 (e.g., 0.0001) |
    | `point`| Double | 是 | **[MANDATORY]** 以微点为单位的点值 (由 SDK 的 TickScale 自动换算) |
    | `description`| String | 是 | 交易品种描述 (当前内容与 name 相同) |
    | `minTradeAmount`| Double | 是 | **[MANDATORY]** 最小交易数量 (手数) |

