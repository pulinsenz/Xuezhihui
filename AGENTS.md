# 项目记忆

## SSH 密钥（本机唯一一对，用于连接服务器 124.222.214.36）

| 项目 | 内容 |
|------|------|
| 类型 | Ed25519 |
| 私钥 | `C:\Users\Yuxiaoyu\.ssh\id_ed25519` |
| 公钥 | `C:\Users\Yuxiaoyu\.ssh\id_ed25519.pub` |
| 备注 | Yuxiaoyu@xiaoyu（本 Windows 机器 8月28日生成） |
| 指纹 | `SHA256:/07H6bu4vY+0Y24ABoupNcetIr0CybKc/6Mp92HmvH0` |

公钥内容：

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHpAxiDOKsjYU9GpH+3N4L5XTPsnTCrWvr8bSZTHqzf8 Yuxiaoyu@xiaoyu
```

说明：

- 私钥绝不能外泄；私钥文件内容禁止打印、提交或上传。
- 文件名为默认命名 `id_ed25519`，连接时无需 `-i` 参数，直接 `ssh ubuntu@124.222.214.36` 自动使用。
- 服务器端 `~/.ssh/authorized_keys` 存有此公钥，故可免密登录。
- 本机无 `~/.ssh/config`，无其他密钥。

## 前端设计 token（2026-11-24 uiup 提案落地）

改前端样式前先读 `frontend/src/style.css` 的 `:root`：品牌色只有 teal 一个（`--brand/--brand-hover/--brand-pressed`，浅底 `--tint`），中性 5 档 `--t1..--t4/--line`，画布 `--bg` 卡面 `--card`，圆角 `--r1 16 / --r2 12 / --r3 8`，卡片阴影 `--shadow-card`（双层柔和，浮层才用 `--shadow-pop`）。新样式必须从这些变量取值，不写裸 hex/任意圆角；字号只用 16/14/12 + 大数字 28（`--text-num`）。

## 本地开发注意事项（2026-11-19 实测）

|坑|事实|对策|
|---|---|---|
|Hyper-V 动态保留端口|`netsh interface ipv4 show excludedportrange protocol=tcp` 随机盖住大片端口（曾含 9000、9500），绑定报"forbidden by access permissions"且 netstat 无占用|MinIO 宿主映射已改 9050/9051（落空隙）；再遇冲突换空隙端口即可|
|Docker 镜像陈旧|`docker compose up -d` 不带 `--build` 永远用旧镜像（曾导致 java/python 容器跑旧代码、健康检查 405/拦截）|改后端代码后必须 `docker compose build <svc> && docker compose up -d <svc>`|
|torch 大包构建超时|requirements 新增 jieba 触发全量 pip install，torch 539MB 在清华/阿里源 ~370KB/s（25 分钟+）反复 ReadTimeout|已用 `python-agent/Dockerfile.hotfix`（基于旧镜像 COPY 新代码+补装 jieba，49s）先恢复；网络空闲时再跑全量 `docker compose build python-agent` 替换掉 hotfix 镜像|
|Maven JAVA_HOME|本机 mvn.cmd 报 JAVA_HOME 不正确，`cmd //c` 转义也不可靠|绕过脚本直接用 classworlds 启动（见下）|
|MySQL 裸连公钥|application.yml 默认 JDBC URL 无 `allowPublicKeyRetrieval=true`，本地裸跑报 Public Key Retrieval not allowed|启动时环境变量覆盖 SPRING_DATASOURCE_URL 加该参数|

裸跑 Java 后端（中间件留在 Docker）：

```bash
# 打包（绕过 mvn.cmd 的 JAVA_HOME 校验）
java -Dmaven.home="D:\develop\apache-maven-3.9.11" \
  -Dmaven.multiModuleProjectDirectory="<repo>\java-backend" \
  -classpath "D:\develop\apache-maven-3.9.11\boot\plexus-classworlds-2.9.0.jar" \
  "-Dclassworlds.conf=D:\develop\apache-maven-3.9.11\bin\m2.conf" \
  org.codehaus.plexus.classworlds.launcher.Launcher -q -B -DskipTests package
```

启动环境变量（值从根 `.env` 读，勿打印）：`JWT_SECRET`、`AGENT_TOKEN`、`MYSQL_PASSWORD`（=MYSQL_ROOT_PASSWORD）、`COS_*`、以及覆盖的 `SPRING_DATASOURCE_URL`（带 allowPublicKeyRetrieval=true，端口 3307）。Redis/Agent 地址默认值（127.0.0.1:6379 / localhost:8000）无需覆盖。
