#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成连点器图标：蓝色圆角方块 + 白色点击准星。
纯标准库实现（zlib/struct 手写 PNG），无第三方依赖。
输出：
  res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png   传统启动图标
  res/drawable/ic_launcher_fg.png                               自适应图标前景（透明底）
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _chunk(tag, data):
    c = struct.pack(">I", len(data)) + tag + data
    return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path, size, pixel_fn):
    """pixel_fn(x, y) -> (r, g, b, a)"""
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter: None
        for x in range(size):
            raw.extend(pixel_fn(x, y))
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + _chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", path, len(png), "bytes")


def rounded_legacy(size, radius_ratio=0.19):
    """传统图标：蓝色渐变圆角方块 + 白色点击准星"""
    rad = size * radius_ratio
    cx = cy = size / 2.0
    ring_r = size * 0.29
    ring_w = size * 0.075
    tick_len = size * 0.41

    def pixel_fn(x, y):
        # 圆角外透明
        dxc = min(x, size - 1 - x)
        dyc = min(y, size - 1 - y)
        if dxc < rad and dyc < rad:
            if math.hypot(rad - dxc, rad - dyc) > rad:
                return (0, 0, 0, 0)
        # 背景渐变 #2563EB -> #1D4ED8
        t = (x + y) / (2.0 * size)
        r = int(37 + (29 - 37) * t)
        g = int(99 + (78 - 99) * t)
        b = int(235 + (216 - 235) * t)
        dx = x - cx
        dy = y - cy
        dist = math.hypot(dx, dy)
        if ring_r - ring_w <= dist <= ring_r + ring_w:
            return (255, 255, 255, 255)
        if abs(dx) < size * 0.04 and ring_r + ring_w <= dist <= tick_len:
            return (255, 255, 255, 255)
        if abs(dy) < size * 0.04 and ring_r + ring_w <= dist <= tick_len:
            return (255, 255, 255, 255)
        return (r, g, b, 255)

    return pixel_fn


def adaptive_foreground(size=432):
    """自适应图标前景：透明底，白色准星位于安全区（中心 66/108）"""
    cx = cy = size / 2.0
    safe_r = size * (66.0 / 108.0) / 2.0
    ring_r = safe_r * 0.62
    ring_w = size * 0.045
    tick_len = safe_r * 0.95

    def pixel_fn(x, y):
        dx = x - cx
        dy = y - cy
        dist = math.hypot(dx, dy)
        if ring_r - ring_w <= dist <= ring_r + ring_w:
            return (255, 255, 255, 255)
        if abs(dx) < size * 0.025 and ring_r + ring_w <= dist <= tick_len:
            return (255, 255, 255, 255)
        if abs(dy) < size * 0.025 and ring_r + ring_w <= dist <= tick_len:
            return (255, 255, 255, 255)
        return (0, 0, 0, 0)

    return pixel_fn


def main():
    dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, size in dens.items():
        p = os.path.join(ROOT, "res", "mipmap-" + name, "ic_launcher.png")
        write_png(p, size, rounded_legacy(size))
    write_png(
        os.path.join(ROOT, "res", "drawable", "ic_launcher_fg.png"),
        432,
        adaptive_foreground(),
    )


if __name__ == "__main__":
    main()
