#!/usr/bin/env python3
"""Auditoria não destrutiva de paridade: referência Windows vs MP4 exportado pelo Android.

Requer: pip install numpy opencv-python
Uso: python tools/compare_v37_android.py referencia_V37.mp4 export_android.mp4 --csv auditoria.csv
NÃO aprova automaticamente qualidade percebida. Não altera nenhum vídeo.
"""
import argparse
import csv
import json
import math
from pathlib import Path

import cv2
import numpy as np


def read_video(path):
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        raise RuntimeError("Vídeo não pode ser lido: " + str(path))
    meta = {
        "frames": int(cap.get(cv2.CAP_PROP_FRAME_COUNT)),
        "width": int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)),
        "height": int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)),
        "fps": float(cap.get(cv2.CAP_PROP_FPS)),
    }
    return cap, meta


def lab_median(frame):
    lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
    h, w = lab.shape[:2]
    crop = lab[int(h * 0.32):int(h * 0.67), int(w * 0.25):int(w * 0.75)]
    return np.median(crop.reshape(-1, 3), axis=0).astype(np.float32)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("reference", type=Path)
    ap.add_argument("android", type=Path)
    ap.add_argument("--csv", type=Path, default=Path("auditoria_android.csv"))
    args = ap.parse_args()
    a, am = read_video(args.reference)
    b, bm = read_video(args.android)
    rows = []
    try:
        if (am["width"], am["height"]) != (bm["width"], bm["height"]):
            raise RuntimeError("As resoluções não são iguais: " + str((am, bm)))
        if not math.isclose(am["fps"], bm["fps"], abs_tol=0.01):
            raise RuntimeError("FPS não coincide: " + str((am, bm)))
        previous_a = previous_b = None
        i = 0
        while True:
            ok_a, fa = a.read()
            ok_b, fb = b.read()
            if ok_a != ok_b:
                raise RuntimeError("Contagem de quadros diferente no quadro " + str(i))
            if not ok_a:
                break
            sa = cv2.resize(fa, (120, 216), interpolation=cv2.INTER_AREA)
            sb = cv2.resize(fb, (120, 216), interpolation=cv2.INTER_AREA)
            la, lb = lab_median(sa), lab_median(sb)
            rows.append({
                "frame": i,
                "time_s": round(i / am["fps"], 6),
                "pixel_mae": round(float(cv2.absdiff(sa, sb).mean()), 4),
                "L_ref": round(float(la[0]), 3),
                "L_android": round(float(lb[0]), 3),
                "a_delta": round(float(lb[1] - la[1]), 3),
                "b_delta": round(float(lb[2] - la[2]), 3),
                "L_jump_ref": round(float(abs(la[0] - previous_a[0])) if previous_a is not None else 0, 3),
                "L_jump_android": round(float(abs(lb[0] - previous_b[0])) if previous_b is not None else 0, 3),
            })
            previous_a, previous_b = la, lb
            i += 1
    finally:
        a.release()
        b.release()
    if not rows:
        raise RuntimeError("Nenhum quadro comparado")
    args.csv.parent.mkdir(parents=True, exist_ok=True)
    with args.csv.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    worst = sorted(rows, key=lambda row: row["pixel_mae"], reverse=True)[:10]
    flashes = sorted(rows, key=lambda row: row["L_jump_android"], reverse=True)[:10]
    print(json.dumps({
        "reference": str(args.reference),
        "android": str(args.android),
        "metadata_reference": am,
        "metadata_android": bm,
        "compared_frames": len(rows),
        "pixel_mae_mean": round(sum(r["pixel_mae"] for r in rows) / len(rows), 4),
        "worst_pixel_frames": worst,
        "largest_light_jumps_android": flashes,
        "csv": str(args.csv),
        "conclusion": "DIAGNOSTICO_APENAS_EXIGE_REVISAO_VISUAL",
    }, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
