# 海防 Agent 生产运行包

目标：Linux x86_64，glibc（例如 Debian/Ubuntu）；Node.js 24.18.0。
不包含 Node 运行时，不适用于 Alpine/musl、ARM64 或 macOS。

```sh
tar -xzf merine-agent-linux-x64.tar.gz
cd agent
DSH_HOME=/var/lib/merine-agent ./start.sh --port 3080
```

使用非 root 账号，确保该账号可写独立的 DSH_HOME。启动日志中的登录链接含访问凭据，勿公开。
默认监听本机，远程访问需另行配置受保护的 HTTPS 反向代理；不要直接暴露端口。具体 nginx 配置、`--public-url`/`--trusted-host` 参数与部署替换清单见包内 `project/deploy/nginx-reverse-proxy.md`。
模型配置在首次启动后设置。本包不包含开发环境密钥、会话、资料或业务数据。

已排除 Office 转换/预览、语音、实验插件、终端操作、Shell 工具、本机应用/目录选择、遥测、日志上传和网络搜索/抓取。
共享子进程模块的 node-pty，以及通用模型适配器的多供应商 SDK 暂留，以免破坏共用依赖。
具体 workspace 排除清单见 production-scope.json；源码仍保留在开发仓库。

这是引擎运行包；Java 用户身份桥和海防业务工具尚未接入，不代表可直接上线为多用户业务系统。
已关闭的能力不构成网络沙箱；模型对话仍向配置的模型服务发送必要上下文。
无需也不应在部署目录执行 pnpm install；其 node_modules 已在目标平台组装。

许可证与第三方声明随包保留。
