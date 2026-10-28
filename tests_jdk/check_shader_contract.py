#!/usr/bin/env python3
"""Regression guard: preview and exported SDR footage must apply RGB gains in the same domain."""
from pathlib import Path
shader=Path("app/src/main/java/com/frame/framematch/RegionEffect.java").read_text(encoding="utf-8")
main=Path("app/src/main/java/com/frame/framematch/MainActivity.java").read_text(encoding="utf-8")
assert 'vec3 r=clamp(c.rgb,0.0,1.0)' in shader, "shader must grade SDR encoded RGB exactly like CPU preview"
assert 'gl_FragColor=vec4(clamp(grade,0.0,1.0),c.a)' in shader, "shader output must not exponentiate gains"
assert 'pow(clamp(grade' not in shader, "gamma power incorrectly amplifies gains"
assert 'intensity.setProgress(65)' in main, "default grade must remain conservative"
assert 'alignSwitch.setChecked(false)' in main, "auto zoom must not be applied by default"
assert 'release=2_800_000L' in Path("app/src/main/java/com/frame/framematch/ContinuityZoom.java").read_text(encoding="utf-8")
print("Shader SDR / CPU-preview equivalence contract: OK")
