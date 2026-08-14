# Music Box

NeoForge 1.21.1 / Java 21 mod that adds a placeable music box. The server owns playback state and resolves tracks; nearby clients connect directly to the validated audio CDN.

## Current milestone (0.2.0)

- Placeable `musicbox:music_box` block and persisted block entity.
- Server-authoritative play/stop events with a configurable broadcast radius.
- Three-second synchronized start window by default.
- LX-style playback URL resolver API.
- MP3 playback on each client through a bundled pure-Java decoder.
- Separate `ui` Gradle module for the in-game browser and interaction state.
- Live Kugou and NetEase platform switching.
- Track search with pagination and selectable playback quality.
- Trending playlists, playlist search, and playlist detail browsing.
- Loading, empty, selection, paging, and error states designed for controller-like in-game interaction.
- Per-platform search memory, recent-search recall, refresh/retry, playlist back-navigation, and keyboard controls.

The music box now keeps a server-authoritative, persisted queue with add, remove, skip, clear, and automatic
track advancement. Track and playlist covers are downloaded asynchronously from allow-listed platform image
hosts and released with the browser screen. Synchronized LRC lyrics are requested by the server through the private resolver
and rendered above the HUD hotbar. MP3 data is decoded to PCM and played through Minecraft's OpenAL channel at
the music box position with distance attenuation. LX sources are supported through the separate `lx-source-bridge` process.

## In-game browser

Right-click a placed music box, then:

1. Switch between Kugou and NetEase.
2. Choose Tracks or Playlists.
3. Search tracks, or leave playlist search empty to browse trending lists.
4. Select a result and use Open/Play; double-clicking also performs the primary action.
5. Cycle `128k`, `320k`, `flac`, and `flac24bit` before requesting playback.

The browser remembers separate queries for every platform/tab during the current client session. `Recent` cycles
through the latest eight searches. Use `Up`/`Down` to select, `Enter` to open or play, `Ctrl+F` to focus search,
and `Esc` to return from a playlist without losing the previous page and scroll position.

Catalog metadata is read from the platforms' public web endpoints. Playback remains separate: a client sends only
track metadata and intent, the server resolves it, and clients stream the resulting URL directly from the CDN.

## Playback resolver API

Configure each server/world in `world/serverconfig/musicbox-server.toml` (the exact world path depends on the host):

```toml
[resolver]
playbackApiUrl = "https://music-api.example.com/v1/music-url"
playbackApiToken = "replace-with-server-secret"
allowedAudioHosts = ["cdn.example.com"]
httpTimeoutSeconds = 15
requireHttps = true
```

Clients have no resolver URL or token setting. `config/musicbox-client.toml` contains only UI preferences such
as `defaultQuality`; the bearer token remains solely in the server config and is never encoded in a game packet.

The server sends the following Bridge JSON for both `musicUrl` and `lyric` (only `action` changes):

```json
{
  "source": "kg",
  "action": "musicUrl",
  "info": {
    "type": "320k",
    "musicInfo": {
      "id": "123456",
      "songmid": "123456",
      "name": "Example song",
      "singer": "Example artist",
      "albumName": "Example album",
      "interval": 240,
      "hash": "128k-content-hash",
      "_types": {
        "128k": {"hash": "128k-content-hash"},
        "320k": {"hash": "320k-content-hash"},
        "flac": {"hash": "flac-content-hash"},
        "flac24bit": {"hash": "hires-content-hash"}
      }
    }
  }
}
```

Accepted `musicUrl` responses:

```json
{"url":"https://cdn.example.com/audio/123456.mp3"}
```

or:

```json
{"data":{"url":"https://cdn.example.com/audio/123456.mp3"}}
```

Accepted lyric responses include `{"lyric":"..."}`, `{"lrc":"..."}`, and either field nested under `data`.
The current decoder supports MP3 streams. The resolver returns JSON, never audio bytes. Server HTTP calls are
asynchronous and capped at 1 MiB; redirects and unexpected Content-Types are rejected. Resolver and final URLs
must be HTTP(S), contain a host, and contain no user-info or fragment. HTTPS is required except for loopback
development endpoints. A final audio host must exactly match or be a subdomain of `allowedAudioHosts`.
Clients repeat a minimal HTTP(S)/host/user-info/fragment check, then download audio directly from that CDN;
Minecraft servers never proxy the audio stream.

### Safe LX compatibility

LX JavaScript sources are deliberately not evaluated in the Minecraft process: a script engine sandbox cannot
reliably prevent filesystem, network, reflection, or credential access once bridged to Java. Run a source you
trust in a separately permissioned local bridge and point the server's `playbackApiUrl` at its loopback endpoint.
The server sends only the documented `musicUrl` and `lyric` actions and adds the configured bearer token.

The included [LX Source Bridge](lx-source-bridge/README.md) accepts one custom-source JS URL directly, including a
GitHub raw URL; no manual HTTP API conversion is needed:

```bash
cd lx-source-bridge
npm install
SOURCE_URL='https://raw.githubusercontent.com/Macrohard0001/lx-ikun-music-sources/main/V260810/%E6%8E%A8%E8%8D%90/%E5%A2%A8%E6%BE%9C%E8%81%9A%E5%90%88%E9%9F%B3%E6%BA%90%20v2.0.0.js' \
BRIDGE_TOKEN='replace-with-a-long-random-secret' \
ALLOWED_SOURCE_HOSTS='raw.githubusercontent.com' \
npm start
```

Configure the server to use the loopback Bridge:

```toml
[resolver]
playbackApiUrl = "http://127.0.0.1:9863/v1/music-url"
playbackApiToken = "replace-with-a-long-random-secret"
allowedAudioHosts = ["the-actual-audio-cdn.example"]
httpTimeoutSeconds = 15
requireHttps = true
```

`playbackApiToken` and `BRIDGE_TOKEN` must match. `allowedAudioHosts` contains the CDN domains returned by the
script, not `127.0.0.1`. The referenced 墨澜 v2.0.0 script declares only `musicUrl`, so the Bridge returns empty
lyrics for it without failing playback. A source that declares `lyric` is forwarded directly; declared `pic` is
also supported for integrations that request it.

The Bridge executes trusted third-party JavaScript and is a security boundary, not a complete sandbox. Keep it on
loopback, run it as a dedicated low-privilege user or constrained container, set `BRIDGE_TOKEN`, and never treat an
untrusted JS URL as safe code. Source scripts can make outbound requests by design. Its README documents host
allowlists, size/time limits, caching, redirects, reloads, and version/hash logging.

A declarative descriptor may be validated by integrations without containing executable code:

```json
{"format":"musicbox-safe-source-v1","name":"Local bridge","resolverUrl":"http://127.0.0.1:9863/v1/music-url"}
```

## Development

```bash
./gradlew runClient
./gradlew runServer
./gradlew build
```

Both clients and the dedicated server need the mod. Only the server administrator configures the resolver;
credentials are neither synchronized nor sent to players.

Run the live catalog integration probe with:

```bash
./gradlew :ui:catalogSmokeTest
```

## Security note

LX custom sources are executable JavaScript. This mod does not execute arbitrary source scripts because importing one would grant it code execution inside the Minecraft client. The HTTP resolver contract retains the useful request/response model without that trust boundary.
