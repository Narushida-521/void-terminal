# Void Terminal

多机实时监控：每台机器跑 agent，中心跑 server。

## 要求

- JDK 25
- 不要把 server 暴露到公网。第一版内部上报接口没有鉴权。

## 启动

```bat
.\gradlew.bat :server:bootRun
.\gradlew.bat :agent:bootRun --args="--voidterminal.agent.node-id=node-a"
.\gradlew.bat :agent:bootRun --args="--voidterminal.agent.node-id=node-b"
```

## 调用方接口

- `GET /api/nodes`
- `GET /api/nodes/{id}`
- `GET /api/nodes/stream` (SSE, event `nodes`)
- `GET /api/nodes/{id}/stream` (SSE, event `snapshot`)

Agent 上报：`POST /internal/nodes/{id}/snapshot`
