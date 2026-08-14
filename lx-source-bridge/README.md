# LX Source Bridge

This standalone Node.js 22+ bridge loads one or more trusted LX Music custom-source JavaScript URLs behind one listener. Each source slot has an independent loader, cache, runtime, capability set, reload state, and health status. The URL path selects the slot; the unchanged LX payload `source` field still means a music platform such as `kg`, `wy`, `kw`, `tx`, or `mg`.

## Quick start

The legacy single-source configuration remains supported and creates the `default` slot:

```bash
cd lx-source-bridge
npm install
export SOURCE_URL='https://sources.example.invalid/default.js'
export BRIDGE_TOKEN='replace-with-a-long-random-secret'
export ALLOWED_SOURCE_HOSTS='sources.example.invalid'
npm start
```

For multiple slots in one process, set `SOURCES_JSON`:

```bash
export SOURCES_JSON='[
  {"id":"molan","sourceUrl":"https://sources.example.invalid/molan.js","cacheDir":"molan","enabled":true},
  {"id":"flower","sourceUrl":"https://sources.example.invalid/flower.js","cacheDir":"flower","enabled":true}
]'
export BRIDGE_TOKEN='replace-with-a-long-random-secret'
export ALLOWED_SOURCE_HOSTS='sources.example.invalid'
npm start
```

The default listener is `127.0.0.1:9863`. The first enabled slot is the compatibility default unless an enabled slot is named `default`. Startup logs each loaded source's name, version, SHA-256 hash, and cache state. One slot failing to load does not prevent the listener or other slots from starting.

`SOURCE_CONFIG_JSON` is an alias for `SOURCES_JSON` and also accepts `{"sources":[...]}`. IDs and `cacheDir` namespaces must match `[a-z0-9][a-z0-9_-]{0,31}`. A namespace is always placed below `CACHE_DIR`; it is not an arbitrary filesystem path. Disabled slots are not loaded and their routes return 404.

## Routes

| Route | Method | Meaning |
| --- | --- | --- |
| `/sources/{id}/v1/music-url` | `POST` | Dispatch the unchanged LX `{source,action,info}` payload to one slot |
| `/sources/{id}/health` | `GET` | Report that slot's runtime, capabilities, cache state, and last load error |
| `/sources/{id}/reload` | `POST` | Download and atomically reload only that slot |
| `/v1/music-url` | `POST` | Compatibility route to the default slot |
| `/health` | `GET` | Compatibility health route to the default slot |
| `/v1/reload` | `POST` | Compatibility reload route to the default slot |

Unknown and disabled IDs return 404. A known but unavailable source returns 503 while other sources continue operating. Every route uses the same `BRIDGE_TOKEN` bearer authentication.

Configure Minecraft's server-only named resolvers with the paths:

```toml
[resolver]
sources = [
  '''{"id":"molan","displayName":"Molan","playbackApiUrl":"http://127.0.0.1:9863/sources/molan/v1/music-url","token":"replace-with-the-shared-bridge-secret","allowedAudioHosts":["audio-a.example.invalid"],"requireHttps":true,"timeoutSeconds":60,"platforms":["kg","wy"],"qualities":["128k","320k"],"capabilities":["musicUrl"],"enabled":true,"permissionLevel":0}''',
  '''{"id":"flower","displayName":"Flower","playbackApiUrl":"http://127.0.0.1:9863/sources/flower/v1/music-url","token":"replace-with-the-shared-bridge-secret","allowedAudioHosts":["audio-b.example.invalid"],"requireHttps":true,"timeoutSeconds":60,"platforms":["kg"],"qualities":["128k"],"capabilities":["musicUrl","lyric"],"enabled":true,"permissionLevel":0}'''
]
```

Each resolver token must equal the shared `BRIDGE_TOKEN`. URLs and tokens remain in Minecraft's server-only configuration; clients receive only source ID, display name, platforms, qualities, and capabilities. Put actual final audio CDN hosts—not the bridge host—in `allowedAudioHosts`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SOURCE_URL` | — | Legacy trusted HTTP(S) JS URL; creates `default` |
| `SOURCES_JSON` | — | Array of `{id,sourceUrl,cacheDir,enabled}` slots |
| `SOURCE_CONFIG_JSON` | — | Alias accepting the array or `{"sources":[...]}` |
| `BRIDGE_HOST` / `BRIDGE_PORT` | `127.0.0.1` / `9863` | Listener; do not bind publicly |
| `BRIDGE_TOKEN` | empty | Shared bearer token; strongly recommended |
| `ALLOWED_SOURCE_HOSTS` | unrestricted | Exact/parent domains allowed for downloads and redirects |
| `ALLOWED_MEDIA_HOSTS` | unrestricted | Optional allowlist for returned audio/image URLs |
| `CACHE_DIR` | `.cache` | Cache root; slots use separate namespaces below it |
| `MAX_SCRIPT_BYTES` | `1048576` | Maximum source script download |
| `MAX_REQUEST_BYTES` | `262144` | Maximum inbound request |
| `MAX_UPSTREAM_BYTES` | `4194304` | Maximum response read by an LX script request |
| `MAX_RESPONSE_BYTES` | `1048576` | Maximum bridge JSON response |
| `REQUEST_TIMEOUT_MS` | `60000` | Script HTTP request/action deadline; maximum `60000` |
| `SCRIPT_TIMEOUT_MS` | `60000` | Synchronous evaluation/init deadline; maximum `60000` |
| `REDIRECT_LIMIT` | `3` | Explicit redirect cap; `0` disables redirects |
| `RELOAD_INTERVAL_MS` | `0` | Per-slot polling interval; `0` disables it |

Reloads activate a script only after successful initialization. Unchanged hashes are not re-evaluated. Startup downloads may fall back to that slot's cache; explicit reload and polling failures leave its active runtime untouched.

The compatibility surface includes `EVENT_NAMES`, `on`, `send`, callback-style `request`, `env`, `version`, `currentScriptInfo`, buffer helpers, MD5, AES encryption, and random bytes. HTTP(S) requests support methods, headers, bodies, forms, timeout, redirects, text, and JSON parsing. A script declaring only `musicUrl` receives stable empty-lyric compatibility.

## Trust boundary

An LX source is executable code. The bridge is a trust boundary, not a complete security sandbox. Scripts run without `process` or `require`, but can make outbound HTTP(S) requests, consume resources, and potentially exploit defects.

- Use only code and update owners you trust; prefer immutable URLs.
- Run as a dedicated low-privilege OS user or constrained container.
- Keep `BRIDGE_HOST=127.0.0.1`, set `BRIDGE_TOKEN`, and configure host allowlists and resource limits.
- Review logged versions and hashes. Never expose Minecraft files or unrelated secrets.

## Tests

Tests use local fixtures and mock servers only:

```bash
npm test
```

They cover multi-source startup, capability and cache isolation, same-platform routing, per-source failure and reload, legacy routes, unknown IDs, music/lyric/pic, authentication, host allowlisting, timeout, and size limits.
