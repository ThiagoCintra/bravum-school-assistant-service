'use strict';
const BASE = process.env.SCHOOL_API_BASE_URL || '';
const KEY = process.env.SCHOOL_ALEXA_SKILL_KEY || '';
class SchoolApiError extends Error {
  constructor(status) { super(status === 403 ? 'UNLINKED' : 'BACKEND_UNAVAILABLE'); this.status = status; }
}
/** Somente caminhos allowlisted no backend e TLS. Nunca transmite a chave administrativa ou o tenant. */
async function api(userId, path, body) {
  if (!/^https:\/\//.test(BASE) || KEY.length < 32) throw new Error('CONFIGURATION_REQUIRED');
  if (!/^amzn1\.ask\.account\.[A-Za-z0-9._-]{5,512}$/.test(userId || ''))
    throw new Error('INVALID_ALEXA_USER');
  if (!/^alexa\/skill\/(?:summary|messages|agenda(?:\?day=(?:hoje|amanh%C3%A3))?|link|subscription)$/.test(path))
    throw new Error('INVALID_ROUTE');
  const base = new URL(BASE.replace(/\/+$/, '') + '/');
  const url = new URL(path, base);
  if (url.origin !== base.origin || !url.pathname.startsWith(base.pathname + 'alexa/skill/'))
    throw new Error('INVALID_ROUTE');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 4500);
  try {
    const response = await fetch(url, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {
        'Authorization': 'Bearer ' + KEY,
        'X-Alexa-User-Id': userId,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal
    });
    if (!response.ok) throw new SchoolApiError(response.status);
    return response.status === 204 ? {} : await response.json();
  } finally { clearTimeout(timeout); }
}
module.exports = { api, SchoolApiError };
