#!/usr/bin/env python3
"""Minimal Android OTA payload.bin parser/extractor (no third-party deps).

Supports REPLACE (raw), REPLACE_BZ, REPLACE_XZ operations, which is what Xiaomi
full-OTA payloads use.
"""
import bz2
import lzma
import os
import struct
import sys


def read_varint(buf, pos):
    result = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            return result, pos
        shift += 7


def parse_fields(buf):
    """Return {field_number: [values]}; values are ints or bytes."""
    fields = {}
    pos = 0
    n = len(buf)
    while pos < n:
        key, pos = read_varint(buf, pos)
        field, wire = key >> 3, key & 7
        if wire == 0:
            val, pos = read_varint(buf, pos)
        elif wire == 2:
            ln, pos = read_varint(buf, pos)
            val = buf[pos:pos + ln]
            pos += ln
        elif wire == 5:
            val = struct.unpack_from("<I", buf, pos)[0]
            pos += 4
        elif wire == 1:
            val = struct.unpack_from("<Q", buf, pos)[0]
            pos += 8
        else:
            raise ValueError(f"unsupported wire type {wire}")
        fields.setdefault(field, []).append(val)
    return fields


def parse_manifest(data):
    root = parse_fields(data)
    parts = []
    for raw in root.get(13, []):
        pf = parse_fields(raw)
        name = pf[1][0].decode() if 1 in pf else "?"
        size = pf[7][0] if 7 in pf and isinstance(pf[7][0], int) else 0
        ops = []
        for opraw in pf.get(8, []):
            of = parse_fields(opraw)
            op = {
                "type": of[1][0] if 1 in of else 0,
                "data_offset": of[2][0] if 2 in of else 0,
                "data_length": of[3][0] if 3 in of else 0,
                "dst": [(parse_fields(e)[1][0], parse_fields(e)[2][0]) for e in of.get(6, [])],
                "dst_length": of[7][0] if 7 in of else 0,
            }
            ops.append(op)
        parts.append({"name": name, "size": size, "ops": ops})
    return parts


def open_payload(path):
    fh = open(path, "rb")
    magic = fh.read(4)
    if magic != b"CrAU":
        raise ValueError("not a payload.bin")
    version, manifest_size = struct.unpack(">QQ", fh.read(16))
    (sig_size,) = struct.unpack(">I", fh.read(4))
    manifest = fh.read(manifest_size)
    data_start = 24 + manifest_size + sig_size
    return fh, data_start, parse_manifest(manifest)


def extract(fh, data_start, part, out_path, block=4096):
    fh.seek(data_start)
    with open(out_path, "wb") as out:
        for op in part["ops"]:
            fh.seek(data_start + op["data_offset"])
            blob = fh.read(op["data_length"])
            t = op["type"]
            if t == 0:
                data = blob
            elif t == 1:
                data = bz2.decompress(blob)
            elif t == 8:
                data = lzma.decompress(blob)
            else:
                raise ValueError(f"unsupported op type {t}")
            for start_block, num_blocks in op["dst"]:
                out.seek(start_block * block)
                out.write(data)


def main():
    if len(sys.argv) < 2:
        print("usage: payload.py <payload.bin> [partition ...]", file=sys.stderr)
        return 2
    path = sys.argv[1]
    fh, data_start, parts = open_payload(path)
    names = {p["name"]: p for p in parts}
    print(f"partitions: {len(parts)}")
    for p in parts:
        print(f"  {p['name']:<16} size={p['size']:>14,} ops={len(p['ops'])}")
    for want in sys.argv[2:]:
        if want not in names:
            print(f"missing partition {want}", file=sys.stderr)
            continue
        out = os.path.join(os.getcwd(), f"{want}.img")
        print(f"extracting {want} -> {out}")
        extract(fh, data_start, names[want], out)
        print(f"  {os.path.getsize(out):,} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

