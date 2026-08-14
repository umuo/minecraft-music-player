export const hostAllowed = (host, allowlist) => !allowlist.length || allowlist.includes('*') || allowlist.some(item => host === item || host.endsWith(`.${item}`))

export async function fetchLimited(url, options = {}, policy) {
  let current = new URL(url)
  const allowedProtocols = options.protocols || ['http:', 'https:']
  for (let redirects = 0; ; redirects++) {
    if (!allowedProtocols.includes(current.protocol) || current.username || current.password || current.hash)
      throw new Error('URL has a forbidden protocol, credentials, or fragment')
    if (options.allowedHosts && !hostAllowed(current.hostname.toLowerCase(), options.allowedHosts))
      throw new Error(`Host is not allowed: ${current.hostname}`)
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(new Error('request timed out')), options.timeoutMs ?? policy.requestTimeoutMs)
    const signal = options.fetch?.signal ? AbortSignal.any([controller.signal, options.fetch.signal]) : controller.signal
    let response
    try {
      response = await fetch(current, { ...options.fetch, redirect: 'manual', signal })
      if (response.status >= 300 && response.status < 400) {
        if (redirects >= policy.redirectLimit) throw new Error('redirect limit exceeded')
        const location = response.headers.get('location')
        if (!location) throw new Error('redirect is missing Location')
        await response.body?.cancel()
        current = new URL(location, current)
        continue
      }
      const bytes = await readLimited(response.body, options.maxBytes ?? policy.maxUpstreamBytes)
      return { response, bytes, finalUrl: current.href }
    } catch (error) {
      throw new Error(controller.signal.aborted ? 'request timed out' : error.message)
    } finally {
      clearTimeout(timeout)
    }
  }
}

export async function readLimited(stream, maxBytes) {
  if (!stream) return Buffer.alloc(0)
  const chunks = []
  let length = 0
  for await (const chunk of stream) {
    length += chunk.length
    if (length > maxBytes) throw new Error(`response exceeds ${maxBytes} bytes`)
    chunks.push(chunk)
  }
  return Buffer.concat(chunks)
}

export function encodeBody(options, headers) {
  if (options.body != null) {
    if (typeof options.body === 'string' || Buffer.isBuffer(options.body) || ArrayBuffer.isView(options.body)) return options.body
    headers.set('content-type', headers.get('content-type') || 'application/json')
    return JSON.stringify(options.body)
  }
  if (options.form != null) {
    headers.set('content-type', headers.get('content-type') || 'application/x-www-form-urlencoded')
    return new URLSearchParams(options.form).toString()
  }
  if (options.formData != null) {
    const form = new FormData()
    for (const [key, value] of Object.entries(options.formData)) form.append(key, value)
    return form
  }
}
