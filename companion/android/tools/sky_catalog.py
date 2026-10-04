#!/usr/bin/env python3
"""The night sky's bundled catalog (game/sky/Stars.kt): the bright stars and the constellations' stick figures.

  python3 companion/android/tools/sky_catalog.py CATALOG [OUT_DIR]

CATALOG is the Yale Bright Star Catalogue, 5th Revised Ed. (Hoffleit & Warren 1991), the plain-text `catalog` file of
CDS catalogue V/50 (https://cdsarc.cds.unistra.fr/ftp/V/50/catalog.gz, gunzipped). The catalogue is a work of the
Yale University Observatory and NASA's Astronomical Data Center, distributed freely by the CDS; it is in the public
domain. OUT_DIR defaults to app/src/main/resources/sky next to this script; it gets

- stars.tsv: every star of magnitude MAG_LIMIT or brighter that rises over a village between 45° and 55° north (north
  of MIN_DEC), and the fainter ones the figures and the Pleiades need:
  HR number, Bayer/Flamsteed name, magnitude, J2000 right ascension and declination (degrees), B-V colour, and the
  proper name of the brightest (the IAU's);
- figures.tsv: each constellation's stick figure as lines between stars (HR numbers), and the Pleiades' stars.

The figures follow the usual modern stick figures (simplified where a figure has many faint stars).
"""
import sys
from math import cos, radians
from pathlib import Path

# The faintest drawn: the bright stars the figures are made of, and the fainter ones that make a dark sky look like one.
MAG_LIMIT = 4.5

# Stars further south never rise over a village between 45° and 55° north.
MIN_DEC = -50.0

# The constellations drawn, each as lines between its stars ("Alp" … Bayer letter, "Del1" a component, "27" a
# Flamsteed number; "Bet Tau" a star of another constellation). The ids are the IAU abbreviations, lowercase.
FIGURES = {
    "uma": ("UMa", ["Eta-Zet", "Zet-Eps", "Eps-Del", "Del-Gam", "Gam-Bet", "Bet-Alp", "Alp-Del"]),
    "umi": ("UMi", ["Alp-Del", "Del-Eps", "Eps-Zet", "Zet-Bet", "Bet-Gam", "Gam-Eta", "Eta-Zet"]),
    "cas": ("Cas", ["Eps-Del", "Del-Gam", "Gam-Alp", "Alp-Bet"]),
    "ori": ("Ori", ["Alp-Lam", "Lam-Gam", "Alp-Zet", "Gam-Del", "Del-Eps", "Eps-Zet", "Zet-Kap", "Del-Bet", "Bet-Kap"]),
    "tau": ("Tau", ["Bet-Eps", "Eps-Del1", "Del1-Gam", "Gam-The2", "The2-Alp", "Alp-Zet", "Gam-Lam"]),
    "gem": ("Gem", ["Alp-Bet", "Alp-Tau", "Tau-Eps", "Eps-Mu", "Mu-Eta", "Bet-Del", "Del-Zet", "Zet-Gam"]),
    "leo": ("Leo", ["Alp-Eta", "Eta-Gam1", "Gam1-Zet", "Zet-Mu", "Mu-Eps", "Alp-The", "The-Bet", "Bet-Del", "Del-Gam1"]),
    "cyg": ("Cyg", ["Alp-Gam", "Gam-Eta", "Eta-Bet1", "Del-Gam", "Gam-Eps", "Eps-Zet", "Del-Iot2"]),
    "lyr": ("Lyr", ["Alp-Eps1", "Alp-Zet1", "Zet1-Del2", "Del2-Gam", "Gam-Bet", "Bet-Zet1"]),
    "aql": ("Aql", ["Gam-Alp", "Alp-Bet", "Alp-Del", "Del-Lam", "Del-Eta", "Eta-The", "Del-Zet"]),
    "sco": ("Sco", ["Bet1-Del", "Del-Pi", "Del-Sig", "Sig-Alp", "Alp-Tau", "Tau-Eps", "Eps-Mu1", "Mu1-Zet2", "Zet2-Eta", "Eta-The", "The-Iot1", "Iot1-Kap", "Kap-Lam"]),
    "cma": ("CMa", ["Bet-Alp", "Alp-Del", "Del-Eps", "Del-Eta"]),
    "cmi": ("CMi", ["Alp-Bet"]),
    "boo": ("Boo", ["Alp-Eps", "Eps-Del", "Del-Bet", "Bet-Gam", "Gam-Rho", "Rho-Alp", "Alp-Eta", "Alp-Zet"]),
    "vir": ("Vir", ["Alp-Gam", "Gam-Del", "Del-Eps", "Gam-Eta", "Eta-Bet", "Alp-Zet", "Zet-Del"]),
    "aur": ("Aur", ["Alp-Bet", "Bet-The", "The-Bet Tau", "Bet Tau-Iot", "Iot-Alp"]),
    "peg": ("Peg", ["Alp-Bet", "Bet-Alp And", "Alp And-Gam", "Gam-Alp", "Alp-Zet", "Zet-The", "The-Eps", "Bet-Eta"]),
    "and": ("And", ["Alp-Del", "Del-Bet", "Bet-Gam1", "Bet-Mu", "Mu-Nu"]),
    "per": ("Per", ["Eta-Gam", "Gam-Alp", "Alp-Del", "Del-Eps", "Eps-Zet", "Alp-Kap", "Kap-Bet", "Bet-Rho"]),
    "sgr": ("Sgr", ["Gam2-Del", "Gam2-Eps", "Del-Eps", "Eps-Zet", "Zet-Phi", "Phi-Del", "Del-Lam", "Lam-Phi", "Phi-Sig", "Sig-Tau", "Tau-Zet"]),
}

# The Pleiades (Gostosevci): the stars an eye sees, a knot of them without lines.
PLEIADES = ("Tau", ["25", "27", "17", "20", "23", "19", "28", "16"])

# The IAU's names of the brightest stars (WGSN), by designation.
PROPER = {
    "Alp CMa": "Sirius", "Alp Boo": "Arcturus", "Alp Lyr": "Vega", "Alp Aur": "Capella", "Bet Ori": "Rigel",
    "Alp CMi": "Procyon", "Alp Ori": "Betelgeuse", "Alp Aql": "Altair", "Alp Tau": "Aldebaran", "Alp Sco": "Antares",
    "Alp Vir": "Spica", "Bet Gem": "Pollux", "Alp PsA": "Fomalhaut", "Alp Cyg": "Deneb", "Alp Leo": "Regulus",
    "Alp Gem": "Castor", "Gam Ori": "Bellatrix", "Bet Tau": "Elnath", "Eps Ori": "Alnilam", "Zet Ori": "Alnitak",
    "Del Ori": "Mintaka", "Kap Ori": "Saiph", "Lam Ori": "Meissa", "Alp UMa": "Dubhe", "Bet UMa": "Merak",
    "Gam UMa": "Phecda", "Del UMa": "Megrez", "Eps UMa": "Alioth", "Zet UMa": "Mizar", "Eta UMa": "Alkaid",
    "Alp UMi": "Polaris", "Bet UMi": "Kochab", "Alp Per": "Mirfak", "Bet Per": "Algol", "Alp And": "Alpheratz",
    "Bet And": "Mirach", "Gam1And": "Almach", "Alp Peg": "Markab", "Bet Peg": "Scheat", "Gam Peg": "Algenib",
    "Eps Peg": "Enif", "Alp Cas": "Schedar", "Bet Cas": "Caph", "Gam Cas": "Navi", "Bet Leo": "Denebola",
    "Gam1Leo": "Algieba", "Eps Sgr": "Kaus Australis", "Sig Sgr": "Nunki", "Bet CMa": "Mirzam", "Eps CMa": "Adhara",
    "Del CMa": "Wezen", "Gam Cyg": "Sadr", "Bet1Cyg": "Albireo", "Gam Aql": "Tarazed", "Gam Gem": "Alhena",
    "Lam Sco": "Shaula", "Eta Tau": "Alcyone", "Bet Aur": "Menkalinan", "Alp CrB": "Alphecca", "Alp Oph": "Rasalhague",
    "Gam Dra": "Eltanin", "Alp Hya": "Alphard", "Bet CMi": "Gomeisa", "Eps Boo": "Izar",
}

GREEK = ["Alp", "Bet", "Gam", "Del", "Eps", "Zet", "Eta", "The", "Iot", "Kap", "Lam", "Mu", "Nu", "Xi", "Omi", "Pi",
         "Rho", "Sig", "Tau", "Ups", "Phi", "Chi", "Psi", "Ome"]


def parse(path):
    stars = []
    for line in Path(path).read_text(encoding="latin-1").splitlines():
        if len(line) < 114 or not line[75:77].strip() or not line[102:107].strip():
            continue  # no J2000 position or magnitude (novae, the clusters' entries)
        hr = int(line[0:4])
        name = line[4:14]
        flam, bayer, comp, con = name[0:3].strip(), name[3:6].strip(), name[6].strip(), name[7:10].strip()
        ra = (int(line[75:77]) + int(line[77:79]) / 60 + float(line[79:83]) / 3600) * 15
        dec = int(line[84:86]) + int(line[86:88]) / 60 + int(line[88:90]) / 3600
        if line[83] == "-":
            dec = -dec
        mag = float(line[102:107])
        bv = float(line[109:114]) if line[109:114].strip() else 0.0
        stars.append(dict(hr=hr, flam=flam, bayer=bayer, comp=comp, con=con, ra=ra, dec=dec, mag=mag, bv=bv))
    return stars


def designation(s):
    if s["bayer"]:
        return f"{s['bayer']}{s['comp'] or ' '}{s['con']}".replace("  ", " ")
    return f"{s['flam']} {s['con']}" if s["flam"] else ""


def find(stars, ref, con):
    """The star [ref] names: "Alp", "Del1", "27", or "Bet Tau" (of another constellation)."""
    if " " in ref:
        ref, con = ref.split(" ")
    if ref.isdigit():
        hits = [s for s in stars if s["flam"] == ref and s["con"] == con]
    else:
        letters = ref.rstrip("0123456789")
        comp = ref[len(letters):]
        assert letters in GREEK, ref
        hits = [s for s in stars if s["bayer"] == letters and s["con"] == con and (s["comp"] == comp or (not comp and s["comp"] in ("", "1")))]
        # a double (ε¹ Lyr is two stars itself): the brighter
        hits = sorted(hits, key=lambda s: s["mag"])[:1]
    assert len(hits) == 1, f"{ref} {con}: no star"
    return hits[0]


def main():
    stars = parse(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parent.parent / "app/src/main/resources/sky"
    out.mkdir(parents=True, exist_ok=True)
    keep = {s["hr"] for s in stars if s["mag"] <= MAG_LIMIT and s["dec"] >= MIN_DEC}
    figures = []
    for fid, (con, lines) in FIGURES.items():
        pairs = []
        for ln in lines:
            a, b = ln.split("-")
            sa, sb = find(stars, a, con), find(stars, b, con)
            keep |= {sa["hr"], sb["hr"]}
            pairs.append(f"{sa['hr']}-{sb['hr']}")
        figures.append((fid, " ".join(pairs)))
    pleiades = [find(stars, r, PLEIADES[0])["hr"] for r in PLEIADES[1]]
    keep |= set(pleiades)
    figures.append(("m45", " ".join(str(h) for h in pleiades)))
    # a close double (ζ Ori A and B, a tenth of a degree apart or less) is one point of light: the brighter
    needed = {int(h) for _, l in figures for h in l.replace("-", " ").split()}
    kept = sorted((s for s in stars if s["hr"] in keep), key=lambda s: s["mag"])
    for i, s in enumerate(kept):
        if s["hr"] in needed:
            continue
        if any(abs(o["dec"] - s["dec"]) < 0.1 and abs(o["ra"] - s["ra"]) * max(0.05, cos(radians(s["dec"]))) < 0.1 and o["hr"] in keep for o in kept[:i]):
            keep.discard(s["hr"])

    # a designation two stars share (ζ Ori A and B) names the brighter
    by_des = {}
    for s in sorted(stars, key=lambda s: -s["mag"]):
        by_des[designation(s)] = s
    for d in PROPER:
        assert d in by_des, f"no star {d}"
    proper = {by_des[d]["hr"]: n for d, n in PROPER.items()}

    head = [
        "# The bright stars the night sky draws (game/sky/Stars.kt), made by companion/android/tools/sky_catalog.py.",
        "# Source: the Yale Bright Star Catalogue, 5th Revised Ed. (Hoffleit & Warren 1991), CDS catalogue V/50: public",
        "# domain. Every star of magnitude %.1f or brighter north of %d°, and the fainter ones the figures and the Pleiades" % (MAG_LIMIT, MIN_DEC),
        "# need.",
        "# Positions: J2000 (degrees). Proper names: the IAU's (WGSN).",
        "# hr\tname\tmag\tra\tdec\tb-v\tproper",
    ]
    rows = []
    for s in sorted((s for s in stars if s["hr"] in keep), key=lambda s: s["hr"]):
        rows.append(f"{s['hr']}\t{designation(s)}\t{s['mag']:.2f}\t{s['ra']:.4f}\t{s['dec']:.4f}\t{s['bv']:.2f}\t{proper.get(s['hr'], '')}")
    (out / "stars.tsv").write_text("\n".join(head + rows) + "\n", encoding="utf-8")
    fhead = [
        "# The constellations' stick figures (game/sky/Stars.kt), made by companion/android/tools/sky_catalog.py: lines",
        "# between stars of stars.tsv (HR numbers); m45, the Pleiades, is a knot of stars without lines.",
        "# id\tlines",
    ]
    (out / "figures.tsv").write_text("\n".join(fhead + [f"{i}\t{l}" for i, l in figures]) + "\n", encoding="utf-8")
    print(f"{len(rows)} stars, {len(figures)} figures")


if __name__ == "__main__":
    main()
