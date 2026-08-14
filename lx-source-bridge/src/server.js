import crypto from 'node:crypto'
import http from 'node:http'
import { readLimited } from './http.js'
import { LxRuntime } from './runtime.js'
import { SourceLoader } from './source-loader.js'

const json = (res, status, body, maxBytes) => {
  const data = Buffer.from(JSON.stringify(body))
  if (data.length > maxBytes) return json(res, 500, { error: 'bridge response is too large' }, Infinity)
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': data.length, 'cache-control': 'no-store' }).end(data)
}
const authorized = (req, token) => {
  if (!token) return true
  const actual = req.headers.authorization || ''
  const expected = `Bearer ${token}`
  return actual.length === expected.length && crypto.timingSafeEqual(Buffer.from(actual), Buffer.from(expected))
}
const errorStatus = error => error.status || 502

class SourceSlot {
  constructor(id, config, loader, log) {
    this.id = id; this.config = config; this.loader = loader; this.log = log
    this.runtime = null; this.reloadPromise = null; this.lastError = null; this.lastAttemptAt = null
  }
  async reload(options) {
    if (this.reloadPromise) return this.reloadPromise
    this.reloadPromise = (async () => {
      this.lastAttemptAt = new Date().toISOString()
      try {
        const source = await this.loader.load(options)
        if (this.runtime?.sourceInfo.hash === source.hash) { this.lastError = null; return { changed: false, source } }
        const runtime = await new LxRuntime(this.config, source, this.log).init()
        this.runtime = runtime; this.lastError = null
        this.log.info(`Loaded LX source slot=${this.id} ${source.name || 'unnamed'} version=${source.version || 'unknown'} sha256=${source.hash}${source.cached ? ' (cache)' : ''}`)
        return { changed: true, source }
      } catch (error) {
        this.lastError = { message: error.message, at: this.lastAttemptAt }
        throw error
      }
    })().finally(() => { this.reloadPromise = null })
    return this.reloadPromise
  }
  health() {
    if (!this.runtime) return { ok: false, sourceId: this.id, error: this.lastError }
    const source = this.runtime.sourceInfo
    return { ok: true, sourceId: this.id, source: { name: source.name, version: source.version, hash: source.hash, cached: !!source.cached }, capabilities: this.runtime.capabilities, lastError: this.lastError }
  }
}

export class Bridge {
  constructor(config, loader, log = console) {
    this.config = config; this.log = log; this.server = null; this.timer = null
    const definitions = config.sources || [{ id: 'default', sourceUrl: config.sourceUrl, cacheDir: config.cacheDir, enabled: true }]
    this.defaultSourceId = config.defaultSourceId || 'default'
    this.slots = new Map(definitions.filter(source => source.enabled).map(source => {
      const slotConfig = { ...config, ...source }
      const slotLoader = loader && definitions.length === 1 ? loader : new SourceLoader(slotConfig, log)
      return [source.id, new SourceSlot(source.id, slotConfig, slotLoader, log)]
    }))
  }
  get runtime() { return this.slots.get(this.defaultSourceId)?.runtime || null }
  async reload(options) { return this.#requiredSlot(this.defaultSourceId).reload(options) }
  async start() {
    await Promise.all([...this.slots.values()].map(slot => slot.reload().catch(error => this.log.error(`Initial load failed for source ${slot.id}: ${error.message}`))))
    this.server = http.createServer((req, res) => this.#handle(req, res))
    await new Promise((resolve, reject) => this.server.once('error', reject).listen(this.config.port, this.config.host, resolve))
    if (this.config.reloadIntervalMs) this.timer = setInterval(() => {
      for (const slot of this.slots.values()) slot.reload({ allowCache: false }).catch(error => this.log.error(`Reload failed for source ${slot.id}: ${error.message}`))
    }, this.config.reloadIntervalMs)
    return this
  }
  address() { return this.server.address() }
  async close() { clearInterval(this.timer); if (this.server) await new Promise(resolve => this.server.close(resolve)) }
  #requiredSlot(id) {
    const slot = this.slots.get(id)
    if (!slot) throw Object.assign(new Error(`unknown source: ${id}`), { status: 404 })
    return slot
  }
  #route(req) {
    const pathname = new URL(req.url, 'http://bridge.local').pathname
    if (pathname === '/health') return { id: this.defaultSourceId, operation: 'health' }
    if (pathname === '/v1/reload') return { id: this.defaultSourceId, operation: 'reload' }
    if (pathname === '/v1/music-url') return { id: this.defaultSourceId, operation: 'dispatch' }
    const match = pathname.match(/^\/sources\/([a-z0-9][a-z0-9_-]{0,31})\/(health|reload|v1\/music-url)$/)
    if (!match) return null
    return { id: match[1], operation: match[2] === 'health' ? 'health' : match[2] === 'reload' ? 'reload' : 'dispatch' }
  }
  async #handle(req, res) {
    try {
      if (!authorized(req, this.config.token)) return json(res, 401, { error: 'unauthorized' }, this.config.maxResponseBytes)
      const route = this.#route(req)
      if (!route) return json(res, 404, { error: 'not found' }, this.config.maxResponseBytes)
      const slot = this.#requiredSlot(route.id)
      if (route.operation === 'health') {
        if (req.method !== 'GET') return json(res, 404, { error: 'not found' }, this.config.maxResponseBytes)
        const health = slot.health()
        return json(res, health.ok ? 200 : 503, health, this.config.maxResponseBytes)
      }
      if (route.operation === 'reload') {
        if (req.method !== 'POST') return json(res, 404, { error: 'not found' }, this.config.maxResponseBytes)
        const result = await slot.reload({ allowCache: false })
        return json(res, 200, { ok: true, sourceId: slot.id, changed: result.changed, hash: result.source.hash }, this.config.maxResponseBytes)
      }
      if (req.method !== 'POST') return json(res, 404, { error: 'not found' }, this.config.maxResponseBytes)
      if (!slot.runtime) return json(res, 503, { error: `source is unavailable: ${slot.id}` }, this.config.maxResponseBytes)
      const raw = await readLimited(req, this.config.maxRequestBytes)
      let payload
      try { payload = JSON.parse(raw.toString('utf8')) } catch { return json(res, 400, { error: 'invalid JSON' }, this.config.maxResponseBytes) }
      if (!payload || typeof payload.source !== 'string' || typeof payload.action !== 'string' || !payload.info || typeof payload.info !== 'object')
        return json(res, 400, { error: 'expected {source, action, info}' }, this.config.maxResponseBytes)
      return json(res, 200, await slot.runtime.dispatch(payload), this.config.maxResponseBytes)
    } catch (error) {
      this.log.error(error.stack || error.message)
      json(res, errorStatus(error), { error: error.message }, this.config.maxResponseBytes)
    }
  }
}
