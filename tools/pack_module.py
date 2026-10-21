#!/usr/bin/env python3
"""Pack a directory into a flashable zip (forward-slash names, no top folder)."""
import os
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: pack_module.py <module-dir> <out.zip>", file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    if os.path.exists(dst):
        os.remove(dst)
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for root, _dirs, files in os.walk(src):
            for name in files:
                full = os.path.join(root, name)
                rel = os.path.relpath(full, src).replace("\\", "/")
                zf.write(full, rel)
    print(f"packed {dst} ({os.path.getsize(dst)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
