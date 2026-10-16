(function () {
  'use strict';
  function videoLink(value) {
    try {
      const u = new URL(value, location.href);
      return u.protocol === 'https:' && /(^|\.)tiktok\.com$/i.test(u.hostname)
        && !u.username && !u.password && (!u.port || u.port === '443')
        && /^\/@[^/]+\/video\/\d+\/?$/.test(u.pathname)
        ? u.origin + u.pathname : null;
    } catch (_) { return null; }
  }
  // Detail pages already identify the video, including when the player is offscreen.
  const direct = videoLink(location.href);
  if (direct) return direct;
  // A feed contains preloaded videos. Only choose the player visible in the viewport.
  const ranked = Array.from(document.querySelectorAll('video')).map(video => {
    const r = video.getBoundingClientRect();
    const area = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0))
      * Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
    return {video, area};
  }).filter(item => item.area > 0).sort((a, b) => b.area - a.area);
  if (!ranked.length) return null;
  let node = ranked[0].video;
  for (let depth = 0; node && depth < 9 && node !== document.body; depth++, node = node.parentElement) {
    const links = Array.from(node.querySelectorAll('a[href]')).map(a => videoLink(a.href)).filter(Boolean);
    const unique = Array.from(new Set(links));
    if (unique.length === 1) return unique[0];
    if (unique.length > 1) break; // Never guess a video from a list of recommendations.
  }
  return null;
})()
