# 第 14 阶段验收：请求追踪 ID 和接口日志

## 本阶段交付

- 每次请求都会有一个 `traceId`。
- 如果请求头带了 `X-Trace-Id`，系统会沿用它。
- 如果请求头没有 `X-Trace-Id`，系统会自动生成一个。
- 响应头会返回 `X-Trace-Id`。
- 统一响应体会返回 `traceId` 字段。
- 每次接口请求都会打印一条访问日志，包含方法、路径、状态码、耗时和 `traceId`。

## 响应格式变化

第 13 阶段的统一响应是：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

第 14 阶段增加 `traceId`：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "trace-demo"
}
```

## 为什么要做这一阶段

项目变复杂以后，只看错误消息不够。一次请求可能经过接口层、业务层、数据库、RocketMQ。

有了 `traceId` 以后，排查问题时可以这样做：

- 用户或前端把失败响应里的 `traceId` 给后端。
- 后端在日志里搜索这个 `traceId`。
- 找到同一次请求的接口路径、状态码、耗时和错误上下文。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 成功响应包含 `traceId`。
- 业务错误响应包含 `traceId`。
- 参数校验错误响应包含 `traceId`。
- 响应头包含 `X-Trace-Id`。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

请求活动列表，并手动传入追踪 ID：

```http
GET http://localhost:18086/api/events
X-Trace-Id: trace-demo-001
```

预期响应头：

```text
X-Trace-Id: trace-demo-001
```

预期响应体：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "traceId": "trace-demo-001"
}
```

同时控制台会出现类似日志：

```text
GET /api/events status=200 durationMs=12 traceId=trace-demo-001
```

## 你需要学会的点

- `traceId` 是一次请求的排查编号。
- 响应头给调用方看，响应体给前端业务代码看，日志给后端排查用。
- 前端、网关、后端都可以传递同一个 `X-Trace-Id`，这样跨系统排查更方便。
- 当前实现是轻量版，只覆盖单个应用内的请求追踪；以后接入网关或链路追踪系统时，可以继续沿用这个请求头。

## 面试表达

可以这样说：

> 我在接口层加了请求 traceId。系统会优先使用请求头里的 X-Trace-Id，没有则自动生成，并把它写入响应头、统一响应体和接口访问日志。这样前端拿到错误响应后，可以把 traceId 给后端，后端直接按 traceId 搜索日志定位同一次请求。

## 下一阶段建议

第 15 阶段建议做“管理端基础查询接口”：按用户查询订单、按订单查询电子票、按票码查询核验状态，让项目更像完整业务系统。
