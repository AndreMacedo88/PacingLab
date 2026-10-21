#!/usr/bin/env python3
"""Read a copy of the app's database for ad-hoc analysis on the laptop.

Copy the database from the phone first (see CLAUDE.md), then for example:

    tools/pacinglab_db.py fixtures-private/db/pacinglab.db workouts 2026-10-20
    tools/pacinglab_db.py fixtures-private/db/pacinglab.db streams <workout id>

As a module: `open_db(path)`, `workouts(db, day=None)`, `stream(db, workout_id, channel)`,
`decode_sleep_stages(blob)` for the `stages` column of `sleep_sessions`.
Stream blobs follow core/database/.../codec/StreamCodec.kt (format version 1).
"""

import sqlite3
import sys
from datetime import datetime, timedelta, timezone

SPEED_SCALE = 1_000.0
DEGREE_SCALE = 1e7
CENTIMETRES = 100.0


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def byte(self):
        b = self.data[self.pos]
        self.pos += 1
        return b

    def var(self):
        result, shift = 0, 0
        while True:
            b = self.byte()
            result |= (b & 0x7F) << shift
            if not b & 0x80:
                return result
            shift += 7

    def signed(self):
        v = self.var()
        return (v >> 1) ^ -(v & 1)

    def times(self, n):
        if n == 0:
            return []
        out = [self.signed()]
        if n == 1:
            return out
        unit = self.var()
        for _ in range(n - 1):
            out.append(out[-1] + self.var() * unit)
        return out

    def deltas(self, n):
        out, previous = [], 0
        for _ in range(n):
            previous += self.signed()
            out.append(previous)
        return out

    def optional(self, n, scale):
        if self.byte() == 0:
            return [None] * n
        present, bits = [], 0
        for i in range(n):
            if i % 8 == 0:
                bits = self.byte()
            present.append(bool(bits & (1 << (i % 8))))
        values = iter(self.deltas(sum(present)))
        return [next(values) / scale if p else None for p in present]


def _open(data):
    reader = _Reader(data)
    version = reader.byte()
    if version != 1:
        raise ValueError(f"unsupported stream format version {version}")
    return reader


def decode_heart_rate(data):
    """[(epoch millis, bpm)]"""
    r = _open(data)
    n = r.var()
    times = r.times(n)
    return list(zip(times, r.deltas(n)))


def decode_samples(data, scale=SPEED_SCALE):
    """[(epoch millis, value)]"""
    r = _open(data)
    n = r.var()
    times = r.times(n)
    return [(t, v / scale) for t, v in zip(times, r.deltas(n))]


def decode_route(data):
    """[(epoch millis, lat, lon, altitude or None, accuracy or None)]"""
    r = _open(data)
    n = r.var()
    times = r.times(n)
    lat = r.deltas(n)
    lon = r.deltas(n)
    alt = r.optional(n, CENTIMETRES)
    acc = r.optional(n, CENTIMETRES)
    return [(times[i], lat[i] / DEGREE_SCALE, lon[i] / DEGREE_SCALE, alt[i], acc[i]) for i in range(n)]


SLEEP_STAGES = {1: "awake", 2: "light", 3: "deep", 4: "rem", 5: "asleep", 6: "out_of_bed"}


def decode_sleep_stages(data):
    """[(start millis, end millis, stage name)]"""
    r = _open(data)
    stages = []
    for _ in range(r.var()):
        start = r.signed()
        end = start + r.signed()
        stages.append((start, end, SLEEP_STAGES.get(r.var(), "unknown")))
    return stages


def open_db(path):
    db = sqlite3.connect(path)
    db.row_factory = sqlite3.Row
    return db


def local_time(millis, offset_seconds):
    zone = timezone(timedelta(seconds=offset_seconds or 0))
    return datetime.fromtimestamp(millis / 1000, zone)


def workouts(db, day=None):
    rows = db.execute("SELECT * FROM workouts ORDER BY start_ms").fetchall()
    if day is None:
        return rows
    return [w for w in rows if local_time(w["start_ms"], w["zone_offset_s"]).date().isoformat() == day]


def stream(db, workout_id, channel):
    row = db.execute(
        "SELECT data FROM workout_streams WHERE workout_id = ? AND channel = ?", (workout_id, channel)
    ).fetchone()
    if row is None:
        return []
    if channel == "heart_rate":
        return decode_heart_rate(row["data"])
    if channel == "route":
        return decode_route(row["data"])
    return decode_samples(row["data"])


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    db = open_db(sys.argv[1])
    command = sys.argv[2]
    if command == "workouts":
        for w in workouts(db, sys.argv[3] if len(sys.argv) > 3 else None):
            start = local_time(w["start_ms"], w["zone_offset_s"])
            minutes = (w["end_ms"] - w["start_ms"]) / 60_000
            distance = f"{w['distance_m'] / 1000:.2f} km" if w["distance_m"] else "-"
            print(f"{w['id']}  {start:%Y-%m-%d %H:%M}  {minutes:5.1f} min  {w['activity']:<20} {w['sport']:<8} "
                  f"{distance:>9}  {w['recording_method']}")
    elif command == "streams":
        for channel in ("heart_rate", "speed", "route"):
            samples = stream(db, sys.argv[3], channel)
            print(f"{channel}: {len(samples)} samples")
    else:
        sys.exit(f"unknown command {command!r}")


if __name__ == "__main__":
    main()
