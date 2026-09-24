// Эталон совы для переноса в Compose (Owl.kt, коммит 17 плана).
// Координаты — в поле 240 × 262; в Compose их масштабируют под размер Canvas.
// Цвета окрасов уйдут в pets.json (коммит 15), геометрия стадий останется в коде.
(function () {
  const PAL = {
    cream:  { name: 'Кремовый',   body: '#F0D4AE', wing: '#D9B488', face: '#FFF5E6', ring: '#C8996A' },
    brown:  { name: 'Коричневый', body: '#A8703F', wing: '#7C4D27', face: '#EFD0A6', ring: '#6A3F1E' },
    smoky:  { name: 'Дымчатый',   body: '#7D7885', wing: '#5A5562', face: '#DCD7E0', ring: '#4A4652' },
    white:  { name: 'Белый',      body: '#F4F5F2', wing: '#D5DBE2', face: '#FFFFFF', ring: '#A9B2BC' },
    ginger: { name: 'Рыжий',      body: '#EC9447', wing: '#C46B2C', face: '#FFE2BF', ring: '#A4561F' },
  };

  // T — верх головы, B — низ тела, W — полуширина, er — радиус глаза,
  // eyk — высота глаз в долях роста, dx — расстояние глаза от центра,
  // tuft — высота ушек, rows — рядов пёрышек на животе.
  const STAGES = {
    cub:   { name: 'Детёныш',   T: 72, B: 236, W: 78, er: 25, eyk: 0.38, dx: 27, tuft: 5,  rows: 2 },
    young: { name: 'Подросток', T: 48, B: 236, W: 74, er: 22, eyk: 0.31, dx: 24, tuft: 14, rows: 3 },
    grown: { name: 'Взрослый',  T: 32, B: 236, W: 82, er: 21, eyk: 0.28, dx: 26, tuft: 22, rows: 4 },
  };

  const EMOTIONS = { calm: 'Спокойно', happy: 'Радость', sad: 'Грусть' };
  const ACCESSORIES = { none: 'Без', scarf: 'Шарф', glasses: 'Очки' };

  const DARK = '#2A2320';
  const BEAK = '#F4A340';
  const BEAK_DARK = '#D98420';
  const SCARF = '#E05252';
  const SCARF_STRIPE = '#FFE1E1';
  const GLASSES = '#3A3A40';
  const BLUSH = '#F7A1B5';
  const CX = 120;

  function cubic(p0, p1, p2, p3, t) {
    const u = 1 - t;
    return [0, 1].map(i => u * u * u * p0[i] + 3 * u * u * t * p1[i] + 3 * u * t * t * p2[i] + t * t * t * p3[i]);
  }

  // Полуширина тела на высоте y: нужна, чтобы шарф лёг ровно по контуру.
  function halfWidth(g, y) {
    const T = g.T, B = g.B, W = g.W, H = B - T;
    const segs = [
      [[CX, T], [CX + 0.8 * W, T], [CX + W, T + 0.35 * H], [CX + W, T + 0.6 * H]],
      [[CX + W, T + 0.6 * H], [CX + W, T + 0.9 * H], [CX + 0.6 * W, B], [CX, B]],
    ];
    let best = null;
    segs.forEach(s => {
      for (let i = 0; i <= 60; i++) {
        const q = cubic(s[0], s[1], s[2], s[3], i / 60);
        if (!best || Math.abs(q[1] - y) < Math.abs(best[1] - y)) best = q;
      }
    });
    return best[0] - CX;
  }

  function eye(x, y, r, side, emotion, p) {
    if (emotion === 'happy') {
      return `<path d="M${x - r * 0.72} ${y + r * 0.25} Q${x} ${y - r * 0.95} ${x + r * 0.72} ${y + r * 0.25}" stroke="${DARK}" stroke-width="${r * 0.2}" fill="none" stroke-linecap="round"/>`;
    }
    const pupilY = emotion === 'calm' ? y + r * 0.08 : y + r * 0.28;
    let s = `<circle cx="${x}" cy="${y}" r="${r}" fill="#FFFFFF" stroke="${p.ring}" stroke-width="3"/>`
      + `<circle cx="${x}" cy="${pupilY}" r="${r * 0.62}" fill="${DARK}"/>`
      + `<circle cx="${x - r * 0.26}" cy="${pupilY - r * 0.26}" r="${r * 0.24}" fill="#FFFFFF"/>`
      + `<circle cx="${x + r * 0.24}" cy="${pupilY + r * 0.26}" r="${r * 0.1}" fill="#FFFFFF"/>`;
    if (emotion === 'sad') {
      // Веко — сегмент круга над наклонной хордой: внешний край ниже внутреннего.
      const [outer, inner] = side < 0 ? [170, 325] : [215, 10];
      const pt = a => [x + r * Math.cos(a * Math.PI / 180), y + r * Math.sin(a * Math.PI / 180)];
      const [x1, y1] = pt(outer);
      const [x2, y2] = pt(inner);
      s += `<path d="M${x1} ${y1} A${r} ${r} 0 0 1 ${x2} ${y2} Z" fill="${p.ring}"/>`
        + `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="${p.ring}" stroke-width="3"/>`;
    }
    return s;
  }

  function svg(color, stage, emotion, accessory) {
    const p = PAL[color], g = STAGES[stage];
    const T = g.T, B = g.B, W = g.W, H = B - T, er = g.er, dx = g.dx;
    const ey = T + g.eyk * H;
    const k = er / 27;
    const kb = k * 1.15;
    let o = '';

    o += `<ellipse cx="${CX}" cy="${B + 8}" rx="${W * 0.72}" ry="7" fill="#000" fill-opacity="0.08"/>`;

    [CX - W * 0.3, CX + W * 0.3].forEach(fx => {
      [[-7, 0], [0, 3], [7, 0]].forEach(([ox, oy]) => {
        o += `<ellipse cx="${fx + ox}" cy="${B + oy}" rx="5" ry="6.5" fill="${BEAK}"/>`;
      });
    });

    [-1, 1].forEach(sd => {
      const b1 = [CX + sd * 0.8 * W, T + 0.17 * H];
      const b2 = [CX + sd * 0.4 * W, T + 0.015 * H];
      const tip = [CX + sd * 0.86 * W, T - g.tuft];
      o += `<path d="M${b1[0]} ${b1[1]} C${b1[0] + sd * 3} ${b1[1] - 14} ${tip[0]} ${tip[1] + 10} ${tip[0]} ${tip[1]} C${tip[0] - sd * 10} ${tip[1] + 4} ${b2[0] + sd * 10} ${b2[1] - 4} ${b2[0]} ${b2[1]} Z" fill="${p.body}" stroke="${p.ring}" stroke-opacity="0.45" stroke-width="2" stroke-linejoin="round"/>`;
    });

    o += `<path d="M${CX} ${T} C${CX + 0.8 * W} ${T} ${CX + W} ${T + 0.35 * H} ${CX + W} ${T + 0.6 * H} C${CX + W} ${T + 0.9 * H} ${CX + 0.6 * W} ${B} ${CX} ${B} C${CX - 0.6 * W} ${B} ${CX - W} ${T + 0.9 * H} ${CX - W} ${T + 0.6 * H} C${CX - W} ${T + 0.35 * H} ${CX - 0.8 * W} ${T} ${CX} ${T} Z" fill="${p.body}" stroke="${p.ring}" stroke-opacity="0.45" stroke-width="2"/>`;

    o += `<ellipse cx="${CX}" cy="${T + 0.72 * H}" rx="${W * 0.62}" ry="${H * 0.27}" fill="${p.face}"/>`;
    for (let i = 0; i < g.rows; i++) {
      const yy = T + 0.66 * H + i * 0.075 * H;
      const n = i % 2 ? 2 : 3;
      for (let j = 0; j < n; j++) {
        const xx = CX + (j - (n - 1) / 2) * 20;
        o += `<path d="M${xx - 6} ${yy} Q${xx} ${yy + 6} ${xx + 6} ${yy}" stroke="${p.wing}" stroke-width="2.4" fill="none" stroke-linecap="round"/>`;
      }
    }

    [-1, 1].forEach(sd => {
      o += `<path d="M${CX + sd * 0.94 * W} ${T + 0.5 * H} C${CX + sd * 1.12 * W} ${T + 0.62 * H} ${CX + sd * 1.04 * W} ${T + 0.9 * H} ${CX + sd * 0.74 * W} ${T + 0.95 * H} C${CX + sd * 0.82 * W} ${T + 0.8 * H} ${CX + sd * 0.84 * W} ${T + 0.62 * H} ${CX + sd * 0.94 * W} ${T + 0.5 * H} Z" fill="${p.wing}"/>`;
      // Пёрышки на крыльях отличают подростка и взрослого от детёныша.
      if (stage !== 'cub') {
        for (let i = 0; i < 3; i++) {
          const yy = T + (0.68 + i * 0.07) * H;
          o += `<path d="M${CX + sd * 0.98 * W} ${yy} Q${CX + sd * 0.9 * W} ${yy + 5} ${CX + sd * 0.84 * W} ${yy + 2}" stroke="${p.ring}" stroke-opacity="0.6" stroke-width="2" fill="none" stroke-linecap="round"/>`;
        }
      }
    });

    [-1, 1].forEach(sd => {
      o += `<circle cx="${CX + sd * dx}" cy="${ey}" r="${er + 9 * k}" fill="${p.face}"/>`;
    });

    if (emotion !== 'sad') {
      [-1, 1].forEach(sd => {
        o += `<ellipse cx="${CX + sd * (dx + er * 0.55)}" cy="${ey + er + 7 * k}" rx="${10 * k}" ry="${5.5 * k}" fill="${BLUSH}" fill-opacity="${emotion === 'happy' ? 0.6 : 0.3}"/>`;
      });
    }

    [-1, 1].forEach(sd => { o += eye(CX + sd * dx, ey, er, sd, emotion, p); });

    const bk = ey + er * 0.42;
    o += `<path d="M${CX} ${bk} Q${CX - 9 * kb} ${bk + 2 * kb} ${CX - 8 * kb} ${bk + 7 * kb} Q${CX - 4 * kb} ${bk + 15 * kb} ${CX} ${bk + 18 * kb} Q${CX + 4 * kb} ${bk + 15 * kb} ${CX + 8 * kb} ${bk + 7 * kb} Q${CX + 9 * kb} ${bk + 2 * kb} ${CX} ${bk} Z" fill="${BEAK}"/>`
      + `<path d="M${CX - 6 * k} ${bk + 8 * k} Q${CX} ${bk + 11 * k} ${CX + 6 * k} ${bk + 8 * k}" stroke="${BEAK_DARK}" stroke-width="1.6" fill="none" stroke-linecap="round"/>`;

    const neckY = ey + er + 12 * k;
    if (accessory === 'scarf') {
      const w = halfWidth(g, neckY) * 0.93;
      const sag = 7;
      o += `<path d="M${CX - w} ${neckY} Q${CX} ${neckY + 2 * sag} ${CX + w} ${neckY} L${CX + w} ${neckY + 15} Q${CX} ${neckY + 15 + 2 * sag} ${CX - w} ${neckY + 15} Z" fill="${SCARF}"/>`;
      [-0.7, -0.35, 0, 0.35, 0.7].forEach(u => {
        const x = CX + u * w;
        const top = neckY + sag * (1 - u * u);
        o += `<path d="M${x - 3.5} ${top} L${x + 3.5} ${top} L${x + 3.5} ${top + 15} L${x - 3.5} ${top + 15} Z" fill="${SCARF_STRIPE}"/>`;
      });
      const tx = CX + w * 0.42, ty = neckY + 10;
      o += `<g transform="rotate(-10 ${tx} ${ty})">`
        + `<rect x="${tx - 8}" y="${ty}" width="16" height="44" rx="5" fill="${SCARF}"/>`
        + `<rect x="${tx - 8}" y="${ty + 12}" width="16" height="6" fill="${SCARF_STRIPE}"/>`
        + `<rect x="${tx - 8}" y="${ty + 26}" width="16" height="6" fill="${SCARF_STRIPE}"/>`
        + [0, 1, 2, 3].map(i => `<line x1="${tx - 6 + i * 4}" y1="${ty + 44}" x2="${tx - 6 + i * 4}" y2="${ty + 50}" stroke="${SCARF}" stroke-width="2" stroke-linecap="round"/>`).join('')
        + `</g>`;
    }
    if (accessory === 'glasses') {
      const r = er + 6 * k;
      [-1, 1].forEach(sd => {
        o += `<circle cx="${CX + sd * dx}" cy="${ey}" r="${r}" fill="none" stroke="${GLASSES}" stroke-width="4"/>`;
      });
      o += `<path d="M${CX - dx + r * 0.92} ${ey - r * 0.35} Q${CX} ${ey - r * 0.75} ${CX + dx - r * 0.92} ${ey - r * 0.35}" stroke="${GLASSES}" stroke-width="4" fill="none"/>`;
    }

    const label = `Сова Финни: ${PAL[color].name.toLowerCase()}, ${STAGES[stage].name.toLowerCase()}, ${EMOTIONS[emotion].toLowerCase()}`;
    return `<svg viewBox="0 0 240 262" width="100%" height="100%" role="img" aria-label="${label}">${o}</svg>`;
  }

  window.FinnyOwl = { PAL, STAGES, EMOTIONS, ACCESSORIES, svg };
})();
