// 布局自检：把溢出信息写进 DOM，供 --dump-dom 读取（无需看图）
(function(){
  const W = 393, H = 873;
  const issues = [];
  const all = document.querySelectorAll('.screen *');
  for (const el of all) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    if (r.right > W + 0.5) {
      issues.push(`H溢出 ${el.className||el.tagName} right=${r.right.toFixed(1)} 「${(el.textContent||'').trim().slice(0,14)}」`);
    }
    // 文本被省略号截断（可接受，但记录）
    if (el.scrollWidth > el.clientWidth + 1 && el.clientHeight > 0) {
      const cs = getComputedStyle(el);
      if (cs.overflow === 'hidden') issues.push(`截断 ${el.className} 「${(el.textContent||'').trim().slice(0,14)}」`);
    }
  }
  const docH = document.documentElement.scrollHeight;
  const out = document.createElement('pre');
  out.id = 'METRICS';
  out.textContent = JSON.stringify({
    docW: document.documentElement.scrollWidth,
    docH: docH,
    overflows: issues
  }, null, 1);
  document.body.appendChild(out);
})();
