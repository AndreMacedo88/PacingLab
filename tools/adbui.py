#!/usr/bin/env python3
"""Minimal adb UI driver for smoke tests on the emulator or a connected phone.

Usage (with tools/env.sh sourced):
    tools/adbui.py dump              list labelled elements and their bounds
    tools/adbui.py tap <text>        tap the element whose text or description matches
    tools/adbui.py shot <file.png>   save a screenshot

Exact matches win over substring matches.
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args, binary=False):
    result = subprocess.run(["adb", *args], capture_output=True, timeout=60)
    return result.stdout if binary else result.stdout.decode(errors="replace")


def nodes():
    # The dump fails while the UI is still settling, so retry a few times.
    for _ in range(5):
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<?xml" in xml:
            return list(ET.fromstring(xml[xml.find("<?xml"):]).iter("node"))
        time.sleep(1.5)
    sys.exit("could not dump the UI hierarchy")


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    command = sys.argv[1]
    if command == "dump":
        for node in nodes():
            label = node.get("text") or node.get("content-desc")
            if label:
                print(f"{label!r} {node.get('bounds')} clickable={node.get('clickable')}")
    elif command == "tap":
        wanted = sys.argv[2]
        found = nodes()
        exact = [n for n in found if wanted in (n.get("text"), n.get("content-desc"))]
        partial = [n for n in found if wanted in f"{n.get('text') or ''}|{n.get('content-desc') or ''}"]
        matches = exact or partial
        if not matches:
            sys.exit(f"no element matching {wanted!r}")
        x, y = center(matches[0])
        adb("shell", "input", "tap", str(x), str(y))
        print(f"tapped {wanted!r} at {x},{y}")
    elif command == "shot":
        with open(sys.argv[2], "wb") as out:
            out.write(adb("exec-out", "screencap", "-p", binary=True))
        print("saved", sys.argv[2])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
