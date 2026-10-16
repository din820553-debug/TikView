(function () {
  'use strict';
  if (window.__tikviewCommentClicks) return;
  window.__tikviewCommentClicks = true;
  document.addEventListener('click', function (event) {
    const node = event.target instanceof Element ? event.target : null;
    if (!node || !node.closest('[data-e2e="comment-icon"], [data-e2e="comment-count"], [data-e2e="browse-comment"]')) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    location.href = 'tikview://comments';
  }, true);
})()
