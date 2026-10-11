import mysqlParser from 'node-sql-parser/build/mysql.js'

const parser = new mysqlParser.Parser()
const identifier = /^[A-Za-z_][A-Za-z0-9_]*$/
const aggregates = new Set(['COUNT', 'SUM', 'AVG', 'MIN', 'MAX'])
const functions = new Set([
  'ABS',
  'ROUND',
  'COALESCE',
  'IFNULL',
  'DATE',
  'DATE_FORMAT',
  'YEAR',
  'MONTH',
  'DAY',
  'LOWER',
  'UPPER',
])
const operators = new Set([
  '=',
  '!=',
  '<>',
  '<',
  '>',
  '<=',
  '>=',
  '+',
  '-',
  '*',
  '/',
  '%',
  'AND',
  'OR',
  'LIKE',
  'NOT LIKE',
  'IN',
  'NOT IN',
  'BETWEEN',
  'NOT BETWEEN',
  'IS',
  'IS NOT',
])
const fail = (): never => {
  throw new Error(
    'SQL 超出首版支持范围：仅支持一个已授权视图的 SELECT、筛选、统计、分组和排序；禁止多语句、关联、子查询、变量、锁及非白名单函数',
  )
}
const quote = (s: string): string => {
  if (!identifier.test(s)) fail()
  return '`' + s + '`'
}
type Node = Record<string, any>
const object = (v: unknown): Node => {
  if (!v || typeof v !== 'object' || Array.isArray(v)) fail()
  return v as Node
}

/** 解析模型 SQL，但绝不执行原文：仅从明确支持的 AST 节点重新生成受限 SQL。 */
export function compileQuery(sql: string, schemas: Record<string, string[]>): { sql: string; view: string } {
  if (typeof sql !== 'string' || sql.length > 12000 || /;|\/\*|--|#|@|\x00/.test(sql)) fail()
  let ast: Node
  try {
    ast = object(parser.astify(sql, { database: 'MySQL' }))
  } catch {
    return fail()
  }
  if (
    ast.type !== 'select' ||
    ast.with ||
    ast._next ||
    ast.set_op ||
    ast.window ||
    ast.locking_read ||
    ast.options?.length ||
    ast.qualify ||
    ast.into?.position ||
    !Array.isArray(ast.from) ||
    ast.from.length !== 1
  )
    fail()
  const from = object(ast.from[0])
  if (
    from.db ||
    from.schema ||
    from.expr ||
    from.join ||
    typeof from.table !== 'string' ||
    !Object.hasOwn(schemas, from.table)
  )
    fail()
  const view: string = from.table
  const tableNames = new Set([view, ...(from.as ? [from.as] : [])])
  const columns = new Set(schemas[view])
  const aliases = new Set<string>()
  let nodes = 0
  function expression(value: unknown, aliasAllowed = false, depth = 0): string {
    if (++nodes > 500 || depth > 20) fail()
    const n = object(value)
    const child = (v: unknown) => expression(v, aliasAllowed, depth + 1)
    switch (n.type) {
      case 'column_ref': {
        if (n.table && !tableNames.has(n.table)) fail()
        if (
          typeof n.column !== 'string' ||
          (!columns.has(n.column) && !(aliasAllowed && aliases.has(n.column.toLowerCase())))
        )
          fail()
        return quote(n.column)
      }
      case 'number':
        if (typeof n.value !== 'number' || !Number.isFinite(n.value)) fail()
        return String(n.value)
      case 'single_quote_string':
      case 'double_quote_string':
      case 'string':
        if (typeof n.value !== 'string' || n.value.length > 2048) fail()
        return `CONVERT(X'${Buffer.from(n.value, 'utf8').toString('hex')}' USING utf8mb4)`
      case 'null':
        return 'NULL'
      case 'bool':
      case 'boolean':
        if (typeof n.value !== 'boolean') fail()
        return n.value ? 'TRUE' : 'FALSE'
      case 'binary_expr': {
        const op = String(n.operator).toUpperCase()
        if (!operators.has(op)) fail()
        if (op === 'BETWEEN' || op === 'NOT BETWEEN') {
          if (n.right?.type !== 'expr_list' || n.right.value?.length !== 2) fail()
          return `(${child(n.left)} ${op} ${child(n.right.value[0])} AND ${child(n.right.value[1])})`
        }
        return `(${child(n.left)} ${op} ${child(n.right)})`
      }
      case 'expr_list':
        if (!Array.isArray(n.value) || !n.value.length || n.value.length > 50) fail()
        return '(' + n.value.map(child).join(', ') + ')'
      case 'aggr_func': {
        const name = String(n.name).toUpperCase()
        if (!aggregates.has(name) || n.over || n.args?.orderby?.length || !n.args?.expr) fail()
        const star = n.args.expr.type === 'star' || (n.args.expr.type === 'column_ref' && n.args.expr.column === '*')
        if (star && name !== 'COUNT') fail()
        if (n.args.distinct && n.args.distinct !== 'DISTINCT') fail()
        return `${name}(${n.args.distinct ? 'DISTINCT ' : ''}${star ? '*' : child(n.args.expr)})`
      }
      case 'function': {
        if (n.name?.schema || n.name?.name?.length !== 1 || n.over || n.suffix) fail()
        const name = String(n.name.name[0].value).toUpperCase()
        if (
          !functions.has(name) ||
          n.args?.type !== 'expr_list' ||
          !Array.isArray(n.args.value) ||
          n.args.value.length > 10
        )
          fail()
        return `${name}(${n.args.value.map(child).join(', ')})`
      }
      default:
        return fail()
    }
  }
  if (!Array.isArray(ast.columns) || !ast.columns.length || ast.columns.length > 20) fail()
  // 禁止 SELECT *，防止视图未来增列时无意扩大返回范围。
  const selected: { expr: string; alias: string }[] = ast.columns.map((c: Node, index: number) => {
    const alias = c.as ?? `column_${index + 1}`
    if (typeof alias !== 'string' || aliases.has(alias.toLowerCase())) fail()
    quote(alias)
    aliases.add(alias.toLowerCase())
    return { expr: expression(c.expr), alias }
  })
  const distinct = ast.distinct && typeof ast.distinct === 'object' ? ast.distinct.type : ast.distinct
  if (distinct && distinct !== 'DISTINCT') fail()
  let query = `SELECT ${distinct ? 'DISTINCT ' : ''}${selected.map((c) => `${c.expr} AS ${quote(c.alias)}`).join(', ')} FROM ${quote(view)}`
  if (ast.where) query += ' WHERE ' + expression(ast.where)
  if (ast.groupby) {
    if (ast.groupby.modifiers?.length || !Array.isArray(ast.groupby.columns) || !ast.groupby.columns.length) fail()
    query += ' GROUP BY ' + ast.groupby.columns.map((c: unknown) => expression(c, true)).join(', ')
  }
  if (ast.having) query += ' HAVING ' + expression(ast.having, true)
  if (ast.orderby) {
    if (!Array.isArray(ast.orderby) || ast.orderby.length > 10) fail()
    query +=
      ' ORDER BY ' +
      ast.orderby
        .map((o: Node) => {
          const direction = o.type ?? 'ASC'
          if (!['ASC', 'DESC'].includes(direction)) fail()
          return expression(o.expr, true) + ' ' + direction
        })
        .join(', ')
  }
  let limit = 201
  if (ast.limit) {
    if (
      ast.limit.value?.length !== 1 ||
      ast.limit.value[0].type !== 'number' ||
      !Number.isInteger(ast.limit.value[0].value) ||
      ast.limit.value[0].value < 1
    )
      fail()
    limit = Math.min(limit, ast.limit.value[0].value)
  }
  query += ` LIMIT ${limit}`
  // 在数据库端限制每个单元格，避免驱动在内存中接收巨型 TEXT/BLOB。
  return {
    view,
    sql: `SELECT ${selected.map((c) => `LEFT(CAST(${quote(c.alias)} AS CHAR), 2049) AS ${quote(c.alias)}`).join(', ')} FROM (${query}) AS merine_result`,
  }
}
