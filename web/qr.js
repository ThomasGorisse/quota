/**
 * QuotaQR — a small, dependency-free QR code generator (byte mode, ISO/IEC 18004).
 * Original code, MIT licensed, © 2026 Thomas Gorisse.
 *
 * It exists so the pairing URL — whose fragment carries the secret token — is rendered
 * entirely on the device. No external QR service ever sees the secret.
 */
(() => {
  'use strict';

  // Error-correction levels. The table index is our own ordering; the format-info bits are
  // the spec's, which is a different permutation — hence two constants rather than one.
  const LEVELS = ['L', 'M', 'Q', 'H'];
  const FORMAT_BITS = [1, 0, 3, 2];

  // Per level, per version 1..40 (index 0 padding): EC codewords per block, then block count.
  // These two are the only figures in the standard that no formula derives; everything else
  // below (capacity, alignment positions, generator polynomials) is computed.
  const EC_PER_BLOCK = [
    [0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
    [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
    [0, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
    [0, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30]
  ];
  const NUM_BLOCKS = [
    [0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
    [0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
    [0, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
    [0, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81]
  ];

  // GF(256) log tables, primitive polynomial x^8+x^4+x^3+x^2+1. Doubled exp table so a
  // product's exponent never needs a modulo.
  const EXP = new Uint8Array(512);
  const LOG = new Uint8Array(256);
  for (let i = 0, x = 1; i < 255; i++) { EXP[i] = x; LOG[x] = i; x = (x << 1) ^ (x & 0x80 ? 0x11d : 0); }
  for (let i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
  const mul = (a, b) => (a === 0 || b === 0 ? 0 : EXP[LOG[a] + LOG[b]]);

  /** Generator polynomial (x-α^0)…(x-α^(n-1)), high degree first. */
  function generator(degree) {
    let poly = [1];
    for (let i = 0; i < degree; i++) {
      const next = new Array(poly.length + 1).fill(0);
      for (let j = 0; j < poly.length; j++) { next[j] ^= poly[j]; next[j + 1] ^= mul(poly[j], EXP[i]); }
      poly = next;
    }
    return poly;
  }

  function remainder(data, degree) {
    const gen = generator(degree);
    const res = new Uint8Array(degree);
    for (const byte of data) {
      const factor = byte ^ res[0];
      res.copyWithin(0, 1); // shift the running remainder left by one codeword
      res[degree - 1] = 0;
      for (let i = 0; i < degree; i++) res[i] ^= mul(gen[i + 1], factor);
    }
    return Array.from(res);
  }

  /** Total codewords a version holds, from its module count minus the function patterns. */
  function totalCodewords(ver) {
    let bits = (16 * ver + 128) * ver + 64;
    if (ver >= 2) {
      const n = Math.floor(ver / 7) + 2;
      bits -= (25 * n - 10) * n - 55;
      if (ver >= 7) bits -= 36;
    }
    return Math.floor(bits / 8);
  }

  const dataCodewords = (ver, ecl) => totalCodewords(ver) - EC_PER_BLOCK[ecl][ver] * NUM_BLOCKS[ecl][ver];

  function alignPositions(ver, size) {
    if (ver === 1) return [];
    const count = Math.floor(ver / 7) + 2;
    const step = ver === 32 ? 26 : Math.ceil((ver * 4 + 4) / (count * 2 - 2)) * 2; // v32 is the one exception
    const pos = [6];
    for (let p = size - 7; pos.length < count; p -= step) pos.splice(1, 0, p);
    return pos;
  }

  function interleave(data, ver, ecl) {
    const blocks = NUM_BLOCKS[ecl][ver], ecLen = EC_PER_BLOCK[ecl][ver], raw = totalCodewords(ver);
    const shortLen = Math.floor(raw / blocks) - ecLen;
    const numShort = blocks - (raw % blocks); // the rest carry one extra data codeword
    const dataParts = [], ecParts = [];
    for (let i = 0, k = 0; i < blocks; i++) {
      const chunk = data.slice(k, k + shortLen + (i < numShort ? 0 : 1));
      k += chunk.length;
      dataParts.push(chunk);
      ecParts.push(remainder(chunk, ecLen));
    }
    // Column-major over the blocks: data first, then EC, short blocks simply having no
    // codeword in the last data column.
    const out = [];
    for (let i = 0; i <= shortLen; i++)
      for (let j = 0; j < blocks; j++) if (i < dataParts[j].length) out.push(dataParts[j][i]);
    for (let i = 0; i < ecLen; i++) for (let j = 0; j < blocks; j++) out.push(ecParts[j][i]);
    return out;
  }

  const bit = (value, i) => ((value >>> i) & 1) !== 0;

  function draw(ver, ecl, codewords) {
    const size = ver * 4 + 17;
    const grid = () => Array.from({ length: size }, () => new Array(size).fill(false));
    const m = grid(), fn = grid(); // fn marks what the mask and the data must not touch
    const set = (x, y, v) => { m[y][x] = v; fn[y][x] = true; };
    // Finder plus separator in one pass: the ring at Chebyshev distance 2 and the
    // separator at distance 4 are the light ones.
    for (const [cx, cy] of [[3, 3], [size - 4, 3], [3, size - 4]]) {
      for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
        const x = cx + dx, y = cy + dy;
        if (x < 0 || x >= size || y < 0 || y >= size) continue;
        const d = Math.max(Math.abs(dx), Math.abs(dy));
        set(x, y, d !== 2 && d !== 4);
      }
    }
    for (let i = 0; i < size; i++) { // timing patterns, in the gaps the finders left
      if (!fn[6][i]) set(i, 6, i % 2 === 0);
      if (!fn[i][6]) set(6, i, i % 2 === 0);
    }
    const pos = alignPositions(ver, size);
    const last = pos.length - 1;
    for (let i = 0; i <= last; i++) for (let j = 0; j <= last; j++) {
      // The three finder corners have no alignment pattern.
      if ((i === 0 && (j === 0 || j === last)) || (i === last && j === 0)) continue;
      for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++)
        set(pos[j] + dx, pos[i] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
    }
    if (ver >= 7) { // version info, twice, with its own 12-bit BCH code
      let rem = ver;
      for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
      const bits = (ver << 12) | rem;
      for (let i = 0; i < 18; i++) {
        const a = size - 11 + (i % 3), b = Math.floor(i / 3);
        set(a, b, bit(bits, i));
        set(b, a, bit(bits, i));
      }
    }
    const drawFormat = (mask) => {
      const data = (FORMAT_BITS[ecl] << 3) | mask;
      let rem = data; // 10-bit BCH remainder, then the spec's fixed mask
      for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
      const bits = (((data << 10) | rem) ^ 0x5412) >>> 0;
      for (let i = 0; i <= 5; i++) set(8, i, bit(bits, i));
      set(8, 7, bit(bits, 6));
      set(8, 8, bit(bits, 7));
      set(7, 8, bit(bits, 8));
      for (let i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
      for (let i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
      for (let i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
      set(8, size - 8, true); // the always-dark module
    };
    drawFormat(0); // reserve the format area before the data snakes around it
    let k = 0;
    for (let right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5; // column 6 is timing, the zigzag steps over it
      for (let v = 0; v < size; v++) for (let j = 0; j < 2; j++) {
        const x = right - j;
        const y = ((right + 1) & 2) === 0 ? size - 1 - v : v; // every other column pair runs upward
        if (!fn[y][x] && k < codewords.length * 8) m[y][x] = bit(codewords[k >>> 3], 7 - (k++ & 7));
      }
    }
    const masks = [
      (x, y) => (x + y) % 2 === 0, (x, y) => y % 2 === 0, (x) => x % 3 === 0, (x, y) => (x + y) % 3 === 0,
      (x, y) => (Math.floor(y / 2) + Math.floor(x / 3)) % 2 === 0, (x, y) => ((x * y) % 2) + ((x * y) % 3) === 0,
      (x, y) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0, (x, y) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0
    ];
    let best = 0, bestScore = Infinity;
    for (let mask = 0; mask < 8; mask++) {
      applyMask(m, fn, size, masks[mask]);
      drawFormat(mask);
      const score = penalty(m, size);
      if (score < bestScore) { bestScore = score; best = mask; }
      applyMask(m, fn, size, masks[mask]); // XOR is its own inverse, so this undoes the trial
    }
    applyMask(m, fn, size, masks[best]);
    drawFormat(best);
    return { size, modules: m };
  }

  function applyMask(m, fn, size, mask) {
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++) if (!fn[y][x] && mask(x, y)) m[y][x] = !m[y][x];
  }

  /** Standard N1..N4 penalty. Lines are scanned as strings: runs and the 1:1:3:1:1
   *  finder lookalike are both plain pattern matches that way. */
  function penalty(m, size) {
    let score = 0, dark = 0;
    const lines = [];
    for (let i = 0; i < size; i++) {
      let row = '', col = '';
      for (let j = 0; j < size; j++) {
        row += m[i][j] ? '1' : '0';
        col += m[j][i] ? '1' : '0';
        if (m[i][j]) dark++;
      }
      lines.push(row, col);
    }
    for (const line of lines) {
      for (const run of line.match(/([01])\1*/g)) if (run.length >= 5) score += run.length - 2;
      const padded = `0000${line}0000`; // the quiet zone counts as light
      for (const p of ['10111010000', '00001011101'])
        for (let i = padded.indexOf(p); i >= 0; i = padded.indexOf(p, i + 1)) score += 40;
    }
    for (let y = 0; y < size - 1; y++) for (let x = 0; x < size - 1; x++) {
      const c = m[y][x];
      if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) score += 3;
    }
    const total = size * size;
    return score + 10 * Math.floor(Math.abs(dark * 20 - total * 10) / total);
  }

  function matrix(text, opts = {}) {
    const ecl = Math.max(0, LEVELS.indexOf(opts.ecc || 'M'));
    const bytes = new TextEncoder().encode(String(text));
    for (let ver = 1; ver <= 40; ver++) {
      const capacity = dataCodewords(ver, ecl) * 8;
      const countBits = ver < 10 ? 8 : 16;
      if (4 + countBits + bytes.length * 8 > capacity) continue;
      const bits = [];
      const push = (v, len) => { for (let i = len - 1; i >= 0; i--) bits.push((v >>> i) & 1); };
      push(4, 4); // byte mode
      push(bytes.length, countBits);
      for (const b of bytes) push(b, 8);
      push(0, Math.min(4, capacity - bits.length));
      push(0, (8 - (bits.length % 8)) % 8);
      const data = [];
      for (let i = 0; i < bits.length; i += 8) data.push(parseInt(bits.slice(i, i + 8).join(''), 2));
      for (let pad = 0xec; data.length * 8 < capacity; pad ^= 0xec ^ 0x11) data.push(pad); // spec pad bytes
      return draw(ver, ecl, interleave(data, ver, ecl));
    }
    throw new Error('QuotaQR: text too long for a version 40 QR code');
  }

  function svg(text, opts = {}) {
    const { dark = '#000', light = '#fff', quietZone = 4 } = opts;
    const { size, modules } = matrix(text, opts);
    const dim = size + quietZone * 2;
    let d = '';
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      if (!modules[y][x]) continue;
      let run = 1; // merge horizontal runs so the path stays small
      while (x + run < size && modules[y][x + run]) run++;
      d += `M${x + quietZone} ${y + quietZone}h${run}v1h-${run}z`;
      x += run - 1;
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${dim} ${dim}" shape-rendering="crispEdges">`
      + `<rect width="${dim}" height="${dim}" fill="${light}"/><path d="${d}" fill="${dark}"/></svg>`;
  }

  window.QuotaQR = { matrix, svg };
})();
