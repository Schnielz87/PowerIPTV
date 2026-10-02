// Rendert die drei Collagen als PNG (1200x600). Danach generate_webp.py ausfuehren.
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 600 } });
  for (const t of ['live', 'movies', 'series']) {
    await p.goto('file://' + __dirname + '/collage.html?type=' + t);
    await p.waitForTimeout(300);
    await p.screenshot({ path: __dirname + '/out_' + t + '.png' });
  }
  await b.close();
})();
