# 第 33 阶段验收：前端技术选型与脚手架准备

## 本阶段目标

本阶段确定前端工作台的技术选型和脚手架准备方案，但暂不创建前端项目。

这样做的原因是：先确认环境、目录、端口、代理和依赖边界，再进入实际编码，避免前端刚开始就变复杂。

## 本阶段交付

新增文档：

```text
docs/frontend-scaffold-plan.md
```

文档明确了：

- 第一版选择 Vue 3 + Vite。
- 前端目录使用 `frontend`。
- 前端端口使用 `5173`。
- 后端端口继续使用 `18086`。
- 本机 Node/npm 环境检查结果。
- PowerShell 下 `npm.ps1` 被拦截时使用 `npm.cmd`。
- Vite `/api` 代理方案。
- 前端初始目录规划。
- 第一版暂不安装的依赖。

同时更新 `.gitignore`，提前忽略：

```text
frontend/node_modules/
frontend/dist/
```

## 环境检查结果

本机检查结果：

| 项目 | 结果 |
| --- | --- |
| Node | `v24.14.0` |
| npm | `11.9.0` |
| 推荐 npm 命令 | `npm.cmd` |

说明：直接输入 `npm` 时，PowerShell 可能会因为执行策略拦截 `npm.ps1`。这不是 Node 没装好，而是 Windows 脚本策略问题。当前阶段使用 `npm.cmd` 即可继续推进。

## 核心决策

### 1. 第一版使用 Vue 3 + Vite

Vue 3 + Vite 足够完成 EventRush 工作台：

- 启动快。
- 配置少。
- 适合单页工作台。
- 学习成本适中。

### 2. 第一版少装依赖

第一版先不安装 ECharts、Pinia、Vue Router、大型 UI 框架和 Axios。

原因是：当前页面可以先用 Vue 自身状态、原生 `fetch` 和普通 CSS 完成。等真的出现重复逻辑或图表需求，再引入依赖。

### 3. 使用 Vite 代理连接后端

前端请求统一写 `/api/...`，由 Vite 代理到：

```text
http://localhost:18086
```

这样可以减少跨域和地址配置问题。

## 验收方式

阅读：

```text
docs/frontend-scaffold-plan.md
```

检查是否能回答：

1. 为什么第一版选 Vue 3 + Vite？
2. 为什么暂时不加大型 UI 框架、Pinia、ECharts？
3. 前端目录为什么放在 `frontend`？
4. 前端和后端分别跑在哪个端口？
5. PowerShell 里 `npm` 被拦截时怎么办？
6. 为什么需要 Vite `/api` 代理？
7. 哪些前端目录不能提交到 Git？

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 文档和 `.gitignore` 变更不影响后端业务逻辑。

## 你需要学会的点

- 脚手架准备不是马上装一堆东西，而是先定运行边界。
- npm 在 Windows PowerShell 下被拦截时，可以优先使用 `npm.cmd`。
- 前端开发服务和后端服务通常是两个端口。
- 代理可以让前端用 `/api` 访问后端，减少跨域麻烦。
- 依赖要按真实需要引入，第一版越轻越容易跑通。

## 下一阶段建议

第 34 阶段建议正式创建前端脚手架：生成 `frontend` 目录，配置 Vite 代理，跑通前端空页面和 `GET /api/events`。
