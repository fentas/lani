/* Lani's site: the sky that turns from dawn to night as you scroll, the clips that play when seen, the site's language
   (en, sl, de, it: switched at once, remembered), the four villages, and the small examples (a word card, the six cases,
   counting sheep, a form question, the car's drills, the village's sounds). No framework, no build step. */
(() => {
  "use strict";
  const root = document.documentElement;
  root.classList.add("js");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)");
  const dark = matchMedia("(prefers-color-scheme: dark)");
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch { /* private mode: nothing kept */ } },
  };

  /* ------------------------------------------------------------------ the strings the script writes, in English */
  // The other languages' are in i18n/<lang>.json under the same keys (with the page's own), machine-written.
  const JS_EN = /*JS_EN*/{
    "js.meta.title": "Lani · Learn Slovene in a little village",
    "js.meta.description": "Lani is an Android game for learning Slovene: a pixel-art village whose people speak to you, a grammar book that opens as you go, and your own Claude Code session as the tutor behind it. Villages in four languages that visit each other. Free software (AGPL).",
    "js.clip.play": "Play the clip",
    "js.clip.pause": "Pause the clip",
    "js.copy.done": "Copied ✓",
    "js.copy.fail": "Select and copy",
    "js.dialog.soup": "Babica Micka brings soup to the fire in the evening. She asks: Dober večer, Jan! Si lačen? The learner picks Dober večer! Ja, zelo sem lačen. She answers Potem sedi k ognju, and offers the soup.",
    "js.dialog.hide": "Zala plays hide-and-seek in the living room. The learner counts to ten, Zala hides, and the turn asks to find her in the scene: the learner taps under the table. Aha, pod mizo si! Joj! Kako si me našel tako hitro?",
    "js.dialog.typed": "Luka warms his hands by the fire in the morning. The turn asks to type the missing word: Dobro jutro, Luka! Seveda, blank. The keyboard comes up, the panel rises over the picture, the learner types sedi and checks. Luka answers Hvala. Ogenj lepo gori.",

    "js.wc.here": "here",
    "js.wc.partner": "partner",
    "js.wc.dober.tag": "adjective",
    "js.wc.dober.gram": "nominative masculine singular: it agrees with večer",
    "js.wc.dober.gloss": "good",
    "js.wc.dober.title": "masculine · feminine · neuter",
    "js.wc.dober.note": "An adjective takes its noun's gender: dober dan, dobra kava, dobro jutro.",
    "js.wc.vecer.tag": "noun, masculine",
    "js.wc.vecer.gram": "nominative singular",
    "js.wc.vecer.gloss": "evening",
    "js.wc.vecer.title": "singular",
    "js.wc.vecer.note": "Dobro jutro in the morning, dober dan by day, dober večer in the evening, lahko noč at bedtime.",
    "js.wc.sedite.tag": "verb, perfective",
    "js.wc.sedite.gram": "second-person plural imperative",
    "js.wc.sedite.gloss": "to sit down",
    "js.wc.sedite.partner.gloss": "to be sitting, to sit",
    "js.wc.sedite.partner.gram": "second-person plural present; second-person plural imperative",
    "js.wc.sedite.partner.note": "sesti: a movement, once · sedeti: a state that lasts",
    "js.wc.sedite.title": "present",
    "js.wc.sedite.after": "imperative",
    "js.wc.sedite.note": "Sedite is for several people, or one you say vi to. To one friend: sedi!",

    "js.case.0.en": "This is a house.",
    "js.case.0.why": "kdo? kaj? The base form, the one in the dictionary.",
    "js.case.1.en": "Near the house there's a linden.",
    "js.case.1.why": "blizu takes the genitive, like iz, od and do.",
    "js.case.2.en": "I'm going to the house.",
    "js.case.2.why": "k takes the dative: towards someone or something.",
    "js.case.3.en": "I see the house.",
    "js.case.3.why": "The object of the verb: the accusative.",
    "js.case.4.en": "There's a garden by the house.",
    "js.case.4.why": "pri takes the locative: where, at whose place.",
    "js.case.5.en": "There's a bench in front of the house.",
    "js.case.5.why": "s, pred, za, pod and nad take the instrumental: s hišo, with the house.",

    "js.count.numbers": "one,two,three,four,five,six,seven,eight,nine,ten",
    "js.count.one": "Here is {n} sheep.",
    "js.count.many": "Here are {n} sheep.",
    "js.count.rule1": "1: ednina · the singular",
    "js.count.rule2": "2: dvojina · the dual, the verb too: sta",
    "js.count.rule34": "{n}: množina · the plural",
    "js.count.rule5": "{n}: rodilnik množine · from five on, the genitive plural, and the verb in the singular: je",
    "js.count.seven": "(sedem is seven, and also »I sit down«)",

    "js.quiz.right": "Correct!",
    "js.quiz.wrong": "Not quite",
    "js.quiz.next": "Next",
    "js.quiz.again": "Once more",
    "js.quiz.0.meta": "sesti — to sit down\n1st person singular, present",
    "js.quiz.0.en": "I sit down on the bench.",
    "js.quiz.0.ok": "Na klop answers kam? (where to?) with the accusative: a movement, so sesti.",
    "js.quiz.0.no.sedim": "Sedim is sedeti, to be sitting, and goes with kje? (where?): Jaz sedim na klopi. Na klop is where to: sesti, sedem.",
    "js.quiz.0.no.sedeš": "Sedeš is ti: you sit down. For jaz: sedem.",
    "js.quiz.0.no.sede": "Sede is on or ona: he or she sits down. For jaz: sedem.",
    "js.quiz.1.meta": "sedeti — to be sitting\n3rd person singular, present",
    "js.quiz.1.en": "Grandma is sitting by the fire.",
    "js.quiz.1.ok": "Pri ognju (pri with the locative) says where she is: a state, sedeti.",
    "js.quiz.1.no.sede": "Sede is sesti, sitting down: Babica sede k ognju, she goes and sits down. Pri ognju is where she is: sedi.",
    "js.quiz.1.no.sedijo": "Sedijo is oni, three or more of them. For babica: sedi.",
    "js.quiz.1.no.sedim": "Sedim is jaz: I am sitting. For babica: sedi.",
    "js.quiz.2.meta": "miza — table\n2. sklon · genitive: koga? česa? — singular\n„When the beard goes nine times round the table, the king will wake up.“",
    "js.quiz.2.en": "When the beard goes nine times round the table, the king will wake up.",
    "js.quiz.2.ok": "Okoli takes the genitive: okoli mize. From the story of Kralj Matjaž.",
    "js.quiz.2.book": "📖 Nova stran v knjigi · New page in the book: Rodilnik: koga? česa?",
    "js.quiz.2.no.mizi": "Mizi is the dative or the locative (k mizi, pri mizi). Okoli takes the genitive: mize.",
    "js.quiz.2.no.miza": "Miza is the base form (kdo? kaj?). Okoli takes the genitive: mize.",
    "js.quiz.2.no.mizo": "Mizo is the accusative or the instrumental (vidim mizo, za mizo). Okoli takes the genitive: mize.",
    "js.quiz.3.meta": "hiša — house\n5. sklon · locative: pri kom? pri čem? — singular",
    "js.quiz.3.en": "There's a garden by the house.",
    "js.quiz.3.ok": "Pri takes the locative: pri hiši.",
    "js.quiz.3.no.hiše": "Hiše is the genitive (blizu hiše) or the plural. Pri takes the locative: hiši.",
    "js.quiz.3.no.hišo": "Hišo is the accusative (vidim hišo) or the instrumental (pred hišo). Pri takes the locative: hiši.",
    "js.quiz.3.no.hiša": "Hiša is the base form. Pri takes the locative: hiši.",
    "js.quiz.blank": "blank",

    "js.radio.start": "Press ▶ to start.",
    "js.radio.end": "Konec · The end. Press ▶ to hear it again.",
    "js.radio.say": "Povej na glas! Say it aloud",
    "js.radio.play": "Play",
    "js.radio.pause": "Pause",
    "js.radio.build": "Gradnja stavkov · Sentence building",
    "js.radio.build.0": "Listen, then say it after me.",
    "js.radio.build.1": "Now add: to the shop",
    "js.radio.build.2": "Now add: tomorrow",
    "js.radio.build.3": "Now add: because I need bread",
    "js.radio.build.0.en": "I'm going.",
    "js.radio.build.1.en": "I'm going to the shop.",
    "js.radio.build.2.en": "Tomorrow I'm going to the shop.",
    "js.radio.build.3.en": "Tomorrow I'm going to the shop because I need bread.",
    "js.radio.riddle": "Uganke · Riddles, by Stari Janez",
    "js.radio.riddle.teller": "Stari Janez:",
    "js.radio.riddle.guess": "Guess aloud!",
    "js.radio.riddle.answer": "A table! Made of wood, four legs, plates on it, the family sits at it.",
    "js.radio.transform": "Preobrat · Transformations: into the past",
    "js.radio.transform.into": "Into the past:",
    "js.radio.transform.say": "Say it in the past",
    "js.radio.transform.0.en": "Micka is cooking lunch.",
    "js.radio.transform.1.en": "Micka cooked lunch.",
    "js.radio.transform.2.en": "Luka is herding the sheep.",
    "js.radio.transform.3.en": "Luka herded the sheep.",
    "js.radio.transform.4.en": "Tone is forging a horseshoe.",
    "js.radio.transform.5.en": "Tone forged a horseshoe.",

    "js.v.labels.note": "Labels read “{target} · {base}”: the village's language first, the one it's explained in after.",
    "js.v.word.gender.m": "masculine",
    "js.v.word.gender.f": "feminine",
    "js.v.word.gender.n": "neuter",
    "js.v.word.plural": "plural",
    "js.v.word.title": "A word from the fire's scene",
    "js.v.shot.alt": "{name} at the fire in {village}, in the app: {happening}. The scene's picture at night, and her first line with its translation.",
    "js.v.primorska.about": "A village on the Trnovo plateau above Nova Gorica: the hills of Brda, Gorizia across the border, the burja, rebula and potica. The first village, with the most content.",
    "js.v.friuli.about": "A village in the Collio hills near Gorizia, facing Brda across the border: the Isonzo, Monte Sabotino, the grape harvest, frico, gubana and ribolla gialla.",
    "js.v.kaernten.about": "A village in the lower Gailtal below the Dobratsch, near Villach, where Austria, Slovenia and Italy meet: the lakes, the Karawanken, the Kirchtag, Kasnudeln and Reindling.",
    "js.v.lakeland.about": "A dale village among the fells of the Lake District, between Keswick and Grasmere: tarns and becks, drystone walls, Herdwick sheep, tatie pot and sticky toffee pudding."
  }/*END*/;

  /* ------------------------------------------------------------------ the site's language */
  const LANGS = ["en", "sl", "de", "it"];
  let lang = LANGS.includes(root.lang) ? root.lang : "en";
  let dict = {};
  const cache = {};
  const t = (k) => (dict[k] != null && dict[k] !== "" ? dict[k] : JS_EN[k] != null ? JS_EN[k] : k);
  const fill = (s, vars) => String(s).replace(/\{(\w+)\}/g, (m, k) => (vars[k] != null ? vars[k] : m));
  const SVG_TEXT = new Set(["text", "title", "desc", "tspan"]);
  const translatable = $$("[data-t]");
  const attributed = $$("[data-ta]");
  translatable.forEach((el) => { el._en = SVG_TEXT.has(el.tagName.toLowerCase()) ? el.textContent : el.innerHTML; });
  attributed.forEach((el) => {
    el._ta = el.dataset.ta.split(/\s+/).map((p) => { const [a, k] = p.split(":"); return { a, k, en: el.getAttribute(a) || "" }; });
  });

  async function load(l) {
    if (l === "en") return {};
    if (cache[l]) return cache[l];
    try {
      const r = await fetch(`i18n/${l}.json`);
      cache[l] = r.ok ? await r.json() : {};
    } catch { cache[l] = {}; }
    return cache[l];
  }
  const rerender = [];
  async function setLang(l, remember) {
    if (!LANGS.includes(l)) l = "en";
    dict = await load(l);
    lang = l;
    root.lang = l;
    for (const el of translatable) {
      const v = dict[el.dataset.t];
      // the translations are trimmed: keep the original's space round a gloss (" · Tone …")
      const html = v != null ? (/^\s/.test(el._en) ? " " : "") + v + (/\s$/.test(el._en) ? " " : "") : el._en;
      if (SVG_TEXT.has(el.tagName.toLowerCase())) el.textContent = html;
      else if (el.innerHTML !== html) el.innerHTML = html;
    }
    for (const el of attributed) for (const { a, k, en } of el._ta) el.setAttribute(a, dict[k] != null ? dict[k] : en);
    document.title = t("js.meta.title");
    $('meta[name="description"]')?.setAttribute("content", t("js.meta.description"));
    $$(".lang-btn").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.lang === l)));
    const sel = $("#lang-select");
    if (sel) sel.value = l;
    if (remember) store.set("lani.lang", l);
    rerender.forEach((f) => f());
    root.classList.remove("i18n-wait");
    measure(); lastMin = -1; paint();
  }
  $$(".lang-btn").forEach((b) => b.addEventListener("click", () => setLang(b.dataset.lang, true)));
  $("#lang-select")?.addEventListener("change", (e) => setLang(e.target.value, true));

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
  const mix = (a, b, t2) => a.map((v, i) => Math.round(v + (b[i] - v) * t2));
  const css = (c) => `rgb(${c[0]} ${c[1]} ${c[2]})`;
  const minutes = (s) => { const [h, m] = s.split(":").map(Number); return h * 60 + m; };
  const pad = (n) => String(n).padStart(2, "0");

  function skyAt(min) {
    let i = 0;
    while (i < SKY.length - 2 && SKY[i + 1][0] <= min) i++;
    const [m0, ...a] = SKY[i], [m1, ...b] = SKY[i + 1];
    const k = Math.min(1, Math.max(0, (min - m0) / (m1 - m0)));
    return a.map((c, j) => {
      let col = mix(hex(c), hex(b[j]), k);
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
    farmer: ["....YYYY....", "...yyyyyy...", ".yyyyyyyyyy.", "..ssssssss..", ".ssssssssss.", ".sskssssks s", ".srssssssrs.", "..ssssssss..",
      "...gggggg...", "..sggggggs..", "..sggggggs..", "...bbbbbb...", "...bb..bb...", "...dd..dd..."],
    grandma: ["....RRRR....", "..RRRRRRRR..", ".RRssssssRR.", ".Rssssssssr.", ".Rskssssksr.", ".Rssssssssr.", ".Rrssssssrr.", "..ssssssss..",
      "..pppppppp..", ".spwwwwwwps.", "..pwwwwwwp..", "..pwwwwwwp..", "..pppppppp..", "...dd..dd..."],
    kid: ["...BBBBBB...", "..BBBBBBBBBB", "..hhhhhhhh..", ".hssssssssh.", ".sskssssks s", ".ssssssssss.", ".srssssssrs.", "..ssssssss..",
      "...RRRRRR...", "..sRRRRRRs..", "...RRRRRR...", "...BBBBBB...", "...BB..BB...", "...dd..dd..."],
    sheep: ["...WWWWW......", ".WWWWWWWWW....", "WWWWWWWWWWWKK.", "WWWWWWWWWWKKKK", "WWWWWWWWWWKeKK", ".WWWWWWWWWWKK.", "..WWWWWWWW....",
      "..K.K...K.K...", "..K.K...K.K..."],
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
  const clipLabels = [];
  if (auto) {
    clips.forEach((v) => {
      v.removeAttribute("controls");
      const wrap = document.createElement("div");
      wrap.className = "vwrap";
      v.replaceWith(wrap);
      const b = document.createElement("button");
      b.type = "button";
      b.className = "vbtn";
      const label = () => { const p = v.paused; b.textContent = p ? "▶" : "⏸"; b.setAttribute("aria-label", t(p ? "js.clip.play" : "js.clip.pause")); };
      b.addEventListener("click", () => {
        if (v.paused) { v.dataset.held = ""; v.play().catch(() => {}); } else { v.dataset.held = "1"; v.pause(); }
      });
      v.addEventListener("play", label);
      v.addEventListener("pause", label);
      wrap.append(v, b);
      label();
      clipLabels.push(label);
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
  rerender.push(() => clipLabels.forEach((f) => f()));

  /* ------------------------------------------------------------------ reveal on scroll */
  const reveals = $$(".reveal");
  if ("IntersectionObserver" in window && !reduce.matches) {
    const ro = new IntersectionObserver((entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add("in"); ro.unobserve(e.target); }
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    reveals.forEach((el) => ro.observe(el));
  } else reveals.forEach((el) => el.classList.add("in"));

  /* ------------------------------------------------------------------ tabs (keyboard as the ARIA pattern says) */
  function tabKeys(tabs, select) {
    tabs.forEach((tb, i) => tb.addEventListener("keydown", (e) => {
      const n = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: 1, ArrowUp: -1 }[e.key];
      let to = null;
      if (n) to = tabs[(i + n + tabs.length) % tabs.length];
      if (e.key === "Home") to = tabs[0];
      if (e.key === "End") to = tabs[tabs.length - 1];
      if (to) { e.preventDefault(); select(to, true); }
    }));
  }

  /* ------------------------------------------------------------------ dialog tabs */
  const dtabs = $$('.tabs [role="tab"]');
  const dclip = $("#dialog-clip");
  const dpanel = $("#panel-dialog");
  let dkey = "soup";
  function selectDialog(tab, focus) {
    dtabs.forEach((tb) => { const on = tb === tab; tb.setAttribute("aria-selected", String(on)); tb.tabIndex = on ? 0 : -1; });
    if (focus) tab.focus();
    dkey = tab.dataset.clip;
    dpanel.setAttribute("aria-labelledby", tab.id);
    $$(".note", dpanel).forEach((n) => { n.hidden = n.dataset.for !== dkey; });
    dclip.pause();
    dclip.poster = `media/${dkey}.webp`;
    dclip.setAttribute("aria-label", t(`js.dialog.${dkey}`));
    dclip.innerHTML = `<source src="media/${dkey}.webm" type="video/webm"><source src="media/${dkey}.mp4" type="video/mp4">`;
    dclip.load();
    if (auto && seen.has(dclip) && !dclip.dataset.held) dclip.play().catch(() => {});
  }
  dtabs.forEach((tb) => tb.addEventListener("click", () => selectDialog(tb)));
  tabKeys(dtabs, selectDialog);
  rerender.push(() => dclip?.setAttribute("aria-label", t(`js.dialog.${dkey}`)));

  /* ------------------------------------------------------------------ the word card */
  const CREDIT = "Wiktionary (CC BY-SA 4.0) via kaikki.org";
  const CTX = "«Dober večer! Sedite, sedite.»";
  const WORDS = {
    dober: { lemma: "dober", tag: "pridevnik", forms: { head: null, rows: [["dober večer", "dobra kava", "dobro jutro"]] } },
    vecer: { lemma: "večer", tag: "samostalnik, moški", forms: { head: null, rows: [["1. večer", "2. večera", "3. večeru"], ["4. večer", "5. pri večeru", "6. z večerom"]] } },
    sedite: {
      lemma: "sesti", here: true, tag: "glagol, dovršni", partner: "sedeti",
      forms: {
        head: ["ednina", "množina", "dvojina"],
        rows: [["jaz sedem", "mi sedemo", "midva sedeva"], ["ti sedeš", "vi sedete", "vidva sedeta"], ["on sede", "oni sedejo", "onadva sedeta"]],
        after: "sedi! · sedite! · sedimo! · sediva! · sedita!",
      },
    },
  };
  const card = $("#wordcard");
  let cardKey = null, cardForm = null;
  function showWord(key, form) {
    cardKey = key; cardForm = form;
    const w = WORDS[key], f = w.forms, k = `js.wc.${key}`;
    $$(".w").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.word === key && b.textContent.trim() === form)));
    const tag = lang === "sl" ? w.tag : `${w.tag} · ${t(`${k}.tag`)}`;
    const table = `<table>${f.head ? `<tr>${f.head.map((h) => `<th lang="sl">${h}</th>`).join("")}</tr>` : ""}${f.rows.map((r) => `<tr>${r.map((c) => `<td lang="sl">${esc(c)}</td>`).join("")}</tr>`).join("")}</table>`;
    card.innerHTML = `
      <p class="wc-form" lang="sl">${esc(form.toLowerCase())}</p>
      <p class="wc-ctx" lang="sl">${esc(CTX)}</p>
      <div class="wc-box">
        <p class="wc-lemma"><span lang="sl">${esc(w.lemma)}</span>${w.here ? ` <small>📍 <span lang="sl">tukaj</span>${lang === "sl" ? "" : ` · ${esc(t("js.wc.here"))}`}</small>` : ""}</p>
        <p class="wc-gram">${esc(tag)} — ${esc(t(`${k}.gram`))}</p>
        <p class="wc-gloss">${esc(t(`${k}.gloss`))}</p>
        ${w.partner ? `<p class="wc-partner">↔ <b><span lang="sl">par</span>${lang === "sl" ? "" : ` · ${esc(t("js.wc.partner"))}`}: <span lang="sl">${esc(w.partner)}</span></b> — ${esc(t(`${k}.partner.gloss`))}<br><span class="wc-gram">${esc(t(`${k}.partner.gram`))}</span><br>${esc(t(`${k}.partner.note`))}</p>` : ""}
        <div class="wc-forms"><p class="wc-gram">📚 ${esc(t(`${k}.title`))}</p>${table}${f.after ? `<p class="wc-gram wc-after">${esc(t(`${k}.after`))}</p><p lang="sl">${esc(f.after)}</p>` : ""}</div>
        <p class="wc-partner">${esc(t(`${k}.note`))}</p>
      </div>
      <p class="wc-credit">${CREDIT}</p>`;
  }
  $$(".w").forEach((b) => b.addEventListener("click", () => showWord(b.dataset.word, b.textContent.trim())));
  rerender.push(() => { if (cardKey) showWord(cardKey, cardForm); });

  /* ------------------------------------------------------------------ the six cases */
  const CASES = [
    { actor: "👉", left: 18, bottom: 34, sl: "To je <b>hiša</b>." },
    { actor: "🌳", left: 74, bottom: 28, sl: "Blizu <b>hiše</b> je lipa." },
    { actor: "🚶", left: 30, bottom: 28, sl: "Grem k <b>hiši</b>." },
    { actor: "👀", left: 14, bottom: 56, sl: "Vidim <b>hišo</b>." },
    { actor: "🌻", left: 72, bottom: 26, sl: "Pri <b>hiši</b> je vrt." },
    { actor: "🪑", left: 45, bottom: 4, sl: "Pred <b>hišo</b> je klop." },
  ];
  const rows = $$(".cases tbody tr"), actor = $("#case-actor"), cline = $("#case-line");
  let caseAt = 0;
  function pickCase(i, moved) {
    caseAt = i;
    rows.forEach((r, k) => r.setAttribute("aria-selected", String(k === i)));
    const c = CASES[i];
    if (moved && i === 2 && !reduce.matches) { actor.style.transition = "none"; actor.style.left = "4%"; void actor.offsetWidth; actor.style.transition = ""; }
    actor.textContent = c.actor;
    actor.style.left = `${c.left}%`;
    actor.style.bottom = `${c.bottom}%`;
    const tr = lang === "sl" ? "" : ` <i>${esc(t(`js.case.${i}.en`))}</i>`;
    cline.innerHTML = `<span lang="sl">${c.sl}</span>${tr}<br><small class="muted">${esc(t(`js.case.${i}.why`))}</small>`;
  }
  rows.forEach((r, i) => {
    r.addEventListener("click", () => pickCase(i, true));
    r.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pickCase(i, true); }
      if (e.key === "ArrowDown" && rows[i + 1]) { e.preventDefault(); rows[i + 1].focus(); pickCase(i + 1, true); }
      if (e.key === "ArrowUp" && rows[i - 1]) { e.preventDefault(); rows[i - 1].focus(); pickCase(i - 1, true); }
    });
  });
  if (rows.length) { pickCase(0); rerender.push(() => pickCase(caseAt)); }

  /* ------------------------------------------------------------------ counting sheep */
  const NUM = ["", "ena", "dve", "tri", "štiri", "pet", "šest", "sedem", "osem", "devet", "deset"];
  const range = $("#count-range"), cl = $("#count-line"), cr = $("#count-rule"), flock = $("#flock");
  let shown = 0;
  function count(n) {
    n = Math.max(1, Math.min(10, n));
    range.value = n;
    const [verb, noun, rule] =
      n === 1 ? ["je", "ovca", "js.count.rule1"] :
      n === 2 ? ["sta", "ovci", "js.count.rule2"] :
      n <= 4 ? ["so", "ovce", "js.count.rule34"] : ["je", "ovc", "js.count.rule5"];
    const words = t("js.count.numbers").split(",");
    const tr = lang === "sl" ? "" : ` <i>${esc(fill(t(n === 1 ? "js.count.one" : "js.count.many"), { n: words[n - 1] || n }))}</i>`;
    cl.innerHTML = `<span lang="sl">Tu <b>${verb}</b> <b>${NUM[n]} ${noun}</b>.</span>${tr}`;
    cr.innerHTML = esc(fill(t(rule), { n })) + (n === 7 ? ` <span class="muted">${esc(t("js.count.seven"))}</span>` : "");
    while (shown < n) { flock.insertAdjacentHTML("beforeend", SHEEP); shown++; }
    while (shown > n) { flock.lastElementChild.remove(); shown--; }
  }
  if (range) {
    range.addEventListener("input", () => count(+range.value));
    $("#count-minus").addEventListener("click", () => count(+range.value - 1));
    $("#count-plus").addEventListener("click", () => count(+range.value + 1));
    count(+range.value);
    rerender.push(() => count(+range.value));
  }

  /* ------------------------------------------------------------------ the form question */
  const QUIZ = [
    { q: ["Jaz", "na klop. (sesti)"], opts: ["sedim", "sedem", "sedeš", "sede"], right: "sedem", full: "Jaz sedem na klop." },
    { q: ["Babica", "pri ognju. (sedeti)"], opts: ["sede", "sedijo", "sedi", "sedim"], right: "sedi", full: "Babica sedi pri ognju." },
    { q: ["Ko bo brada devetkrat okoli", ", se bo kralj zbudil. (miza)"], opts: ["mizi", "miza", "mizo", "mize"], right: "mize", full: "Ko bo brada devetkrat okoli mize, se bo kralj zbudil.", book: true },
    { q: ["Pri", "je vrt. (hiša)"], opts: ["hiše", "hiši", "hišo", "hiša"], right: "hiši", full: "Pri hiši je vrt." },
  ];
  const qm = $("#quiz-meta"), qq = $("#quiz-q"), qo = $("#quiz-opts"), qf = $("#quiz-fb"), qn = $("#quiz-next"), qc = $("#quiz-count");
  let qi = 0, score = 0, picked = null;
  function nextLabel() {
    const sl = qi < QUIZ.length - 1 ? "Naprej" : "Še enkrat";
    const other = t(qi < QUIZ.length - 1 ? "js.quiz.next" : "js.quiz.again");
    qn.innerHTML = `<span lang="sl">${sl}</span>${lang === "sl" ? "" : ` · ${esc(other)}`}`;
  }
  function ask() {
    const q = QUIZ[qi];
    picked = null;
    qm.textContent = t(`js.quiz.${qi}.meta`);
    qq.innerHTML = `${esc(q.q[0])} <span class="gap" role="img" aria-label="${esc(t("js.quiz.blank"))}">&nbsp;</span>${q.q[1].startsWith(",") ? "" : " "}${esc(q.q[1])}`;
    qo.innerHTML = q.opts.map((o) => `<button type="button" class="opt" lang="sl">${esc(o)}</button>`).join("");
    qf.textContent = "";
    qf.className = "quiz-fb";
    qn.hidden = true;
    qc.textContent = `${qi + 1} / ${QUIZ.length}`;
    $$(".opt", qo).forEach((b) => b.addEventListener("click", () => answer(b.textContent)));
  }
  function feedback() {
    const q = QUIZ[qi], ok = picked === q.right;
    $$(".opt", qo).forEach((x) => { x.disabled = true; x.classList.toggle("right", x.textContent === q.right); x.classList.toggle("wrong", !ok && x.textContent === picked); });
    qf.className = "quiz-fb" + (ok ? "" : " no");
    const head = ok ? `<span lang="sl">Pravilno!</span>${lang === "sl" ? "" : ` · ${esc(t("js.quiz.right"))}`} ✅`
      : `<span lang="sl">Ni čisto prav</span>${lang === "sl" ? "" : ` · ${esc(t("js.quiz.wrong"))}`}`;
    qf.innerHTML = `<h4>${head}</h4>
      <p lang="sl"><b>${esc(q.full)}</b></p>${lang === "sl" ? "" : `<p><i>${esc(t(`js.quiz.${qi}.en`))}</i></p>`}
      <p>${esc(t(ok ? `js.quiz.${qi}.ok` : `js.quiz.${qi}.no.${picked}`))}</p>${ok && q.book ? `<p class="book">${esc(t(`js.quiz.${qi}.book`))}</p>` : ""}`;
    qn.hidden = false;
    nextLabel();
    if (qi === QUIZ.length - 1) qc.textContent = `${score} / ${QUIZ.length} ✓`;
  }
  function answer(pick) {
    picked = pick;
    if (pick === QUIZ[qi].right) score++;
    feedback();
    qn.focus({ preventScroll: true });
  }
  if (qq) {
    qn.addEventListener("click", () => { if (qi < QUIZ.length - 1) qi++; else { qi = 0; score = 0; } ask(); });
    ask();
    rerender.push(() => {
      qm.textContent = t(`js.quiz.${qi}.meta`);
      $(".gap", qq)?.setAttribute("aria-label", t("js.quiz.blank"));
      if (picked) feedback();
    });
  }

  /* ------------------------------------------------------------------ the car's drills */
  const DRILLS = {
    build: {
      kind: "js.radio.build",
      steps: [
        { hear: true, p: "js.radio.build.0", sl: "Grem.", en: "js.radio.build.0.en" },
        { p: "js.radio.build.1", sl: "Grem v trgovino.", en: "js.radio.build.1.en" },
        { p: "js.radio.build.2", sl: "Jutri grem v trgovino.", en: "js.radio.build.2.en" },
        { p: "js.radio.build.3", sl: "Jutri grem v trgovino, ker potrebujem kruh.", en: "js.radio.build.3.en" },
      ],
    },
    riddle: {
      kind: "js.radio.riddle",
      steps: [
        { hear: true, p: "js.radio.riddle.teller", sl: "Iz lesa sem." },
        { hear: true, p: "js.radio.riddle.teller", sl: "Imam štiri noge." },
        { hear: true, p: "js.radio.riddle.teller", sl: "Na meni so krožniki." },
        { hear: true, p: "js.radio.riddle.teller", sl: "Družina sedi pri meni. Kaj sem?" },
        { p: "js.radio.riddle.guess", sl: "Miza!", en: "js.radio.riddle.answer" },
      ],
    },
    transform: {
      kind: "js.radio.transform",
      steps: [
        { hear: true, p: "js.radio.transform.into", sl: "Micka kuha kosilo.", en: "js.radio.transform.0.en" },
        { p: "js.radio.transform.say", sl: "Micka je kuhala kosilo.", en: "js.radio.transform.1.en" },
        { hear: true, p: "js.radio.transform.into", sl: "Luka pase ovce.", en: "js.radio.transform.2.en" },
        { p: "js.radio.transform.say", sl: "Luka je pasel ovce.", en: "js.radio.transform.3.en" },
        { hear: true, p: "js.radio.transform.into", sl: "Tone kuje podkev.", en: "js.radio.transform.4.en" },
        { p: "js.radio.transform.say", sl: "Tone je koval podkev.", en: "js.radio.transform.5.en" },
      ],
    },
  };
  const radio = $("#radio");
  if (radio) {
    const rk = $("#radio-kind"), rp = $("#radio-prompt"), rs = $("#radio-sl"), rf = $("#radio-fill"), play = $("#radio-play");
    let drill = DRILLS.build, si = -1, playing = false, timer = 0, phase = "";
    const WAIT = 3200, HOLD = 2600;
    const clear = () => { clearTimeout(timer); timer = 0; };
    function fillBar(ms) {
      rf.style.transition = "none"; rf.style.width = "0";
      if (ms && !reduce.matches) { void rf.offsetWidth; rf.style.transition = `width ${ms}ms linear`; rf.style.width = "100%"; }
    }
    function prompt() {
      if (si < 0) { rp.textContent = phase === "end" ? t("js.radio.end") : t("js.radio.start"); return; }
      const s = drill.steps[si];
      const revealed = phase !== "say";
      rp.innerHTML = esc(t(s.p)) + (revealed && s.en && lang !== "sl" ? ` <span class="radio-tr">· ${esc(t(s.en))}</span>` : "")
        + (phase === "say" && playing ? ` <span class="radio-tr">· ${esc(t("js.radio.say"))}</span>` : "");
    }
    function show(i, reveal) {
      si = i;
      const s = drill.steps[i];
      const hidden = !s.hear && !reveal;
      rs.textContent = s.sl;
      rs.classList.toggle("hide", hidden);
      rs.setAttribute("aria-hidden", String(hidden));
      phase = hidden ? "say" : "heard";
      prompt();
    }
    function step() {
      clear();
      if (phase === "say") { show(si, true); fillBar(0); }
      else if (si < drill.steps.length - 1) { show(si + 1, false); }
      else { si = -1; phase = "end"; rs.textContent = ""; setPlaying(false); prompt(); fillBar(0); return; }
      if (playing && !reduce.matches) {
        if (phase === "say") { fillBar(WAIT); prompt(); timer = setTimeout(step, WAIT); }
        else { fillBar(0); timer = setTimeout(step, HOLD); }
      }
    }
    function setPlaying(on) {
      playing = on;
      radio.classList.toggle("playing", on);
      play.textContent = on ? "⏸" : "▶";
      play.setAttribute("aria-label", t(on ? "js.radio.pause" : "js.radio.play"));
      if (!on) { clear(); fillBar(0); }
    }
    play.addEventListener("click", () => {
      if (reduce.matches) { step(); return; }
      if (playing) { setPlaying(false); prompt(); return; }
      setPlaying(true);
      step();
    });
    $("#radio-next").addEventListener("click", () => { clear(); if (phase === "say") show(si, true); step(); });
    $("#radio-prev").addEventListener("click", () => { clear(); fillBar(0); show(Math.max(0, si - 1), true); if (playing) timer = setTimeout(step, HOLD); });
    $$(".chip-btn", radio).forEach((b) => b.addEventListener("click", () => {
      $$(".chip-btn", radio).forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
      setPlaying(false);
      drill = DRILLS[b.dataset.drill];
      rk.textContent = t(drill.kind);
      si = -1; phase = "";
      rs.textContent = "";
      prompt();
    }));
    const redo = () => { rk.textContent = t(drill.kind); play.setAttribute("aria-label", t(playing ? "js.radio.pause" : "js.radio.play")); prompt(); };
    redo();
    rerender.push(redo);
  }

  /* ------------------------------------------------------------------ the four villages */
  const vtabs = $$(".vtab");
  let villages = null, village = store.get("lani.village") || "primorska", gender = store.get("lani.gender") === "f" ? "f" : "m";
  // {learner} is the learner's name, {m:…|f:…} the form for their gender (the app's own rule)
  const render = (s) => String(s || "").replace(/\{learner\}/g, gender === "f" ? "Ana" : "Jan").replace(/\{m:([^|}]*)\|f:([^}]*)\}/g, (m, a, b) => (gender === "f" ? b : a));
  const other = (v) => (lang !== v.lang ? lang : null); // the language to explain the village's in, none when it's the same
  function drawVillage() {
    if (!villages) return;
    const v = villages.villages.find((x) => x.id === village) || villages.villages[0];
    vtabs.forEach((tb) => { const on = tb.dataset.village === v.id; tb.setAttribute("aria-selected", String(on)); tb.tabIndex = on ? 0 : -1; });
    const vp = $("#vpanel");
    vp.setAttribute("aria-labelledby", `vtab-${v.id}`);
    vp.dataset.village = v.id;
    const o = other(v);
    const L = (txt) => `<span lang="${v.lang}">${esc(render(txt))}</span>`;
    const tr = (obj) => (o && obj[o] ? esc(render(obj[o])) : "");
    $("#v-region").innerHTML = `${v.flag} ${esc(v.region[lang] || v.region.en)} <small class="muted" lang="${v.lang}">${esc(v.village)}</small>`;
    $("#v-about").textContent = t(`js.v.${v.id}.about`);
    $("#v-teller").innerHTML = `${esc(v.teller.name)} · ${esc(v.teller.role[lang] || v.teller.role.en)}`;
    $("#v-line").innerHTML = L(v.teller.line[v.lang]);
    $("#v-line-tr").innerHTML = tr(v.teller.line);
    $("#v-story").innerHTML = `📖 ${L(v.story.title[v.lang])}${o && v.story.title[o] && v.story.title[o] !== v.story.title[v.lang] ? ` <span class="muted">· ${esc(v.story.title[o])}</span>` : ""}`;
    $("#v-story-lines").innerHTML = v.story.lines.map((l) => `<li>${L(l[v.lang])}${o ? `<i>${tr(l)}</i>` : ""}</li>`).join("");
    const w = v.word, wl = w[v.lang] || w.id;
    const g = w.gender ? t(`js.v.word.gender.${w.gender}`) : "";
    const ex = w[`example_${v.lang}`], exo = o ? w[`example_${o}`] : null;
    const note = lang === "en" ? w.note : w[`note_${lang}`];
    $("#v-word").innerHTML = `<p class="vcard-k">${w.emoji} ${esc(t("js.v.word.title"))}</p>
      <p class="vword-head"><span class="vword-word" lang="${v.lang}">${esc(wl)}</span>${o && w[o] ? ` <span class="muted">· ${esc(w[o])}</span>` : ""}</p>
      <p class="wc-gram">${g ? esc(g) + " · " : ""}${esc(t("js.v.word.plural"))}: <span lang="${v.lang}">${esc(w.plural || "")}</span></p>
      ${ex ? `<p class="vword-ex"><span lang="${v.lang}">${esc(ex)}</span>${exo ? ` <i>${esc(exo)}</i>` : ""}</p>` : ""}
      ${note ? `<p class="muted small">${esc(note)}</p>` : ""}`;
    const img = $("#v-shot");
    img.src = v.shot;
    img.alt = fill(t("js.v.shot.alt"), { name: v.granny, village: v.region[lang] || v.region.en, happening: v.happening[lang] || v.happening.en });
    $("#v-shot-cap").innerHTML = `${L(v.happening[v.lang])}${o && v.happening[o] ? ` <span class="gloss-any">· ${esc(v.happening[o])}</span>` : ""}`;
    // the app's labels in this pair: the village's language, explained in the site's (or English, or Slovene)
    const base = o || (v.lang === "en" ? "sl" : "en");
    $("#labels").innerHTML = Object.values(villages.labels).map((l) => `<li><span lang="${v.lang}">${esc(l[v.lang])}</span> · <span lang="${base}">${esc(l[base])}</span></li>`).join("");
    const names = { en: "English", sl: "slovenščina", de: "Deutsch", it: "italiano" };
    $("#labels-note").textContent = fill(t("js.v.labels.note"), { target: names[v.lang], base: names[base] });
    $$(".learner-btn").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.gender === gender)));
  }
  function selectVillage(tab, focus) {
    village = tab.dataset.village;
    store.set("lani.village", village);
    drawVillage();
    if (focus) tab.focus();
  }
  vtabs.forEach((tb) => tb.addEventListener("click", () => selectVillage(tb)));
  tabKeys(vtabs, selectVillage);
  const lbtns = $$(".learner-btn");
  lbtns.forEach((b) => b.addEventListener("click", () => { gender = b.dataset.gender; store.set("lani.gender", gender); drawVillage(); }));
  fetch("villages.json").then((r) => r.json()).then((d) => { villages = d; drawVillage(); }).catch(() => {});
  rerender.push(drawVillage);

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
    const was = b.innerHTML;
    try { await navigator.clipboard.writeText(text); b.textContent = t("js.copy.done"); }
    catch { b.textContent = t("js.copy.fail"); }
    setTimeout(() => { b.innerHTML = was; }, 1600);
  }));

  /* ------------------------------------------------------------------ the menu closes on a pick */
  $$(".menu a").forEach((a) => a.addEventListener("click", () => { $(".menu").open = false; }));

  /* ------------------------------------------------------------------ start in the language chosen before the paint */
  if (lang !== "en") setLang(lang, false);
  else { $$(".lang-btn").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.lang === "en"))); const sel = $("#lang-select"); if (sel) sel.value = "en"; }
})();
