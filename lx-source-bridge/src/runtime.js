import crypto from 'node:crypto'
import vm from 'node:vm'
import { encodeBody, fetchLimited, hostAllowed } from './http.js'

const EVENT_NAMES = Object.freeze({ request: 'request', inited: 'inited', updateAlert: 'updateAlert' })
const safeUrl = (value, kind, config) => {
  if (typeof value !== 'string' || value.length === 0 || value.length > 4096) throw new Error(`${kind} URL is invalid`)
  const url = new URL(value)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.hash) throw new Error(`${kind} URL is unsafe`)
  if (!hostAllowed(url.hostname.toLowerCase(), config.mediaHosts)) throw new Error(`${kind} host is not allowed`)
  return url.href
}

export class LxRuntime {
  constructor(config, sourceInfo, log = console) {
    this.config = config; this.sourceInfo = sourceInfo; this.log = log
    this.handler = null; this.capabilities = null
  }
  async init() {
    let initResolve, initReject
    const initialized = new Promise((resolve, reject) => { initResolve = resolve; initReject = reject })
    const lx = {
      EVENT_NAMES, env: 'desktop', version: '2.0.0',
      currentScriptInfo: { name: this.sourceInfo.name || '', description: this.sourceInfo.description || '', version: this.sourceInfo.version || '', author: this.sourceInfo.author || '', homepage: this.sourceInfo.homepage || '', rawScript: this.sourceInfo.rawScript },
      on: async (name, handler) => {
        if (name !== EVENT_NAMES.request || typeof handler !== 'function') throw new Error(`Unsupported event: ${name}`)
        this.handler = handler
      },
      send: async (name, data) => {
        if (name === EVENT_NAMES.updateAlert) return
        if (name !== EVENT_NAMES.inited) throw new Error(`Unsupported event: ${name}`)
        if (this.capabilities) throw new Error('Script is inited')
        this.capabilities = normalizeCapabilities(data)
        initResolve(this.capabilities)
      },
      request: (url, options = {}, callback) => {
        const controller = new AbortController()
        this.#request(url, options, controller.signal).then(resp => callback(null, resp, resp.body), error => callback(error, null, null))
        return () => controller.abort()
      },
      utils: {
        buffer: { from: (...args) => Buffer.from(...args), bufToString: (buf, format) => Buffer.from(buf).toString(format) },
        crypto: {
          md5: value => crypto.createHash('md5').update(value).digest('hex'),
          randomBytes: size => crypto.randomBytes(size),
          aesEncrypt: (data, mode, key, iv) => {
            const cipher = crypto.createCipheriv(mode, key, iv || null)
            return Buffer.concat([cipher.update(data), cipher.final()])
          },
        },
      },
    }
    const context = vm.createContext({ globalThis: null, lx, Buffer, URL, URLSearchParams, TextEncoder, TextDecoder, console: this.log, setTimeout, clearTimeout })
    context.globalThis = context
    try { new vm.Script(this.sourceInfo.rawScript, { filename: 'lx-custom-source.js' }).runInContext(context, { timeout: this.config.scriptTimeoutMs }) }
    catch (error) { initReject(error); throw error }
    const timer = setTimeout(() => initReject(new Error('source did not send inited event')), this.config.scriptTimeoutMs)
    try { await initialized } finally { clearTimeout(timer) }
    if (!this.handler) throw new Error('source did not register request handler')
    return this
  }
  async #request(url, options, outerSignal) {
    const headers = new Headers(options.headers || {})
    const body = encodeBody(options, headers)
    const result = await fetchLimited(url, {
      timeoutMs: Math.min(Number(options.timeout) || this.config.requestTimeoutMs, this.config.requestTimeoutMs),
      maxBytes: this.config.maxUpstreamBytes,
      fetch: { method: options.method || 'GET', headers, body, signal: outerSignal },
    }, this.config)
    let parsed = result.bytes.toString('utf8')
    try { parsed = JSON.parse(parsed) } catch {}
    return { statusCode: result.response.status, statusMessage: result.response.statusText, headers: Object.fromEntries(result.response.headers), bytes: result.bytes.length, raw: result.bytes, body: parsed }
  }
  supports(source, action) { return this.capabilities.sources[source]?.actions.includes(action) ?? false }
  async dispatch(payload) {
    const { source, action, info } = payload
    const declared = this.capabilities.sources[source]
    if (!declared) throw Object.assign(new Error(`source is not supported: ${source}`), { status: 400 })
    if (!declared.actions.includes(action)) {
      if (action === 'lyric') { this.log.info(`Source ${source} does not declare lyric; returning empty lyric`); return { lyric: '' } }
      throw Object.assign(new Error(`action is not supported: ${source}/${action}`), { status: 400 })
    }
    if (action === 'musicUrl' && info?.type && declared.qualitys.length && !declared.qualitys.includes(info.type))
      throw Object.assign(new Error(`quality is not supported: ${info.type}`), { status: 400 })
    let timer
    const result = await Promise.race([
      Promise.resolve().then(() => this.handler({ source, action, info })),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('source request timed out')), this.config.requestTimeoutMs) }),
    ]).finally(() => clearTimeout(timer))
    if (action === 'musicUrl') return { url: safeUrl(typeof result === 'string' ? result : result?.url, 'audio', this.config) }
    if (action === 'pic') return { url: safeUrl(typeof result === 'string' ? result : result?.url || result?.pic, 'picture', this.config) }
    if (action === 'lyric') {
      const lyric = typeof result === 'string' ? result : result?.lyric
      if (typeof lyric !== 'string' || Buffer.byteLength(lyric) > 200_000) throw new Error('lyric is invalid or too large')
      return { lyric }
    }
    throw Object.assign(new Error(`unknown action: ${action}`), { status: 400 })
  }
}

function normalizeCapabilities(data) {
  if (!data?.sources || typeof data.sources !== 'object') throw new Error('inited event is missing sources')
  const sources = {}
  for (const [id, source] of Object.entries(data.sources)) {
    if (!source || source.type !== 'music' || !Array.isArray(source.actions) || !Array.isArray(source.qualitys)) continue
    sources[id] = { name: typeof source.name === 'string' ? source.name : id, type: 'music', actions: [...new Set(source.actions.filter(x => ['musicUrl', 'lyric', 'pic'].includes(x)))], qualitys: [...new Set(source.qualitys.filter(x => typeof x === 'string'))] }
  }
  if (!Object.keys(sources).length) throw new Error('source declares no usable music capability')
  return { sources }
}
