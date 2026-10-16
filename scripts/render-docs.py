"""Render the README charts in docs/images/ from the repository's own data.

Every number on these charts comes from a file in the repo, so the pictures cannot
drift from the code:

  * sensor traces       detection/src/test/resources/traces/*.csv
  * detector verdicts   the real :detection classes, via scripts/TraceVerdicts.java
  * thresholds          DetectorConfig.java builder defaults
  * countdown default   AlertStateMachine.kt
  * alert timeline      docs/evidence/alert-outage-timeline-2026-10-14.csv
                        (rows for the outage alert, copied from build/kpi/kpi_events.csv
                        of a `scripts/kpi-run.ps1 -Quick` run)

Usage (JDK 17 on PATH, standard library only):
    python scripts/render-docs.py

Writes hero-fall-{light,dark}.svg and traces-{light,dark}.svg.
"""

import csv
import datetime
import math
import os
import re
import subprocess
import sys
from pathlib import Path
from types import SimpleNamespace
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent
TRACES = ROOT / "detection/src/test/resources/traces"
CLASSES = ROOT / "detection/build/classes/java/main"
OUT = ROOT / "docs/images"
TIMELINE = ROOT / "docs/evidence/alert-outage-timeline-2026-10-14.csv"

DISPLAY = "'Barlow Condensed','Bahnschrift SemiCondensed','Bahnschrift','Arial Narrow',sans-serif"
MONO = "'JetBrains Mono','Cascadia Mono',Consolas,'SF Mono',monospace"

# Colours mean alert states and are the same in every chart and Mermaid diagram:
# red = emergency detected, yellow = countdown (cancellable), blue = sending,
# green = acknowledged, grey = everything else.
THEMES = {
    "light": dict(ink="#1B1F24", muted="#59636E", faint="#D1D9E0", red="#C8102E",
                  yellow="#E0A800", yellow_ink="#7A5A00", blue="#005EB8", green="#00843D"),
    "dark": dict(ink="#E6EDF3", muted="#9198A1", faint="#3D444D", red="#FF6B7D",
                 yellow="#F2C230", yellow_ink="#F2C230", blue="#58A6FF", green="#3FB950"),
}

ALERT_ORDER = ["fall_forward", "fall_backward", "fall_lateral", "fall_faint", "mandown_collapse"]
QUIET_ORDER = ["drop_phone_desk", "drop_phone_bounce", "drop_phone_pickup", "sit_down",
               "sit_quick", "walk_normal", "walk_fast", "walk_stairs"]


# ---------------------------------------------------------------- data

def detector_defaults():
    src = (ROOT / "detection/src/main/java/com/safesignal/detection/DetectorConfig.java").read_text()
    return {k: float(v) for k, v in re.findall(r"private (?:double|long) (\w+) = ([\d.]+);", src)}


def countdown_default():
    src = (ROOT / "protocol/src/main/kotlin/com/safesignal/protocol/AlertStateMachine.kt").read_text()
    return int(re.search(r"DEFAULT_COUNTDOWN_SECONDS = (\d+)", src).group(1))


def read_trace(name):
    with open(TRACES / f"{name}.csv", newline="") as f:
        rows = list(csv.DictReader(f))
    return [(int(r["tMs"]), math.sqrt(float(r["x"]) ** 2 + float(r["y"]) ** 2 + float(r["z"]) ** 2))
            for r in rows]


def run_verdicts():
    """Replay every trace through FallDetector and ManDownDetector (Java)."""
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run([str(gradlew), ":detection:classes", "-q"], cwd=ROOT, check=True)
    files = sorted(str(p) for p in TRACES.glob("*.csv"))
    out = subprocess.run(["java", "-cp", str(CLASSES), str(ROOT / "scripts/TraceVerdicts.java"), *files],
                         cwd=ROOT, check=True, capture_output=True, text=True).stdout
    verdicts = {}
    for line in out.splitlines():
        kind, file, *rest = line.split("\t")
        v = verdicts.setdefault(file.removesuffix(".csv"), {"states": [], "events": []})
        if kind == "STATE":
            v["states"].append((int(rest[0]), rest[1]))
        elif kind == "EVENT":
            v["events"].append((int(rest[0]), rest[1], rest[2]))
    return verdicts


def fired_at(v):
    """When the detector raised its event, or None."""
    for t, kind, _ in v["events"]:
        if kind == "FALL":
            return next(st for st, s in v["states"] if s == "TRIGGERED"), kind
        return t, kind
    return None, None


def rejected_impacts(v):
    """Impacts that entered the stillness check and were then dropped (movement resumed)."""
    out, pending = [], None
    for t, s in v["states"]:
        if s == "MONITORING_STILLNESS":
            pending = t
        elif s == "MONITORING" and pending is not None:
            out.append(pending)
            pending = None
    return out


def expected(name):
    return "FALL" if name.startswith("fall_") else "MAN_DOWN" if name.startswith("mandown_") else None


def read_timeline():
    with open(TIMELINE, newline="") as f:
        rows = list(csv.DictReader(f))
    t0 = int(next(r["epochMs"] for r in rows if r["event"] == "DETECTED"))

    def at(event):
        return int(next(r["epochMs"] for r in rows if r["event"] == event)) - t0

    attempts = [(int(r["epochMs"]) - t0, int(r["detail"].split("=")[1]))
                for r in rows if r["event"] == "SEND_ATTEMPT"]
    delivered_attempt = int(next(r["detail"] for r in rows if r["event"] == "DELIVERED").split("=")[1])
    return dict(date=datetime.datetime.fromtimestamp(t0 / 1000, datetime.timezone.utc).date().isoformat(),
                raised=at("RAISED"), delivered=at("DELIVERED"), ack=at("ACK_SEEN"),
                attempts=attempts, delivered_attempt=delivered_attempt)


# ---------------------------------------------------------------- svg

class Svg:
    def __init__(self, w, h, theme):
        self.w, self.h, self.c, self.parts = w, h, SimpleNamespace(**THEMES[theme]), []

    def line(self, x1, y1, x2, y2, color, width=1.0, dash=None, opacity=1.0):
        d = f' stroke-dasharray="{dash}"' if dash else ""
        self.parts.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{color}" '
                          f'stroke-width="{width}" opacity="{opacity}"{d}/>')

    def rect(self, x, y, w, h, fill, opacity=1.0, extra=""):
        self.parts.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" fill="{fill}" '
                          f'opacity="{opacity}"{extra}/>')

    def circle(self, x, y, r, fill, stroke=None):
        s = f' stroke="{stroke}" stroke-width="2"' if stroke else ""
        self.parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r}" fill="{fill}"{s}/>')

    def polyline(self, pts, color, width=1.25):
        p = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
        self.parts.append(f'<polyline points="{p}" fill="none" stroke="{color}" stroke-width="{width}" '
                          f'stroke-linejoin="round"/>')

    def text(self, x, y, s, color, size=14, font=DISPLAY, anchor="start", weight=500, caps=False):
        ls = ' letter-spacing="0.12em"' if caps else ""
        self.parts.append(f'<text x="{x:.1f}" y="{y:.1f}" fill="{color}" font-family="{font}" '
                          f'font-size="{size}" font-weight="{weight}" text-anchor="{anchor}"{ls}>'
                          f'{escape(s.upper() if caps else s)}</text>')

    def bracket(self, x1, x2, y, color):
        self.parts.append(f'<path d="M{x1:.1f},{y - 5:.1f} V{y:.1f} H{x2:.1f} V{y - 5:.1f}" fill="none" '
                          f'stroke="{color}" stroke-width="1.25"/>')

    def save(self, path, title, desc, defs=""):
        body = "\n".join(self.parts)
        path.write_text(
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" '
            f'viewBox="0 0 {self.w} {self.h}" role="img" aria-labelledby="t d">\n'
            f'<title id="t">{escape(title)}</title>\n<desc id="d">{escape(desc)}</desc>\n'
            f'<defs>{defs}</defs>\n{body}\n</svg>\n', encoding="utf-8")


# ---------------------------------------------------------------- hero

def render_hero(theme, cfg, verdicts, tl, default_countdown):
    W, H, PT, PB = 1200, 420, 84, 290
    s = Svg(W, H, theme)
    c = s.c
    samples = read_trace("fall_forward")
    v = verdicts["fall_forward"]
    states = dict((st, t) for t, st in v["states"])
    t_ff, t_imp, t_fire = states["FREE_FALL"], states["MONITORING_STILLNESS"], states["TRIGGERED"]
    peak = float(re.search(r"Impact ([\d.]+)", v["events"][0][2]).group(1))
    y_max = max(35.0, math.ceil(peak / 5) * 5)

    def y(m):
        return PB - min(m, y_max) / y_max * (PB - PT)

    # Segment A: the physical fall, high time resolution
    AX0, AX1 = 64, 636
    a0 = (t_ff - 1000) // 500 * 500
    a1 = t_fire + 450

    def xa(t):
        return AX0 + (t - a0) / (a1 - a0) * (AX1 - AX0)

    s.text(AX0, 36, "Sensor", c.ink, 15, weight=600, caps=True)
    s.text(AX0 + 70, 36, "fall_forward.csv replayed through FallDetector", c.muted, 12, MONO)

    for m in (0, 10, 20, 30):
        s.text(AX0 - 10, y(m) + 4, str(m), c.muted, 11, MONO, "end", 400)
    s.text(AX0 - 10, PT - 14, "m/s²", c.muted, 11, MONO, "end", 400)
    s.line(AX0, PB, AX1, PB, c.faint)

    ff, imp = cfg["freeFallThreshold"], cfg["impactThreshold"]
    s.line(AX0, y(ff), AX1, y(ff), c.muted, 1, "4 4", 0.8)
    s.text(AX0 + 6, y(ff) + 15, f"free fall below {ff:g}", c.muted, 12, MONO, weight=400)
    s.line(AX0, y(imp), AX1, y(imp), c.red, 1, "4 4", 0.7)
    s.text(AX0 + 6, y(imp) - 7, f"impact above {imp:g}", c.red, 12, MONO, weight=400)

    s.rect(xa(t_imp), PT, xa(t_fire) - xa(t_imp), PB - PT, c.faint, 0.35)
    s.polyline([(xa(t), y(m)) for t, m in samples if a0 <= t <= a1], c.ink, 1.5)

    t_peak, m_peak = max(((t, m) for t, m in samples if t_imp <= t <= t_imp + cfg["stillnessSettlingMs"]),
                         key=lambda p: p[1])
    s.circle(xa(t_peak), y(m_peak), 4, c.red)
    s.text(xa(t_peak) + 9, y(m_peak) + 4, f"impact {peak:g} m/s²", c.red, 14, weight=600)

    s.line(xa(t_fire), PT - 22, xa(t_fire), PB, c.red, 2)
    s.text(xa(t_fire) - 8, PT - 10, f"FallDetector fires · {t_fire / 1000:.2f} s", c.red, 14, anchor="end",
           weight=600)

    by = PB + 18
    s.bracket(xa(t_ff), xa(t_imp), by, c.ink)
    s.text((xa(t_ff) + xa(t_imp)) / 2, by + 18, "free fall", c.ink, 14, anchor="middle")
    s.text((xa(t_ff) + xa(t_imp)) / 2, by + 33, f"{t_imp - t_ff} ms", c.muted, 11, MONO, "middle", 400)
    s.bracket(xa(t_imp), xa(t_fire), by, c.ink)
    s.text((xa(t_imp) + xa(t_fire)) / 2, by + 18, "lies still", c.ink, 14, anchor="middle")
    s.text((xa(t_imp) + xa(t_fire)) / 2, by + 33, f"{(t_fire - t_imp) / 1000:g} s near 1 g", c.muted, 11,
           MONO, "middle", 400)

    ty = PB + 80
    s.line(AX0, ty, AX1, ty, c.faint)
    for t in range(math.ceil(a0 / 1000) * 1000, a1 + 1, 1000):
        s.line(xa(t), ty, xa(t), ty + 5, c.muted)
        s.text(xa(t), ty + 19, f"{t // 1000} s", c.muted, 11, MONO, "middle", 400)

    # Hazard tape marks the change of time scale between the two segments
    defs = (f'<pattern id="hazard-{theme}" width="12" height="12" patternUnits="userSpaceOnUse" '
            f'patternTransform="rotate(45)"><rect width="12" height="12" fill="{c.ink}"/>'
            f'<rect width="6" height="12" fill="{c.yellow}"/></pattern>')
    s.rect(652, PT - 22, 22, ty + 5 - (PT - 22), f"url(#hazard-{theme})")

    # Segment B: the alert, from detection to acknowledgement, low time resolution
    BX0, BX1 = 692, 1160
    b1 = math.ceil(tl["ack"] / 1000 + 0.5) * 1000

    def xb(t):
        return BX0 + t / b1 * (BX1 - BX0)

    s.text(BX0, 36, "Alert", c.ink, 15, weight=600, caps=True)
    s.text(BX0 + 62, 36, f"outage run {tl['date']}, server stopped", c.muted, 12, MONO)

    RY, RH = 176, 34
    s.line(BX0, PT - 22, BX0, RY + RH, c.red, 2)
    s.text(BX0 + 8, PT - 10, "+0 = detection", c.red, 14, weight=600)

    s.rect(xb(0), RY, xb(tl["raised"]) - xb(0), RH, c.yellow)
    s.text(xb(0), RY - 27, f"countdown {tl['raised'] / 1000:g} s", c.yellow_ink, 14, weight=600)
    s.text(xb(0), RY - 12, f"test setting; app default {default_countdown} s", c.muted, 11, MONO,
           weight=400)

    s.rect(xb(tl["raised"]), RY, xb(tl["delivered"]) - xb(tl["raised"]), RH, c.blue, 0.16)
    s.line(xb(tl["raised"]), RY, xb(tl["delivered"]), RY, c.blue, 1.5)
    s.line(xb(tl["raised"]), RY + RH, xb(tl["delivered"]), RY + RH, c.blue, 1.5)
    failed = [(t, n) for t, n in tl["attempts"] if n != tl["delivered_attempt"]]
    for t, _ in failed:
        cx, cy = xb(t), RY + RH / 2
        s.line(cx - 4, cy - 4, cx + 4, cy + 4, c.muted, 1.5)
        s.line(cx - 4, cy + 4, cx + 4, cy - 4, c.muted, 1.5)
    gaps = [b[0] - a[0] for a, b in zip(tl["attempts"], tl["attempts"][1:])]
    s.text(xb(tl["raised"]), RY + RH + 22, "sending · server down", c.blue, 14, weight=600)
    s.text(xb(tl["raised"]), RY + RH + 37,
           f"{len(failed)} attempts refused, backoff {gaps[0] / 1000:.1f} → {max(gaps) / 1000:.1f} s",
           c.muted, 11, MONO, weight=400)

    s.circle(xb(tl["delivered"]), RY + RH / 2, 6, c.blue)
    s.line(xb(tl["delivered"]), RY - 4, xb(tl["delivered"]), PT + 22, c.blue, 1)
    s.text(xb(tl["delivered"]), PT + 14, f"delivered on attempt {tl['delivered_attempt']}", c.blue, 14,
           anchor="end", weight=600)
    s.text(xb(tl["delivered"]), PT + 30, f"{(tl['delivered'] - tl['raised']) / 1000:.1f} s after raise",
           c.muted, 11, MONO, "end", 400)

    s.circle(xb(tl["ack"]), RY + RH / 2, 7, "none", c.green)
    s.line(xb(tl["ack"]), RY + RH + 4, xb(tl["ack"]), RY + RH + 52, c.green, 1)
    s.text(xb(tl["ack"]), RY + RH + 68, "acknowledged", c.green, 14, anchor="end", weight=600)
    s.text(xb(tl["ack"]), RY + RH + 83, f"+{tl['ack'] - tl['delivered']} ms after delivery", c.muted, 11,
           MONO, "end", 400)

    s.line(BX0, ty, BX1, ty, c.faint)
    for t in range(0, b1 + 1, 5000):
        s.line(xb(t), ty, xb(t), ty + 5, c.muted)
        s.text(xb(t), ty + 19, f"+{t // 1000} s", c.muted, 11, MONO, "middle", 400)

    desc = (f"Left: acceleration magnitude of fall_forward.csv. Free fall below {ff:g} m/s² for "
            f"{t_imp - t_ff} ms, impact of {peak:g} m/s², then {(t_fire - t_imp) / 1000:g} s lying still; "
            f"FallDetector fires at {t_fire / 1000:.2f} s. Right: an alert raised while the server was down: "
            f"{tl['raised'] / 1000:g} s countdown, {len(failed)} refused send attempts, delivered on attempt "
            f"{tl['delivered_attempt']}, acknowledged {tl['ack'] - tl['delivered']} ms later.")
    s.save(OUT / f"hero-fall-{theme}.svg", "One fall, end to end", desc, defs)


# ---------------------------------------------------------------- trace grid

def render_traces(theme, cfg, verdicts):
    W, M, COLS, GAP, CW, CH = 1200, 40, 5, 20, 208, 118
    rows_quiet = math.ceil(len(QUIET_ORDER) / COLS)
    H = 104 + CH + 48 + rows_quiet * (CH + 18) + 6
    s = Svg(W, H, theme)
    c = s.c
    y_max = 35.0

    matched = sum(1 for n in ALERT_ORDER + QUIET_ORDER if fired_at(verdicts[n])[1] == expected(n))
    total = len(ALERT_ORDER) + len(QUIET_ORDER)
    s.text(M, 34, f"{matched} of {total} traces behave as labelled", c.ink, 20, weight=600)
    s.text(M, 56, f"thresholds from DetectorConfig: free fall < {cfg['freeFallThreshold']:g} · impact > "
                  f"{cfg['impactThreshold']:g} m/s² · still {cfg['stillnessDurationMs'] / 1000:g} s · "
                  f"man-down after {cfg['manDownInactivityDurationMs'] / 1000:g} s without movement",
           c.muted, 12, MONO, weight=400)

    def cell(name, x0, y0):
        samples = read_trace(name)
        v = verdicts[name]
        dur = samples[-1][0]
        px0, px1, py0, py1 = x0, x0 + CW, y0 + 22, y0 + 86

        def x(t):
            return px0 + t / dur * (px1 - px0)

        def y(m):
            return py1 - min(m, y_max) / y_max * (py1 - py0)

        s.text(x0, y0 + 12, name, c.ink, 12, MONO)
        s.text(px1, y0 + 12, f"{dur / 1000:g} s", c.muted, 11, MONO, "end", 400)
        s.line(px0, py1, px1, py1, c.faint)
        s.line(px0, y(cfg["freeFallThreshold"]), px1, y(cfg["freeFallThreshold"]), c.muted, 1, "3 3", 0.5)
        s.line(px0, y(cfg["impactThreshold"]), px1, y(cfg["impactThreshold"]), c.red, 1, "3 3", 0.5)
        for t in rejected_impacts(v):
            s.line(x(t), py0 - 2, x(t), py1, c.muted, 1.25, "2 2")
        s.polyline([(x(t), y(m)) for t, m in samples], c.ink, 1)

        t_fire, kind = fired_at(v)
        if t_fire is not None:
            s.line(x(t_fire), py0 - 4, x(t_fire), py1, c.red, 2)
            label, color = f"{kind} at {t_fire / 1000:.2f} s", c.red
        else:
            label = "no alert" + (" · impact rejected" if rejected_impacts(v) else "")
            color = c.muted
        ok = "✓ " if kind == expected(name) else "✗ expected " + str(expected(name)) + " · "
        s.text(x0, y0 + 106, ok + label, color, 14, weight=600 if t_fire is not None else 500)

    s.text(M, 92, "Should alert", c.ink, 13, weight=600, caps=True)
    s.text(M + 112, 92, f"{len(ALERT_ORDER)} traces", c.muted, 12, MONO, weight=400)
    for i, name in enumerate(ALERT_ORDER):
        cell(name, M + i * (CW + GAP), 104)

    qy = 104 + CH + 48
    s.text(M, qy - 12, "Must stay quiet", c.ink, 13, weight=600, caps=True)
    s.text(M + 140, qy - 12, f"{len(QUIET_ORDER)} traces", c.muted, 12, MONO, weight=400)
    for i, name in enumerate(QUIET_ORDER):
        cell(name, M + (i % COLS) * (CW + GAP), qy + (i // COLS) * (CH + 18))

    desc = (f"Acceleration magnitude for all {total} labelled sensor traces, replayed through the real "
            f"FallDetector and ManDownDetector. {matched} of {total} behave as labelled: the four falls and "
            f"the collapse raise an alert; drops, sitting and walking do not.")
    s.save(OUT / f"traces-{theme}.svg", "Detector verdicts on every sensor trace", desc)


def main():
    cfg = detector_defaults()
    verdicts = run_verdicts()
    missing = set(ALERT_ORDER + QUIET_ORDER) ^ set(verdicts)
    if missing:
        sys.exit(f"Trace list out of date, update ALERT_ORDER/QUIET_ORDER: {sorted(missing)}")
    tl = read_timeline()
    countdown = countdown_default()
    OUT.mkdir(parents=True, exist_ok=True)
    for theme in THEMES:
        render_hero(theme, cfg, verdicts, tl, countdown)
        render_traces(theme, cfg, verdicts)
    print(f"Wrote hero-fall and traces SVGs (light, dark) to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
