# EventRush 第二阶段验收说明

## 本阶段交付了什么

第二阶段把抢票库存从“应用内简单扣减”升级为“Redis 预扣库存 + Lua 原子扣减”。

当前 Redis 抢票链路：

1. 应用启动时，把票种库存预加载到 Redis。
2. 用户请求抢票。
3. Lua 脚本在 Redis 内一次完成三件事：检查库存、检查用户是否已抢、扣减库存。
4. Redis 预扣成功后，应用创建待支付订单。
5. 同一用户重复抢同一场次同一票种时，系统拒绝。

## 如何验证

先运行自动测试：

```powershell
mvn test
```

期望结果：

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

启动 Redis：

```powershell
D:\Redis\Redis-x64-5.0.14.1\redis-server.exe --port 6379
```

启动应用，并开启 Redis 库存模式：

```powershell
mvn "-Dspring-boot.run.arguments=--server.port=18082 --eventrush.stock.redis-enabled=true" spring-boot:run
```

访问地址：

```text
http://localhost:18082
```

## 手动接口验收流程

查询活动列表：

```http
GET http://localhost:18082/api/events
```

抢一张票：

```http
POST http://localhost:18082/api/orders/grab
Content-Type: application/json

{
  "userId": 200,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

查看 Redis 库存：

```powershell
D:\Redis\Redis-x64-5.0.14.1\redis-cli.exe GET eventrush:stock:101:1001
```

期望结果：初始库存是 `50`，抢成功一次后变成 `49`。

查看用户是否进入已抢集合：

```powershell
D:\Redis\Redis-x64-5.0.14.1\redis-cli.exe SISMEMBER eventrush:grabbed:101:1001 200
```

期望结果：返回 `1`。

再用同一个用户抢同一张票。期望结果：系统返回 `400`，拒绝重复抢票。

## 你这一阶段需要真正学会什么

- 为什么抢票接口不能直接查 MySQL 库存再扣 MySQL 库存。
- Redis Lua 的价值：把“查库存、查是否重复、扣库存”放到 Redis 里一次执行，避免并发下多个请求交叉执行。
- Redis 预扣只是流量削峰和防超卖的第一步，订单最终仍然要落 MySQL。
- Redis 扣减成功但订单创建失败时，需要后续补偿，这个会在订单可靠性阶段继续补。
- 当前内存订单用了 `synchronized`，只是为了保护第一版内存数据；接入 MySQL 后，应使用唯一索引和事务来兜底。

## 面试表达

我没有让抢票接口直接操作数据库库存，而是先把热点票种库存预加载到 Redis。用户抢票时，通过 Lua 脚本在 Redis 中原子完成库存检查、重复抢票检查和库存扣减。这样可以把大量失败请求挡在 Redis 层，降低数据库压力，同时避免并发请求导致超卖或同一用户重复抢票。

## 下一阶段

下一阶段会引入 MySQL 表结构和订单持久化。到那时，Redis 预扣负责高并发入口，MySQL 负责最终订单数据和唯一约束兜底。
