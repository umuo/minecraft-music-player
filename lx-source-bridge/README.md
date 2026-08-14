# LX Source Bridge

This standalone Node.js 22+ bridge downloads one trusted LX Music custom-source JavaScript URL, provides the relevant `globalThis.lx` API, and exposes the HTTP contract expected by `ServerPlaybackResolver`.

## Quick start

```bash
cd lx-source-bridge
npm install
export SOURCE_URL='https://raw.githubusercontent.com/Macrohard0001/lx-ikun-music-sources/main/V260810/%E6%8E%A8%E8%8D%90/%E5%A2%A8%E6%BE%9C%E8%81%9A%E5%90%88%E9%9F%B3%E6%BA%90%20v2.0.0.js'
export BRIDGE_TOKEN='replace-with-a-long-random-secret'
export ALLOWED_SOURCE_HOSTS='raw.githubusercontent.com'
npm start
```

The default listener is `127.0.0.1:9863`. A GitHub raw URL can be used directly; the script does not need to be rewritten as an HTTP API. Startup logs the source name, declared version, SHA-256 hash, and whether a cached copy was used.

Configure `world/serverconfig/musicbox-server.toml`:

```toml
[resolver]
playbackApiUrl = "http://127.0.0.1:9863/v1/music-url"
playbackApiToken = "replace-with-a-long-random-secret"
allowedAudioHosts = ["the-cdn-host-returned-by-the-source.example"]
httpTimeoutSeconds = 15
requireHttps = true
```

`playbackApiToken` must equal `BRIDGE_TOKEN`. Loopback HTTP is accepted by the mod even when `requireHttps` is true; audio returned to clients must still use HTTPS. Put actual final audio CDN hostnames, not the Bridge hostname, in `allowedAudioHosts`. The current client decoder supports MP3 streams.

The referenced 墨澜聚合音源 v2.0.0 declares only `musicUrl`. The Bridge therefore returns `{"lyric":""}` and logs that lyric is undeclared, so the resolver's parallel lyric request does not prevent playback. If a later source declares `lyric` or `pic`, the Bridge invokes it and returns `{"lyric":"..."}` or `{"url":"..."}` automatically.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SOURCE_URL` | required | Trusted HTTP(S) LX source JS URL |
| `BRIDGE_HOST` / `BRIDGE_PORT` | `127.0.0.1` / `9863` | Listener; do not bind publicly |
| `BRIDGE_TOKEN` | empty | Bearer token; setting it is strongly recommended |
| `ALLOWED_SOURCE_HOSTS` | unrestricted | Comma-separated exact/parent domains for source downloads and redirects |
| `ALLOWED_MEDIA_HOSTS` | unrestricted | Optional Bridge-side allowlist for returned audio/image URLs |
| `CACHE_DIR` | `.cache` | Script and metadata cache |
| `MAX_SCRIPT_BYTES` | `1048576` | Maximum source script download |
| `MAX_REQUEST_BYTES` | `262144` | Maximum inbound Mod request |
| `MAX_UPSTREAM_BYTES` | `4194304` | Maximum response read by an LX script request |
| `MAX_RESPONSE_BYTES` | `1048576` | Maximum Bridge JSON response |
| `REQUEST_TIMEOUT_MS` | `15000` | Script request/action deadline |
| `SCRIPT_TIMEOUT_MS` | `5000` | Synchronous evaluation/init deadline |
| `REDIRECT_LIMIT` | `3` | Explicit redirect cap; `0` disables redirects |
| `RELOAD_INTERVAL_MS` | `0` | Source polling interval; `0` disables it |

`POST /v1/reload` downloads again and atomically activates the script only after successful initialization. It requires the same bearer token. Unchanged hashes are not re-evaluated. A startup download failure falls back to the last cache; explicit reload and polling failures leave the active runtime untouched. Authenticated `GET /health` reports hash, version, cache state, and capabilities.

The compatibility surface includes `EVENT_NAMES` (`request`, `inited`, `updateAlert`), `on`, `send`, callback-style `request`, `env`, `version`, `currentScriptInfo`, buffer helpers, and crypto `md5`, AES encryption, and random bytes. HTTP(S) requests support method, headers, body, URL-encoded form, `formData`, timeout, redirects, text bodies, and automatic JSON parsing.

## Trust boundary

An LX source is executable code. The Bridge is the trust boundary—not a complete security sandbox. Although scripts run in a restricted VM context without `process` or `require`, they can intentionally reach arbitrary external HTTP(S) domains through the LX request API, consume resources, and potentially exploit defects. Network access is a source feature and must not be represented as full isolation.

- Use only a JS URL whose code and update owner you trust. HTTPS authenticates transport, not future code at a moving URL.
- Run as a dedicated low-privilege OS user or in a tightly limited container; do not expose Minecraft files or unrelated secrets.
- Keep `BRIDGE_HOST=127.0.0.1`; never expose the Bridge publicly.
- Set `BRIDGE_TOKEN`, `ALLOWED_SOURCE_HOSTS`, container CPU/memory limits, and outbound firewall rules where practical.
- Review logged versions/hashes. An immutable raw commit URL gives stronger change control than a branch URL.

## Tests

Tests use only local fixtures and mock HTTP servers; no real music API is contacted.

```bash
npm test
```

They cover capabilities, music/lyric/pic, missing-lyric compatibility, errors, bearer authentication, download/reload/cache behavior, host allowlisting, timeout, and size limits.
