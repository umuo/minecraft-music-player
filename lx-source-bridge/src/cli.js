#!/usr/bin/env node
import { loadConfig } from './config.js'
import { Bridge } from './server.js'
import { SourceLoader } from './source-loader.js'

try {
  const config = loadConfig()
  const bridge = await new Bridge(config, new SourceLoader(config)).start()
  console.log(`LX Source Bridge listening on http://${config.host}:${bridge.address().port}`)
  if (!config.token) console.warn('BRIDGE_TOKEN is empty; set it even when listening on loopback')
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await bridge.close(); process.exit(0) })
} catch (error) {
  console.error(error.stack || error.message)
  process.exit(1)
}
