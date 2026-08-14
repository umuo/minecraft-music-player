import path from 'node:path'

const integer = (env, name, fallback, min = 1) => {
  const value = env[name] == null || env[name] === '' ? fallback : Number(env[name])
  if (!Number.isSafeInteger(value) || value < min) throw new Error(`${name} must be an integer >= ${min}`)
  return value
}

const list = value => value?.split(',').map(item => item.trim().toLowerCase()).filter(Boolean) ?? []

export function loadConfig(env = process.env, cwd = process.cwd()) {
  if (!env.SOURCE_URL) throw new Error('SOURCE_URL is required')
  const sourceUrl = new URL(env.SOURCE_URL)
  if (!['http:', 'https:'].includes(sourceUrl.protocol)) throw new Error('SOURCE_URL must use HTTP(S)')
  return {
    sourceUrl: sourceUrl.href,
    host: env.BRIDGE_HOST || '127.0.0.1',
    port: integer(env, 'BRIDGE_PORT', 9863, 0),
    token: env.BRIDGE_TOKEN || '',
    cacheDir: path.resolve(cwd, env.CACHE_DIR || '.cache'),
    sourceHosts: list(env.ALLOWED_SOURCE_HOSTS),
    mediaHosts: list(env.ALLOWED_MEDIA_HOSTS),
    maxScriptBytes: integer(env, 'MAX_SCRIPT_BYTES', 1024 * 1024),
    maxRequestBytes: integer(env, 'MAX_REQUEST_BYTES', 256 * 1024),
    maxUpstreamBytes: integer(env, 'MAX_UPSTREAM_BYTES', 4 * 1024 * 1024),
    maxResponseBytes: integer(env, 'MAX_RESPONSE_BYTES', 1024 * 1024),
    requestTimeoutMs: integer(env, 'REQUEST_TIMEOUT_MS', 15_000),
    scriptTimeoutMs: integer(env, 'SCRIPT_TIMEOUT_MS', 5_000),
    redirectLimit: integer(env, 'REDIRECT_LIMIT', 3, 0),
    reloadIntervalMs: integer(env, 'RELOAD_INTERVAL_MS', 0, 0),
  }
}
