# 生产反向代理配置（nginx）

本文件随交付包分发（包内路径 `agent/project/deploy/nginx-reverse-proxy.md`）。部署到内网时按这里的清单替换域名、证书、路径与入口地址；Agent 本体不改代码。

背景与信任模型见包根 `DEPLOY.md`：Agent 默认只监听 `127.0.0.1:3080`，通过一个**进程级启动凭据**（启动 URL 里的 `?token=`）换取签名 Cookie，之后所有访问者共享同一个「操作员」身份。反向代理解决「内网用户能否访问」，不改变单操作员模型。

## 1. 前置

- Agent 已用 systemd 等托管启动，且只监听回环：

  ```sh
  DSH_HOME=/var/lib/merine-agent ./start.sh --port 3080 \
    --public-url https://agent.example/ --trusted-host agent.example
  ```

  - `--public-url`：只影响打印的 URL、`DSH_WEB_URL` 与页面来源展示，**不放行任何请求**。
  - `--trusted-host`：浏览器实际使用的 authority（`host` 或 `host:port`；不带端口表示任意端口），**必须与实际访问地址一致**，否则 `/api` 全部 403。
  - 端口 3080 只对代理开放（本机回环或防火墙白名单），不要直接暴露到网络。
- 内网 DNS（或 hosts）能把 `agent.example` 解析到代理。
- nginx 与 Agent 同机：上游写 `http://127.0.0.1:3080`。nginx 在容器里：上游写宿主机地址（Linux 用 docker0 网关地址，Mac 用 `host.docker.internal`），不能写 `127.0.0.1`。

## 2. nginx 配置

### 方式一：挂在根路径

```nginx
server {
  listen 443 ssl;
  server_name agent.example;
  ssl_certificate     /etc/nginx/certs/agent.crt;
  ssl_certificate_key /etc/nginx/certs/agent.key;

  location / {
    # 信任栅栏按收到的 Host 判定，必须保留浏览器侧 Host，不能改写成 127.0.0.1
    proxy_set_header Host $host;
    # 流式事件走 /api 下的 WebSocket，必须转发升级握手
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    # 长回答与流式输出不能被缓冲或按默认 60s 掐断
    proxy_buffering off;
    proxy_read_timeout 3600s;
    proxy_pass http://127.0.0.1:3080;
    # TLS 终止在代理时补 Secure（nginx >= 1.19.3）；若 Agent 自身监听 TLS 可省略
    proxy_cookie_flags ~ secure;
  }
}
```

启动参数对应：`--public-url https://agent.example/ --trusted-host agent.example`。

### 方式二：挂在子路径 `/agent/`

```nginx
server {
  listen 443 ssl;
  server_name portal.example;
  ssl_certificate     /etc/nginx/certs/portal.crt;
  ssl_certificate_key /etc/nginx/certs/portal.key;

  location /agent/ {
    proxy_set_header Host $host;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_buffering off;
    proxy_read_timeout 3600s;
    # 监听器只认根路径路由：先剥掉挂载前缀再转发
    rewrite ^/agent/(.*)$ /$1 break;
    proxy_pass http://127.0.0.1:3080;
    # Cookie 作用域跟着挂载点，否则浏览器不会带回
    proxy_cookie_path / /agent/;
    proxy_cookie_flags ~ secure;
  }
}
```

启动参数对应：`--public-url https://portal.example/agent/ --trusted-host portal.example`。

### 可选：Agent 自身终止 TLS

若不想让代理改 Cookie，可给 `start.sh` 传 `--tls-cert` / `--tls-key`，监听器直接以 HTTPS 提供，Cookie 由 Agent 自己标记 `Secure`；代理只做转发（仍需保留 Host 与升级头）。

## 3. 部署时要替换的清单

| 项目 | 位置 | 说明 |
| --- | --- | --- |
| 域名/authority | nginx `server_name`、`--trusted-host`、`--public-url` | 三处必须一致 |
| 证书路径 | nginx `ssl_certificate*`（或 `--tls-cert/--tls-key`） | 覆盖实际访问的主机名/IP |
| 监听端口 | `start.sh --port`、nginx `proxy_pass` | 对外端口与内网端口分开 |
| 子路径前缀 | `location`、`rewrite`、`proxy_cookie_path`、`--public-url` | 四者一致 |
| 主站顶部入口 | `apps/web/src/app/AppShell.tsx` 的 `href`（当前硬编码 `http://127.0.0.1:3080`） | 改成部署地址（如 `https://agent.example/`）后重新构建主站 |
| 持久目录 | `DSH_HOME` | 非 root 账号可写；升级只换代码不动该目录 |

## 4. 验证步骤

1. `curl -I https://agent.example/` 未带 Cookie 时应为 401/重定向，而不是 403（403 检查 Host/Origin 与 `--trusted-host`）。
2. 用启动日志里的 `https://agent.example/?token=...` 打开一次，交换出 Cookie；再访问裸地址应正常显示页面。
3. 发一轮长回答，确认流式输出不被缓冲、WebSocket 不空闲断开。
4. 重启 Agent 后 `?token=` 会变化：旧链接失效，从新日志里取，或已持有 Cookie 的浏览器可直接访问。

## 5. 限制（不要在部署时绕过）

- **单实例**：会话与运行登记在进程内/`DSH_HOME` 文件里，不要在同一个域名后挂多个实例做负载均衡。
- **单操作员**：所有用户共享同一个身份与数据范围；按登录用户区分权限需要先完成「受控查询 + 身份」设计。
- 启动日志中的登录链接含进程凭据，勿写入工单、聊天记录或公共文档；只发给使用者。
