# 第 34 阶段验收：创建前端脚手架

## 本阶段目标

本阶段正式创建 EventRush 前端工作台脚手架，并跑通最小接口联通页。

这一阶段不追求完整 UI 功能，只确认三件事：

- 前端项目能安装依赖。
- 前端项目能构建成功。
- 前端可以通过 Vite 代理请求后端 `/api/events`。

## 本阶段交付

新增目录：

```text
frontend
```

核心文件：

| 文件 | 作用 |
| --- | --- |
| `frontend/package.json` | 前端项目依赖和脚本 |
| `frontend/vite.config.js` | Vite 配置，包含 `/api` 代理 |
| `frontend/src/App.vue` | EventRush 最小工作台联通页 |
| `frontend/src/style.css` | 工作台基础样式 |
| `frontend/index.html` | 前端页面入口 |

## 已完成能力

### 1. Vue 3 + Vite 脚手架

前端项目使用：

| 分类 | 结果 |
| --- | --- |
| 框架 | Vue 3 |
| 构建工具 | Vite |
| 包管理 | npm |
| 前端端口 | `5173` |
| 后端端口 | `18086` |

### 2. Vite 代理

`frontend/vite.config.js` 已配置：

```js
server: {
  port: 5173,
  proxy: {
    '/api': 'http://localhost:18086',
  },
}
```

前端代码里可以直接请求：

```js
fetch('/api/events')
```

### 3. 活动接口联通页

当前页面会：

- 请求 `GET /api/events`。
- 展示活动数量、场次数量、票档数量和剩余库存。
- 展示活动、场次、票档和库存。
- 展示后端返回的 `traceId`。
- 后端未启动或请求失败时给出明确提示。

## 本阶段验证

### 前端构建

在 `frontend` 目录执行：

```powershell
npm.cmd run build
```

结果：

```text
vite build 成功
```

### 后端测试

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。

## 本地启动方式

窗口 1：启动后端。

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

窗口 2：启动前端。

```powershell
cd frontend
npm.cmd run dev
```

访问：

```text
http://localhost:5173
```

## 当前页面验收标准

后端启动后，打开前端页面，应看到：

1. 页面标题为 EventRush 工作台。
2. 活动、场次、票档、剩余库存四个统计值。
3. 活动列表、场次和票档库存。
4. `traceId`。
5. 点击“重新加载”可以再次请求活动接口。

如果后端未启动，应看到“请确认后端服务运行在 18086 端口”的提示。

## 你需要学会的点

- 前端脚手架创建后，第一步不是做复杂页面，而是先证明能跑、能构建、能连后端。
- Vite 代理让前端用 `/api` 访问后端，避免本地跨域问题。
- `npm.cmd run build` 用来验证前端项目能否被正式构建。
- `node_modules` 和 `dist` 不提交到 Git，依赖和构建产物可以重新生成。
- 第一版页面只做接口联通，是为了给后续抢票、支付、验票功能打基础。

## 下一阶段建议

第 35 阶段建议实现用户抢票基础链路：活动票档选择、输入 `userId`、同步抢票、展示 `orderId` 和订单状态。
