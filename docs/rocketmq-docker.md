# RocketMQ Docker 单机部署

## 1. 临时启用 Docker 命令

如果没有配置 PATH，在 PowerShell 当前窗口执行：

```powershell
$env:PATH = "C:\Users\19703\AppData\Local\Programs\DockerDesktop\resources\bin;$env:PATH"
```

## 2. 拉取镜像

优先使用 Apache 官方镜像：

```powershell
docker pull apache/rocketmq:5.3.2
```

如果网络中断，直接重复执行同一条命令，Docker 会复用已经下载的层。

## 3. 启动 NameServer

```powershell
docker run -d --name eventrush-rocketmq-namesrv -p 9876:9876 apache/rocketmq:5.3.2 sh mqnamesrv
```

## 4. 启动 Broker

```powershell
docker run -d --name eventrush-rocketmq-broker -p 10911:10911 -p 10909:10909 -v "D:\Alearn\EventRush\docs\rocketmq\broker.conf:/home/rocketmq/broker.conf" apache/rocketmq:5.3.2 sh mqbroker -c /home/rocketmq/broker.conf
```

这里挂载的 `broker.conf` 会让 Broker 把自己注册成 `127.0.0.1:10911`，这样运行在 Windows 宿主机上的 EventRush 可以正常连接。

## 5. 验证容器

```powershell
docker ps
```

预期看到：

- `eventrush-rocketmq-namesrv`
- `eventrush-rocketmq-broker`

## 6. 启动 EventRush

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.rocket-enabled=true --rocketmq.name-server=127.0.0.1:9876"
```

## 7. 常用清理命令

```powershell
docker rm -f eventrush-rocketmq-broker eventrush-rocketmq-namesrv
```
