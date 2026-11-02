"""Tests for companion/bin/qa's road_clips: what the road step has the dev bridge voice before a small library ("road:mini")
on a learner it has no clips of. Run: python3 -m unittest discover -s companion/tests

No emulator, no bridge: the dev bridge's answers are given here.
"""
from __future__ import annotations

import datetime
import importlib.machinery
import importlib.util
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
_path = Path(__file__).resolve().parents[1] / "bin" / "qa"
_loader = importlib.machinery.SourceFileLoader("qa_road", str(_path))
_spec = importlib.util.spec_from_loader("qa_road", _loader)
qa = importlib.util.module_from_spec(_spec)
sys.modules["qa_road"] = qa
_loader.exec_module(qa)

TODAY = datetime.date.today()


def day(n: int) -> str:
    return (TODAY + datetime.timedelta(days=n)).isoformat()


def line(sl: str | None = None, choices: list | None = None) -> dict:
    return {**({"sl": sl, "en": "…"} if sl else {}), "choices": choices or []}


def choice(sl: str, ok: bool, reply: str | None = None) -> dict:
    return {"sl": sl, "ok": ok, **({"reply": {"sl": reply}} if reply else {})}


class Bridge:
    """The dev bridge's answers, and what was asked of /voice/say; [engine]: whether it voices what it lacks."""

    def __init__(self, engine=True):
        self.engine = engine
        self.said: list[str] = []
        self.cards = {f"vocab_{i}": {"type": "vocabulary", "content": f"beseda{i}", "answer": "word", "due_date": day(i)} for i in range(6)}
        self.cards["err"] = {"type": "error_pattern", "content": "napaka", "due_date": day(0)}
        self.cards["later"] = {"type": "vocabulary", "content": "pozneje", "due_date": day(9)}

    def __call__(self, path: str, body=None, method=None):
        if path == "/state":
            return {"databases": {"spaced_repetition": {"items": self.cards}}}
        if path == "/packs":
            return [{"id": "praznik", "festival": "kres", "total": 9, "level": "A1"},
                    {"id": "b2", "total": 9, "learned": 0, "level": "B2"},
                    {"id": "kuhinja", "total": 9, "learned": 0, "level": "A1"},
                    {"id": "hisa", "total": 9, "learned": 2, "level": "A2"}]
        if path.startswith("/packs/"):
            pid = path.rsplit("/", 1)[1]
            words = [{"id": f"w{i}", "sl": f"{pid}{i}", "example_sl": f"Primer {pid}{i}."} for i in range(6)]
            return {"id": pid, "language": "sl", "words": words, "learned": ["w0"]}
        if path == "/scenes":
            long = {"id": "dolg", "lines": [line(f"Vrstica {i}.") for i in range(9)]}
            short = {"id": "kratek", "lines": [line("Dober dan!"), line(choices=[choice("Dober dan.", True, "Kako si?"), choice("Adijo.", False)])]}
            return [{"id": "it", "language": "it", "dialogs": [{"id": "x", "lines": [line("Ciao!")]}]},
                    {"id": "kuhinja", "language": "sl", "dialogs": [long, short]}]
        if path == "/stories":
            return [{"id": "zgodba", "language": "sl", "levels": {"A1": {"lines": [line("Nekoč je bil mlin."), line("{name}, poslušaj.")]}}}]
        if path == "/voice/say":
            self.said.append(body["text"])
            assert body["voice"] == "female"
            return {"url": "/voice/file/x.mp3"} if self.engine else None
        raise AssertionError(path)


class RoadClips(unittest.TestCase):
    def run_with(self, bridge: Bridge):
        qa.dev_bridge, qa.api = bridge, lambda path, **_: {"language": "sl"}
        return qa.road_clips()

    def test_a_small_library_s_texts_voiced_in_the_female_narrator_s_voice(self):
        b = Bridge()
        self.assertIsNone(self.run_with(b))
        # four at a time: in any order
        self.assertEqual({
            # four cards due within three days (no error pattern, none due later)
            "beseda0", "beseda1", "beseda2", "beseda3",
            # the pack the road teaches first: the one under way, four words not learned yet, each with its example
            "hisa1", "Primer hisa1.", "hisa2", "Primer hisa2.", "hisa3", "Primer hisa3.", "hisa4", "Primer hisa4.",
            # the shortest dialog of the village's language: its lines, the right choice and its reply, never the wrong one
            "Dober dan!", "Dober dan.", "Kako si?",
            # a story's evening at A1, a line said to the learner by name left out (said live)
            "Nekoč je bil mlin.",
        }, set(b.said))
        self.assertEqual(len(b.said), len(set(b.said)), "each text once")

    def test_at_most_45_texts_the_bridge_keeps_live_slots_for_the_app(self):
        b = Bridge()
        b.cards = {f"c{i}": {"type": "vocabulary", "content": f"c{i}", "due_date": day(0)} for i in range(4)}
        orig = b.__call__

        def big(path, body=None, method=None):
            if path == "/scenes":
                return [{"id": "s", "language": "sl", "dialogs": [{"id": "d", "lines": [line(f"V{i}.") for i in range(80)]}]}]
            return orig(path, body, method)

        qa.dev_bridge, qa.api = big, lambda path, **_: {"language": "sl"}
        self.assertIsNone(qa.road_clips())
        self.assertEqual(45, len(b.said))

    def test_no_engine_and_no_clips_the_step_skips_and_says_why(self):
        why = self.run_with(Bridge(engine=False))
        self.assertIn("LANI_DEV_FAKE_ELEVENLABS=1", why)


if __name__ == "__main__":
    unittest.main()
