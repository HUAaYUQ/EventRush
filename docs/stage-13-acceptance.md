# 第 13 阶段验收：接口统一响应格式和错误码

## 本阶段交付

- 所有 `/api` 接口成功响应统一包装成 `success/code/message/data`。
- 业务异常统一返回 `BUSINESS_ERROR`。
- 参数校验异常统一返回 `VALIDATION_ERROR`。
- 原业务代码基本不用改，统一包装由接口层自动完成。
- 新增接口层自动化测试，覆盖成功响应、业务错误、参数错误。

## 统一格式

成功：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败：

```json
{
  "success": false,
  "code": "BUSINESS_ERROR",
  "message": "event not found",
  "data": null
}
```

## 为什么要做这一阶段

前端最怕接口格式不稳定：有的接口直接返回对象，有的接口返回错误详情，有的接口字段名还不一样。

统一格式以后，前端可以固定按这套规则处理：

- `success=true`：读取 `data` 渲染页面。
- `success=false`：读取 `code` 和 `message` 展示错误。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 成功接口返回 `success=true` 和 `data`。
- 业务异常返回 `success=false`、`code=BUSINESS_ERROR`。
- 参数校验异常返回 `success=false`、`code=VALIDATION_ERROR`。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

请求活动列表：

```http
GET http://localhost:18086/api/events
```

预期看到：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": [
    {
      "id": 1
    }
  ]
}
```

请求不存在的活动：

```http
GET http://localhost:18086/api/events/999
```

预期看到：

```json
{
  "success": false,
  "code": "BUSINESS_ERROR",
  "message": "event not found",
  "data": null
}
```

## 你需要学会的点

- HTTP 状态码告诉客户端请求大类是否成功，比如 `200` 或 `400`。
- 业务 `code` 告诉前端具体是什么错误，比如 `BUSINESS_ERROR` 或 `VALIDATION_ERROR`。
- 统一响应格式能减少前端判断分支，也方便后续接入错误提示、日志追踪和接口文档。
- 这一阶段没有重写每个 Controller，而是用统一响应包装器自动处理，改动更小。

## 面试表达

可以这样说：

> 我给接口层增加了统一响应结构，成功时返回 success=true、code=OK 和 data，失败时返回 success=false、错误码和错误消息。业务异常和参数校验异常分别映射为 BUSINESS_ERROR 和 VALIDATION_ERROR。这样前端不用分别适配成功对象和 ProblemDetail，接口契约更稳定。

## 下一阶段建议

第 14 阶段建议做“接口操作日志和请求追踪 ID”：每次请求生成 traceId，响应里返回，日志里也记录，方便排查线上问题。
