/* Lani's site: the sky that turns from dawn to night as you scroll, the clips that play when seen, and the small
   examples (a word card, the six cases, counting sheep, a form question, the car's drills, the village's sounds).
   No framework, no build step. */
(() => {
  "use strict";
  const root = document.documentElement;
  root.classList.add("js");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const dark = matchMedia("(prefers-color-scheme: dark)");
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

  /* ------------------------------------------------------------------ the sky */
  // [minute of the day, top, middle, horizon]
  const SKY = [
    [0, "#0a1430", "#16244a", "#24345e"],
    [300, "#1b2a54", "#39497c", "#6b6f9c"],
    [390, "#7d8fc4", "#f0b48e", "#fde0bc"],
    [540, "#76b4e8", "#a8d2f2", "#e7f3fb"],
    [780, "#579fe4", "#93c7f1", "#dcefff"],
    [1020, "#6a9ed6", "#efcc8e", "#fbe4b4"],
    [1140, "#4a5899", "#e3826a", "#f6bd78"],
    [1230, "#212b5a", "#573e72", "#a85c78"],
    [1320, "#0a1430", "#16244a", "#2a3460"],
    [1440, "#0a1430", "#16244a", "#24345e"],
  ];
  const NIGHT = [11, 18, 32];
  const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
  const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
  const css = (c) => `rgb(${c[0]} ${c[1]} ${c[2]})`;
  const minutes = (s) => { const [h, m] = s.split(":").map(Number); return h * 60 + m; };
  const pad = (n) => String(n).padStart(2, "0");

  function skyAt(min) {
    let i = 0;
    while (i < SKY.length - 2 && SKY[i + 1][0] <= min) i++;
    const [m0, ...a] = SKY[i], [m1, ...b] = SKY[i + 1];
    const t = Math.min(1, Math.max(0, (min - m0) / (m1 - m0)));
    return a.map((c, k) => {
      let col = mix(hex(c), hex(b[k]), t);
      if (dark.matches) col = mix(col, NIGHT, 0.55);
      return col;
    });
  }

  const sky = $(".sky");
  const clockTime = $(".clock-time"), clockIcon = $(".clock-icon");
  const marks = $$("[data-time]");
  let anchors = [];
  function measure() {
    anchors = marks.map((el) => ({ top: el.getBoundingClientRect().top + scrollY, min: minutes(el.dataset.time) }));
  }
  function timeNow() {
    if (!anchors.length) return 390;
    const y = scrollY + innerHeight * 0.4;
    if (y <= anchors[0].top) return anchors[0].min;
    for (let i = 0; i < anchors.length - 1; i++) {
      const a = anchors[i], b = anchors[i + 1];
      if (y < b.top) return a.min + (b.min - a.min) * ((y - a.top) / Math.max(1, b.top - a.top));
    }
    return anchors[anchors.length - 1].min;
  }
  const ramp = (x, a, b) => Math.min(1, Math.max(0, (x - a) / (b - a)));
  let lastMin = -1;
  function paint() {
    const min = Math.round(timeNow());
    if (min === lastMin) return;
    lastMin = min;
    const [top, mid, bot] = skyAt(min);
    const s = sky.style;
    s.setProperty("--sky-top", css(top));
    s.setProperty("--sky-mid", css(mid));
    s.setProperty("--sky-bot", css(bot));
    // the sun from 06:00 to 19:30, the moon from 20:00
    const day = ramp(min, 360, 1170);
    s.setProperty("--sun-x", `${90 - day * 80}vw`);
    s.setProperty("--sun-y", `${78 - Math.sin(day * Math.PI) * 62}vh`);
    s.setProperty("--sun-o", String(Math.min(ramp(min, 350, 420), 1 - ramp(min, 1120, 1180))));
    const night = ramp(min, 1200, 1440);
    s.setProperty("--moon-o", String(ramp(min, 1190, 1250)));
    if (min >= 1190) {
      s.setProperty("--sun-x", `${88 - night * 56}vw`);
      s.setProperty("--sun-y", `${70 - Math.sin(Math.min(1, night * 1.4) * Math.PI / 2) * 52}vh`);
    }
    s.setProperty("--stars", String(Math.max(ramp(min, 1150, 1290), 1 - ramp(min, 300, 400))));
    const h = Math.floor(min / 60) % 24, m = Math.floor(min % 60);
    clockTime.textContent = `${pad(h)}:${pad(m)}`;
    clockIcon.textContent = min < 330 ? "🌙" : min < 450 ? "🌅" : min < 1110 ? "☀️" : min < 1230 ? "🌇" : "🌙";
  }
  function stars() {
    const w = innerWidth, h = innerHeight * 0.8;
    let seed = 7;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    $$(".stars").forEach((el, k) => {
      const n = Math.round((w * h) / (k ? 22000 : 9000));
      const list = [];
      for (let i = 0; i < n; i++) list.push(`${Math.round(rnd() * w)}px ${Math.round(rnd() * h)}px 0 ${rnd() < 0.12 ? 1 : 0}px ${rnd() < 0.2 ? "#ffe9a8" : "#fff"}`);
      el.style.boxShadow = list.join(",");
    });
  }
  let ticking = false;
  const onScroll = () => { if (!ticking) { ticking = true; requestAnimationFrame(() => { ticking = false; paint(); }); } };
  addEventListener("scroll", onScroll, { passive: true });
  addEventListener("resize", () => { measure(); stars(); lastMin = -1; paint(); });
  dark.addEventListener?.("change", () => { lastMin = -1; paint(); });
  addEventListener("load", () => { measure(); lastMin = -1; paint(); });
  // the sun and the moon as pixel discs (the moon a crescent)
  function disc(r, color, cut) {
    const d = r * 2;
    let rects = "";
    for (let y = 0; y < d; y++) for (let x = 0; x < d; x++) {
      const cx = x + 0.5 - r, cy = y + 0.5 - r;
      if (cx * cx + cy * cy > r * r) continue;
      if (cut && (cx - cut[0]) ** 2 + (cy - cut[1]) ** 2 < cut[2] ** 2) continue;
      rects += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
    }
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${d} ${d}" shape-rendering="crispEdges" fill="${color}">${rects}</svg>`;
    return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
  }
  $(".sun").style.backgroundImage = disc(7, "#ffd76a");
  $(".moon").style.backgroundImage = disc(7, "#f4f1e1", [3.2, -2.6, 5.6]);
  measure(); stars(); paint();

  /* ------------------------------------------------------------------ pixel sprites */
  // rows of letters, one per pixel; "." is empty; an outline is added round every sprite
  const PAL = {
    y: "#e8c35a", Y: "#b8902e", s: "#f2c9a0", k: "#2a1e14", r: "#e98a7a", g: "#3f8f4a", b: "#6b4a2b", d: "#3b2a1a",
    R: "#c0392b", p: "#7a4a8a", w: "#f6f1e4", B: "#2f6db5", h: "#6b3f1f", W: "#fbfaf3", K: "#3a2e28", e: "#ffffff",
  };
  const SPRITES = {
    farmer: [
      "....YYYY....",
      "...yyyyyy...",
      ".yyyyyyyyyy.",
      "..ssssssss..",
      ".ssssssssss.",
      ".sskssssks s",
      ".srssssssrs.",
      "..ssssssss..",
      "...gggggg...",
      "..sggggggs..",
      "..sggggggs..",
      "...bbbbbb...",
      "...bb..bb...",
      "...dd..dd...",
    ],
    grandma: [
      "....RRRR....",
      "..RRRRRRRR..",
      ".RRssssssRR.",
      ".Rssssssssr.",
      ".Rskssssksr.",
      ".Rssssssssr.",
      ".Rrssssssrr.",
      "..ssssssss..",
      "..pppppppp..",
      ".spwwwwwwps.",
      "..pwwwwwwp..",
      "..pwwwwwwp..",
      "..pppppppp..",
      "...dd..dd...",
    ],
    kid: [
      "...BBBBBB...",
      "..BBBBBBBBBB",
      "..hhhhhhhh..",
      ".hssssssssh.",
      ".sskssssks s",
      ".ssssssssss.",
      ".srssssssrs.",
      "..ssssssss..",
      "...RRRRRR...",
      "..sRRRRRRs..",
      "...RRRRRR...",
      "...BBBBBB...",
      "...BB..BB...",
      "...dd..dd...",
    ],
    sheep: [
      "...WWWWW......",
      ".WWWWWWWWW....",
      "WWWWWWWWWWWKK.",
      "WWWWWWWWWWKKKK",
      "WWWWWWWWWWKeKK",
      ".WWWWWWWWWWKK.",
      "..WWWWWWWW....",
      "..K.K...K.K...",
      "..K.K...K.K...",
    ],
  };
  function sprite(rows, outline = "#2a1e14") {
    const g = rows.map((r) => r.replace(/ /g, "s").split(""));
    const H = g.length + 2, W = Math.max(...g.map((r) => r.length)) + 2;
    const at = (x, y) => (g[y - 1] && g[y - 1][x - 1] && g[y - 1][x - 1] !== "." ? g[y - 1][x - 1] : null);
    let out = "";
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const c = at(x, y);
      if (c) out += `<rect x="${x}" y="${y}" width="1" height="1" fill="${PAL[c]}"/>`;
      else if (at(x - 1, y) || at(x + 1, y) || at(x, y - 1) || at(x, y + 1)) out += `<rect x="${x}" y="${y}" width="1" height="1" fill="${outline}"/>`;
    }
    return `<svg viewBox="0 0 ${W} ${H}" shape-rendering="crispEdges" aria-hidden="true">${out}</svg>`;
  }
  const SHEEP = sprite(SPRITES.sheep);
  $$(".walker").forEach((el, i) => { el.innerHTML = sprite(SPRITES[["farmer", "grandma", "kid"][i % 3]]); });

  /* ------------------------------------------------------------------ clips: play while seen */
  // With reduced motion nothing plays by itself and the native controls stay. Otherwise a clip plays while it's seen,
  // and a small button pauses it (and keeps it paused).
  const clips = $$("video.clip");
  const seen = new Set();
  const auto = "IntersectionObserver" in window && !reduce.matches;
  if (auto) {
    clips.forEach((v) => {
      v.removeAttribute("controls");
      const wrap = document.createElement("div");
      wrap.className = "vwrap";
      v.replaceWith(wrap);
      const b = document.createElement("button");
      b.type = "button";
      b.className = "vbtn";
      const label = () => { const p = v.paused; b.textContent = p ? "▶" : "⏸"; b.setAttribute("aria-label", p ? "Play the clip" : "Pause the clip"); };
      b.addEventListener("click", () => {
        if (v.paused) { v.dataset.held = ""; v.play().catch(() => {}); } else { v.dataset.held = "1"; v.pause(); }
      });
      v.addEventListener("play", label);
      v.addEventListener("pause", label);
      wrap.append(v, b);
      label();
    });
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) {
        const v = e.target;
        if (e.isIntersecting) { seen.add(v); if (!v.dataset.held) { v.preload = "auto"; v.play().catch(() => {}); } }
        else { seen.delete(v); v.pause(); }
      }
    }, { threshold: 0.35 });
    clips.forEach((v) => io.observe(v));
  }

  /* ------------------------------------------------------------------ reveal on scroll */
  const reveals = $$(".reveal");
  if ("IntersectionObserver" in window && !reduce.matches) {
    const ro = new IntersectionObserver((entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add("in"); ro.unobserve(e.target); }
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    reveals.forEach((el) => ro.observe(el));
  } else reveals.forEach((el) => el.classList.add("in"));

  /* ------------------------------------------------------------------ dialog tabs */
  const DIALOGS = {
    soup: { poster: "media/soup.webp", label: "Babica Micka brings soup to the fire in the evening. She asks: Dober večer, Jan! Si lačen? The learner picks Dober večer! Ja, zelo sem lačen. She answers Potem sedi k ognju, and offers the soup." },
    hide: { poster: "media/hide.webp", label: "Zala plays hide-and-seek in the living room. The learner counts to ten, Zala hides, and the turn asks to find her in the scene: the learner taps under the table. Aha, pod mizo si! Joj! Kako si me našel tako hitro?" },
    typed: { poster: "media/typed.webp", label: "Luka warms his hands by the fire in the morning. The turn asks to type the missing word: Dobro jutro, Luka! Seveda, blank. The keyboard comes up, the panel rises over the picture, the learner types sedi and checks. Luka answers Hvala. Ogenj lepo gori." },
  };
  const tabs = $$('[role="tab"]');
  const dclip = $("#dialog-clip");
  const panel = $("#panel-dialog");
  function selectTab(tab, focus) {
    tabs.forEach((t) => { const on = t === tab; t.setAttribute("aria-selected", String(on)); t.tabIndex = on ? 0 : -1; });
    if (focus) tab.focus();
    const key = tab.dataset.clip, d = DIALOGS[key];
    panel.setAttribute("aria-labelledby", tab.id);
    $$(".note", panel).forEach((n) => { n.hidden = n.dataset.for !== key; });
    dclip.pause();
    dclip.poster = d.poster;
    dclip.setAttribute("aria-label", d.label);
    dclip.innerHTML = `<source src="media/${key}.webm" type="video/webm"><source src="media/${key}.mp4" type="video/mp4">`;
    dclip.load();
    if (auto && seen.has(dclip) && !dclip.dataset.held) dclip.play().catch(() => {});
  }
  tabs.forEach((t, i) => {
    t.addEventListener("click", () => selectTab(t));
    t.addEventListener("keydown", (e) => {
      const n = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
      if (n) { e.preventDefault(); selectTab(tabs[(i + n + tabs.length) % tabs.length], true); }
      if (e.key === "Home") { e.preventDefault(); selectTab(tabs[0], true); }
      if (e.key === "End") { e.preventDefault(); selectTab(tabs[tabs.length - 1], true); }
    });
  });

  /* ------------------------------------------------------------------ the word card */
  const CREDIT = "Wiktionary (CC BY-SA 4.0) via kaikki.org";
  const CTX = "«Dober večer! Sedite, sedite.»";
  const WORDS = {
    dober: {
      form: "Dober", lemma: "dober", tag: "pridevnik · adjective",
      gram: "nominative masculine singular: it agrees with večer", gloss: "good",
      forms: { title: "moški · ženski · srednji", rows: [["dober večer", "dobra kava", "dobro jutro"]] },
      note: "An adjective takes its noun's gender: dober dan, dobra kava, dobro jutro.",
    },
    vecer: {
      form: "večer", lemma: "večer", tag: "samostalnik · noun, moški · masculine",
      gram: "nominative singular", gloss: "evening",
      forms: { title: "ednina · singular", rows: [["1. večer", "2. večera", "3. večeru"], ["4. večer", "5. pri večeru", "6. z večerom"]] },
      note: "Dobro jutro in the morning, dober dan by day, dober večer in the evening, lahko noč at bedtime.",
    },
    sedite: {
      form: "sedite", lemma: "sesti", here: true, tag: "glagol · verb — dovršni · perfective",
      gram: "second-person plural imperative", gloss: "to sit down",
      partner: { lemma: "sedeti", gloss: "to be sitting, to sit", gram: "second-person plural present; second-person plural imperative", note: "sesti: a movement, once · sedeti: a state that lasts" },
      forms: {
        title: "sedanjik · present", head: ["ednina", "množina", "dvojina"],
        rows: [["jaz sedem", "mi sedemo", "midva sedeva"], ["ti sedeš", "vi sedete", "vidva sedeta"], ["on sede", "oni sedejo", "onadva sedeta"]],
        after: ["velelnik · imperative", "sedi! · sedite! · sedimo! · sediva! · sedita!"],
      },
      note: "Sedite is for several people, or one you say vi to. To one friend: sedi!",
    },
  };
  const card = $("#wordcard");
  function showWord(key, btn) {
    const w = WORDS[key];
    $$(".w").forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
    const f = w.forms;
    const table = `<table>${f.head ? `<tr>${f.head.map((h) => `<th lang="sl">${h}</th>`).join("")}</tr>` : ""}${f.rows.map((r) => `<tr>${r.map((c) => `<td lang="sl">${esc(c)}</td>`).join("")}</tr>`).join("")}</table>`;
    card.innerHTML = `
      <p class="wc-form" lang="sl">${esc(btn.textContent.trim().toLowerCase())}</p>
      <p class="wc-ctx" lang="sl">${esc(CTX)}</p>
      <div class="wc-box">
        <p class="wc-lemma"><span lang="sl">${esc(w.lemma)}</span>${w.here ? ' <small>📍 <span lang="sl">tukaj</span> · here</small>' : ""}</p>
        <p class="wc-gram">${esc(w.tag)} — ${esc(w.gram)}</p>
        <p class="wc-gloss">${esc(w.gloss)}</p>
        ${w.partner ? `<p class="wc-partner">↔ <b><span lang="sl">par</span> · partner: <span lang="sl">${esc(w.partner.lemma)}</span></b> — ${esc(w.partner.gloss)}<br><span class="wc-gram">${esc(w.partner.gram)}</span><br>${esc(w.partner.note)}</p>` : ""}
        <div class="wc-forms"><p class="wc-gram">📚 <span lang="sl">${esc(f.title)}</span></p>${table}${f.after ? `<p class="wc-gram" style="margin-top:8px">${esc(f.after[0])}</p><p lang="sl">${esc(f.after[1])}</p>` : ""}</div>
        <p class="wc-partner">${esc(w.note)}</p>
      </div>
      <p class="wc-credit">${CREDIT}</p>`;
  }
  $$(".w").forEach((b) => b.addEventListener("click", () => showWord(b.dataset.word, b)));

  /* ------------------------------------------------------------------ the six cases */
  const CASES = [
    { actor: "👉", left: 18, bottom: 34, sl: "To je <b>hiša</b>.", en: "This is a house.", why: "kdo? kaj? The base form, the one in the dictionary." },
    { actor: "🌳", left: 74, bottom: 28, sl: "Blizu <b>hiše</b> je lipa.", en: "Near the house there's a linden.", why: "blizu takes the genitive, like iz, od and do." },
    { actor: "🚶", left: 30, bottom: 28, sl: "Grem k <b>hiši</b>.", en: "I'm going to the house.", why: "k takes the dative: towards someone or something." },
    { actor: "👀", left: 14, bottom: 56, sl: "Vidim <b>hišo</b>.", en: "I see the house.", why: "the object of the verb: the accusative." },
    { actor: "🌻", left: 72, bottom: 26, sl: "Pri <b>hiši</b> je vrt.", en: "There's a garden by the house.", why: "pri takes the locative: where, at whose place." },
    { actor: "🪑", left: 45, bottom: 4, sl: "Pred <b>hišo</b> je klop.", en: "There's a bench in front of the house.", why: "s, pred, za, pod and nad take the instrumental: s hišo, with the house." },
  ];
  const rows = $$(".cases tbody tr"), actor = $("#case-actor"), cline = $("#case-line");
  function pickCase(i) {
    rows.forEach((r, k) => r.setAttribute("aria-selected", String(k === i)));
    const c = CASES[i];
    if (i === 2 && !reduce.matches) { actor.style.transition = "none"; actor.style.left = "4%"; actor.offsetWidth; actor.style.transition = ""; }
    actor.textContent = c.actor;
    actor.style.left = `${c.left}%`;
    actor.style.bottom = `${c.bottom}%`;
    cline.innerHTML = `<span lang="sl">${c.sl}</span> <i>${esc(c.en)}</i><br><small class="muted">${esc(c.why)}</small>`;
  }
  rows.forEach((r, i) => {
    r.addEventListener("click", () => pickCase(i));
    r.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pickCase(i); }
      if (e.key === "ArrowDown" && rows[i + 1]) { e.preventDefault(); rows[i + 1].focus(); pickCase(i + 1); }
      if (e.key === "ArrowUp" && rows[i - 1]) { e.preventDefault(); rows[i - 1].focus(); pickCase(i - 1); }
    });
  });
  if (rows.length) pickCase(0);

  /* ------------------------------------------------------------------ counting sheep */
  const NUM = ["", "ena", "dve", "tri", "štiri", "pet", "šest", "sedem", "osem", "devet", "deset"];
  const EN = ["", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"];
  const range = $("#count-range"), cl = $("#count-line"), cr = $("#count-rule"), flock = $("#flock");
  let shown = 0;
  function count(n) {
    n = Math.max(1, Math.min(10, n));
    range.value = n;
    const [verb, noun, rule] =
      n === 1 ? ["je", "ovca", "1: ednina · the singular"] :
      n === 2 ? ["sta", "ovci", "2: dvojina · the dual, the verb too: sta"] :
      n <= 4 ? ["so", "ovce", `${n}: množina · the plural`] :
      ["je", "ovc", `${n}: rodilnik množine · from five on, the genitive plural, and the verb in the singular: je`];
    cl.innerHTML = `<span lang="sl">Tu <b>${verb}</b> <b>${NUM[n]} ${noun}</b>.</span> <i>Here ${n === 1 ? "is" : "are"} ${EN[n]} sheep.</i>`;
    cr.innerHTML = esc(rule) + (n === 7 ? ' <span class="muted">(<span lang="sl">sedem</span> is seven, and also »I sit down«)</span>' : "");
    while (shown < n) { flock.insertAdjacentHTML("beforeend", SHEEP); shown++; }
    while (shown > n) { flock.lastElementChild.remove(); shown--; }
  }
  if (range) {
    range.addEventListener("input", () => count(+range.value));
    $("#count-minus").addEventListener("click", () => count(+range.value - 1));
    $("#count-plus").addEventListener("click", () => count(+range.value + 1));
    count(+range.value);
  }

  /* ------------------------------------------------------------------ the form question */
  const QUIZ = [
    {
      meta: "sesti — to sit down\n1. oseba ednine · first person singular, sedanjik · present",
      q: ["Jaz", "na klop. (sesti)"], opts: ["sedim", "sedem", "sedeš", "sede"], right: "sedem",
      full: "Jaz sedem na klop.", en: "I sit down on the bench.",
      ok: "Na klop answers kam? (where to?) with the accusative: a movement, so sesti.",
      no: {
        sedim: "Sedim is sedeti, to be sitting, and goes with kje? (where?): Jaz sedim na klopi. Na klop is where to: sesti, sedem.",
        sedeš: "Sedeš is ti: you sit down. For jaz: sedem.",
        sede: "Sede is on or ona: he or she sits down. For jaz: sedem.",
      },
    },
    {
      meta: "sedeti — to be sitting\n3. oseba ednine · third person singular, sedanjik · present",
      q: ["Babica", "pri ognju. (sedeti)"], opts: ["sede", "sedijo", "sedi", "sedim"], right: "sedi",
      full: "Babica sedi pri ognju.", en: "Grandma is sitting by the fire.",
      ok: "Pri ognju (pri with the locative) says where she is: a state, sedeti.",
      no: {
        sede: "Sede is sesti, sitting down: Babica sede k ognju, she goes and sits down. Pri ognju is where she is: sedi.",
        sedijo: "Sedijo is oni, three or more of them. For babica: sedi.",
        sedim: "Sedim is jaz: I am sitting. For babica: sedi.",
      },
    },
    {
      meta: "miza — table\n2. sklon · genitive: koga? česa? — ednina · singular\n„When the beard goes nine times round the table, the king will wake up.“",
      q: ["Ko bo brada devetkrat okoli", ", se bo kralj zbudil. (miza)"], opts: ["mizi", "miza", "mizo", "mize"], right: "mize",
      full: "Ko bo brada devetkrat okoli mize, se bo kralj zbudil.", en: "When the beard goes nine times round the table, the king will wake up.",
      ok: "Okoli takes the genitive: okoli mize. From the story of Kralj Matjaž.",
      book: "📖 Nova stran v knjigi · New page in the book: Rodilnik: koga? česa?",
      no: {
        mizi: "Mizi is the dative or the locative (k mizi, pri mizi). Okoli takes the genitive: mize.",
        miza: "Miza is the base form (kdo? kaj?). Okoli takes the genitive: mize.",
        mizo: "Mizo is the accusative or the instrumental (vidim mizo, za mizo). Okoli takes the genitive: mize.",
      },
    },
    {
      meta: "hiša — house\n5. sklon · locative: pri kom? pri čem? — ednina · singular",
      q: ["Pri", "je vrt. (hiša)"], opts: ["hiše", "hiši", "hišo", "hiša"], right: "hiši",
      full: "Pri hiši je vrt.", en: "There's a garden by the house.",
      ok: "Pri takes the locative: pri hiši.",
      no: {
        hiše: "Hiše is the genitive (blizu hiše) or the plural. Pri takes the locative: hiši.",
        hišo: "Hišo is the accusative (vidim hišo) or the instrumental (pred hišo). Pri takes the locative: hiši.",
        hiša: "Hiša is the base form. Pri takes the locative: hiši.",
      },
    },
  ];
  const qm = $("#quiz-meta"), qq = $("#quiz-q"), qo = $("#quiz-opts"), qf = $("#quiz-fb"), qn = $("#quiz-next"), qc = $("#quiz-count");
  let qi = 0, score = 0;
  function ask() {
    const q = QUIZ[qi];
    qm.textContent = q.meta;
    qq.innerHTML = `${esc(q.q[0])} <span class="gap" aria-label="blank">&nbsp;</span>${q.q[1].startsWith(",") ? "" : " "}${esc(q.q[1])}`;
    qo.innerHTML = q.opts.map((o) => `<button type="button" class="opt" lang="sl">${esc(o)}</button>`).join("");
    qf.textContent = "";
    qf.className = "quiz-fb";
    qn.hidden = true;
    qc.textContent = `${qi + 1} / ${QUIZ.length}`;
    $$(".opt", qo).forEach((b) => b.addEventListener("click", () => answer(b)));
  }
  function answer(b) {
    const q = QUIZ[qi], pick = b.textContent, ok = pick === q.right;
    if (ok) score++;
    $$(".opt", qo).forEach((x) => { x.disabled = true; if (x.textContent === q.right) x.classList.add("right"); });
    if (!ok) b.classList.add("wrong");
    qf.className = "quiz-fb" + (ok ? "" : " no");
    qf.innerHTML = `<h4>${ok ? '<span lang="sl">Pravilno!</span> ✅' : '<span lang="sl">Ni čisto prav</span> · Not quite'}</h4>
      <p lang="sl"><b>${esc(q.full)}</b></p><p><i>${esc(q.en)}</i></p>
      <p>${esc(ok ? q.ok : q.no[pick])}</p>${ok && q.book ? `<p class="book">${esc(q.book)}</p>` : ""}`;
    qn.hidden = false;
    qn.textContent = "";
    qn.insertAdjacentHTML("beforeend", qi < QUIZ.length - 1 ? '<span lang="sl">Naprej</span> · Next' : '<span lang="sl">Še enkrat</span> · Once more');
    if (qi === QUIZ.length - 1) qc.textContent = `${score} / ${QUIZ.length} ✓`;
    qn.focus({ preventScroll: true });
  }
  if (qq) {
    qn.addEventListener("click", () => { if (qi < QUIZ.length - 1) qi++; else { qi = 0; score = 0; } ask(); });
    ask();
  }

  /* ------------------------------------------------------------------ the car's drills */
  const DRILLS = {
    build: {
      kind: '<span lang="sl">Gradnja stavkov</span> · Sentence building',
      steps: [
        { hear: true, prompt: "Listen, then say it after me.", sl: "Grem.", en: "I'm going." },
        { prompt: "Now add: to the shop", sl: "Grem v trgovino.", en: "I'm going to the shop." },
        { prompt: "Now add: tomorrow", sl: "Jutri grem v trgovino.", en: "Tomorrow I'm going to the shop." },
        { prompt: "Now add: because I need bread", sl: "Jutri grem v trgovino, ker potrebujem kruh.", en: "Tomorrow I'm going to the shop because I need bread." },
      ],
    },
    riddle: {
      kind: '<span lang="sl">Uganke</span> · Riddles, by Stari Janez',
      steps: [
        { hear: true, prompt: "Stari Janez:", sl: "Iz lesa sem.", en: "" },
        { hear: true, prompt: "Stari Janez:", sl: "Imam štiri noge.", en: "" },
        { hear: true, prompt: "Stari Janez:", sl: "Na meni so krožniki.", en: "" },
        { hear: true, prompt: "Stari Janez:", sl: "Družina sedi pri meni. Kaj sem?", en: "" },
        { prompt: "Guess aloud!", sl: "Miza!", en: "A table! Made of wood, four legs, plates on it, the family sits at it." },
      ],
    },
    transform: {
      kind: '<span lang="sl">Preobrat</span> · Transformations: into the past',
      steps: [
        { hear: true, prompt: "Into the past:", sl: "Micka kuha kosilo.", en: "Micka is cooking lunch." },
        { prompt: "Say it in the past", sl: "Micka je kuhala kosilo.", en: "Micka cooked lunch." },
        { hear: true, prompt: "Into the past:", sl: "Luka pase ovce.", en: "Luka is herding the sheep." },
        { prompt: "Say it in the past", sl: "Luka je pasel ovce.", en: "Luka herded the sheep." },
        { hear: true, prompt: "Into the past:", sl: "Tone kuje podkev.", en: "Tone is forging a horseshoe." },
        { prompt: "Say it in the past", sl: "Tone je koval podkev.", en: "Tone forged a horseshoe." },
      ],
    },
  };
  const radio = $("#radio");
  if (radio) {
    const rk = $("#radio-kind"), rp = $("#radio-prompt"), rs = $("#radio-sl"), rf = $("#radio-fill"), play = $("#radio-play");
    let drill = DRILLS.build, si = -1, playing = false, timer = 0, phase = "";
    const WAIT = 3200, HOLD = 2600;
    const clear = () => { clearTimeout(timer); timer = 0; };
    function fill(ms) {
      rf.style.transition = "none"; rf.style.width = "0";
      if (ms && !reduce.matches) { rf.offsetWidth; rf.style.transition = `width ${ms}ms linear`; rf.style.width = "100%"; }
    }
    function show(i, reveal) {
      si = i;
      const s = drill.steps[i];
      rp.textContent = s.prompt;
      rs.textContent = s.sl;
      const hidden = !s.hear && !reveal;
      rs.classList.toggle("hide", hidden);
      rs.setAttribute("aria-hidden", String(hidden));
      if (!hidden && s.en) rp.innerHTML = `${esc(s.prompt)} <span style="opacity:.75">· ${esc(s.en)}</span>`;
      phase = hidden ? "say" : "heard";
    }
    function step() {
      clear();
      if (phase === "say") { show(si, true); fill(0); }
      else if (si < drill.steps.length - 1) { show(si + 1, false); }
      else { rp.textContent = "Konec · The end. Press ▶ to hear it again."; rs.textContent = ""; phase = "end"; si = -1; setPlaying(false); fill(0); return; }
      if (playing && !reduce.matches) {
        if (phase === "say") { fill(WAIT); rp.insertAdjacentHTML("beforeend", ' <span style="opacity:.75">· <span lang="sl">Povej na glas!</span> Say it aloud</span>'); timer = setTimeout(step, WAIT); }
        else { fill(0); timer = setTimeout(step, HOLD); }
      }
    }
    function setPlaying(on) {
      playing = on;
      radio.classList.toggle("playing", on);
      play.textContent = on ? "⏸" : "▶";
      play.setAttribute("aria-label", on ? "Pause" : "Play");
      if (!on) { clear(); fill(0); }
    }
    play.addEventListener("click", () => {
      if (reduce.matches) { step(); return; }
      if (playing) { setPlaying(false); return; }
      setPlaying(true);
      step();
    });
    $("#radio-next").addEventListener("click", () => { clear(); if (phase === "say") show(si, true); step(); });
    $("#radio-prev").addEventListener("click", () => { clear(); fill(0); show(Math.max(0, si - 1), true); if (playing) timer = setTimeout(step, HOLD); });
    $$(".chip-btn", radio).forEach((b) => b.addEventListener("click", () => {
      $$(".chip-btn", radio).forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
      setPlaying(false);
      drill = DRILLS[b.dataset.drill];
      rk.innerHTML = drill.kind;
      si = -1; phase = "";
      rp.textContent = "Press ▶ to start.";
      rs.textContent = "";
    }));
  }

  /* ------------------------------------------------------------------ the village's sounds */
  const audio = $("#sounds");
  const ONCE = new Set(["anvil", "cowbell"]);
  const ogg = audio && audio.canPlayType('audio/ogg; codecs="vorbis"') !== "";
  let current = null;
  function stopSound() {
    if (!current) return;
    audio.pause();
    $$(`.snd[data-sound="${current}"]`).forEach((b) => b.setAttribute("aria-pressed", "false"));
    current = null;
  }
  $$(".snd").forEach((b) => b.addEventListener("click", () => {
    const name = b.dataset.sound;
    if (current === name) { stopSound(); return; }
    stopSound();
    audio.src = `media/sound/${name}.${ogg ? "ogg" : "m4a"}`;
    audio.loop = !ONCE.has(name);
    audio.volume = 0.8;
    audio.play().then(() => {
      current = name;
      $$(`.snd[data-sound="${name}"]`).forEach((x) => x.setAttribute("aria-pressed", "true"));
    }).catch(() => {});
  }));
  audio?.addEventListener("ended", stopSound);

  /* ------------------------------------------------------------------ copy buttons */
  $$(".copy").forEach((b) => b.addEventListener("click", async () => {
    const text = b.previousElementSibling.innerText.trim();
    try { await navigator.clipboard.writeText(text); b.textContent = "Copied ✓"; }
    catch { b.textContent = "Select and copy"; }
    setTimeout(() => { b.textContent = "Copy"; }, 1600);
  }));

  /* ------------------------------------------------------------------ the menu closes on a pick */
  $$(".menu a").forEach((a) => a.addEventListener("click", () => { $(".menu").open = false; }));
})();
