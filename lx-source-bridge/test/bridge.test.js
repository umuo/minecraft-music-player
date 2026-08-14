import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { loadConfig } from '../src/config.js'
import { Bridge } from '../src/server.js'
import { SourceLoader } from '../src/source-loader.js'

const fixtures = path.join(import.meta.dirname, 'fixtures')
const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const close = server => new Promise(resolve => server.close(resolve))
const fixture = name => fs.readFile(path.join(fixtures, name))

async function upstream(sourceName = 'full-source.txt') {
  let source = await fixture(sourceName)
  let sourceRequests = 0
  const server = http.createServer(async (req, res) => {
    if (req.url === '/source.js') { sourceRequests++; res.writeHead(200, { 'content-type': 'application/javascript' }).end(source); return }
    if (req.url?.startsWith('/resolve')) { res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify({ url: 'https://audio.example.test/song.mp3' })); return }
    if (req.url === '/slow') { setTimeout(() => res.end('late'), 200); return }
    if (req.url === '/large') { res.end(Buffer.alloc(2048)); return }
    res.writeHead(404).end()
  })
  await listen(server)
  const base = `http://127.0.0.1:${server.address().port}`
  return { server, base, get sourceRequests() { return sourceRequests }, setSource(value) { source = Buffer.from(value) } }
}

async function setup(t, sourceName = 'full-source.txt', overrides = {}) {
  const remote = await upstream(sourceName)
  const cacheDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lx-bridge-test-'))
  const config = loadConfig({ SOURCE_URL: `${remote.base}/source.js`, BRIDGE_PORT: '0', BRIDGE_TOKEN: 'secret', CACHE_DIR: cacheDir, REQUEST_TIMEOUT_MS: '100', ...overrides }, cacheDir)
  const logs = []; const log = { info: value => logs.push(String(value)), warn: value => logs.push(String(value)), error: value => logs.push(String(value)), log: () => {} }
  const bridge = await new Bridge(config, new SourceLoader(config, log), log).start()
  const base = `http://127.0.0.1:${bridge.address().port}`
  t.after(async () => { await bridge.close(); await close(remote.server); await fs.rm(cacheDir, { recursive: true, force: true }) })
  return { remote, config, bridge, base, logs }
}

const post = (base, route, body, token = 'secret') => fetch(base + route, { method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify(body) })
const payload = (action, base, id = '42') => ({ source: 'kg', action, info: { type: '128k', musicInfo: { id, api: base } } })

test('downloads, initializes, and serves musicUrl, lyric, and pic', async t => {
  const app = await setup(t)
  const music = await post(app.base, '/v1/music-url', payload('musicUrl', app.remote.base))
  assert.equal(music.status, 200); assert.deepEqual(await music.json(), { url: 'https://audio.example.test/song.mp3' })
  const lyric = await post(app.base, '/v1/music-url', payload('lyric', app.remote.base))
  assert.deepEqual(await lyric.json(), { lyric: '[00:00.00]fixture lyric' })
  const pic = await post(app.base, '/v1/music-url', payload('pic', app.remote.base))
  assert.deepEqual(await pic.json(), { url: 'https://img.example.test/cover.jpg' })
  const health = await fetch(app.base + '/health', { headers: { authorization: 'Bearer secret' } }).then(r => r.json())
  assert.equal(health.source.version, '9.8.7'); assert.deepEqual(health.capabilities.sources.kg.actions, ['musicUrl', 'lyric', 'pic'])
})

test('requires token and validates source/action/quality and handler errors', async t => {
  const app = await setup(t)
  assert.equal((await post(app.base, '/v1/music-url', payload('musicUrl', app.remote.base), 'wrong')).status, 401)
  assert.equal((await post(app.base, '/v1/music-url', { ...payload('musicUrl', app.remote.base), source: 'wy' })).status, 400)
  assert.equal((await post(app.base, '/v1/music-url', { ...payload('musicUrl', app.remote.base), info: { type: 'flac', musicInfo: { id: '1' } } })).status, 400)
  const failed = await post(app.base, '/v1/music-url', payload('musicUrl', app.remote.base, 'error'))
  assert.equal(failed.status, 502); assert.match((await failed.json()).error, /fixture failure/)
})

test('returns stable empty lyric when source declares only musicUrl', async t => {
  const app = await setup(t, 'music-only-source.txt')
  const response = await post(app.base, '/v1/music-url', payload('lyric', app.remote.base))
  assert.equal(response.status, 200); assert.deepEqual(await response.json(), { lyric: '' })
  assert.ok(app.logs.some(line => line.includes('does not declare lyric')))
})

test('reload detects changed source and cache survives download failure', async t => {
  const app = await setup(t, 'music-only-source.txt')
  const before = app.bridge.runtime.sourceInfo.hash
  app.remote.setSource((await fixture('music-only-source.txt')).toString().replace('song.mp3', 'new.mp3'))
  const reloaded = await post(app.base, '/v1/reload', {})
  assert.equal(reloaded.status, 200); assert.equal((await reloaded.json()).changed, true)
  assert.notEqual(app.bridge.runtime.sourceInfo.hash, before)
  await close(app.remote.server)
  const cached = await new SourceLoader(app.config, { warn() {} }).load()
  assert.equal(cached.cached, true); assert.equal(cached.hash, app.bridge.runtime.sourceInfo.hash)
})

test('enforces source download size and request timeout', async t => {
  const remote = await upstream()
  const cacheDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lx-limit-test-'))
  t.after(async () => { await close(remote.server); await fs.rm(cacheDir, { recursive: true, force: true }) })
  const tooSmall = loadConfig({ SOURCE_URL: `${remote.base}/source.js`, CACHE_DIR: cacheDir, MAX_SCRIPT_BYTES: '10' }, cacheDir)
  await assert.rejects(() => new SourceLoader(tooSmall).load({ allowCache: false }), /exceeds 10 bytes/)
  const config = loadConfig({ SOURCE_URL: `${remote.base}/slow`, CACHE_DIR: cacheDir, REQUEST_TIMEOUT_MS: '20' }, cacheDir)
  await assert.rejects(() => new SourceLoader(config).load({ allowCache: false }), /timed out/)
})

test('source allowlist rejects a non-listed download host', async t => {
  const remote = await upstream()
  const cacheDir = await fs.mkdtemp(path.join(os.tmpdir(), 'lx-host-test-'))
  t.after(async () => { await close(remote.server); await fs.rm(cacheDir, { recursive: true, force: true }) })
  const config = loadConfig({ SOURCE_URL: `${remote.base}/source.js`, CACHE_DIR: cacheDir, ALLOWED_SOURCE_HOSTS: 'example.com' }, cacheDir)
  await assert.rejects(() => new SourceLoader(config).load({ allowCache: false }), /not allowed/)
})
