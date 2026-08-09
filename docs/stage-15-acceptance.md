# 第 15 阶段验收：管理端基础查询接口

## 本阶段交付

- 新增按用户查询订单接口。
- 新增按订单查询电子票接口。
- 新增按票码查询电子票接口。
- 查询接口继续使用统一响应格式和 `traceId`。
- 新增自动化测试，覆盖订单和电子票管理查询。

## 新增接口

按用户查询订单：

```http
GET /api/admin/users/{userId}/orders
```

按订单查询电子票：

```http
GET /api/admin/orders/{orderId}/ticket
```

按票码查询电子票：

```http
GET /api/admin/tickets/{ticketCode}
```

## 为什么要做这一阶段

前面的阶段主要面向用户链路：抢票、支付、出票、核验。

真实系统还需要后台或客服查询能力，比如：

- 用户说自己抢票成功但找不到订单，后台能按用户查订单。
- 用户支付成功但没看到票，后台能按订单查电子票。
- 入场核验异常时，后台能按票码查票状态。

本阶段先做只读查询，不做管理端页面和权限系统，先把基础业务查询能力补齐。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 管理查询接口返回统一响应格式。
- 按用户能查到订单。
- 按订单能查到电子票。
- 按票码能查到电子票状态。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

先抢票：

```http
POST http://localhost:18086/api/orders/grab
Content-Type: application/json
X-Trace-Id: trace-admin-demo-001

{
  "userId": 9800,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

再支付：

```http
POST http://localhost:18086/api/orders/把这里换成订单id/pay
X-Trace-Id: trace-admin-demo-002
```

按用户查订单：

```http
GET http://localhost:18086/api/admin/users/9800/orders
X-Trace-Id: trace-admin-demo-003
```

按订单查电子票：

```http
GET http://localhost:18086/api/admin/orders/把这里换成订单id/ticket
X-Trace-Id: trace-admin-demo-004
```

按票码查电子票：

```http
GET http://localhost:18086/api/admin/tickets/把这里换成ticketCode
X-Trace-Id: trace-admin-demo-005
```

## 你需要学会的点

- 用户端接口负责“办理业务”，管理端接口负责“查询和排查业务”。
- 后台查询接口通常是只读接口，优先保证数据准确、响应清晰。
- 本阶段没有做权限，这是刻意拆分；真实系统里 `/api/admin/**` 后续必须加登录和角色校验。
- 管理端接口也要复用统一响应格式和 `traceId`，这样排查体验一致。

## 面试表达

可以这样说：

> 我补充了管理端基础查询接口，包括按用户查询订单、按订单查询电子票、按票码查询电子票。接口仍然走统一响应结构并返回 traceId，方便客服或后台排查订单、出票和核验问题。当前阶段先实现只读查询，权限控制会在后续阶段单独接入。

## 下一阶段建议

第 16 阶段建议做“管理端权限保护”：为 `/api/admin/**` 增加简单的管理密钥校验，先建立后台接口不能裸奔的安全边界。
