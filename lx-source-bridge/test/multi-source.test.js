import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { loadConfig } from '../src/config.js'
import { Bridge } from '../src/server.js'

const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const close = server => new Promise(resolve => server.close(resolve))
const source = ({ name, url, actions = ['musicUrl'], qualitys = ['128k'], platform = 'kg' }) => `
/** @name ${name}\n+ * @version 1.0.0 */
const { EVENT_NAMES, on, send } = globalThis.lx
on(EVENT_NAMES.request, ({ action }) => {
  if (action === 'musicUrl') return '${url}'
  if (action === 'lyric') return { lyric: '[00:00.00]${name}' }
})
send(EVENT_NAMES.inited, { sources: { ${platform}: { name: '${name}', type: 'music', actions: ${JSON.stringify(actions)}, qualitys: ${JSON.stringify(qualitys)} } } })`

async function setup(t) {
  const scripts = new Map([
    ['/molan.js', source({ name: 'Molan', url: 'https://audio.example.test/molan.mp3', actions: ['musicUrl', 'lyric'], qualitys: ['128k', '320k'] })],
    ['/flower.js', source({ name: 'Flower', url: 'https://audio.example.test/flower.mp3' })],
  ])
  const requests = new Map()
  const upstream = http.createServer((req, res) => {
    requests.set(req.url, (requests.get(req.url) || 0) + 1)
    const script = scripts.get(req.url)
    if (script == null) return res.writeHead(500).end('broken')
    res.writeHead(200, { 'content-type': 'application/javascript' }).end(script)
  })
  await listen(upstream)
  const upstreamBase = `http://127.0.0.1:${upstream.address().port}`
  const cacheRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'lx-multi-test-'))
  const sources = [
    { id: 'molan', sourceUrl: `${upstreamBase}/molan.js`, cacheDir: 'molan-cache', enabled: true },
    { id: 'flower', sourceUrl: `${upstreamBase}/flower.js`, cacheDir: 'flower-cache', enabled: true },
    { id: 'disabled', sourceUrl: `${upstreamBase}/disabled.js`, enabled: false },
    { id: 'broken', sourceUrl: `${upstreamBase}/broken.js`, enabled: true },
  ]
  const config = loadConfig({ SOURCES_JSON: JSON.stringify(sources), CACHE_DIR: cacheRoot, BRIDGE_PORT: '0', BRIDGE_TOKEN: 'placeholder-test-token' }, cacheRoot)
  const logs = []; const log = { info: value => logs.push(String(value)), warn: value => logs.push(String(value)), error: value => logs.push(String(value)), log() {} }
  const bridge = await new Bridge(config, null, log).start()
  const base = `http://127.0.0.1:${bridge.address().port}`
  t.after(async () => { await bridge.close(); await close(upstream); await fs.rm(cacheRoot, { recursive: true, force: true }) })
  return { base, bridge, cacheRoot, config, scripts, requests, logs }
}

const request = (app, route, body = {}, method = 'POST') => fetch(app.base + route, {
  method, headers: { authorization: 'Bearer placeholder-test-token', 'content-type': 'application/json' },
  ...(method === 'POST' ? { body: JSON.stringify(body) } : {}),
})
const music = { source: 'kg', action: 'musicUrl', info: { type: '128k', musicInfo: { id: '1' } } }

test('starts multiple sources, isolates capabilities, routes the same platform, and keeps legacy routes', async t => {
  const app = await setup(t)
  const molan = await request(app, '/sources/molan/v1/music-url', music)
  const flower = await request(app, '/sources/flower/v1/music-url', music)
  assert.deepEqual(await molan.json(), { url: 'https://audio.example.test/molan.mp3' })
  assert.deepEqual(await flower.json(), { url: 'https://audio.example.test/flower.mp3' })

  const molanHealth = await request(app, '/sources/molan/health', {}, 'GET').then(response => response.json())
  const flowerHealth = await request(app, '/sources/flower/health', {}, 'GET').then(response => response.json())
  assert.deepEqual(molanHealth.capabilities.sources.kg.actions, ['musicUrl', 'lyric'])
  assert.deepEqual(flowerHealth.capabilities.sources.kg.actions, ['musicUrl'])

  // The first enabled source is the default when no slot has id "default".
  assert.equal((await request(app, '/v1/music-url', music).then(response => response.json())).url, 'https://audio.example.test/molan.mp3')
  assert.equal((await request(app, '/health', {}, 'GET').then(response => response.json())).sourceId, 'molan')
  assert.equal((await request(app, '/v1/reload')).status, 200)
})

test('one failed source does not affect others and unknown or disabled IDs return 404', async t => {
  const app = await setup(t)
  assert.equal((await request(app, '/sources/broken/health', {}, 'GET')).status, 503)
  assert.equal((await request(app, '/sources/broken/v1/music-url', music)).status, 503)
  assert.equal((await request(app, '/sources/molan/v1/music-url', music)).status, 200)
  for (const id of ['missing', 'disabled']) {
    assert.equal((await request(app, `/sources/${id}/health`, {}, 'GET')).status, 404)
    assert.equal((await request(app, `/sources/${id}/v1/music-url`, music)).status, 404)
    assert.equal((await request(app, `/sources/${id}/reload`)).status, 404)
  }
})

test('reload and cache directories are isolated per source', async t => {
  const app = await setup(t)
  const flowerBefore = app.bridge.slots.get('flower').runtime.sourceInfo.hash
  const molanBefore = app.bridge.slots.get('molan').runtime.sourceInfo.hash
  app.scripts.set('/molan.js', source({ name: 'Molan v2', url: 'https://audio.example.test/molan-v2.mp3', actions: ['musicUrl', 'lyric'], qualitys: ['128k', '320k'] }))
  const response = await request(app, '/sources/molan/reload')
  assert.equal(response.status, 200); assert.equal((await response.json()).changed, true)
  assert.notEqual(app.bridge.slots.get('molan').runtime.sourceInfo.hash, molanBefore)
  assert.equal(app.bridge.slots.get('flower').runtime.sourceInfo.hash, flowerBefore)
  assert.equal(app.requests.get('/flower.js'), 1)

  const molanCache = await fs.readFile(path.join(app.cacheRoot, 'molan-cache', 'source.js'), 'utf8')
  const flowerCache = await fs.readFile(path.join(app.cacheRoot, 'flower-cache', 'source.js'), 'utf8')
  assert.match(molanCache, /molan-v2\.mp3/); assert.match(flowerCache, /flower\.mp3/)
  assert.notEqual(app.config.sources[0].cacheDir, app.config.sources[1].cacheDir)
})

test('SOURCE_CONFIG_JSON object form and legacy SOURCE_URL remain compatible', () => {
  const legacy = loadConfig({ SOURCE_URL: 'https://example.test/source.js' }, '/tmp/example')
  assert.equal(legacy.defaultSourceId, 'default'); assert.equal(legacy.sources[0].id, 'default')
  assert.equal(legacy.sources[0].cacheDir, '/tmp/example/.cache')
  const configured = loadConfig({ SOURCE_CONFIG_JSON: JSON.stringify({ sources: [{ id: 'one', sourceUrl: 'https://example.test/one.js' }] }) }, '/tmp/example')
  assert.equal(configured.defaultSourceId, 'one'); assert.equal(configured.sources[0].sourceUrl, 'https://example.test/one.js')
})
