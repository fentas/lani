// Fits each unit of a printed sheet (companion/COURSE.md, "Printed sheets") to the page before it prints: its one-page
// layout if that fits (as it is, with the table beside the rule, then tighter, without the optional parts, tightest), else
// its two-page one the same way. A unit that fits on neither gets a third page saying so, which the generator's page
// count catches. Then the pages get their numbers and the contents its page numbers. What was chosen is written into
// #fit-report (the generator reads it with --dump-dom).
;(async function () {
  if (document.fonts && document.fonts.ready) await document.fonts.ready
  const MM = 96 / 25.4
  const booklet = document.documentElement.dataset.mode === 'booklet'
  // a page's content past its bottom or right edge; a millimetre of slack for rounding (the answers turned upside down
  // round their box up a pixel or two), the footer's margin keeps it off the footer
  const overflows = layout =>
    [...layout.querySelectorAll('.sheet > .body')].some(b => b.scrollHeight > b.clientHeight + MM || b.scrollWidth > b.clientWidth + MM)
  const report = []

  for (const unit of document.querySelectorAll('.unit')) {
    const one = unit.querySelector('.layout.one')
    const two = unit.querySelector('.layout.two')
    const setDensity = d => {
      unit.classList.toggle('d1', d >= 1)
      unit.classList.toggle('d2', d >= 2)
      unit.classList.toggle('d3', d >= 3)
    }
    const setSide = (layout, side) => layout.querySelectorAll('.top').forEach(t => t.classList.toggle('side', side))
    /** Whether the tables are narrow enough to stand beside the rule (half the page at most). */
    const canSide = layout => {
      const top = layout.querySelector('.top')
      if (!top || !top.querySelector('.rule') || !top.querySelector('.tbl')) return false
      setSide(layout, false)
      const widest = Math.max(...[...top.querySelectorAll('.tbl')].map(t => t.querySelector('table').getBoundingClientRect().width))
      return widest <= top.getBoundingClientRect().width * 0.5
    }
    // how far the one page runs over at its tightest, for the report
    let over = 0
    let chosen = null
    for (const [layout, other, name] of [
      [one, two, 'one'],
      [two, one, 'two'],
    ]) {
      layout.hidden = false
      other.hidden = true
      setDensity(0)
      const side = canSide(layout)
      for (let d = 0; d <= 3 && !chosen; d++) {
        setDensity(d)
        for (const s of side ? [false, true] : [false]) {
          setSide(layout, s)
          if (!overflows(layout)) {
            chosen = { layout: name, density: d, side: s }
            break
          }
          if (name === 'one' && d === 3) {
            const mm = Math.max(...[...layout.querySelectorAll('.sheet > .body')].map(b => b.scrollHeight - b.clientHeight)) / MM
            over = over ? Math.min(over, mm) : mm
          }
        }
      }
      if (chosen) {
        other.remove()
        break
      }
    }
    if (!chosen) {
      one.remove()
      setDensity(3)
      const sheet = document.createElement('div')
      sheet.className = 'sheet overflow'
      sheet.innerHTML = `<div class="body">⚠ ${unit.dataset.unit}: more than two pages</div>`
      two.appendChild(sheet)
    }
    // the room left becomes ruled lines to write in, where there is enough of it
    for (const n of unit.querySelectorAll('.notes')) if (n.getBoundingClientRect().height < 13 * MM) n.classList.add('empty')
    const sheets = unit.querySelectorAll('.sheet')
    if (!booklet && sheets.length > 1) sheets.forEach((s, i) => {
      const pn = s.querySelector('.pn')
      if (pn) pn.textContent = `${i + 1}/${sheets.length}`
    })
    report.push({ unit: unit.dataset.unit, pages: sheets.length, fits: !!chosen, ...(chosen ?? {}), ...(over ? { over: Math.round(over) } : {}) })
  }

  if (booklet) {
    const all = [...document.querySelectorAll('.sheet')]
    all.forEach((s, i) => {
      const pn = s.querySelector('.pn')
      if (pn && i > 0) pn.textContent = String(i + 1)
    })
    for (const p of document.querySelectorAll('.t-p[data-unit]')) {
      const first = document.querySelector(`.unit[data-unit="${p.dataset.unit}"] .sheet`)
      if (first) p.textContent = String(all.indexOf(first) + 1)
    }
  }
  document.getElementById('fit-report').textContent = JSON.stringify(report)
  document.documentElement.dataset.fitted = 'yes'
})()
