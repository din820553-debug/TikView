(function () {
  'use strict';
  // This stylesheet only activates after TikTok has rendered a real comments list.
  // Login, captcha, network errors and changed markup remain visible as normal pages.
  if (window.__tikviewCommentsCleanup) window.__tikviewCommentsCleanup();
  let timer;
  let observer;
  function cleanup() { clearTimeout(timer); if (observer) observer.disconnect(); }
  window.__tikviewCommentsCleanup = cleanup;
  function adapt() {
    let panel = document.querySelector('[data-e2e="comment-list"], [data-e2e="comment-list-container"], [class*="DivCommentListContainer"]');
    if (!panel) return false;
    // Include an adjacent comment composer when it shares a small sidebar container.
    for (let parent = panel.parentElement, depth = 0; parent && parent !== document.body && depth < 3; parent = parent.parentElement, depth++) {
      if (parent.querySelector('video')) break;
      if (parent.querySelector('[contenteditable="true"], textarea, [data-e2e="comment-input"]')) {
        panel = parent;
        break;
      }
    }
    let style = document.getElementById('tikview-comment-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'tikview-comment-style';
      style.textContent = `
        [data-tv-hidden="1"] { display:none !important; }
        [data-tv-path="1"] { width:100% !important; min-width:0 !important; max-width:none !important;
          margin:0 !important; padding:0 !important; height:auto !important; min-height:0 !important;
          position:static !important; transform:none !important; overflow:visible !important; }
        [data-tv-panel="1"] { display:block !important; width:100% !important; min-width:0 !important;
          max-width:none !important; height:auto !important; max-height:none !important;
          overflow:visible !important; padding:12px !important; box-sizing:border-box !important; }
        html, body { background:#fff !important; color:#161823 !important; }
      `;
      document.head.appendChild(style);
    }
    // Recompute the path if the site replaces its comments during navigation.
    document.querySelectorAll('[data-tv-hidden]').forEach(n => n.removeAttribute('data-tv-hidden'));
    document.querySelectorAll('[data-tv-path]').forEach(n => n.removeAttribute('data-tv-path'));
    document.querySelectorAll('[data-tv-panel]').forEach(n => n.removeAttribute('data-tv-panel'));
    panel.setAttribute('data-tv-panel', '1');
    let child = panel;
    while (child.parentElement && child !== document.body) {
      const parent = child.parentElement;
      parent.setAttribute('data-tv-path', '1');
      Array.from(parent.children).forEach(sibling => {
        if (sibling !== child && !['STYLE', 'SCRIPT', 'LINK'].includes(sibling.tagName)) sibling.setAttribute('data-tv-hidden', '1');
      });
      child = parent;
    }
    document.documentElement.style.setProperty('overflow', 'auto', 'important');
    document.body.style.setProperty('overflow', 'auto', 'important');
    cleanup();
    return true;
  }
  if (!adapt()) {
    observer = new MutationObserver(() => {
      // Disconnect while applying attributes to avoid observing our own layout changes.
      observer.disconnect();
      if (!adapt()) observer.observe(document.documentElement, {childList:true, subtree:true});
    });
    observer.observe(document.documentElement, {childList:true, subtree:true});
    timer = setTimeout(cleanup, 20000);
  }
})()
