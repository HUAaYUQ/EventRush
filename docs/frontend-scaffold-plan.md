# EventRush 前端技术选型与脚手架准备

这份文档承接 `docs/ui-workbench-requirements.md`，用于确定第一版前端工作台的技术方案、目录位置、运行方式和本机环境注意事项。

本阶段只做准备，不创建前端项目。原因是：先把边界定清楚，再动脚手架，后面不容易返工。

## 结论

第一版前端建议使用：

| 分类 | 选择 |
| --- | --- |
| 前端框架 | Vue 3 |
| 构建工具 | Vite |
| 包管理 | npm |
| 图标 | lucide-vue-next，实际开发时再安装 |
| 请求 | 原生 `fetch` 优先，确实需要拦截器时再加 Axios |
| 样式 | 普通 CSS，先不引入大型 UI 框架 |
| 图表 | 第一版不引入图表库，压测区先做数字摘要 |
| 前端目录 | `frontend` |
| 前端端口 | `5173` |
| 后端端口 | `18086` |

这个方案的原则是：够用、清晰、少依赖。

## 本机环境检查

当前电脑已经具备前端基础环境：

| 项目 | 结果 |
| --- | --- |
| Node | `v24.14.0` |
| npm | `11.9.0` |
| Node 路径 | `D:\Node.js\node.exe` |
| npm PowerShell 脚本 | `D:\Node.js\npm.ps1` |
| npm 命令文件 | `D:\Node.js\npm.cmd` |

注意：直接执行 `npm -v` 时，PowerShell 可能因为执行策略拦截 `npm.ps1`。

推荐先使用：

```powershell
npm.cmd -v
```

后续创建和运行前端项目时，也可以优先使用：

```powershell
npm.cmd create vite@latest frontend -- --template vue
npm.cmd install
npm.cmd run dev
```

如果你想从根源解决 PowerShell 脚本拦截，可以之后再单独处理执行策略；当前项目推进不需要急着改系统策略。

## 为什么选 Vue 3 + Vite

Vue 3 + Vite 适合当前项目，原因是：

- 启动快，适合本地学习和演示。
- 组件组织清晰，适合工作台页面。
- 不需要复杂工程配置。
- 和后端分离，方便单独运行。
- 后续要加图表、请求封装、状态管理时也能平滑扩展。

暂不选择更重的方案：

| 暂不选择 | 原因 |
| --- | --- |
| Nuxt | 当前不是服务端渲染网站，不需要 |
| Next/React | 可以做，但和当前 Vue 学习成本相比没有明显收益 |
| 大型 UI 框架 | 第一版界面不复杂，先用 CSS 控制可读性 |
| ECharts | 第一版压测区先做数字摘要，有真实历史报告后再上图表 |
| Pinia | 第一版单页状态不复杂，先用 Vue 自身状态即可 |
| Axios | 第一版原生 `fetch` 足够，后面重复逻辑变多再加 |

## 目录规划

后续创建前端项目时，建议目录为：

```text
EventRush
├─ frontend
│  ├─ index.html
│  ├─ package.json
│  ├─ vite.config.js
│  └─ src
│     ├─ App.vue
│     ├─ main.js
│     ├─ api
│     │  └─ eventrushApi.js
│     ├─ components
│     │  ├─ EventSelector.vue
│     │  ├─ GrabPanel.vue
│     │  ├─ TicketPanel.vue
│     │  ├─ VerifyPanel.vue
│     │  ├─ AdminPanel.vue
│     │  ├─ RequestLog.vue
│     │  └─ PressureEvidencePanel.vue
│     └─ styles
│        └─ main.css
├─ src
├─ docs
└─ pom.xml
```

第一版可以先少建组件。如果一个页面能清楚完成演示，不需要为了“看起来工程化”拆太碎。

建议起步文件：

| 文件 | 作用 |
| --- | --- |
| `frontend/src/App.vue` | 工作台主页面和整体状态 |
| `frontend/src/api/eventrushApi.js` | 后端接口调用 |
| `frontend/src/styles/main.css` | 全局样式 |

组件可以等 `App.vue` 变长后再拆。

## 接口代理

前端开发服务运行在：

```text
http://localhost:5173
```

后端运行在：

```text
http://localhost:18086
```

建议在 Vite 中配置代理：

```js
export default {
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:18086'
    }
  }
}
```

这样前端请求可以直接写：

```js
fetch('/api/events')
```

好处是：

- 开发时不需要手动拼后端地址。
- 避免浏览器跨域问题。
- 演示时只需要记住前端地址。

## 启动顺序

后续真正创建前端后，本地建议两个窗口分别启动。

窗口 1：启动后端。

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

窗口 2：启动前端。

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问：

```text
http://localhost:5173
```

## 忽略规则

前端项目创建后，不应该提交：

```text
frontend/node_modules/
frontend/dist/
```

原因：

- `node_modules` 是依赖安装结果，体积大，不进 Git。
- `dist` 是构建产物，可以随时重新生成。

本阶段已经提前把这两个目录加入 `.gitignore`。

## 第一版实现顺序

后续真正写前端时，建议按下面顺序推进：

1. 创建 Vite + Vue 项目，确认页面能打开。
2. 配置 `/api` 代理，确认能请求 `GET /api/events`。
3. 做活动和票档选择。
4. 做同步抢票、支付、查票、验票完整链路。
5. 做管理端查询区。
6. 做最近请求记录和 traceId 展示。
7. 做压测证据区。
8. 最后整理样式和响应式。

不要一开始就做完整视觉系统。先让主链路真实可用。

## 第一版暂不安装的依赖

为了保持项目简单，第一版先不安装：

- ECharts
- Pinia
- Vue Router
- 大型 UI 组件库
- Axios

这些不是不能用，而是暂时不需要。等出现明确需求再加。

## 你需要学会的点

- 技术选型要服务于项目目标，不是哪个技术热就用哪个。
- 前后端分离项目通常需要同时启动后端和前端。
- Vite 代理能解决本地开发时的跨域和地址拼接问题。
- `node_modules` 和 `dist` 不应该提交到 Git。
- 依赖越少，第一版越容易跑通；等重复逻辑真的出现，再抽象和加库。
