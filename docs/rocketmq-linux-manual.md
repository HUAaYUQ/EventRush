# RocketMQ Linux 手动部署记录

## 1. 安装 Java

RocketMQ 需要 Java。Ubuntu 上可以安装 JDK 17：

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
```

设置当前终端的 Java 环境：

```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH=$JAVA_HOME/bin:$PATH
```

## 2. 启动 NameServer

进入 RocketMQ 解压目录：

```bash
cd ~/rocketmq-all-5.3.1-bin-release
nohup sh bin/mqnamesrv > namesrv.log 2>&1 &
```

验证：

```bash
jps
ss -lntp | grep 9876
```

看到 `NamesrvStartup` 和 `9876` 监听即可。

## 3. 启动单机 Broker

自动获取虚拟机 IP 并生成配置：

```bash
VM_IP=$(hostname -I | awk '{print $1}')

cat > broker.conf <<EOF
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
namesrvAddr=127.0.0.1:9876
brokerIP1=$VM_IP
listenPort=10911
autoCreateTopicEnable=true
deleteWhen=04
fileReservedTime=48
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
EOF
```

启动 Broker：

```bash
nohup sh bin/mqbroker -c broker.conf > broker.log 2>&1 &
```

验证：

```bash
jps
ss -lntp | grep -E '9876|10911|10909'
tail -n 80 broker.log
```

看到 `BrokerStartup` 和 `The broker[...] boot success` 即可。

## 4. Windows 连通性验证

在 Windows PowerShell 执行：

```powershell
Test-NetConnection 192.168.233.128 -Port 9876
Test-NetConnection 192.168.233.128 -Port 10911
```

两条都显示 `TcpTestSucceeded : True`，EventRush 就可以连接 RocketMQ。

## 5. 启动 EventRush

```powershell
java -jar D:\Alearn\EventRush\target\eventrush-0.0.1-SNAPSHOT.jar --server.port=18086 --eventrush.queue.rocket-enabled=true --rocketmq.name-server=192.168.233.128:9876
```
