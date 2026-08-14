# Redis Lua 抢票压测开关说明

本文档说明如何开启 Redis Lua 库存扣减，并用同一套压测参数对比默认方案和 Redis Lua 方案。

## 当前结论先说

当前项目的 Redis Lua 方案主要用于学习和验证“库存检查、重复用户检查、扣库存、记录用户”这一组 Redis 操作的原子性。

本地压测时要注意：

- 默认配置下不依赖 Redis。
- 开启 Redis 库存后，应用启动时会把票档库存预加载到 Redis。
- 抢票请求会先经过 Redis Lua，再继续创建订单并更新应用内库存。
- 当前项目仍是单体学习项目，本地 H2 压测结果不能代表生产性能。

## 涉及代码

| 文件 | 说明 |
| --- | --- |
| `src/main/resources/application.yml` | Redis 库存开关：`eventrush.stock.redis-enabled` |
| `src/main/java/com/eventrush/service/TicketingService.java` | 抢票主流程，决定是否启用 Redis 库存扣减 |
| `src/main/java/com/eventrush/service/RedisTicketStockService.java` | Redis 库存预加载、扣减、释放 |
| `src/main/resources/lua/grab-ticket.lua` | Lua 脚本，原子完成库存和重复用户检查 |
| `scripts/pressure-grab.ps1` | 本地抢票压测脚本 |

## Lua 脚本做了什么

`grab-ticket.lua` 的核心逻辑：

1. 读取 Redis 库存。
2. 如果库存不存在，返回 `-3`。
3. 判断用户是否已经抢过该票档。
4. 如果用户已抢过，返回 `-2`。
5. 如果库存不足，返回 `-1`。
6. 扣减库存。
7. 把用户 ID 记录到已抢集合。
8. 返回扣减后的库存。

对应业务错误：

| Lua 返回值 | 业务含义 |
| --- | --- |
| `-1` | 库存不足 |
| `-2` | 用户重复抢票 |
| `-3` | Redis 库存未初始化 |

## 默认方案压测

默认配置：

```yaml
eventrush:
  stock:
    redis-enabled: false
```

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

执行压测：

```powershell
.\scripts\pressure-grab.ps1 -BaseUrl "http://localhost:18086" -Users 40 -StartUserId 9100 -SessionId 101 -TicketCategoryId 1002
```

把结果记录到：

```text
docs/pressure-test-report-template.md
```

## Redis Lua 方案压测

### 1. 启动 Redis

如果本机已有 Redis，确认端口是默认的：

```text
localhost:6379
```

如果 Redis 不在默认地址，可以通过 Spring Boot 配置覆盖：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6379 --eventrush.stock.redis-enabled=true"
```

### 2. 开启 Redis 库存

启动应用时加上：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.stock.redis-enabled=true"
```

应用启动时会执行库存预加载，把当前票档库存写入 Redis。

### 3. 使用同一组参数压测

为了公平对比，Redis Lua 方案要使用和默认方案相同的参数：

```powershell
.\scripts\pressure-grab.ps1 -BaseUrl "http://localhost:18086" -Users 40 -StartUserId 9100 -SessionId 101 -TicketCategoryId 1002
```

建议每轮对比前重启应用，避免上一轮订单和 Redis 已抢用户集合影响结果。

## 对比时看什么

| 指标 | 怎么看 |
| --- | --- |
| `success` | 成功数量不能超过票档库存 |
| `failed` | 失败应该主要来自库存不足或重复抢票 |
| `qps` | 同参数下对比吞吐 |
| `avgMs` | 平均响应耗时 |
| `p95Ms` | 大多数请求的尾部耗时 |
| `p99Ms` | 极端请求耗时 |
| 500 错误 | 不应该出现系统异常 |

## 推荐记录格式

| 模式 | Users | 票档 | 库存 | success | failed | qps | avgMs | p95Ms | p99Ms | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 默认方案 | 40 | 1002 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |
| Redis Lua 方案 | 40 | 1002 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 | 待填写 |

## 常见问题

### Redis 未启动会怎样？

如果开启了 `eventrush.stock.redis-enabled=true`，但 Redis 没启动，应用启动或抢票时会失败。因此只有准备做 Redis 方案压测时才打开这个开关。

### 为什么要重启应用？

当前项目启动时会预加载 Redis 库存。重复压测会创建订单，也会记录已抢用户。为了让两轮数据更干净，建议每组压测前重启应用，并换一组新的 `StartUserId`。

### 这个结果能不能写成生产性能？

不能。本地 H2、Windows、PowerShell 脚本都只能作为学习基线。正式性能结论要用 MySQL、Redis、稳定压测工具和固定机器环境。

## 面试表达

可以这样说：

> 我给抢票链路准备了 Redis Lua 库存扣减开关。默认方案不依赖 Redis，开启 `eventrush.stock.redis-enabled=true` 后，应用启动时会把票档库存预加载到 Redis，抢票时先通过 Lua 脚本原子完成库存检查、重复用户检查、扣库存和记录用户。压测时我会用同一组 Users、票档和用户 ID 范围对比默认方案和 Redis Lua 方案，重点看是否超卖、失败是否符合预期，以及 QPS、P95、P99 的变化。
