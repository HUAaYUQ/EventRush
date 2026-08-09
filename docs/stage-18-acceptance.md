# 第 18 阶段验收：管理端密钥配置安全化

## 本阶段交付

- 管理端密钥支持通过环境变量 `EVENTRUSH_ADMIN_KEY` 覆盖。
- 本地未配置环境变量时，仍使用默认值 `eventrush-admin-key`，方便学习和本地启动。
- 自动化测试使用测试专用密钥 `stage18-test-admin-key`，验证配置覆盖生效。
- 新增 HTTP 验收文件：`requests/stage-18.http`。

## 为什么要做这一阶段

第 16 阶段我们给 `/api/admin/**` 加了管理端密钥保护。这个密钥如果永远写死在配置文件里，就不适合部署：

- 代码推到 GitHub 后，默认密钥所有人都能看到。
- 不同环境应该使用不同密钥，比如本地、测试、生产。
- 密钥变更不应该要求重新改代码。

所以这一阶段把密钥改成“配置文件给默认值，环境变量可覆盖”。

## 配置方式

配置文件中现在是：

```yaml
eventrush:
  admin:
    key: ${EVENTRUSH_ADMIN_KEY:eventrush-admin-key}
```

含义是：

- 如果系统里配置了 `EVENTRUSH_ADMIN_KEY`，就使用环境变量的值。
- 如果没配置，就退回默认值 `eventrush-admin-key`。

## Windows PowerShell 启动示例

临时设置当前窗口的管理端密钥：

```powershell
$env:EVENTRUSH_ADMIN_KEY="my-local-admin-key"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

这个设置只对当前 PowerShell 窗口有效，关闭窗口后失效。

## Linux 启动示例

```bash
export EVENTRUSH_ADMIN_KEY="my-server-admin-key"
java -jar target/eventrush-0.0.1-SNAPSHOT.jar
```

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 测试环境使用 `stage18-test-admin-key` 访问管理端接口成功。
- 不带管理端密钥仍返回 `401 Unauthorized`。

## 手动验收

启动应用时先设置环境变量：

```powershell
$env:EVENTRUSH_ADMIN_KEY="my-local-admin-key"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

错误密钥：

```http
GET http://localhost:18086/api/admin/users/9800/orders
X-Trace-Id: trace-admin-wrong-key
X-Admin-Key: eventrush-admin-key
```

预期返回 `401`。

正确密钥：

```http
GET http://localhost:18086/api/admin/users/9800/orders
X-Trace-Id: trace-admin-env-key
X-Admin-Key: my-local-admin-key
```

预期可以通过管理端鉴权。

## 你需要学会的点

- 密钥、密码、Token 这类敏感配置不要依赖代码里的固定值。
- 环境变量是部署时传配置的常见方式。
- `${ENV_NAME:default}` 表示“优先读环境变量，读不到就用默认值”。
- 学习项目可以保留默认值方便启动，但真正部署时应该显式设置环境变量。

## 面试表达

可以这样说：

> 我把管理端密钥改成了可配置形式，通过 EVENTRUSH_ADMIN_KEY 环境变量覆盖默认值。本地开发可以继续使用默认密钥，部署环境则可以单独设置真实密钥，避免敏感配置固定写死在代码仓库里。同时测试里使用专门的配置值验证覆盖逻辑生效。

## 下一阶段建议

第 19 阶段建议做“项目 README 首页”：把项目介绍、技术栈、启动方式、核心接口文档链接和阶段验收文档入口整理到仓库首页。
