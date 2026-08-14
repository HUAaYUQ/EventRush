# EventRush Frontend

EventRush 前端工作台，使用 Vue 3 + Vite。

## 启动

先启动后端：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

再启动前端：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问：

```text
http://localhost:5173
```

前端通过 Vite 代理把 `/api` 请求转发到 `http://localhost:18086`。
