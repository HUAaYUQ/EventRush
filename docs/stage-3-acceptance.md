# EventRush 第三阶段验收说明

## 本阶段交付了什么

第三阶段把订单从内存数据升级为数据库持久化。

当前订单链路：

1. 用户抢票。
2. 系统扣减库存。
3. 系统向 `ticket_order` 表插入一条 `PENDING_PAYMENT` 订单。
4. 用户支付订单。
5. 系统把订单状态从 `PENDING_PAYMENT` 更新为 `PAID`。
6. 系统生成电子票。

默认开发环境使用 H2 文件数据库，方便本机直接运行；同时提供 MySQL 建表脚本，后续可以切换到 MySQL。

## 新增文件

- `src/main/resources/schema.sql`：默认 H2 数据库建表脚本。
- `docs/sql/mysql-schema.sql`：MySQL 建表脚本。
- `src/main/resources/application-mysql.yml`：MySQL profile 配置。
- `src/main/java/com/eventrush/service/TicketOrderRepository.java`：订单数据库读写。

## 如何验证

运行自动测试：

```powershell
mvn test
```

期望结果：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

启动应用：

```powershell
mvn "-Dspring-boot.run.arguments=--server.port=18083 --eventrush.stock.redis-enabled=false" spring-boot:run
```

访问地址：

```text
http://localhost:18083
```

## 手动接口验收流程

抢一张票：

```http
POST http://localhost:18083/api/orders/grab
Content-Type: application/json

{
  "userId": 400,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

查询订单：

```http
GET http://localhost:18083/api/orders/替换成抢票接口返回的 id
```

期望结果：订单状态是 `PENDING_PAYMENT`。

支付订单：

```http
POST http://localhost:18083/api/orders/替换成抢票接口返回的 id/pay
```

再次查询订单：

```http
GET http://localhost:18083/api/orders/替换成抢票接口返回的 id
```

期望结果：订单状态变成 `PAID`。

## 使用 MySQL

当前本机 MySQL 3306 没有运行，所以默认使用 H2。要切换到 MySQL：

1. 启动 MySQL。
2. 执行 `docs/sql/mysql-schema.sql`。
3. 设置环境变量：

```powershell
$env:EVENTRUSH_MYSQL_USERNAME="root"
$env:EVENTRUSH_MYSQL_PASSWORD="你的密码"
```

4. 使用 MySQL profile 启动：

```powershell
mvn "-Dspring-boot.run.profiles=mysql" spring-boot:run
```

## 你这一阶段需要真正学会什么

- 为什么订单必须落库：订单是核心交易记录，不能只放在应用内存。
- 为什么订单状态更新要带条件：只有 `PENDING_PAYMENT` 订单才能被支付。
- 为什么数据库唯一约束重要：即使应用层漏判，数据库也能兜底阻止同一用户重复抢同一票种。
- Redis 和数据库的分工：Redis 负责高并发入口的快速预扣，数据库负责最终订单事实。
- 当前电子票还在内存里，这是下一步要持久化的对象。

## 面试表达

在 Redis 预扣库存之后，我把订单创建落到了数据库。订单表包含用户、活动、场次、票种、订单状态、创建时间、支付时间和过期时间。支付时不会直接覆盖状态，而是只允许待支付订单更新为已支付。为了防止同一用户重复抢同一场次同一票种，我在数据库层增加了唯一约束，作为应用层判断之外的最后兜底。

## 下一阶段

下一阶段会持久化电子票，并继续推进订单可靠性：超时取消、库存释放、支付和取消的状态冲突控制。
