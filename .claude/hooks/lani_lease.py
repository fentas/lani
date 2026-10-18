#!/usr/bin/env python3
"""
Who writes a learner's data repository: the lease (docs/plans/05-built/git-sync.md, "The lease").

A node and a phone (the app's hosted mode, plan 5) can share one data repository on GitHub. One side writes it at a
time: the one the lease names. The lease is a file in the repository, app/lease.json:

    {
      "schema": "lani.lease/v1",
      "holder": {"side": "node", "device": "jan-desktop", "name": "jan-desktop", "mode": "self-hosted"},
      "offer": null,
      "since": "2026-10-18T21:00:00Z",
      "renewed": "2026-10-18T21:00:00Z",
      "expires": "2026-11-01T21:00:00Z"
    }

- holder: who writes now. null while a handover waits for the other side (offer), or after a release.
- offer: the side the lease is handed over to ({side, device?, name?, mode}), by whom ("by") and when ("at"). That side
  may take the lease; the side that offered it may take it back.
- since: when the holder took it. renewed: its last renewal (at most once a day, in a commit of the holder's). expires:
  renewed + 14 days. A lease that ran out may be taken without a handover; it never lets anyone write by itself.

A repository without a lease is the node's, as it was before the lease existed. The phone takes it only when it is
offered to it, free or run out, or by a takeover the learner confirms (the old device is lost). The old side sees a
takeover at its next push, which the repository refuses: lani_data_repo.py then fetches the repository's lease into
.git/lani-lease-remote.json, and the node stops writing.

The same rules in the app: companion/android/app/src/main/java/si/lanisce/lani/sync/Lease.kt; in the bridge:
companion/bridge/src/lease.ts. Keep the three in step.

LANI_LEASE=ignore (the environment) lets this node write whatever the lease says: for an emergency only.
"""
from __future__ import annotations

import json
import os
import socket
from datetime import datetime, timedelta, timezone
from pathlib import Path

SCHEMA = "lani.lease/v1"
FILE = "app/lease.json"
# The repository's lease, as a push that was refused found it (in .git/: never committed).
REMOTE_NOTE = "lani-lease-remote.json"
TERM = timedelta(days=14)
RENEW_EVERY = timedelta(days=1)
SIDES = ("node", "phone")
MODES = {"node": "self-hosted", "phone": "hosted"}

# What a side may do with the lease, by its state (state()):
#   mine           it holds the lease: it writes
#   offered        handed over to it: it may take the lease
#   offered-away   it handed the lease over and the other side hasn't taken it yet: it may take it back
#   free           released, or no lease and no data yet: anyone may take it
#   expired        the holder didn't renew it in time: it may be taken without a handover
#   unclaimed      no lease, but data a node wrote (the phone's view): taken only by a confirmed takeover
#   other          another side or device holds it, or it is offered to another: taken only by a confirmed takeover
TAKEABLE = ("mine", "offered", "offered-away", "free", "expired")


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def iso(t: datetime) -> str:
    return t.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_time(s) -> datetime | None:
    if not isinstance(s, str):
        return None
    try:
        t = datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None
    return t if t.tzinfo else t.replace(tzinfo=timezone.utc)


def this_node() -> dict:
    """This node as a party of the lease: its host name (LANI_LEASE_DEVICE to name it otherwise)."""
    host = (os.environ.get("LANI_LEASE_DEVICE") or socket.gethostname() or "node").strip()[:64] or "node"
    return {"side": "node", "device": host, "name": host, "mode": MODES["node"]}


def _text(v, n: int) -> str:
    return "".join(c for c in v if c.isprintable()).strip()[:n] if isinstance(v, str) else ""


def party(p) -> dict | None:
    """A holder or an offer's side, cleaned; None when it isn't one."""
    if not isinstance(p, dict) or p.get("side") not in SIDES:
        return None
    out = {"side": p["side"], "device": _text(p.get("device"), 64), "name": _text(p.get("name"), 60),
           "mode": p.get("mode") if p.get("mode") in MODES.values() else MODES[p["side"]]}
    return out


def parse(text: str) -> dict:
    """A lease read from its file. One that can't be read is {"invalid": True}: held by nobody this side knows."""
    try:
        d = json.loads(text)
    except (ValueError, TypeError):
        return {"invalid": True}
    if not isinstance(d, dict) or not str(d.get("schema", "")).startswith("lani.lease/"):
        return {"invalid": True}
    out = {"schema": SCHEMA, "holder": party(d.get("holder")), "offer": None}
    o = d.get("offer")
    if isinstance(o, dict) and party(o):
        out["offer"] = {**party(o), "by": party(o.get("by")), "at": o.get("at") if parse_time(o.get("at")) else None}
    for k in ("since", "renewed", "expires"):
        out[k] = d[k] if parse_time(d.get(k)) else None
    return out


def dump(lease: dict) -> str:
    return json.dumps(lease, indent=2, ensure_ascii=False) + "\n"


def read_file(path: Path) -> dict | None:
    try:
        return parse(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        return None
    except OSError:
        return {"invalid": True}


def read(data: Path) -> dict | None:
    """The lease of the data directory [data]: app/lease.json, or None when it has none."""
    return read_file(Path(data) / FILE)


def effective(data: Path) -> dict | None:
    """The lease this node goes by: its own copy, unless a refused push found the repository's in another's hands."""
    local = read(data)
    note = read_file(Path(data) / ".git" / REMOTE_NOTE)
    if note is not None and state(note, this_node()) not in ("mine",):
        return {**note, "seen_remote": True}
    return local


def same(a: dict | None, me: dict) -> bool:
    """Whether [a] is [me]: the node by its side (a node moved to another machine is still the node), a phone by its
    device too."""
    return bool(a) and a.get("side") == me["side"] and (me["side"] == "node" or a.get("device") == me.get("device"))


def offered_to(o: dict | None, me: dict) -> bool:
    return bool(o) and o.get("side") == me["side"] and (not o.get("device") or me["side"] == "node" or o.get("device") == me.get("device"))


def expired(lease: dict, now: datetime | None = None) -> bool:
    t = parse_time(lease.get("expires"))
    return t is not None and (now or now_utc()) > t


def state(lease: dict | None, me: dict, now: datetime | None = None, has_data: bool = True) -> str:
    """What [lease] is for [me] (see TAKEABLE above). [has_data]: whether the repository holds a learner's data (the
    phone's view of a repository without a lease)."""
    if lease is None:
        return "mine" if me["side"] == "node" else ("unclaimed" if has_data else "free")
    if lease.get("invalid"):
        return "other"
    holder, offer = lease.get("holder"), lease.get("offer")
    if holder:
        if same(holder, me):
            return "mine"
        return "expired" if expired(lease, now) else "other"
    if offer:
        if offered_to(offer, me):
            return "offered"
        if same(offer.get("by"), me):
            return "offered-away"
        return "expired" if expired(lease, now) else "other"
    return "free"


def may_write(lease: dict | None, me: dict) -> bool:
    return state(lease, me) == "mine"


def may_take(lease: dict | None, me: dict, now: datetime | None = None, has_data: bool = True) -> bool:
    return state(lease, me, now, has_data) in TAKEABLE


def held(me: dict, now: datetime | None = None) -> dict:
    """A lease [me] holds from [now]."""
    now = now or now_utc()
    return {"schema": SCHEMA, "holder": party(me), "offer": None, "since": iso(now), "renewed": iso(now),
            "expires": iso(now + TERM)}


def offered(to: dict, by: dict, now: datetime | None = None) -> dict:
    """A lease handed over to [to] by [by]: nobody writes until [to] takes it (or [by] takes it back)."""
    now = now or now_utc()
    return {"schema": SCHEMA, "holder": None, "offer": {**party(to), "by": party(by), "at": iso(now)},
            "since": iso(now), "renewed": iso(now), "expires": iso(now + TERM)}


def renewed(lease: dict | None, me: dict, now: datetime | None = None) -> dict | None:
    """[lease] renewed by its holder [me] when its last renewal is a day old or more; None when nothing is due."""
    now = now or now_utc()
    if not lease or lease.get("invalid") or not same(lease.get("holder"), me):
        return None
    last = parse_time(lease.get("renewed"))
    if last is not None and now - last < RENEW_EVERY:
        return None
    return {**lease, "renewed": iso(now), "expires": iso(now + TERM)}


def write(data: Path, lease: dict) -> Path:
    """Writes [lease] as [data]'s app/lease.json, atomically."""
    p = Path(data) / FILE
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_name(p.name + ".tmp")
    tmp.write_text(dump(lease), encoding="utf-8")
    os.replace(tmp, p)
    return p


def who(p: dict | None) -> str:
    """"the phone (Pixel 8)", "the node (jan-desktop)"."""
    if not p:
        return "nobody"
    name = p.get("name") or p.get("device")
    return f"the {p['side']}" + (f" ({name})" if name else "")


def describe(lease: dict | None) -> str:
    """One line: who writes the data, since when."""
    if lease is None:
        return "no lease: the node writes the data (as before the lease)"
    if lease.get("invalid"):
        return f"{FILE} can't be read: nobody may write until it is fixed (or removed, on the side that writes)"
    since = (lease.get("since") or "?").replace("T", " ").rstrip("Z")
    if lease.get("holder"):
        out = f"{who(lease['holder'])} writes the data, since {since} UTC"
    elif lease.get("offer"):
        o = lease["offer"]
        out = f"handed over to {who(o)} by {who(o.get('by'))} on {since} UTC: waiting for {who(o)} to take it"
    else:
        out = "released: nobody writes the data until a side takes it"
    if lease.get("seen_remote"):
        out += " (the repository's lease: a push of this node's was refused)"
    return out


def node_refusal(data: Path) -> str | None:
    """Why this node may not write the data directory [data] now (the lease names another side), or None when it may.
    LANI_LEASE=ignore: it may."""
    if (os.environ.get("LANI_LEASE") or "").lower() == "ignore":
        return None
    lease = effective(data)
    st = state(lease, this_node())
    if st == "mine":
        return None
    how = ("take it back with `lani handover --take`" if st in TAKEABLE
           else "once it is handed back to the node, take it with `lani handover --take`")
    return f"Lani's data isn't this node's to write now: {describe(lease)}. This node only reads it; {how}."
