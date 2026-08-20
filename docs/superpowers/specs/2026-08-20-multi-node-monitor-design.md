# Void Terminal 多机监控重写设计

日期：2026-08-20  
仓库：https://github.com/Narushida-521/void-terminal  
状态：待实现

## 背景

当前仓库是单进程 Spring WebFlux 应用：在本机用 OSHI 采 CPU/内存/磁盘/流量，用 SSE 每秒推一次；另外每 2 秒 ping 联通/移动/电信。接口是 `/api/metrics/stream`、`/api/info/static`、`/api/latency/stream`。

要把它重写成「每台机器一个 agent、一个中心 server」的多机实时监控后端。第一版不登录、不存库、不做前端。允许改掉现有 API。

## 目标

- 同一仓库里能同时跑 1 个 server 和多个 agent。
- 调用方能列出节点、看单机最新快照、用 SSE 收多机或单机更新。
- 采集逻辑复用现有 OSHI 和双平台 ping，不再作为对外主接口。
- 进程重启后内存节点清空；agent 再上报会重新出现。

## 非目标（第一版明确不做）

- 登录、鉴权、HTTPS、agent 与 server 之间的密钥。
- 数据库、文件持久化、历史曲线。
- 前端页面。
- Spring Boot / Java / OSHI 版本升级。
- SSH 拉取、中心去扫机器。
- 告警、阈值、通知。

## 架构

Gradle 多模块，三个模块：

| 模块 | 角色 |
| --- | --- |
| `model` | 共享 DTO / 枚举，无 Spring |
| `agent` | 跑在被监控机器上：采集 + 定时 POST |
| `server` | 中心服务：收上报、内存登记、对外 REST/SSE |

```text
[机器 A: agent] --POST snapshot--> [server 内存表] --GET/SSE--> [调用方]
[机器 B: agent] --POST snapshot-->
```

旧的单机 SSE 主路径删除，由 agent + server 取代。agent 第一版不对外提供 `/api/metrics/stream` 一类接口。

## 仓库结构

```text
void-terminal/
  settings.gradle          # include model, agent, server
  build.gradle             # 统一 Java 21、Spring Boot、公共依赖
  model/src/main/java/com/nxd/voidterminal/model/
  agent/src/main/java/com/nxd/voidterminal/agent/
  server/src/main/java/com/nxd/voidterminal/server/
```

根项目不再作为可启动的单体应用。现有 `src/main/java/com/nxd/voidterminal` 中的采集和 ping 代码迁入 `agent`，整理后复用，不原样堆进 server。

Java 21、当前 Gradle 9.1.0、Spring Boot 4.0.0-M3 保持不变。`oshi-core`、`jnb-ping` 只留在 `agent`。

## 数据模型

全部放在 `model`，字段用具体类型，不用裸 `Flux`/`Mono`/`List`。

### `NodeStatus`

`ONLINE` | `OFFLINE`

### `SystemMetrics`

只保留实际采集的字段：

- `cpuCores`（int）
- `cpuUsage`（double，百分比，两位小数）
- `memoryUsed` / `memoryTotal`（double，GiB）
- `memoryUsage`（double，百分比）
- `diskUsed` / `diskTotal`（double，GiB）
- `diskUsage`（double，百分比）
- `networkTotalReceived` / `networkTotalSent`（double，GiB）

现有模型里未赋值的 `networkLatency`、`packetLossRate`、`pingTarget` 删掉，延迟单独建模。

### `LatencyMetrics`

- `unicom` / `mobile` / `telecom`（double，毫秒）
- 失败或超时记 `-1`，不中断整份快照

### `StaticSystemInfo`

沿用现有字段：`osInfo`、`hostName`、`dnsServer`、`cpuInfo`、`cpuArch`、`baseboardInfo`、`computerSystemInfo`、`physicalMemoryInfo`、`graphicsCardInfo`。

### `NodeSnapshot`

agent 上报和单机查询/SSE 共用：

- `nodeId`（String，必填，与配置和 URL 一致）
- `nodeName`（String）
- `hostname`（String）
- `reportedAt`（ISO-8601 瞬时时间）
- `metrics`（`SystemMetrics`，必填）
- `latency`（`LatencyMetrics`，必填）
- `staticInfo`（`StaticSystemInfo`，可空）

`staticInfo` 规则：每个 agent 第一次上报必带；之后每 60 秒带一次。server 对每个节点记住最近一次非空 `staticInfo`，列表和查询在后续短上报里仍然能读到。

### `NodeView`

`GET /api/nodes` 和多机 SSE 使用：

- `nodeId`、`nodeName`、`hostname`
- `status`（`ONLINE` / `OFFLINE`）
- `lastSeenAt`
- `metrics`（最新一份；离线后仍返回最后一次）
- `latency`（同上）
- `staticInfo`（server 缓存的最近一次）

## Agent

可启动的 Spring Boot 应用。`spring.main.web-application-type=none`，不对外开 HTTP。用 `WebClient` POST 到 server。

### 配置

```properties
voidterminal.agent.node-id=
voidterminal.agent.node-name=
voidterminal.agent.server-url=http://localhost:8080
voidterminal.agent.report-interval=1s
voidterminal.agent.ping.unicom=ha-cu-v4.ip.zstaticcdn.com
voidterminal.agent.ping.mobile=ha-cm-v4.ip.zstaticcdn.com
voidterminal.agent.ping.telecom=222.88.88.88
```

`node-id` 为空则启动失败，不回退到主机名（避免多机撞名被覆盖）。`node-name` 为空时用 `node-id`。

### 采集

- 实时指标：从现有 `MetricsServiceImpl` 迁出并清理。每秒采一次。磁盘判断仍排除 nfs/smb/iso/loop/tmpfs。网卡只统计「有 IP 且非 loopback」；现有 `||` / `&&` 优先级错误一并修掉。
- 采集放到 `boundedElastic`，不占默认事件循环。
- 去掉每秒打印每个磁盘的 INFO 和 `System.out.println`。
- Ping：保留 POSIX `jnb-ping` 与 Windows 系统 `ping` 两套实现，按操作系统条件装配。最新 RTT 写入每份快照；某一线路失败记 `-1`。

### 上报

- 按 `report-interval` POST `{server-url}/internal/nodes/{node-id}/snapshot`。
- body 是完整 `NodeSnapshot`，`nodeId` 必须等于配置和 URL。
- 失败（连接失败、5xx、超时）：记 warn，等下一轮，进程不退出。
- 4xx：记 error，仍下一轮重试（方便改完配置后自动恢复）。

## Server

可启动的 Spring WebFlux 应用，默认端口 `8080`。

CORS 只留在 server：允许 `http://localhost:5173`，方法和头与现在一致，方便以后接前端。

### 内存登记

`ConcurrentHashMap<String, NodeRecord>`。

`NodeRecord`：`NodeSnapshot` 最新值、缓存的 `StaticSystemInfo`、`lastSeenAt`、`status`。

- 合法 POST：写入/更新记录，`status=ONLINE`，`lastSeenAt=now`。未知 `nodeId` 第一次上报即登记。
- 超过 5 秒未上报：标 `OFFLINE`，记录仍保留。
- 超过 30 秒未上报：从 map 删除。
- 超时扫描每 1 秒一次。

### 对 agent

`POST /internal/nodes/{id}/snapshot`

- Content-Type：`application/json`
- `{id}` 与 body.`nodeId` 必须相同且非空，否则 `400`，不写内存。
- body 缺 `metrics` 或 `latency`：`400`。
- 成功：`204`。

第一版内部接口不鉴权。文档和 README 写明：不要把 server 暴露到公网。

### 对调用方

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | `/api/nodes` | `200` + `NodeView[]`；没有节点时 `[]` |
| GET | `/api/nodes/{id}` | 存在则 `200` + `NodeView`；否则 `404` |
| GET | `/api/nodes/stream` | SSE，每秒推当前全部 `NodeView[]`；空列表也照推，连接不关 |
| GET | `/api/nodes/{id}/stream` | SSE，每秒推该节点 `NodeView`；节点不存在时先 `404` 结束，不挂空流 |

SSE：`MediaType.TEXT_EVENT_STREAM`。多机事件名 `nodes`，单机事件名 `snapshot`。

删除旧接口：`/api/metrics/stream`、`/api/info/static`、`/api/latency/stream`、`/api/ping/test`。

## 错误处理

- agent 上报失败：只打日志，下个间隔重试。
- 单条 ping 失败：该线路 `-1`，指标其余字段照常上报。
- OSHI 单次采集失败：本轮不 POST，打 error，下轮再采。
- 未知节点首次合法 POST：自动登记。
- 坏请求：`400`，内存不变。
- 查询不存在的节点：REST `404`；单机 SSE 同样 `404`。
- 多机 SSE 在零节点时保持连接，每秒推 `[]`。

## 测试

删掉根上那个空壳 `VoidTerminalApplicationTests`。

**server**

- 上报合法快照后，`GET /api/nodes` 能看到该节点且为 `ONLINE`。
- `{id}` 与 body.`nodeId` 不一致返回 `400`，map 不出现该节点。
- 超过 5 秒未上报变为 `OFFLINE`；超过 30 秒从列表消失。测试里用可配置的超时（生产默认 5s/30s，测试注入更短值）。
- 订阅 `/api/nodes/stream` 后能收到包含已上报节点的事件。

**agent**

- 采集结果含齐 `SystemMetrics` 与 `LatencyMetrics` 字段（ping 可用测试替身，避免真发 ICMP）。
- server 不可达时上报被接住，不抛到调度器外。

**不做**

- 第一版不写端到端多进程测试。手工验收见下。

## 手工验收

1. 启动 server（`:8080`）。
2. 启动两个 agent，`node-id` 分别为 `node-a`、`node-b`，都指向该 server。
3. `GET /api/nodes` 看到两台，状态 `ONLINE`。
4. `GET /api/nodes/stream` 持续推两边指标。
5. 停掉 `node-b`：约 5 秒后变为 `OFFLINE`，约 30 秒后从列表消失；`node-a` 不受影响。

## 实现时的代码约束

- 对外接口和共享模型带完整泛型。
- 构造器注入，不用 `@Resource`。
- 类名使用 PascalCase（现有 `corsConfig` 迁到 server 时改成 `CorsConfig`）。
- 不顺手做版本升级或加新监控项（网卡速率、进程列表等）。
