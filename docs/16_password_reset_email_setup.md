# CampusLens 密码找回邮件配置（Windows / QQ 邮箱）

本文用于在另一台 Windows 电脑上配置 CampusLens 密码找回邮件。项目通过标准 SMTP + STARTTLS 发送 6 位验证码，本文以 QQ 邮箱作为发件邮箱。收件人可以使用 QQ、163、Outlook、学校邮箱等任意能正常收信的邮箱。

> 安全要求：密码必须填写 QQ 邮箱生成的 **SMTP 授权码**，不是 QQ 密码或邮箱登录密码。不要把真实邮箱、授权码写入源码、`.env`、Markdown、截图或 Git 提交。

## 1. 前置条件

- 项目已在新电脑上完成基础环境配置，可以从项目根目录运行 `scripts\start.cmd`。
- 准备一个可正常登录的 QQ 邮箱，用它作为 CampusLens 发件邮箱。
- 用于测试找回的 CampusLens 账号已绑定邮箱。新注册账号必须填写邮箱；历史账号需先在“个人中心”补充邮箱。

## 2. 开启 QQ 邮箱 SMTP 并获取授权码

QQ 邮箱页面可能随版本调整，以页面实际名称为准。

1. 在浏览器登录 QQ 邮箱。
2. 进入“设置”，查找“账户”、“账户与安全”或“POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV 服务”。
3. 开启 SMTP 发信能力，例如“POP3/SMTP 服务”或“IMAP/SMTP 服务”。
4. 按页面要求完成手机验证，生成一个 SMTP 授权码。
5. 立即将授权码保存到可靠的密码管理器中。不要在群聊、文档或 Git 仓库中传递。

如果 QQ 邮箱后台没有上述开关，先根据页面提示完成账号安全验证，或查看 QQ 邮箱官方帮助。

## 3. 在 Windows 中永久保存配置

打开 PowerShell，逐行执行下列命令。先将两个占位符替换为真实值，但不要把替换后的命令保存到项目文件中。

```powershell
[Environment]::SetEnvironmentVariable("CAMPUSLENS_MAIL_HOST", "smtp.qq.com", "User")
[Environment]::SetEnvironmentVariable("CAMPUSLENS_MAIL_PORT", "587", "User")
[Environment]::SetEnvironmentVariable("CAMPUSLENS_MAIL_USERNAME", "<你的QQ邮箱>", "User")
[Environment]::SetEnvironmentVariable("CAMPUSLENS_MAIL_PASSWORD", "<QQ邮箱SMTP授权码>", "User")
[Environment]::SetEnvironmentVariable("CAMPUSLENS_MAIL_FROM", "<你的QQ邮箱>", "User")
```

例如，`CAMPUSLENS_MAIL_USERNAME` 和 `CAMPUSLENS_MAIL_FROM` 应使用同一个完整地址，格式为 `123456789@qq.com`。

使用下列命令检查非敏感配置：

```powershell
[Environment]::GetEnvironmentVariable("CAMPUSLENS_MAIL_HOST", "User")
[Environment]::GetEnvironmentVariable("CAMPUSLENS_MAIL_PORT", "User")
[Environment]::GetEnvironmentVariable("CAMPUSLENS_MAIL_USERNAME", "User")
[Environment]::GetEnvironmentVariable("CAMPUSLENS_MAIL_FROM", "User")
```

不要直接输出授权码。只检查它是否已存在：

```powershell
$mailPassword = [Environment]::GetEnvironmentVariable("CAMPUSLENS_MAIL_PASSWORD", "User")
if ([string]::IsNullOrWhiteSpace($mailPassword)) { "未配置" } else { "已配置" }
Remove-Variable mailPassword
```

## 4. 重启项目使配置生效

已经运行的后端不会自动重读环境变量。在项目根目录执行：

```powershell
scripts\stop.cmd
```

关闭当前 PowerShell 窗口，重新打开一个 PowerShell，进入 CampusLens 项目根目录，再执行：

```powershell
scripts\start.cmd
```

项目的统一启动脚本会读取当前 Windows 用户级的 `CAMPUSLENS_MAIL_*` 环境变量，无需修改 `application.yml`。

## 5. 完整验收流程

### 5.1 确认 SMTP 网络可达

在 PowerShell 执行：

```powershell
Test-NetConnection smtp.qq.com -Port 587
```

期望看到 `TcpTestSucceeded : True`。该检查只能证明网络可达，不能证明邮箱或授权码正确。

### 5.2 发送并使用验证码

1. 在浏览器打开 CampusLens 前端。
2. 准备一个已绑定真实邮箱的测试账号。
3. 在普通浏览器窗口中正常登录一次，保留该窗口且不要点击退出，用于最后检查旧会话失效。
4. 打开无痕窗口或另一个独立浏览器，进入登录面板并点击“忘记密码？”。不要使用同一浏览器的普通标签页，因为同源标签页可能共享登录状态。
5. 输入测试账号绑定的邮箱，点击“发送验证码”。
6. 查看收件箱和垃圾邮件目录，确认收到主题为“CampusLens 密码重置验证码”的邮件。
7. 在 10 分钟内输入邮件中的 6 位验证码和一个不同于原密码的新密码。新密码至少 8 位。
8. 使用新密码登录，确认登录成功。
9. 回到第 3 步保留的旧会话，刷新个人中心或访问需要登录的功能，确认系统要求重新登录。

默认规则：验证码有效期为 10 分钟，同一账号 60 秒内不重复发送，最多允许 5 次错误尝试，并且只能成功使用一次。成功重置密码后，该账号的旧会话会被撤销。

## 6. 常见故障排查

### 页面显示发送成功，但一直收不到邮件

发送接口无论邮箱是否已绑定，都会返回统一提示：

```text
如果该邮箱已绑定账号，验证码邮件将发送到该邮箱
```

这是为了避免泄露某个邮箱是否注册过，不代表 SMTP 投递一定成功。请按以下顺序排查：

1. 确认输入的是测试账号已绑定的邮箱。
2. 检查垃圾邮件、订阅邮件和邮箱拦截记录。
3. 确认 60 秒内没有对同一账号重复发送。
4. 查看标题为 `CampusLens Backend` 的后端窗口，搜索日志 `Password reset email delivery failed via SMTP`。
5. 修正配置后，等待 60 秒再重新发送。发送失败时，后端会删除未投递成功的验证码记录。

### 日志显示认证失败（常见为 535）

- 确认 `CAMPUSLENS_MAIL_PASSWORD` 是 SMTP 授权码，不是 QQ 密码。
- 确认 QQ 邮箱的 SMTP 服务仍然开启，授权码未被撤销。
- 确认用户名是完整 QQ 邮箱地址，并且 `CAMPUSLENS_MAIL_FROM` 与用户名一致。
- 重新生成授权码后，更新用户环境变量并重启后端。

### 端口连接失败或超时

- 运行 `Test-NetConnection smtp.qq.com -Port 587`。
- 检查校园网、公司网络、VPN、防火墙或安全软件是否拦截出站 TCP 587。
- 尝试切换到手机热点进行对比，以判断是否为当前网络限制。
- 当前项目固定使用 STARTTLS，不要将 QQ 邮箱端口直接改为隐式 TLS 的 465。

### 环境变量已配置，但后端仍报“邮件服务尚未配置”

- 确认是在启动 CampusLens 的同一个 Windows 用户下配置用户环境变量。
- 按第 3 节命令确认 `CAMPUSLENS_MAIL_USERNAME` 和 `CAMPUSLENS_MAIL_FROM` 非空。
- 先运行 `scripts\stop.cmd`，关闭旧终端，再从新 PowerShell 运行 `scripts\start.cmd`。
- 如果当前 PowerShell 曾设置过错误的临时 `$env:CAMPUSLENS_MAIL_*` 变量，关闭该窗口，避免临时值覆盖用户级配置。

## 7. 其他发件邮箱服务商

项目当前只实测过 QQ 邮箱。更换发件服务商时，必须同时满足“支持 SMTP 用户名/密码认证”和“支持 STARTTLS”两个条件。

| 发件服务商 | SMTP 主机 | 端口 | 凭据 | 当前项目兼容性 |
| --- | --- | ---: | --- | --- |
| QQ 邮箱 | `smtp.qq.com` | `587` | SMTP 授权码 | 已实测，推荐 |
| Gmail | `smtp.gmail.com` | `587` | 开启两步验证后生成的应用专用密码 | 协议匹配，需根据 Google 账号政策实测 |
| Outlook.com | `smtp-mail.outlook.com` | `587` | 官方当前要求 OAuth2 / Modern Auth | **不能直接使用**；现有后端未实现 OAuth2 |
| 163 邮箱 | 以 163 邮箱后台当前公布值为准 | 必须选择 STARTTLS 端口 | 客户端授权码 | 未实测；常见的 465 隐式 TLS 不适配当前后端 |

不要只替换用户名和密码。发件服务商变更后，至少需要重新确认 `CAMPUSLENS_MAIL_HOST`、`CAMPUSLENS_MAIL_PORT`、认证方式和 TLS 模式。

## 8. 可选参数

以下参数不影响基本配置，通常保留默认值：

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `CAMPUSLENS_PASSWORD_RESET_VALID_MINUTES` | `10` | 验证码有效分钟数 |
| `CAMPUSLENS_PASSWORD_RESET_COOLDOWN_SECONDS` | `60` | 同一账号两次发送的最小间隔 |
| `CAMPUSLENS_MAIL_CONNECTION_TIMEOUT_MS` | `5000` | SMTP 建立连接超时 |
| `CAMPUSLENS_MAIL_TIMEOUT_MS` | `10000` | SMTP 读取超时 |
| `CAMPUSLENS_MAIL_WRITE_TIMEOUT_MS` | `10000` | SMTP 写入超时 |

如需修改，也应使用 `[Environment]::SetEnvironmentVariable(..., "User")` 保存，并在修改后重启后端。

## 9. 相关官方说明

- [Google 应用专用密码](https://support.google.com/accounts/answer/185833)
- [Outlook.com POP、IMAP 和 SMTP 设置](https://support.microsoft.com/en-US/Outlook/pop-imap-and-smtp-settings-for-outlook-com)
