#!/usr/bin/env python3
"""Minimal liblp (Android dynamic partition) reader/extractor.

Usage:
    lpunpack.py super.img                 # list partitions
    lpunpack.py super.img odm out.img     # extract one partition
"""
import struct
import sys

GEOMETRY_MAGIC = 0x616C4467
HEADER_MAGIC = 0x414C5030
SECTOR = 512


def find_header(f):
    f.seek(4096)
    data = f.read(1 << 16)
    i = data.find(struct.pack("<I", HEADER_MAGIC))
    if i < 0:
        i = data.find(struct.pack(">I", HEADER_MAGIC))
    if i < 0:
        raise ValueError("metadata header not found")
    return 4096 + i


def read_geometry(f):
    f.seek(4096)
    g = f.read(4096)
    magic, = struct.unpack_from("<I", g, 0)
    if magic != GEOMETRY_MAGIC:
        raise ValueError(f"bad geometry magic {magic:#x}")
    max_size, = struct.unpack_from("<I", g, 40)
    slots, = struct.unpack_from("<I", g, 44)
    blk, = struct.unpack_from("<I", g, 48)
    return {"metadata_max_size": max_size, "slot_count": slots, "logical_block_size": blk}


def read_table(f, base, desc):
    off, num, esz = desc
    f.seek(base + off)
    return f.read(num * esz)


def parse_metadata(f, start):
    f.seek(start)
    h = f.read(256)
    magic, major, minor, hsize = struct.unpack_from("<IHHI", h, 0)
    if magic != HEADER_MAGIC:
        raise ValueError(f"bad header magic {magic:#x}")
    tables_size, = struct.unpack_from("<I", h, 44)
    descs = {}
    for i, name in enumerate(("partitions", "extents", "groups", "block_devices")):
        descs[name] = struct.unpack_from("<III", h, 80 + i * 12)
    base = start + hsize
    return {"header_size": hsize, "tables_size": tables_size, "base": base,
            "partitions": read_table(f, base, descs["partitions"]),
            "extents": read_table(f, base, descs["extents"]),
            "block_devices": read_table(f, base, descs["block_devices"]),
            "descs": descs}


def main():
    img = sys.argv[1]
    want = sys.argv[2] if len(sys.argv) > 2 else None
    out = sys.argv[3] if len(sys.argv) > 3 else None
    with open(img, "rb") as f:
        geo = read_geometry(f)
        start = find_header(f)
        md = parse_metadata(f, start)
        print(f"metadata@{start:#x} max={geo['metadata_max_size']} slots={geo['slot_count']} "
              f"hdr={md['header_size']} table_bytes={md['tables_size']}")

        pdesc, edesc = md["descs"]["partitions"], md["descs"]["extents"]
        bdesc = md["descs"]["block_devices"]
        parts = []
        for i in range(pdesc[1]):
            off = i * pdesc[2]
            name = md["partitions"][off:off + 36].split(b"\x00")[0].decode()
            attrs, first, num, group = struct.unpack_from("<IIII", md["partitions"], off + 36)
            parts.append({"name": name, "first": first, "num": num})
        exts = []
        for i in range(edesc[1]):
            off = i * edesc[2]
            nsec, ttype, tdata, tsrc = struct.unpack_from("<QIII", md["extents"], off)
            exts.append((nsec, ttype, tdata))
        bdev = []
        for i in range(bdesc[1]):
            off = i * bdesc[2]
            first, align, aoff, size = struct.unpack_from("<QIIQ", md["block_devices"], off)
            bdev.append(first)
        base_sector = bdev[0] if bdev else 0

        for p in parts:
            nsec = sum(exts[p["first"] + i][0] for i in range(p["num"]))
            print(f"  {p['name']:<20} {nsec * SECTOR:>14,} B")

        if want:
            for p in parts:
                if p["name"] != want:
                    continue
                with open(out, "wb") as o:
                    for i in range(p["num"]):
                        nsec, ttype, tdata = exts[p["first"] + i]
                        if ttype != 0:
                            print(f"  skip extent type {ttype}", file=sys.stderr)
                            continue
                        f.seek(tdata * SECTOR)
                        left = nsec * SECTOR
                        while left > 0:
                            chunk = f.read(min(left, 8 << 20))
                            if not chunk:
                                break
                            o.write(chunk)
                            left -= len(chunk)
                print(f"extracted {want} -> {out}")
                return 0
            print(f"partition {want} not found", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
