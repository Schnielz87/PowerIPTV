// Raeumliche Navigation fuer die Fernbedienung: Pfeiltasten springen zum naechstgelegenen
// fokussierbaren Element (.focusable) in der aktuellen Ebene (Seite oder Dialog).

let current = null;
const scopes = []; // Stapel: [Seite, Dialog, Dialog ...]

export function pushScope(root, opts = {}) {
  scopes.push({ root, opts, last: null });
}

export function popScope() {
  const s = scopes.pop();
  const top = scopes[scopes.length - 1];
  if (top && top.last && document.body.contains(top.last)) setFocus(top.last, { noScroll: true });
  else focusFirst();
  return s;
}

export function replaceScope(root, opts = {}) {
  scopes.length = 0;
  pushScope(root, opts);
}

export function scope() {
  return scopes[scopes.length - 1];
}

export function getFocus() {
  return current && document.body.contains(current) ? current : null;
}

function visible(el) {
  if (!el.offsetParent && getComputedStyle(el).position !== 'fixed') return false;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0;
}

function candidates() {
  const s = scope();
  if (!s) return [];
  return Array.prototype.filter.call(s.root.querySelectorAll('.focusable'), (el) => !el.disabled && visible(el));
}

export function setFocus(el, opts = {}) {
  if (!el) return;
  if (current && current !== el) {
    current.classList.remove('focused');
    if (current._onBlur) current._onBlur();
  }
  current = el;
  el.classList.add('focused');
  const s = scope();
  if (s) s.last = el;
  if (!opts.noScroll) scrollIntoView(el);
  if (el._onFocus) el._onFocus();
}

export function focusFirst(selector) {
  const s = scope();
  if (!s) return;
  const el = (selector && s.root.querySelector(selector)) || s.root.querySelector('.focusable.autofocus') || candidates()[0];
  if (el) setFocus(el);
}

/** Sichtbarkeit sicherstellen: naechsten scrollbaren Container (.scroll-y / .scroll-x) verschieben. */
export function scrollIntoView(el) {
  let p = el.parentElement;
  while (p && p !== document.body) {
    const y = p.classList.contains('scroll-y');
    const x = p.classList.contains('scroll-x');
    if (x || y) {
      const pr = p.getBoundingClientRect();
      const r = el.getBoundingClientRect();
      const m = 40;
      if (y) {
        if (r.top < pr.top + m) p.scrollTop -= pr.top + m - r.top;
        else if (r.bottom > pr.bottom - m) p.scrollTop += r.bottom - (pr.bottom - m);
      }
      if (x) {
        if (r.left < pr.left + m) p.scrollLeft -= pr.left + m - r.left;
        else if (r.right > pr.right - m) p.scrollLeft += r.right - (pr.right - m);
      }
    }
    p = p.parentElement;
  }
}

/** Naechstes Element in Richtung dir ('left' | 'right' | 'up' | 'down'). */
export function move(dir) {
  const cur = getFocus();
  if (!cur) { focusFirst(); return true; }
  // Feste Nachbarn (z.B. Seitenleiste -> Inhalt)
  const fixed = cur['nav' + dir[0].toUpperCase() + dir.slice(1)];
  if (fixed) {
    const t = typeof fixed === 'function' ? fixed() : fixed;
    if (t === 'stop') return true;
    if (t && t.classList) { setFocus(t); return true; }
  }
  const c = cur.getBoundingClientRect();
  const cx = c.left + c.width / 2, cy = c.top + c.height / 2;
  let best = null, bestScore = Infinity;
  const sameGroup = cur.closest('.nav-group');
  for (const el of candidates()) {
    if (el === cur) continue;
    const r = el.getBoundingClientRect();
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    let primary, secondary, overlap;
    if (dir === 'left' || dir === 'right') {
      primary = dir === 'left' ? c.left - r.right : r.left - c.right;
      if ((dir === 'left' ? x >= cx : x <= cx)) continue;
      overlap = Math.min(c.bottom, r.bottom) - Math.max(c.top, r.top);
      secondary = Math.abs(y - cy);
    } else {
      primary = dir === 'up' ? c.top - r.bottom : r.top - c.bottom;
      if ((dir === 'up' ? y >= cy : y <= cy)) continue;
      overlap = Math.min(c.right, r.right) - Math.max(c.left, r.left);
      secondary = Math.abs(x - cx);
    }
    primary = Math.max(0, primary);
    let score = primary + secondary * (overlap > 0 ? 0.3 : 2.5);
    if (sameGroup && el.closest('.nav-group') === sameGroup) score *= 0.6;
    if (score < bestScore) { bestScore = score; best = el; }
  }
  if (best) { setFocus(best); return true; }
  const s = scope();
  if (s && s.opts.onEdge) return s.opts.onEdge(dir, cur) !== false;
  return false;
}

/** Element fokussierbar machen (OK = onEnter, sonst click). */
export function focusable(el, onEnter, extra = {}) {
  el.classList.add('focusable');
  if (onEnter) el._onEnter = onEnter;
  Object.assign(el, extra);
  el.addEventListener('click', () => { setFocus(el, { noScroll: true }); if (el._onEnter) el._onEnter(); });
  el.addEventListener('mouseenter', () => setFocus(el, { noScroll: true }));
  return el;
}

export function activate() {
  const el = getFocus();
  if (!el) return false;
  if (el.tagName === 'INPUT') { el.focus(); return true; }
  if (el._onEnter) { el._onEnter(); return true; }
  return false;
}
