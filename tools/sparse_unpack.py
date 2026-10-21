#!/usr/bin/env python3
"""Unsparse an Android sparse image into a raw image."""
import struct
import sys

MAGIC = 0xED26FF3A
CHUNK_RAW = 0xCAC1
CHUNK_FILL = 0xCAC2
CHUNK_DONT_CARE = 0xCAC3
CHUNK_CRC32 = 0xCAC4


def main():
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "rb") as f, open(dst, "wb") as o:
        hdr = f.read(28)
        magic, major, minor, fhsz, chsz, blk, total_blks, total_chunks, csum = struct.unpack("<IHHHHIIII", hdr)
        if magic != MAGIC:
            raise ValueError(f"not sparse: {magic:#x}")
        print(f"sparse v{major}.{minor} blk={blk} blocks={total_blks} chunks={total_chunks}")
        written = 0
        for i in range(total_chunks):
            ch = f.read(chsz)
            ctype, _, out_blocks, total_sz = struct.unpack("<HHII", ch)
            data_len = total_sz - chsz
            out_len = out_blocks * blk
            if ctype == CHUNK_RAW:
                left = data_len
                while left > 0:
                    d = f.read(min(left, 8 << 20))
                    if not d:
                        break
                    o.write(d)
                    left -= len(d)
            elif ctype == CHUNK_FILL:
                fill = f.read(4)
                o.write(fill * (out_len // 4))
            elif ctype == CHUNK_DONT_CARE:
                o.seek(out_len, 1)
            elif ctype == CHUNK_CRC32:
                f.read(data_len)
                continue
            else:
                raise ValueError(f"unknown chunk type {ctype:#x}")
            written += out_len
            if i % 200 == 0:
                print(f"  chunk {i}/{total_chunks} written={written:,}", flush=True)
        print(f"done written={written:,}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
