// Renders video.html frame by frame in headless Chrome and encodes MP4s with ffmpeg.
//   node render.mjs [land|port|both] [fps] [--still t1,t2,...]
import puppeteer from 'puppeteer-core';
import ffmpeg from 'ffmpeg-static';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const stillIdx = args.indexOf('--still');
const stills = stillIdx >= 0 ? args[stillIdx + 1].split(',').map(Number) : null;
const which = args[0] && !args[0].startsWith('--') ? args[0] : 'both';
const fps = Number(args[1]) || 60;
const formats = which === 'both' ? ['land', 'port'] : [which];
const OUT = path.join(HERE, 'out');
fs.mkdirSync(OUT, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  args: ['--allow-file-access-from-files', '--hide-scrollbars', '--force-color-profile=srgb'],
});

for (const f of formats) {
  const page = await browser.newPage();
  const [W, H] = f === 'land' ? [1920, 1080] : [1080, 1920];
  await page.setViewport({ width: W, height: H, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(HERE, 'video.html') + '?f=' + f);
  await page.evaluate(() => window.ready);
  const duration = await page.evaluate(() => window.DURATION);
  const clip = { x: 0, y: 0, width: W, height: H };

  if (stills) {
    for (const t of stills) {
      await page.evaluate((t) => window.render(t), t);
      await page.screenshot({ path: path.join(OUT, `still-${f}-${t.toFixed(2)}.png`), clip });
    }
    console.log(`${f}: ${stills.length} stills`);
    continue;
  }

  const file = path.join(OUT, `keeply-promo-${f === 'land' ? '16x9' : '9x16'}.mp4`);
  const ff = spawn(ffmpeg, ['-y', '-f', 'image2pipe', '-framerate', String(fps), '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '15', '-pix_fmt', 'yuv420p', '-profile:v', 'high',
    '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709', '-movflags', '+faststart', file],
    { stdio: ['pipe', 'ignore', 'inherit'] });
  const frames = Math.round(duration * fps);
  const start = Date.now();
  for (let i = 0; i < frames; i++) {
    await page.evaluate((t) => window.render(t), i / fps);
    const buf = await page.screenshot({ type: 'png', clip, optimizeForSpeed: true });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if (i % fps === 0) process.stdout.write(`\r${f}: ${i}/${frames} frames, ${((Date.now() - start) / 1000).toFixed(0)}s`);
  }
  ff.stdin.end();
  await new Promise(r => ff.on('close', r));
  console.log(`\n${f}: wrote ${file}`);
  await page.close();
}
await browser.close();
