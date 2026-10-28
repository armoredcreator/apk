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
assert 'gain=clamp(gain,vec3(.84),vec3(1.18))' in shader, "export must cap compound regional gains"
assert 'float fade=.35+.65*smoothstep(.02,.12,lum)' in shader, "protect shadows and highlights in export"
math=Path("app/src/main/java/com/frame/framematch/RegionMath.java").read_text(encoding="utf-8")
assert 'gain=clamp(gain,.84f,1.18f)' in math, "preview must use identical bounds"
assert 'float fade=.35f+.65f*smoothstep(.02f,.12f,lum)' in math, "preview needs tonal protection"
assert 'blendStable' in math and 'compact(frameOffset(uri,false,500_000L))' in main, "early boundary sample is missing"
assert 'release=2_800_000L' in Path("app/src/main/java/com/frame/framematch/ContinuityZoom.java").read_text(encoding="utf-8")
choices=Path("app/src/main/java/com/frame/framematch/MatchChoices.java").read_text(encoding="utf-8")
assert 'public static final int COUNT=6' in choices
assert 'scaled(calibrated,.45f,.45f,.45f,.45f)' in choices
assert 'scaled(calibrated,.9f,1f,0f,0f)' in choices
assert 'scaled(calibrated,.3f,0f,1f,1f)' in choices
assert 'selectedModes.set(clipIndex,mode)' in main
assert 'MatchResult result=computeChoices(firstFrames,lastFrames,earlyFrames,nearEndFrames,' in main
assert 'if(regionSwitch.isChecked() && selectedModes.get(i)!=MatchChoices.ORIGINAL)' in main
assert 'HorizontalScrollView scroll=new HorizontalScrollView(this)' in main
assert 'new Bitmap[3+MatchChoices.COUNT]' in main
assert 'perCutZones' in main and 'chooseCutZone' in main
assert 'cutIntensities' in main and 'chooseCutStrength' in main
assert 'MatchChoices.CUSTOM' in main
master=Path("app/src/main/java/com/frame/framematch/MasterColorMatch.java").read_text(encoding="utf-8")
assert 'final Bitmap masterLast=lasts.get(0)' in main, "reference must always come from first clip"
assert 'final Bitmap masterNear=ends.get(0)' in main, "early palette sample must always come from first clip"
assert 'MasterColorMatch.calibrate(masterBoundary,masterNearby' in main, "all scene color matches must use Clip 1"
assert 'RegionMath.Plan previous=output.selected.get(i-1)' not in main, "cascaded color reference reintroduced"
assert 'RegionMath.analyze(' in master and 'RegionMath.blendStable' in master
assert 'ContinuityZoom.plan(timeline,adjacentFace)' in main, "movement continuity still uses adjacent clips"
assert '"CLIP 1: cores"' in main
print("Shader SDR, master Clip 1, seis alternativas, cor por zona e exportação: OK")

quality=Path("app/src/main/java/com/frame/framematch/QualityProfile.java").read_text(encoding="utf-8")
assert "outputSize(boolean followMaster)" in quality
assert "frameRateCeiling(boolean followMaster)" in quality
assert "bitrateEstimated" in quality
assert 'MediaFormat.KEY_FRAME_RATE' in main and 'MediaFormat.KEY_BIT_RATE' in main
assert 'matchSizeSwitch.setChecked(true)' in main
assert 'matchBitrateSwitch.setChecked(false)' in main
assert 'matchFpsSwitch.setChecked(false)' in main
assert 'enableEverything()' in main and 'applyModeToAll()' in main
assert 'item.setFrameRate(masterProfile.frameRateCeiling(true))' in main
assert '.setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()' in main
assert 'masterProfile.outputSize(matchSizeSwitch.isChecked())' in main
assert 'METADATA_KEY_VIDEO_WIDTH' in main and 'METADATA_KEY_VIDEO_HEIGHT' in main
print("QualityProfile, opções simultâneas e seleção em lote: OK")
