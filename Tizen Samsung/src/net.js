// Netzwerk ueber XMLHttpRequest (in Tizen-Web-Apps mit <access origin="*"> ohne CORS-Beschraenkung).

export function getText(url, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open('GET', url, true);
    x.timeout = timeoutMs;
    x.onload = () => {
      if (x.status >= 200 && x.status < 300) resolve(x.responseText);
      else reject(new Error(`Server antwortet mit HTTP ${x.status}`));
    };
    x.onerror = () => reject(new Error('Keine Verbindung zum Server'));
    x.ontimeout = () => reject(new Error('Zeitüberschreitung – Server antwortet nicht'));
    x.send();
  });
}

export async function getJson(url, timeoutMs) {
  const t = await getText(url, timeoutMs);
  if (!t || !t.trim()) return [];
  try { return JSON.parse(t); } catch (e) { throw new Error('Ungültige Server-Antwort'); }
}

export function query(params) {
  return Object.keys(params).filter((k) => params[k] != null)
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`).join('&');
}

/** POST mit JSON (fuer KI-Empfehlungen). Liefert { status, body }. */
export function postJson(url, body, headers = {}, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open('POST', url, true);
    x.timeout = timeoutMs;
    x.setRequestHeader('Content-Type', 'application/json');
    Object.keys(headers).forEach((k) => x.setRequestHeader(k, headers[k]));
    x.onload = () => resolve({ status: x.status, body: x.responseText });
    x.onerror = () => reject(new Error('Keine Verbindung zum Server'));
    x.ontimeout = () => reject(new Error('Zeitüberschreitung'));
    x.send(JSON.stringify(body));
  });
}
