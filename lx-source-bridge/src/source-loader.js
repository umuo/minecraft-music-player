import crypto from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fetchLimited } from './http.js'

const metadata = raw => Object.fromEntries([...raw.matchAll(/^\s*\*\s*@([\w-]+)\s+(.+)$/gm)].map(match => [match[1], match[2].trim()]))

export class SourceLoader {
  constructor(config, log = console) { this.config = config; this.log = log }
  async load({ allowCache = true } = {}) {
    await fs.mkdir(this.config.cacheDir, { recursive: true, mode: 0o700 })
    try {
      const result = await fetchLimited(this.config.sourceUrl, {
        allowedHosts: this.config.sourceHosts,
        maxBytes: this.config.maxScriptBytes,
      }, this.config)
      if (!result.response.ok) throw new Error(`source download returned HTTP ${result.response.status}`)
      const rawScript = result.bytes.toString('utf8')
      if (rawScript.includes('\u0000')) throw new Error('source is not valid text')
      const hash = crypto.createHash('sha256').update(result.bytes).digest('hex')
      const info = { ...metadata(rawScript), hash, sourceUrl: result.finalUrl, loadedAt: new Date().toISOString(), rawScript }
      await Promise.all([
        fs.writeFile(path.join(this.config.cacheDir, 'source.js'), rawScript, { mode: 0o600 }),
        fs.writeFile(path.join(this.config.cacheDir, 'source.json'), JSON.stringify({ ...info, rawScript: undefined }, null, 2), { mode: 0o600 }),
      ])
      return info
    } catch (error) {
      if (!allowCache) throw error
      try {
        const [rawScript, saved] = await Promise.all([
          fs.readFile(path.join(this.config.cacheDir, 'source.js'), 'utf8'),
          fs.readFile(path.join(this.config.cacheDir, 'source.json'), 'utf8'),
        ])
        const info = { ...JSON.parse(saved), rawScript, cached: true }
        this.log.warn(`Source download failed; using cached ${info.hash}: ${error.message}`)
        return info
      } catch { throw error }
    }
  }
}
