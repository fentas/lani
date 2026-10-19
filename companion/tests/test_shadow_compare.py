"""Tests for companion/bin/shadow-compare. Run: python3 -m unittest discover -s companion/tests

The exports here are made up (lani.shadow-export/v0 as the app writes it): an older upload without lanes and a newer
one with the matrix, overlapping on a day.
"""
from __future__ import annotations

import contextlib
import importlib.machinery
import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "shadow-compare"
_loader = importlib.machinery.SourceFileLoader("shadow_compare", str(_path))
_spec = importlib.util.spec_from_loader("shadow_compare", _loader)
sc = importlib.util.module_from_spec(_spec)
sys.modules["shadow_compare"] = sc  # dataclasses look their module up
_loader.exec_module(sc)

L1 = "claude-haiku-5-5@low"  # replies as text
L2 = "claude-sonnet-5-5@high"  # replies by tool, fails a turn
ALSO = "also:claude-opus-5-5@high"
NO_TURN = "— no turn (the lane was off or skipped then)"


def ms(y, mo, d, h, mi=0, s=0) -> int:
    return int(datetime(y, mo, d, h, mi, s, tzinfo=timezone.utc).timestamp() * 1000)


T18, T17, T16 = ms(2026, 10, 18, 10), ms(2026, 10, 17, 10), ms(2026, 10, 16, 10)


def event(id, kind, at, text, **kw):
    handling = "AT_ONCE" if kind in ("chat", "answer", "session_end") else "DIGEST"
    return {"id": id, "kind": kind, "at": at, "path": f"/{kind}", "text": text, "handling": handling, **kw}


def call(id, turn, cost, first, total, task="CHAT", model="claude-sonnet-5-5", stop="end_turn", **kw):
    c = {"id": id, "turnId": turn, "model": model, "eventKind": "chat", "task": task, "at": T18,
         "usage": {"input": 10, "cacheWrite5m": 0, "cacheWrite1h": 0, "cacheRead": 0, "output": 5},
         "totalMs": total, "threadAfter": 0, "thread": "day", "effortByMessage": False, "images": 0, **kw}
    if cost is not None:
        c["cost"] = cost
    if first is not None:
        c["firstTokenMs"] = first
    if stop is not None:
        c["stopReason"] = stop
    return c


def tool(name, kind, input, **kw):
    return {"id": "tc-" + name, "name": name, "input": input, "kind": kind, "result": "ok", "isError": False,
            "dryRun": False, "errors": [], "at": 0, "ms": 1, **kw}


def turn(id, events, kind, task, model, event_kind, calls, replies=(), tools=(), status="DONE", **kw):
    return {"id": id, "eventIds": list(events), "kind": kind, "task": task, "model": model, "eventKind": event_kind,
            "queuedAt": T18, "endedAt": T18, "status": status, "replies": list(replies), "toolCalls": list(tools),
            "callIds": [c["id"] for c in calls], "text": "", "attempts": 1, "notBefore": 0, "replyFromText": False, **kw}


def reply(text, at, conv="c1"):
    return {"conversationId": conv, "text": text, "at": at}


def real(text, at, event_id=None):
    r = {"reply": reply(text, at)}
    if event_id:
        r["eventId"] = event_id
    return r


def day(name, events, turns, calls, reals, lanes=None):
    d = {"day": name, "stats": {"calls": len(calls)},
         "log": {"day": name, "events": events, "turns": turns, "calls": calls, "real": reals, "guide": {}, "restarts": []}}
    if lanes is not None:
        d["lanes"] = {k: {"stats": {}, "log": {"day": name, "turns": t, "calls": c, "guide": {}, "restarts": []}}
                      for k, (t, c) in lanes.items()}
    return d


def export(exported_at, days, matrix=None):
    doc = {"schema": "lani.shadow-export/v0", "exported_at": exported_at, "app": "test", "base_url": "http://mock",
           "tools": ["reply", "guide"], "prefix_chars": 100,
           "settings": {"enabled": True, "preset": "BALANCED", "budgetPerDay": 3.0}, "notebook": {"text": ""},
           "queue": [], "days": days}
    if matrix is not None:
        doc["matrix"] = matrix
    return doc


CHAT_REPLY = "Vilica.\n\n# fork\n- a list item"


def day18():
    """A chat (real, primary, an "also run on", both lanes), an answer graded, a digest of two events, a closing."""
    ev = [
        event("e_chat", "chat", T18, "Kako se reče fork?", conversationId="c1"),
        event("e_ans", "answer", T18 + 60_000, "vilica → vilico?", data={"correct": False}),
        event("e_rev", "review_done", T18 + 120_000, "12 reviews"),
        event("e_words", "words_added", T18 + 180_000, "3 words"),
    ]
    cp = [call("c_p1", "t_chat", 0.01, 800, 2000, stop="tool_use"), call("c_p2", "t_chat", 0.02, 1200, 3000)]
    ca = [call("c_a1", "t_also", 0.05, 1500, 4000, model="claude-opus-5-5", thread="also", effort="high")]
    cg = [call("c_g1", "t_grade", 0.004, 600, 1500, task="GRADING", model="claude-haiku-5-5")]
    cd = [call("c_d1", "t_dig", 0.003, 500, 1000, task="ROUTINE", model="claude-haiku-5-5")]
    cc = [call("c_c1", "t_close", 0.006, 900, 2500, thread="closing")]
    turns = [
        turn("t_chat", ["e_chat"], "DAY", "CHAT", "claude-sonnet-5-5", "chat", cp, effort="medium",
             replies=[reply(CHAT_REPLY, T18 + 5000)],
             tools=[tool("guide", "local", {"topic": "cases"}), tool("dictionary_lookup", "read", {"word": "vilica"}),
                    tool("reply", "reply", {"conversation_id": "c1", "text": "SECRET_REPLY_TEXT_IN_INPUT"})]),
        turn("t_also", ["e_chat"], "ALSO", "CHAT", "claude-opus-5-5", "chat", ca, effort="high",
             replies=[reply("Vilica (also).", T18 + 9000)], tools=[tool("reply", "reply", {"conversation_id": "c1"})]),
        turn("t_grade", ["e_ans"], "STATELESS", "GRADING", "claude-haiku-5-5", "answer", cg, effort="high",
             replies=[reply("Almost: vilico.", T18 + 63_000)],
             tools=[tool("record_session", "write", {"session_id": "s1", "date": "2026-10-18"}, dryRun=True),
                    tool("notebook_note", "local", {"text": "struggles with the accusative"})]),
        turn("t_dig", ["e_rev", "e_words"], "STATELESS", "ROUTINE", "claude-haiku-5-5", "digest", cd, effort="high",
             replies=[reply("Lepo!", T18 + 400_000)], why="the evening's digest"),
        turn("t_close", [], "CLOSING", "CHAT", "claude-sonnet-5-5", "closing", cc,
             tools=[tool("notebook_write", "local", {"text": "notebook"})]),
    ]
    reals = [real("Vilica je fork.", T18 + 3000, "e_chat"), real("Skoraj: vilico.", T18 + 62_000, "e_ans"),
             real("A reply to nothing known.", T18 + 1000)]
    l1c = [call("l1c1", "l1_chat", 0.001, 300, 700, model="claude-haiku-5-5", lane=L1),
           call("l1c2", "l1_grade", 0.001, 200, 600, task="GRADING", model="claude-haiku-5-5", lane=L1),
           call("l1c3", "l1_dig", 0.001, 250, 500, task="ROUTINE", model="claude-haiku-5-5", lane=L1)]
    l1t = [
        turn("l1_chat", ["e_chat"], "DAY", "CHAT", "claude-haiku-5-5", "chat", l1c[:1], effort="low", lane=L1,
             replies=[reply("Vilica, as text.", T18 + 4000)], replyFromText=True),
        turn("l1_grade", ["e_ans"], "DAY", "GRADING", "claude-haiku-5-5", "answer", l1c[1:2], effort="low", lane=L1,
             replies=[reply("L1 grade.", T18 + 61_000)], tools=[tool("reply", "reply", {"conversation_id": "c1", "text": "L1 grade."})]),
        turn("l1_dig", ["e_rev", "e_words"], "DAY", "ROUTINE", "claude-haiku-5-5", "digest", l1c[2:], effort="low", lane=L1),
    ]
    l2c = [call("l2c1", "l2_chat", 0.02, 1000, 2500, lane=L2),
           call("l2c2", "l2_grade", None, None, 100, task="GRADING", stop=None, error="overloaded", lane=L2)]
    l2t = [
        turn("l2_chat", ["e_chat"], "DAY", "CHAT", "claude-sonnet-5-5", "chat", l2c[:1], effort="high", lane=L2,
             replies=[reply("Vilica (L2).", T18 + 6000)],
             tools=[tool("guide", "local", {"topic": "cases"}), tool("reply", "reply", {"conversation_id": "c1", "text": "Vilica (L2)."})]),
        turn("l2_grade", ["e_ans"], "DAY", "GRADING", "claude-sonnet-5-5", "answer", l2c[1:], effort="high", lane=L2,
             status="FAILED", error="HTTP 529 overloaded"),
    ]
    return day("2026-10-18", ev, turns, cp + ca + cg + cd + cc, reals, lanes={L1: (l1t, l1c), L2: (l2t, l2c)})


def day17(copy: str, cost: float, lanes: bool):
    ev = [event("e_old", "chat", T17, "Dober dan!", conversationId="c1")]
    c = [call("c17", "t17", cost, 700, 1800)]
    t = [turn("t17", ["e_old"], "DAY", "CHAT", "claude-sonnet-5-5", "chat", c, effort="medium",
              replies=[reply(f"{copy} reply", T17 + 4000)], tools=[tool("reply", "reply", {"conversation_id": "c1"})])]
    return day("2026-10-17", ev, t, c, [real(f"{copy} real", T17 + 10_000, "e_old")], lanes={} if lanes else None)


def day16():
    """The old format: no lanes. A grading cut at max_tokens, a digest skipped without a call."""
    ev = [event("e16", "session_end", T16, "Session: 20 min"), event("e16w", "words_added", T16 + 1000, "2 words")]
    c = [call("c16", "t16", 0.002, 400, 900, task="GRADING", model="claude-haiku-5-5", stop="max_tokens")]
    t = [turn("t16", ["e16"], "STATELESS", "GRADING", "claude-haiku-5-5", "session_end", c, status="TRUNCATED",
              replies=[reply("Session graded.", T16 + 20_000)]),
         turn("t16s", ["e16w"], "STATELESS", "ROUTINE", "", "digest", [], status="SKIPPED", why="nothing worth saying: no call")]
    return day("2026-10-16", ev, t, c, [real("Good session!", T16 + 30_000, "e16")])


MATRIX = {"settings": {"enabled": True, "lanes": [{"family": "HAIKU", "effort": "low"}], "budgetPerDay": 1.0},
          "lanes": [{"id": L1, "family": "HAIKU", "effort": "low", "model": "claude-haiku-5-5"},
                    {"id": L2, "family": "SONNET", "effort": "high", "model": "claude-sonnet-5-5"},
                    {"family": "OPUS", "effort": "max", "skipped": "the key has no access"}]}


class ShadowCompareTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.dev = self.root / "data" / "app" / "dev"
        self.dev.mkdir(parents=True)
        # the older upload (no lanes, no matrix) and the newer one (lanes), overlapping on 2026-10-17
        self.old = self.write("shadow-2026-10-17.json", export("2026-10-17T20:00:00Z", [day16(), day17("OLD COPY", 0.5, False)]))
        self.new = self.write("shadow-2026-10-18.json", export("2026-10-18T21:00:00.500Z", [day17("NEW COPY", 0.01, True), day18()], MATRIX))

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name, doc) -> Path:
        p = self.dev / name
        p.write_text(json.dumps(doc) if not isinstance(doc, str) else doc)
        return p

    def run_main(self, *args) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = sc.main(list(args))
        return code, out.getvalue(), err.getvalue()

    def table(self, md: str) -> dict[str, dict[str, str]]:
        """The first summary table: column name -> {header: value}."""
        lines = md.splitlines()
        start = next(i for i, l in enumerate(lines) if l.startswith("| column |"))
        head = [h.strip() for h in lines[start].strip("|").split("|")]
        rows = {}
        for l in lines[start + 2:]:
            if not l.startswith("|"):
                break
            cells = [c.strip() for c in l.strip("|").split("|")]
            rows[cells[0]] = dict(zip(head, cells))
        return rows

    # --- loading -------------------------------------------------------------------------------

    def test_newest_upload_wins_each_day(self):
        for order in ([self.old, self.new], [self.new, self.old]):
            data = sc.merge(sc.load(order))
            self.assertEqual(sorted(data.days), ["2026-10-16", "2026-10-17", "2026-10-18"])
            self.assertEqual(data.source["2026-10-17"].path, self.new)
            self.assertEqual(data.source["2026-10-16"].path, self.old)
            self.assertIn("lanes", data.days["2026-10-17"])
        code, out, _ = self.run_main(str(self.new), str(self.old))
        self.assertEqual(code, 0)
        self.assertIn("> NEW COPY reply", out)
        self.assertIn("> NEW COPY real", out)
        self.assertNotIn("OLD COPY", out)

    def test_instants_order_by_time_not_text(self):
        # Java's Instant.toString leaves the fraction out at a whole second: "…:10Z" is after "…:09.999Z"
        self.assertGreater(sc.parse_instant("2026-10-18T17:31:10Z"), sc.parse_instant("2026-10-18T17:31:09.999Z"))
        self.assertGreater(sc.parse_instant("2026-10-18T17:31:10.5Z"), sc.parse_instant("2026-10-18T17:31:10Z"))
        self.assertIsNone(sc.parse_instant("yesterday"))

    def test_bad_files_are_left_out_with_a_warning(self):
        self.write("shadow-broken.json", "{not json")
        self.write("shadow-other.json", {"schema": "something/else"})
        code, out, err = self.run_main(str(self.dev))
        self.assertEqual(code, 0)
        self.assertIn("skipped", err)
        self.assertIn("shadow-broken.json", err)
        self.assertIn("shadow-other.json", err)
        self.assertEqual(self.table(out)["primary"]["calls"], "7")

    def test_default_is_the_learners_dev_uploads(self):
        saved = os.environ.get("LANI_DATA_DIR")
        os.environ["LANI_DATA_DIR"] = str(self.root / "data")
        try:
            code, out, _ = self.run_main()
        finally:
            if saved is None:
                os.environ.pop("LANI_DATA_DIR")
            else:
                os.environ["LANI_DATA_DIR"] = saved
        self.assertEqual(code, 0)
        self.assertIn("`shadow-2026-10-18.json`", out)
        os.environ["LANI_DATA_DIR"] = str(self.root / "nowhere")
        try:
            code, _, err = self.run_main()
        finally:
            if saved is None:
                os.environ.pop("LANI_DATA_DIR")
            else:
                os.environ["LANI_DATA_DIR"] = saved
        self.assertEqual(code, 1)
        self.assertIn("no upload", err)

    # --- the summary ---------------------------------------------------------------------------

    def test_summary_numbers(self):
        _, out, _ = self.run_main(str(self.dev))
        t = self.table(out)
        self.assertEqual(list(t), ["real", "primary", L1, L2, ALSO])
        p = t["primary"]
        # closing's call and spend counted, its turn never a reply; the skipped digest made no call
        self.assertEqual((p["days"], p["turns"], p["events"], p["calls"]), ("3", "6", "6", "7"))
        self.assertEqual(p["spend/day"], "$0.0183")  # 0.055 over 3 days
        self.assertEqual(p["median first token (ms)"], "700")
        self.assertEqual(p["median total (ms)"], "1800")
        self.assertEqual(p["replies"], "5")
        self.assertEqual(p["from text"], "0/5 (0%)")
        self.assertEqual((p["errors"], p["refusals"], p["truncations"]), ("0", "0", "1"))
        self.assertEqual((p["guide"], p["dictionary_lookup"], p["writes"], p["notes"]), ("1", "1", "1", "2"))
        a = t[L1]
        self.assertEqual((a["days"], a["turns"], a["events"], a["calls"], a["spend/day"]), ("1", "3", "4", "3", "$0.0030"))
        self.assertEqual((a["median first token (ms)"], a["median total (ms)"]), ("250", "600"))
        self.assertEqual((a["replies"], a["from text"], a["errors"]), ("2", "1/2 (50%)", "0"))
        b = t[L2]
        # the failed turn: its call's error and the turn both count; its events weren't answered
        self.assertEqual((b["turns"], b["events"], b["calls"], b["spend/day"]), ("2", "1", "2", "$0.0200"))
        self.assertEqual((b["median first token (ms)"], b["median total (ms)"]), ("1000", "2500"))
        self.assertEqual((b["replies"], b["from text"], b["errors"], b["guide"]), ("1", "0/1 (0%)", "2", "1"))
        c = t[ALSO]
        self.assertEqual((c["calls"], c["spend/day"], c["replies"], c["median first token (ms)"]), ("1", "$0.0500", "1", "1500"))
        r = t["real"]
        self.assertEqual((r["days"], r["events"], r["replies"]), ("3", "4", "5"))
        self.assertEqual(r["median reply delay (s)"], "10.0")  # 2, 3, 10, 30 s: the upper middle
        self.assertEqual(r["calls"], "—")
        self.assertIn("skipped: the key has no access", out)
        self.assertIn("1 of the real tutor's replies matched no event", out)

    def test_an_old_export_alone(self):
        code, out, _ = self.run_main(str(self.old))
        self.assertEqual(code, 0)
        t = self.table(out)
        self.assertEqual(list(t), ["real", "primary"])
        self.assertEqual((t["primary"]["calls"], t["primary"]["truncations"]), ("2", "1"))
        self.assertNotIn(NO_TURN, out)
        self.assertIn("> OLD COPY reply", out)

    # --- the items -----------------------------------------------------------------------------

    def test_out_writes_a_file_per_task(self):
        out_dir = self.root / "report"
        code, out, _ = self.run_main("--out", str(out_dir), str(self.dev))
        self.assertEqual(code, 0)
        self.assertEqual(sorted(p.name for p in out_dir.iterdir()), ["chat.md", "grading.md", "routine.md", "summary.md"])
        self.assertIn("summary.md", out)
        summary = (out_dir / "summary.md").read_text()
        self.assertIn("| primary |", summary)
        chat = (out_dir / "chat.md").read_text()
        self.assertTrue(chat.startswith("# Chat\n"))
        self.assertIn(f"{sc.local(T18)} · chat · `e_chat`", chat)
        self.assertIn("> Kako se reče fork?", chat)
        self.assertIn("**real** · +3 s", chat)
        self.assertIn("> Vilica je fork.", chat)
        self.assertIn("**primary** · `claude-sonnet-5-5` · medium · DONE · 2 calls · $0.0300 · first token 800 ms · tool · +5 s", chat)
        self.assertIn(f"**{L1}** · `claude-haiku-5-5` · low · DONE · 1 call · $0.0010 · first token 300 ms · as text", chat)
        self.assertIn("> Vilica, as text.", chat)
        self.assertIn(f"**{L2}** · `claude-sonnet-5-5` · high · DONE · 1 call · $0.0200 · first token 1000 ms · tool", chat)
        self.assertIn(f"**{ALSO}** · `claude-opus-5-5` · high · DONE", chat)
        self.assertIn("> Vilica (also).", chat)
        # tool calls in brief: reply's text left out
        self.assertIn('- **guide** `topic="cases"`', chat)
        self.assertIn('- **reply** `conversation_id="c1"`', chat)
        self.assertNotIn("SECRET_REPLY_TEXT_IN_INPUT", chat)
        # the closing is no item
        self.assertNotIn("closing", chat)
        self.assertNotIn("notebook_write", chat)
        # the old day's chat: the lanes had no turn
        self.assertIn(f"**{L1}** {NO_TURN}", chat)

        grading = (out_dir / "grading.md").read_text()
        self.assertIn("· answer · `e_ans`", grading)
        self.assertIn('"correct":false', grading)
        self.assertIn('- **record_session** `session_id="s1", date="2026-10-18"` · dry run', grading)
        self.assertIn(f"**{L2}** · `claude-sonnet-5-5` · high · FAILED · 1 call · $— · none · error: `HTTP 529 overloaded`", grading)
        self.assertIn("**primary** · `claude-haiku-5-5` · — · TRUNCATED · stateless · 1 call · $0.0020", grading)
        self.assertIn("· session_end · `e16`", grading)
        self.assertNotIn(f"| {ALSO} |", grading)  # the chat's "also run on" has no row here

        routine = (out_dir / "routine.md").read_text()
        self.assertIn("digest of 2 events", routine)
        self.assertIn("review_done · `e_rev`: 12 reviews", routine)
        self.assertIn("words_added · `e_words`: 3 words", routine)
        self.assertIn("**real** — no reply matched", routine)
        self.assertIn("> Lepo!", routine)
        self.assertIn(f"**{L1}** · `claude-haiku-5-5` · low · DONE · 1 call · $0.0010 · first token 250 ms · none", routine)
        self.assertIn(f"**{L2}** {NO_TURN}", routine)
        self.assertIn("SKIPPED · stateless · no call", routine)
        # oldest first
        self.assertLess(routine.index("## 2026-10-16"), routine.index("## 2026-10-18"))

    def test_replies_are_whole_blockquotes(self):
        _, out, _ = self.run_main(str(self.dev))
        self.assertIn("> Vilica.\n>\n> # fork\n> - a list item", out)
        self.assertNotIn("\n# fork", out)
        self.assertNotIn("\n- a list item", out)

    def test_task_filter(self):
        out_dir = self.root / "graded"
        self.run_main("--out", str(out_dir), "--task", "grading", str(self.dev))
        self.assertEqual(sorted(p.name for p in out_dir.iterdir()), ["grading.md", "summary.md"])
        t = self.table((out_dir / "summary.md").read_text())
        self.assertEqual(t["primary"]["calls"], "2")  # c_g1, c16
        self.assertEqual(t["real"]["replies"], "2")  # to e_ans and e16
        _, out, _ = self.run_main("--task", "chat,routine", str(self.dev))
        self.assertIn("## Chat", out)
        self.assertIn("## Routine", out)
        self.assertNotIn("## Grading", out)
        with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
            sc.main(["--task", "nope", str(self.dev)])

    def test_lanes_filter(self):
        _, out, _ = self.run_main("--lanes", "haiku", str(self.dev))
        t = self.table(out)
        self.assertEqual(list(t), ["real", "primary", L1])
        self.assertNotIn(f"**{L2}**", out)
        self.assertNotIn(ALSO, out)
        _, out, _ = self.run_main("--lanes", "sonnet-5-5@high", "--lanes", "opus", str(self.dev))
        self.assertEqual(list(self.table(out)), ["real", "primary", L2, ALSO])

    def test_day_filter(self):
        _, out, err = self.run_main("--day", "2026-10-17", str(self.dev))
        t = self.table(out)
        self.assertEqual((t["primary"]["days"], t["primary"]["calls"], t["real"]["replies"]), ("1", "1", "1"))
        self.assertIn("> NEW COPY reply", out)
        self.assertNotIn("e_chat", out)
        self.assertNotIn("e16", out)
        _, out, err = self.run_main("--day", "2026-10-16,2026-10-01", str(self.dev))
        self.assertIn("no day 2026-10-01", err)
        self.assertIn("`e16`", out)
        self.assertNotIn("`e_old`", out)

    # --- other providers ---------------------------------------------------------------------------

    def test_openai_lanes_and_the_plans_api_equivalent(self):
        plan_lane, key_lane = "openai:gpt-6.1-sol@medium", "openai:gpt-6-luna@high"
        ev = [event("e_x", "chat", T18, "Živjo", conversationId="c1")]
        cp = [call("c_x", "t_x", 0.01, 800, 2000)]
        tp = [turn("t_x", ["e_x"], "DAY", "CHAT", "claude-sonnet-5-5", "chat", cp, effort="medium", replies=[reply("Živjo!", T18 + 3000)])]
        pc = [call("p1", "p_t", 0.0, 900, 2100, model="gpt-6.1-sol", lane=plan_lane, provider="openai", billing="chatgpt_plan", apiEquivalent=0.012, effort="medium"),
              call("p2", "p_t", 0.0, 400, 1200, model="gpt-6.1-sol", lane=plan_lane, provider="openai", billing="chatgpt_plan", apiEquivalent=0.004, effort="medium")]
        pt = [turn("p_t", ["e_x"], "DAY", "CHAT", "gpt-6.1-sol", "chat", pc, effort="medium", lane=plan_lane, replies=[reply("Živjo (Sol)!", T18 + 4000)])]
        kc = [call("k1", "k_t", 0.003, 500, 900, model="gpt-6-luna", lane=key_lane, provider="openai", billing="api_key", effort="high")]
        kt = [turn("k_t", ["e_x"], "DAY", "CHAT", "gpt-6-luna", "chat", kc, effort="high", lane=key_lane, replies=[reply("Živjo (Luna)!", T18 + 2500)])]
        matrix = {"settings": {"enabled": True}, "lanes": [
            {"id": key_lane, "provider": "openai", "family": "LUNA", "effort": "high", "model": "gpt-6-luna"},
            {"id": plan_lane, "provider": "openai", "family": "SOL", "effort": "medium", "model": "gpt-6.1-sol"},
            {"provider": "openai", "family": "ASTRA", "effort": "low", "skipped": "the OpenAI credential lists no astra model (gpt-6-astra)"}]}
        d = day("2026-10-18", ev, tp, cp, [real("Živjo, Jan.", T18 + 2000, "e_x")], lanes={plan_lane: (pt, pc), key_lane: (kt, kc)})
        dev = self.root / "openai"
        dev.mkdir()
        (dev / "shadow-2026-10-18.json").write_text(json.dumps(export("2026-10-18T21:00:00Z", [d], matrix)))
        code, out, _ = self.run_main(str(dev))
        self.assertEqual(code, 0)
        t = self.table(out)
        self.assertEqual(list(t), ["real", "primary", key_lane, plan_lane])
        self.assertEqual((t[plan_lane]["spend/day"], t[plan_lane]["≈API/day"], t[plan_lane]["calls"]), ("$0.0000", "$0.0160", "2"))
        self.assertEqual((t[key_lane]["spend/day"], t[key_lane]["≈API/day"]), ("$0.0030", "$0.0030"))
        self.assertEqual(t["primary"]["≈API/day"], "$0.0100")
        self.assertIn(f"**{plan_lane}** · `gpt-6.1-sol` · medium · DONE · 2 calls · $0.0000 ChatGPT plan (≈$0.0160 API) · first token 900 ms", out)
        self.assertIn(f"**{key_lane}** · `gpt-6-luna` · high · DONE · 1 call · $0.0030 · first token 500 ms", out)
        self.assertIn("skipped: the OpenAI credential lists no astra model", out)
        # --lanes by the provider
        _, out, _ = self.run_main("--lanes", "openai", str(dev))
        self.assertEqual(list(self.table(out)), ["real", "primary", key_lane, plan_lane])
        _, out, _ = self.run_main("--lanes", "luna", str(dev))
        self.assertEqual(list(self.table(out)), ["real", "primary", key_lane])

    def test_gemini_and_openrouter_lanes(self):
        flash, argon, routed = "gemini:gemini-3.8-flash@medium", "gemini:gemini-4-argon@high", "openrouter:google/gemini-4-argon@medium"
        ev = [event("e_g", "chat", T18, "Kako se reče stove?", conversationId="c1")]
        cp = [call("c_g", "t_g", 0.01, 800, 2000)]
        tp = [turn("t_g", ["e_g"], "DAY", "CHAT", "claude-sonnet-5-5", "chat", cp, effort="medium", replies=[reply("Peč.", T18 + 3000)])]
        fc = [call("f1", "f_t", 0.0, 600, 1500, model="gemini-3.8-flash", lane=flash, provider="gemini", billing="free_tier", apiEquivalent=0.002, effort="medium")]
        ft = [turn("f_t", ["e_g"], "DAY", "CHAT", "gemini-3.8-flash", "chat", fc, effort="medium", lane=flash, replies=[reply("Peč (Flash).", T18 + 2500)])]
        ac = [call("a1", "a_t", 0.03, 1900, 9000, model="gemini-4-argon", lane=argon, provider="gemini", billing="api_key", effort="high")]
        at = [turn("a_t", ["e_g"], "DAY", "CHAT", "gemini-4-argon", "chat", ac, effort="high", lane=argon, replies=[reply("Peč (Argon).", T18 + 9500)])]
        rc = [call("r1", "r_t", 0.0123, 700, 3000, model="google/gemini-4-argon", lane=routed, provider="openrouter", billing="openrouter_credits", effort="medium")]
        rt = [turn("r_t", ["e_g"], "DAY", "CHAT", "google/gemini-4-argon", "chat", rc, effort="medium", lane=routed, replies=[reply("Peč (OpenRouter).", T18 + 3500)])]
        matrix = {"settings": {"enabled": True}, "lanes": [
            {"id": flash, "provider": "gemini", "family": "FLASH", "effort": "medium", "model": "gemini-3.8-flash"},
            {"id": argon, "provider": "gemini", "family": "ARGON", "effort": "high", "model": "gemini-4-argon"},
            {"id": routed, "provider": "openrouter", "family": "ROUTED", "effort": "medium", "model": "google/gemini-4-argon"},
            {"provider": "gemini", "family": "ARGON", "effort": "low", "model": "gemini-4-argon", "skipped": "Gemini 4 Argon has no free tier (the key is on it)"}]}
        d = day("2026-10-19", ev, tp, cp, [real("Peč, Jan.", T18 + 2000, "e_g")], lanes={flash: (ft, fc), argon: (at, ac), routed: (rt, rc)})
        dev = self.root / "gemini"
        dev.mkdir()
        (dev / "shadow-2026-10-19.json").write_text(json.dumps(export("2026-10-19T21:00:00Z", [d], matrix)))
        code, out, _ = self.run_main(str(dev))
        self.assertEqual(code, 0)
        t = self.table(out)
        self.assertEqual(list(t), ["real", "primary", flash, argon, routed])
        # the free tier: $0 a day, its API-equivalent beside it; OpenRouter's lane at its reported cost
        self.assertEqual((t[flash]["spend/day"], t[flash]["≈API/day"]), ("$0.0000", "$0.0020"))
        self.assertEqual((t[argon]["spend/day"], t[argon]["≈API/day"]), ("$0.0300", "$0.0300"))
        self.assertEqual((t[routed]["spend/day"], t[routed]["≈API/day"]), ("$0.0123", "$0.0123"))
        self.assertIn(f"**{flash}** · `gemini-3.8-flash` · medium · DONE · 1 call · $0.0000 Gemini free tier (≈$0.0020 API) · first token 600 ms", out)
        self.assertIn(f"**{routed}** · `google/gemini-4-argon` · medium · DONE · 1 call · $0.0123 OpenRouter's price · first token 700 ms", out)
        self.assertIn("> Peč (OpenRouter).", out)
        self.assertIn("skipped: Gemini 4 Argon has no free tier", out)
        # --lanes by the provider, or by a part of an OpenRouter model's id
        _, out, _ = self.run_main("--lanes", "gemini:", str(dev))
        self.assertEqual(list(self.table(out)), ["real", "primary", flash, argon])
        _, out, _ = self.run_main("--lanes", "openrouter", str(dev))
        self.assertEqual(list(self.table(out)), ["real", "primary", routed])
        _, out, _ = self.run_main("--lanes", "google/", str(dev))
        self.assertEqual(list(self.table(out)), ["real", "primary", routed])


if __name__ == "__main__":
    unittest.main()
