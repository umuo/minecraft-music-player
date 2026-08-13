# Music Box

NeoForge 1.21.1 / Java 21 mod that adds a placeable music box. A server owns the playback state while every nearby client resolves and plays the same HTTP audio stream.

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

Queue management, cover rendering, lyrics, OpenAL positional audio, and LX JavaScript source execution are separate follow-up milestones.

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

Catalog metadata is read from the platforms' public web endpoints. Playback remains separate: every client calls the configured resolver API only after the server accepts and broadcasts a track request.

## Playback resolver API

Configure the client in `config/musicbox-client.toml`:

```toml
[resolver]
playbackApiUrl = "https://music-api.example.com/v1/music-url"
playbackApiToken = ""
defaultQuality = "320k"
allowedAudioHosts = ["cdn.example.com"]
httpTimeoutSeconds = 15
```

The mod sends a normalized version of LX Music's `musicUrl` request:

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

Accepted responses:

```json
{"url":"https://cdn.example.com/audio/123456.mp3"}
```

or:

```json
{"data":{"url":"https://cdn.example.com/audio/123456.mp3"}}
```

The current decoder supports MP3 streams. The resolver must return an HTTP(S) URL rather than audio bytes. Redirects are rejected, HTTPS is required outside localhost, and resolved hosts must be explicitly allowed.

## Development

```bash
./gradlew runClient
./gradlew runServer
./gradlew build
```

Both clients and the dedicated server need the mod. Each player configures their own resolver API; credentials are never sent through the Minecraft server.

Run the live catalog integration probe with:

```bash
./gradlew :ui:catalogSmokeTest
```

## Security note

LX custom sources are executable JavaScript. This mod does not execute arbitrary source scripts because importing one would grant it code execution inside the Minecraft client. The HTTP resolver contract retains the useful request/response model without that trust boundary.
