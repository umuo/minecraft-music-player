import path from 'node:path'

const integer = (env, name, fallback, min = 1, max = Number.MAX_SAFE_INTEGER) => {
  const value = env[name] == null || env[name] === '' ? fallback : Number(env[name])
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error(`${name} must be an integer in ${min}..${max}`)
  return value
}

const list = value => value?.split(',').map(item => item.trim().toLowerCase()).filter(Boolean) ?? []
const SOURCE_ID = /^[a-z0-9][a-z0-9_-]{0,31}$/

const parseSources = env => {
  const raw = env.SOURCES_JSON || env.SOURCE_CONFIG_JSON
  if (!raw) {
    if (!env.SOURCE_URL) throw new Error('SOURCE_URL or SOURCES_JSON is required')
    return [{ id: 'default', sourceUrl: env.SOURCE_URL, cacheDir: 'default', enabled: true }]
  }
  let parsed
  try { parsed = JSON.parse(raw) } catch (error) { throw new Error(`Invalid source configuration JSON: ${error.message}`) }
  if (!Array.isArray(parsed)) parsed = parsed.sources
  if (!Array.isArray(parsed) || parsed.length === 0) throw new Error('source configuration must be a non-empty array (or {"sources": [...]})')
  return parsed
}

const sourceSlot = (entry, baseCacheDir, seen) => {
  if (!entry || typeof entry !== 'object' || Array.isArray(entry)) throw new Error('each source must be an object')
  const id = String(entry.id || '')
  if (!SOURCE_ID.test(id)) throw new Error(`Invalid source id: ${id || '(empty)'}`)
  if (seen.has(id)) throw new Error(`Duplicate source id: ${id}`)
  seen.add(id)
  if (typeof entry.enabled !== 'undefined' && typeof entry.enabled !== 'boolean') throw new Error(`enabled must be boolean for source ${id}`)
  const enabled = entry.enabled !== false
  if (typeof entry.sourceUrl !== 'string' || !entry.sourceUrl) throw new Error(`sourceUrl is required for source ${id}`)
  const sourceUrl = new URL(entry.sourceUrl)
  if (!['http:', 'https:'].includes(sourceUrl.protocol)) throw new Error(`sourceUrl must use HTTP(S) for source ${id}`)
  const namespace = entry.cacheDir == null || entry.cacheDir === '' ? id : String(entry.cacheDir)
  if (!SOURCE_ID.test(namespace)) throw new Error(`cacheDir must be a safe namespace for source ${id}`)
  return { id, sourceUrl: sourceUrl.href, cacheDir: path.join(baseCacheDir, namespace), enabled }
}

export function loadConfig(env = process.env, cwd = process.cwd()) {
  const cacheDir = path.resolve(cwd, env.CACHE_DIR || '.cache')
  const seen = new Set()
  const sources = parseSources(env).map(entry => sourceSlot(entry, cacheDir, seen))
  // Legacy deployments cached directly in CACHE_DIR; keep that cache usable after upgrading.
  if (!env.SOURCES_JSON && !env.SOURCE_CONFIG_JSON) sources[0].cacheDir = cacheDir
  const enabled = sources.filter(source => source.enabled)
  if (!enabled.length) throw new Error('at least one source must be enabled')
  const defaultSourceId = enabled.some(source => source.id === 'default') ? 'default' : enabled[0].id
  return {
    sources, defaultSourceId,
    // Preserve the old single-source fields for SourceLoader and callers using SOURCE_URL.
    ...(sources.length === 1 ? sources[0] : {}),
    host: env.BRIDGE_HOST || '127.0.0.1',
    port: integer(env, 'BRIDGE_PORT', 9863, 0),
    token: env.BRIDGE_TOKEN || '',
    cacheRoot: cacheDir,
    sourceHosts: list(env.ALLOWED_SOURCE_HOSTS),
    mediaHosts: list(env.ALLOWED_MEDIA_HOSTS),
    maxScriptBytes: integer(env, 'MAX_SCRIPT_BYTES', 1024 * 1024),
    maxRequestBytes: integer(env, 'MAX_REQUEST_BYTES', 256 * 1024),
    maxUpstreamBytes: integer(env, 'MAX_UPSTREAM_BYTES', 4 * 1024 * 1024),
    maxResponseBytes: integer(env, 'MAX_RESPONSE_BYTES', 1024 * 1024),
    requestTimeoutMs: integer(env, 'REQUEST_TIMEOUT_MS', 60_000, 1, 60_000),
    scriptTimeoutMs: integer(env, 'SCRIPT_TIMEOUT_MS', 60_000, 1, 60_000),
    redirectLimit: integer(env, 'REDIRECT_LIMIT', 3, 0),
    reloadIntervalMs: integer(env, 'RELOAD_INTERVAL_MS', 0, 0),
  }
}
