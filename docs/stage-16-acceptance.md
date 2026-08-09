# 第 16 阶段验收：管理端权限保护

## 本阶段交付

- `/api/admin/**` 接口必须带 `X-Admin-Key` 请求头。
- 默认管理密钥是 `eventrush-admin-key`。
- 密钥可通过配置 `eventrush.admin.key` 修改。
- 未携带或携带错误密钥时，返回 `401 Unauthorized`。
- 未授权响应继续使用统一响应格式，并带 `traceId`。
- 普通用户接口不受影响。

## 为什么要做这一阶段

第 15 阶段新增了管理端查询接口。管理端接口能查订单和电子票，不能裸奔。

这一阶段先做轻量保护：

- 不引入完整登录系统。
- 不增加 Spring Security。
- 用一个请求头密钥保护 `/api/admin/**`。

这不是最终生产级权限方案，但足够建立“后台接口必须有权限边界”的意识。

## 请求头

```http
X-Admin-Key: eventrush-admin-key
```

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 带正确 `X-Admin-Key` 可以访问管理端接口。
- 不带 `X-Admin-Key` 会返回 `401`。
- 未授权响应包含 `success=false`、`code=UNAUTHORIZED`、`traceId`。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

未携带管理密钥：

```http
GET http://localhost:18086/api/admin/users/9800/orders
X-Trace-Id: trace-admin-denied
```

预期：

```json
{
  "success": false,
  "code": "UNAUTHORIZED",
  "message": "admin key is invalid",
  "data": null,
  "traceId": "trace-admin-denied"
}
```

携带正确管理密钥：

```http
GET http://localhost:18086/api/admin/users/9800/orders
X-Trace-Id: trace-admin-ok
X-Admin-Key: eventrush-admin-key
```

预期可以正常返回订单列表。

## 你需要学会的点

- 后台接口不能裸奔，即使只是查询接口也要保护。
- `401 Unauthorized` 表示当前请求没有通过身份校验。
- 当前方案是轻量版管理密钥，适合学习和单体项目早期阶段。
- 真实生产系统更常见的是登录态、JWT、网关鉴权、RBAC 角色权限。

## 面试表达

可以这样说：

> 我给管理端接口增加了基础鉴权过滤器，只保护 /api/admin/** 路径。请求必须携带 X-Admin-Key，校验失败返回 401 和统一错误响应，同时保留 traceId，方便排查。这个阶段先用轻量密钥建立后台权限边界，后续可以替换成 JWT 或 RBAC。

## 下一阶段建议

第 17 阶段建议做“接口文档整理”：把所有阶段的核心接口汇总成一份中文 API 文档，方便你复习和展示项目。
