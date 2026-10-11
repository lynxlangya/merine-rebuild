import { lstat, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { isIP, connect } from 'node:net'
import { checkServerIdentity, type TLSSocket } from 'node:tls'
import { createConnection, type Connection } from 'mysql2/promise'

export interface Grant {
  id: string
  name: string
  version: number
  address: string
  host: string
  port: number
  database: string
  username: string
  password: string
  tls: boolean
  views: string[]
  scope: string
}
interface BridgeConfig {
  url: string
  token: string
  localForwardPort?: number
}

/** 部署私有文件只保存服务令牌；数据库密码按次从 Java 获取，不写入会话或配置。 */
export async function loadGrant(signal: AbortSignal): Promise<{ grant: Grant; forward?: number }> {
  const home = process.env.DSH_HOME
  if (!home) throw new Error('未设置 DSH_HOME，查询工具未启用')
  const path = join(home, 'query-bridge.json')
  let config: BridgeConfig
  try {
    const stat = await lstat(path)
    if (
      !stat.isFile() ||
      stat.size > 8192 ||
      (process.platform !== 'win32' && ((stat.mode & 0o077) !== 0 || stat.uid !== process.getuid?.()))
    )
      throw new Error()
    config = JSON.parse(await readFile(path, 'utf8')) as BridgeConfig
  } catch {
    throw new Error('请配置当前 Agent 私有目录的 query-bridge.json，并设置权限为 600')
  }
  const url = new URL(config.url)
  const local = ['127.0.0.1', '[::1]'].includes(url.hostname)
  if (
    (url.protocol !== 'https:' && !(url.protocol === 'http:' && local)) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== '/internal/agent/data-source' ||
    !/^[a-f0-9]{64}$/i.test(config.token)
  )
    throw new Error('连接桥配置无效：仅允许 HTTPS 或回环 HTTP，令牌须为 64 位十六进制')
  if (
    config.localForwardPort !== undefined &&
    (!local ||
      !Number.isInteger(config.localForwardPort) ||
      config.localForwardPort < 1 ||
      config.localForwardPort > 65535)
  )
    throw new Error('本机端口映射配置无效')
  let value: unknown
  try {
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${config.token}` },
      redirect: 'error',
      signal,
    })
    if (!response.ok) throw new Error()
    // Java 的连接信息应非常小，拒绝意外的巨大响应。
    const reader = response.body?.getReader()
    if (!reader) throw new Error()
    let text = ''
    let bytes = 0
    const decoder = new TextDecoder()
    try {
      while (true) {
        const part = await reader.read()
        if (part.done) break
        bytes += part.value.length
        if (bytes > 65536) throw new Error()
        text += decoder.decode(part.value, { stream: true })
      }
      value = JSON.parse(text + decoder.decode())
    } finally {
      await reader.cancel().catch(() => {})
    }
  } catch {
    throw new Error('数据源连接信息不可用：请检查连接桥令牌、数据源启用状态及只读授权')
  }
  const g = value as Grant
  if (
    !g ||
    typeof g.id !== 'string' ||
    typeof g.name !== 'string' ||
    !Number.isInteger(g.version) ||
    !isIP(g.address) ||
    typeof g.host !== 'string' ||
    !Number.isInteger(g.port) ||
    g.port < 1 ||
    g.port > 65535 ||
    !/^[a-zA-Z0-9_]+$/.test(g.database) ||
    typeof g.username !== 'string' ||
    typeof g.password !== 'string' ||
    typeof g.tls !== 'boolean' ||
    typeof g.scope !== 'string' ||
    !Array.isArray(g.views) ||
    !g.views.length ||
    g.views.length > 50 ||
    !g.views.every((v) => typeof v === 'string' && /^[a-zA-Z0-9_]+$/.test(v))
  )
    throw new Error('连接桥响应不符合服务端契约')
  return { grant: g, ...(config.localForwardPort === undefined ? {} : { forward: config.localForwardPort }) }
}

export async function withDatabase<T>(
  signal: AbortSignal,
  run: (connection: Connection, grant: Grant) => Promise<T>,
): Promise<T> {
  const { grant, forward } = await loadGrant(signal)
  signal.throwIfAborted()
  let connection: Connection | undefined
  const abort = () => connection?.destroy()
  signal.addEventListener('abort', abort, { once: true })
  try {
    // localForwardPort 仅用于本机 Docker 发布端口；远程地址必须校验证书。
    connection = await createConnection({
      host: forward ? '127.0.0.1' : grant.host,
      stream: () => connect({ host: forward ? '127.0.0.1' : grant.address, port: forward ?? grant.port }),
      port: forward ?? grant.port,
      user: grant.username,
      password: grant.password,
      database: grant.database,
      connectTimeout: 3000,
      multipleStatements: false,
      supportBigNumbers: true,
      bigNumberStrings: true,
      dateStrings: true,
      timezone: 'Z',
      rowsAsArray: true,
      ...(grant.tls && !forward ? { ssl: { rejectUnauthorized: true, verifyIdentity: true } } : {}),
    })
    signal.throwIfAborted()
    if (grant.tls && !forward) {
      const socket = (connection as unknown as { connection: { stream: TLSSocket } }).connection.stream
      const error = checkServerIdentity(grant.host, socket.getPeerCertificate(true))
      if (error) throw error
    }
    await connection.query("SET SESSION time_zone = '+00:00'")
    await connection.query('SET SESSION max_execution_time = 5000')
    await connection.query('SET SESSION TRANSACTION READ ONLY')
    await connection.query('START TRANSACTION READ ONLY')
    return await run(connection, grant)
  } finally {
    signal.removeEventListener('abort', abort)
    connection?.destroy()
    grant.password = ''
  }
}
