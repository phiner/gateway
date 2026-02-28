# Redis API 对接文档
本文段定义了与交易系统（网关与引擎）进行交互的 Redis Pub/Sub 接口与数据存储规范。它旨在供其他服务（包括 AI 工具、UI 界面及监控脚本）使用。

### ⚠️ 序列化协议说明
所有数据负载都使用 **MessagePack (MsgPack)** 进行二进制序列化，而不是原始 JSON。
为了确保跨语言（Rust, Java, Python等）通讯的可读性与扩展性，必须遵循以下规范：
*   **Map 模式**: 必须包含字段名（Field Names）。
*   **禁止使用 Array 模式**: 禁止将对象压缩为纯数组，以防止字段顺序变动导致的反序列化崩溃。
*   **Java 端建议**: 确保 Jackson DTO 不要开启 `BeanAsArray` 或类似的压缩配置。

---

## 交互模式

与系统交互主要有三种方式：

1.  **全局状态 (State)**: 网关将当前的实盘状态（持仓、历史、频率等）持久化在 Redis Hash 中。客户端应优先轮询或根据广播信号读取这些 Hash。
2.  **广播频道 (Events)**: 网关发布实时事件（Tick、K线、信号、更新通知）。客户端根据信号决定何时更新本地缓存。
3.  **指令与请求 (Commands)**: 客户端发送异步指令执行操作或触发数据校准。响应通常通过全局频道广播，而非私有频道。

---

## 1. 广播频道 (网关 -> 客户端)

### 市场数据

**重要提示**: 所有频道名称中的交易品种（instrument），例如 `EUR/USD`，在 Redis 的 Channel 和 Key 中都会**完整保留斜杠 (`/`)**。

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
    | 字段名 | 类型 | 描述 |
    | :--- | :--- | :--- |
    | `instrument` | String | 交易品种名称 (带斜杠) |
    | `time` | Long | Unix 时间戳 (毫秒) |
    | `ask` | Double | 卖出价 |
    | `bid` | Double | 买入价 |

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

#### 📈 获取K线历史数据
网关将每个品种和周期的 K 线存储在一个 **Redis List** 中。
*   **Key 格式**: `kline:{instrument}:{period}`
*   **操作**: `LRANGE kline:EUR/USD:1Min 0 -1`
*   **说明**: 列表中的第一个元素是**最新**的历史 K 线。存储上限由网关配置决定。

---

## 2. 账号、订单与系统事件

#### ▶️ `order:event`
实时发布与订单生命周期相关的事件。该数据结构直接反映了 JForex 平台的消息和订单对象。
*   **字段说明**:
    | 字段名 | 类型 | 描述 |
    | :--- | :--- | :--- |
    | `messageId`| String | 唯一订单/消息ID |
    | `eventType`| String | 事件类型: "ORDER_SUBMIT_OK", "ORDER_FILL_OK", "ORDER_CLOSE_OK" 等 |
    | `creationTime`| Long | 事件 Unix 时间戳 (毫秒) |
    | `orderLabel` | String | 订单自定义标签 |
    | `instrument`| String | 交易品种名称 |
    | `orderState`| String | 状态: "CREATED", "OPENED", "FILLED", "CLOSED" |
    | `orderCommand`| String | 动作: "BUY", "SELL", "BUY_LIMIT" |
    | `amount` | Double | 数量 (手数) |
    | `openPrice`| Double | 开仓价格 |
    | `fillTime` | Long | 成交时间 (毫秒) |
    | `closePrice`| Double | 平仓价格 |
    | `closeTime` | Long | 关闭时间 (毫秒) |

#### ▶️ `account:status`
同步账户资金状态。
*   **字段**: `balance` (余额), `equity` (净值), `baseEquity` (结算价值), `margin` (已用保证金), `unrealizedPL` (未实现盈亏)。

#### ▶️ `gateway:status` / `gateway:error`
网关连接状态与错误消息广播。

#### ▶️ `gateway:positions:updated`
每当活跃持仓的 Redis Hash (`gateway:positions:active`) 被全量刷新后，网关会向此频道发送一个通知。
*   **负载**: 当前 Unix 时间戳 (毫秒字符串)。
*   **用途**: 客户端监听到该信号后，应从 Hash 中重新拉取最新持仓。

---

## 3. 指令频道 (客户端 -> 交易系统)

### 开仓与平仓
*   **`order:open`**: 市价开仓指令。
    *   参数: `instrument`, `orderType` (BUY/SELL), `amount`, `label`, `slippage`, `stopLossPrice`, `takeProfitPrice`。
*   **`order:close`**: 市价平仓指令。
    *   参数: `orderId` (网关返回的 dealId 或 label)。
*   **`order:submit`**: 提交挂单 (Limit/Stop)。
*   **`order:modify`**: 修改活跃订单的止盈止损。
    - **参数结构**:
        | 字段名 | 类型 | 描述 |
        | :--- | :--- | :--- |
        | `orderId` | String | (必填) 订单的 `dealId` 或标签名 `label` |
        | `stopLossPrice` | Double | (可选) 新的止损价格。若为 `0` 或不传则不修改 |
        | `takeProfitPrice` | Double | (可选) 新的止盈止损价格。若为 `0` 或不传则不修改 |
        | `requestId` | String | (可选) 用于日志追踪的唯一 ID |
    - **技术说明**: 网关采用**异步顺序更新**机制。如果同时修改 SL 和 TP，网关会先发起 SL 修改，在监听到服务器确认成功后，再自动触发 TP 修改。这可以有效避免由于修改频率过快（1秒内多次）被交易所服务器拒绝的问题。

### 持仓同步请求
*   **请求频道**: `system:request:positions`
*   **说明**: 触发网关进行持仓数据校准。完成后会更新 `gateway:positions:active` 并发送更新通知。

### 历史订单查询
*   **请求频道**: `system:request:orders_history`
*   **请求参数**:
    - `requestId`: (可选，用于追踪日志)
    - `instrument`: (必填) 指定交易品种。
    - `startTime`: (必填) 开始时间。
    - `endTime`: (可选) 结束时间。
*   **说明**: 触发网关从 Dukascopy 异步拉取历史记录。完成后会合并至 `gateway:orders:history` 并发送更新通知。

---

## 4. 系统配置 (静态 Keys)

*   **`gateway:config:instruments` (Set)**: 当前订阅的品种列表。
*   **`gateway:config:periods` (Set)**: 处理的 K 线周期列表。
*   **`gateway:config:trade_multiplier` (String)**: 全局开仓手数放大倍数。
*   **`gateway:config:instrument_info` (Hash)**: 品种属性详情。
    *   包含: `pip` (点值), `point` (微点值), `minTradeAmount` (最小交易量)。

---

## 5. 运行时交易状态 (Runtime Trading State)

这些 Key 存储了引擎运行时的动态状态，供监控与 UI 界面使用。

#### 📊 `trades:active` (Hash)
存储当前系统中所有处于活跃状态（持仓中或挂单中）的交易信号快照。
*   **Field**: `deal_reference` (唯一交易号)
*   **Value**: `SignalIntent` 对象 (包含入场详情、信心值等)
*   **生命周期**:
    -   **创建**: 下单并发往网关时写入。
    -   **清理**: 监听到 `ORDER_CLOSE_OK`（平仓成功）事件后自动删除。

#### 📜 `state:signals` (List)
存储系统生成的全量历史交易信号流。
*   **内容**: `SignalIntent` 对象。
*   **限制**: 保留最新的 1000 条记录。

#### ⚙️ `state:indicator:{instrument}:{period}` (Binary)
存储最近一次计算的指标结果（ATR等）。

#### 🧬 `state:features:{instrument}:{period}` (Binary)
存储最近一次特征化产生的特征矩阵（FeatureMatrix），供 UI 展示。

#### 📋 `gateway:positions:active` (Hash)
存储当前系统中所有处于 `FILLED` 状态的活跃持仓全量快照。
*   **Field**: `dealId` (唯一订单ID)
*   **Value**: `PositionDTO` 对象 (MessagePack 序列化)
*   **更新机制**: 
    - 订单状态变更（成交、平仓、修改 SL/TP）时触发**全量刷新**。
    - 收到 `system:request:positions` 请求后触发**全量刷新**。
    - 配合 `gateway:positions:updated` 频道实现高效同步。
    - 启动时自动初始化。

#### 📜 `gateway:orders:history` (Hash)
存储网关获取到的已完成订单（CLOSED/CANCELLED）的累积历史库。
*   **Field**: `dealId` (唯一订单ID)
*   **Value**: `OrderHistoryDTO` 对象 (MessagePack 序列化)
*   **更新机制**: 
    - 收到 `system:request:orders_history` 请求后，从 Dukascopy 拉取并**合并**。
    - 配合 `gateway:orders:history:updated` 频道同步。
    - 这是一个增量累积的操作，旨在构建网关侧的历史数据库。

#### ⏲️ `GATEWAY_HEARTBEAT_INTERVAL` (Environment Variable)
配置心跳与资金同步的频率。
*   **默认值**: `15000` (15 秒)。
*   **说明**: 仅同步资金状态，不对持仓进行定时校准（校准由手动请求或事件触发）。
