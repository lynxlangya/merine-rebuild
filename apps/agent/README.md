# 海防业务 Agent 引擎

本目录是 `merine-rebuild` 的 Web Agent 引擎基础，来自 DeepSeek Harness 裁剪副本。上游基线为 `d7432673`，版本保持 `0.2.1-alpha.2`；保留原包名与插件体系，避免在文档整理中改变运行行为。

## 当前范围

- `apps/cli`：启动器、服务端装配和可选 headless 入口。
- `apps/web`：浏览器界面。
- `packages`：模型适配、会话、编排、工具、插件与 Web 服务等引擎模块。
- `vendor`、`native`、`patches`、`scripts`：框架源码、原生能力、依赖补丁与构建支持。

业务包、Java 身份桥和受控数据访问尚未接入，原有 Shell、文件等通用工具也尚未按海防场景收敛。运行前请阅读[安全说明](SAFETY.md)。

<a id="run-from-source"></a>

## 安装与构建

在本目录执行。验证环境使用 Node `24.18.0`，pnpm 版本由 `packageManager` 固定为 `11.28.5`。临时 shim 不改变全局 pnpm 配置；请先让当前进程的 PATH 指向所需 Node 版本。

```sh
node --version
corepack enable --install-directory /tmp/agent-bin
PATH="/tmp/agent-bin:$PATH" pnpm install --frozen-lockfile
PATH="/tmp/agent-bin:$PATH" pnpm run build
PATH="/tmp/agent-bin:$PATH" pnpm dsh --version
```

构建依次生成原生支持、Host/Client 库和 Web 静态资源。不要删除 TypeScript 配置、锁文件、依赖补丁或构建脚本。

生产包在外层仓库根执行 `./scripts/dev.sh package`，输出到 `dist/agent/merine-agent-linux-x64.tar.gz`，同时输出 API 和主系统 Web。打包在临时工作区排除 Office、语音、实验插件、终端/Shell 工具、本机桌面入口及已关闭的外传能力，不删除开发源码；共享子进程和通用模型适配依赖暂留。目标为 Linux x86_64 glibc + Node 24.18.0，使用编译后的 JS 启动。详见[三份产物说明](../../infra/PACKAGES.md)。

<a id="run"></a>

## 启动 Web Agent

```sh
DSH_HOME="$PWD/.dsh-home" PATH="/tmp/agent-bin:$PATH" \
  pnpm dsh web --no-open --port 3080
```

`DSH_HOME` 隔离运行配置、凭据与会话；始终显式设置，避免使用默认的 `~/.dsh`。`.dsh-home/` 已忽略，不提交其中的运行数据。启动器使用已构建产物，不会重新构建；未认证访问返回 `401`。同一端口只启动一个实例，冒烟使用其他端口和独立临时 `DSH_HOME`。

详细入口与 headless 参数见 [CLI 参考](apps/cli/reference/README.md)，Web 使用说明见[使用指南](docs/user/guide/index.md)。不要在未获授权时运行真实模型请求。

## 本项目关闭的外部能力

随附 Web、headless 和 SDK 配置默认停用反馈遥测、DeepSeek 会话日志扩展、插件清单上传、联网搜索和网页抓取；Web 的全部内置预设同时停用搜索与抓取工具。上游产品埋点、反馈入口及上传／搜索设置入口也已停用。源码和依赖保留，未删除实现。

这些限制通过 `packages/bundle/*/cordis.patch.yml` 与 Web 预设装配生效，不依赖 `DSH_TELEMETRY_DISABLED` 单一开关。已有自定义 profile、home patch 或动态插件可以覆盖装配，部署时仍需核对最终配置。

正常模型对话仍会向选定供应商发送回答所需的上下文；自动标题和上下文压缩也会调用模型。关闭上述能力不等于禁止进程联网，也不限制 Shell、用户自行安装的插件或自定义工具。正式业务部署仍需专用工具白名单、身份隔离和网络出口控制。

## 已裁剪的上游内容

桌面应用、官网、基准、Python SDK、内部笔记及顶层测试快照已在前序工作中移除。本轮移除历史升级指南、双语配对元数据、翻译流程文档和明确无关的上游流程指南；有中文对照的保留文档统一使用无语言后缀的 `.md`。

相关英文独有资料暂留，未机器翻译。翻译测试夹具及未确认的测试、发布、Git hook 脚本继续保留，不代表这些上游流程在裁剪后的目录中已经可用。许可证与第三方声明保持原文。

## 后续业务包

1. 建立 `semantics / tools / service` 业务包，分离引擎能力与海防规则。
2. 先接现有只读接口，后续评估只读数据库账号与视图；模型始终不直连数据库。
3. 建立多用户身份桥，由 Java 校验权限和数据范围，并保留业务写入、审计与数据库迁移职责。
4. 通过稳定的业务包边界复用引擎，不把海防业务规则写进通用核心。

## 许可证

[MIT License](LICENSE)；第三方依赖声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。保留上游归属，本文描述本项目副本的用途。
