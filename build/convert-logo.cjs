const fs = require('fs');
const path = require('path');
const { chromium } = require('../learning/nh-trainer/browser-source/node_modules/playwright-core');
(async () => {
 const browser = await chromium.launch({ channel: 'msedge', headless: true });
 try {
  const page = await browser.newPage();
  const source = fs.readFileSync(process.argv[2]).toString('base64');
  const png = await page.evaluate(async (source) => {
   const img = new Image(); img.src = 'data:image/webp;base64,' + source; await img.decode();
   const canvas = document.createElement('canvas'); canvas.width = img.naturalWidth; canvas.height = img.naturalHeight;
   canvas.getContext('2d').drawImage(img, 0, 0);
   return canvas.toDataURL('image/png').split(',')[1];
  }, source);
  fs.mkdirSync(path.dirname(process.argv[3]), { recursive: true });
  fs.writeFileSync(process.argv[3], Buffer.from(png, 'base64'));
 } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
