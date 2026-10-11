/** 本项目的外传能力必须在所有随附入口及 Web 预设中默认关闭。 */
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it, vi } from 'vitest'
import { boot, composeEntries, loadProfile, PROFILE_TEMPLATES } from '@deepseek-ai/dsh-app-boot'
import type {} from '@deepseek-ai/dsh-deepseek-llm-api-extensions'
import type {} from '@deepseek-ai/dsh-web'

const anchor = fileURLToPath(new URL('../package.json', import.meta.url))
const disabledPackages = new Set([
  'session-log-deepseek', 'plugin-package-inventory-deepseek', 'session-telemetry-otel',
  'host-product-telemetry-otel', 'client-product-analytics',
  'web-search-deepseek', 'web-fetch-http', 'tool-web', 'command-feedback',
  'message-feedback', 'client-ui-message-feedback', 'client-ui-settings-session-log',
  'client-ui-settings-plugin-inventory', 'client-ui-settings-web-search',
].map(name => `@deepseek-ai/dsh-${name}`))

function rowsFor(name: string, home: string) {
  const profile = loadProfile('dsh', name, anchor, home)
  return composeEntries([...profile.layers.map(layer => layer.patches), profile.patches])
}

function checkNested(value: unknown): number {
  if (Array.isArray(value)) return value.reduce<number>((count, item) => count + checkNested(item), 0)
  if (value === null || typeof value !== 'object') return 0
  const row = value as Record<string, unknown>
  const targeted = typeof row.name === 'string' && disabledPackages.has(row.name)
  if (targeted) expect(row.disabled, `${String(row.id)}: ${String(row.name)}`).toBe(true)
  return Number(targeted) + Object.values(row).reduce<number>((count, item) => count + checkNested(item), 0)
}

describe('project privacy defaults', () => {
  it.each(Object.keys(PROFILE_TEMPLATES))('%s disables upstream uploads and web access across its real bundle layers', (name) => {
    const home = mkdtempSync(join(tmpdir(), 'merine-agent-privacy-'))
    try {
      const rows = rowsFor(name, home)
      expect(checkNested(rows)).toBeGreaterThanOrEqual(2)
      for (const id of ['session-log-deepseek', 'plugin-package-inventory-deepseek']) {
        expect(rows.find(row => row.id === id)?.disabled, id).toBe(true)
      }
      if (name === 'web') {
        const presets = rows.filter(row => row.name === '@deepseek-ai/dsh-agent-preset')
        expect(presets.length).toBeGreaterThanOrEqual(3)
        for (const preset of presets) checkNested(preset.config)
      }
    } finally {
      rmSync(home, { recursive: true, force: true })
    }
  })

  it('boots the actual disabled entries without upload services or callable web providers', async () => {
    const home = mkdtempSync(join(tmpdir(), 'merine-agent-privacy-runtime-'))
    const network = vi.fn(() => { throw new Error('External requests are forbidden in this test') })
    vi.stubGlobal('fetch', network)
    try {
      const rows = rowsFor('web', home).filter(row => disabledPackages.has(row.name ?? '')
        || row.id === 'deepseek-llm-api-extensions' || row.id === 'web')
      const root = join(home, 'cordis.yml')
      writeFileSync(root, '[]\n')
      const bundleAnchor = new URL('../../../packages/bundle/base/package.json', import.meta.url).href
      const ctx = await boot('privacy-test', root, [{ insert: rows }], undefined, bundleAnchor)
      try {
        expect(ctx.get('sessionTelemetry')).toBeUndefined()
        expect(ctx.get('productTelemetry')).toBeUndefined()
        const extensions = await ctx.deepseekLlmApiExtensions.prepare({
          signal: new AbortController().signal, sessionId: 'synthetic-privacy-check', body: {},
        })
        expect(extensions.fields).toEqual({})
        await expect(ctx.web.search({ query: 'synthetic confidential query' }))
          .rejects.toMatchObject({ code: 'WEB_PROVIDER_CONFIGURED_MISSING' })
        await expect(ctx.web.fetch({ url: 'http://127.0.0.1:1/private' }))
          .rejects.toMatchObject({ code: 'WEB_PROVIDER_CONFIGURED_MISSING' })
        expect(network).not.toHaveBeenCalled()
      } finally {
        await ctx.fiber.dispose()
      }
    } finally {
      vi.unstubAllGlobals()
      rmSync(home, { recursive: true, force: true })
    }
  })
})
