# EventRush 第六阶段验收说明

## 本阶段交付了什么

第六阶段实现 Redis 缓存和用户级抢票限流。

当前缓存链路：

1. 用户查询活动详情。
2. 系统先查 Redis 缓存。
3. 如果缓存命中，直接返回活动详情。
4. 如果缓存未命中，读取本地活动数据，并写入 Redis，设置过期时间。

当前限流链路：

1. 用户请求抢票。
2. 系统先用 Redis Lua 做固定窗口计数。
3. 如果窗口内请求次数没有超过限制，继续抢票。
4. 如果超过限制，系统拒绝请求，避免热点接口被单个用户频繁打爆。

## 新增和修改

- `EventCacheService`：活动详情 Redis 缓存。
- `RateLimitService`：抢票接口 Redis Lua 限流。
- `lua/rate-limit.lua`：限流脚本。
- 活动详情接口支持 Redis 缓存。
- 抢票接口支持用户级限流。

## 如何验证

运行自动测试：

```powershell
mvn test
```

期望结果：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

确认 Redis 已启动：

```powershell
D:\Redis\Redis-x64-5.0.14.1\redis-cli.exe PING
```

期望结果：

```text
PONG
```

启动应用，开启缓存和限流：

```powershell
mvn "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.cache.redis-enabled=true --eventrush.rate-limit.redis-enabled=true --eventrush.rate-limit.grab-limit=1 --eventrush.rate-limit.grab-window-seconds=30 --eventrush.stock.redis-enabled=false" spring-boot:run
```

访问地址：

```text
http://localhost:18086
```

## 活动详情缓存验收

请求活动详情：

```http
GET http://localhost:18086/api/events/1
```

检查 Redis 缓存键：

```powershell
D:\Redis\Redis-x64-5.0.14.1\redis-cli.exe EXISTS eventrush:event:1
```

期望结果：

```text
1
```

## 抢票限流验收

第一次抢票：

```http
POST http://localhost:18086/api/orders/grab
Content-Type: application/json

{
  "userId": 800,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

同一用户立刻再请求一次：

```http
POST http://localhost:18086/api/orders/grab
Content-Type: application/json

{
  "userId": 800,
  "sessionId": 101,
  "ticketCategoryId": 9999
}
```

期望结果：第二次请求返回 `400`，被限流挡住。

查看 Redis 限流计数：

```powershell
D:\Redis\Redis-x64-5.0.14.1\redis-cli.exe GET eventrush:rate:grab:800
```

期望结果：计数大于 `1`。

## 你这一阶段需要真正学会什么

- 缓存适合读多写少的热点数据，比如活动详情。
- 缓存不是库存事实来源，库存扣减仍然要走 Redis 预扣和数据库订单链路。
- 限流要尽量挡在核心业务前面，避免无意义请求打到数据库。
- Redis Lua 可以保证计数和过期时间设置在 Redis 内原子执行。
- 限流不是为了让所有请求成功，而是为了让系统在高峰下稳定失败。

## 面试表达

对于活动详情这类读多写少的数据，我使用 Redis 做热点缓存，减少重复查询压力。对于抢票接口，我在进入核心业务前增加用户级限流，用 Redis Lua 在固定窗口内完成请求计数和过期时间设置。超过限制的请求会被快速拒绝，避免热点接口把数据库和订单链路打满。

## 下一阶段

下一阶段可以进入压测准备：补 JMeter 或 Gatling 脚本，对比普通扣库存、Redis Lua 预扣、开启限流后的吞吐和错误分布。
