import crypto from 'node:crypto'
import http from 'node:http'
import { readLimited } from './http.js'
import { LxRuntime } from './runtime.js'

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

export class Bridge {
  constructor(config, loader, log = console) { this.config = config; this.loader = loader; this.log = log; this.runtime = null; this.server = null; this.reloadPromise = null }
  async reload(options) {
    if (this.reloadPromise) return this.reloadPromise
    this.reloadPromise = (async () => {
      const source = await this.loader.load(options)
      if (this.runtime?.sourceInfo.hash === source.hash) return { changed: false, source }
      const runtime = await new LxRuntime(this.config, source, this.log).init()
      this.runtime = runtime
      this.log.info(`Loaded LX source ${source.name || 'unnamed'} version=${source.version || 'unknown'} sha256=${source.hash}${source.cached ? ' (cache)' : ''}`)
      return { changed: true, source }
    })().finally(() => { this.reloadPromise = null })
    return this.reloadPromise
  }
  async start() {
    await this.reload()
    this.server = http.createServer((req, res) => this.#handle(req, res))
    await new Promise((resolve, reject) => this.server.once('error', reject).listen(this.config.port, this.config.host, resolve))
    if (this.config.reloadIntervalMs) this.timer = setInterval(() => this.reload({ allowCache: false }).catch(error => this.log.error(`Reload failed: ${error.message}`)), this.config.reloadIntervalMs)
    return this
  }
  address() { return this.server.address() }
  async close() { clearInterval(this.timer); if (this.server) await new Promise(resolve => this.server.close(resolve)) }
  async #handle(req, res) {
    try {
      if (!authorized(req, this.config.token)) return json(res, 401, { error: 'unauthorized' }, this.config.maxResponseBytes)
      if (req.method === 'GET' && req.url === '/health') return json(res, 200, { ok: true, source: { name: this.runtime.sourceInfo.name, version: this.runtime.sourceInfo.version, hash: this.runtime.sourceInfo.hash, cached: !!this.runtime.sourceInfo.cached }, capabilities: this.runtime.capabilities }, this.config.maxResponseBytes)
      if (req.method === 'POST' && req.url === '/v1/reload') {
        const result = await this.reload({ allowCache: false })
        return json(res, 200, { ok: true, changed: result.changed, hash: result.source.hash }, this.config.maxResponseBytes)
      }
      if (req.method !== 'POST' || req.url !== '/v1/music-url') return json(res, 404, { error: 'not found' }, this.config.maxResponseBytes)
      const raw = await readLimited(req, this.config.maxRequestBytes)
      let payload
      try { payload = JSON.parse(raw.toString('utf8')) } catch { return json(res, 400, { error: 'invalid JSON' }, this.config.maxResponseBytes) }
      if (!payload || typeof payload.source !== 'string' || typeof payload.action !== 'string' || !payload.info || typeof payload.info !== 'object')
        return json(res, 400, { error: 'expected {source, action, info}' }, this.config.maxResponseBytes)
      const result = await this.runtime.dispatch(payload)
      json(res, 200, result, this.config.maxResponseBytes)
    } catch (error) {
      this.log.error(error.stack || error.message)
      json(res, error.status || 502, { error: error.message }, this.config.maxResponseBytes)
    }
  }
}
