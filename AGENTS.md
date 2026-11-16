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
