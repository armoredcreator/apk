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
assert 'blendStable' in math and 'compact(frameOffset(uri,false,(selectedOffset+500)*1000L))' in main, "early boundary sample is missing"
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
assert 'new Bitmap[4+MatchChoices.COUNT]' in main
assert 'perCutZones' in main and 'chooseCutZone' in main
assert 'cutIntensities' in main and 'chooseCutStrength' in main
assert 'MatchChoices.CUSTOM' in main
master=Path("app/src/main/java/com/frame/framematch/MasterColorMatch.java").read_text(encoding="utf-8")
assert 'final Bitmap masterLast=lasts.get(0)' in main, "reference must always come from first clip"
assert 'final Bitmap masterNear=ends.get(0)' in main, "early palette sample must always come from first clip"
assert 'TemporalColor.consolidate(a,b,c)' in main, "all scene color matches must use Clip 1"
assert 'RegionMath.Plan previous=output.selected.get(i-1)' not in main, "cascaded color reference reintroduced"
assert 'RegionMath.analyze(' in master and 'RegionMath.blendStable' in master
assert 'lastLandmarks.get(i-1),firstLandmarks.get(i)' in main, "movement continuity still uses adjacent clips"
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

align=Path("app/src/main/java/com/frame/framematch/AlignmentMath.java").read_text(encoding="utf-8")
face=Path("app/src/main/java/com/frame/framematch/FaceTimeline.java").read_text(encoding="utf-8")
zoom=Path("app/src/main/java/com/frame/framematch/ContinuityZoom.java").read_text(encoding="utf-8")
assert 'setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)' in face
assert 'FaceLandmark.LEFT_EYE' in face and 'FaceLandmark.RIGHT_EYE' in face
assert 'ContinuityZoom.facePlan(' in main
assert 'lastLandmarks.get(i-1),firstLandmarks.get(i)' in main
assert 'AlignmentMath.estimate' in zoom
assert 'Math.abs(tx)>.16f||Math.abs(ty)>.16f' in align
assert 'Bitmap[] previews=new Bitmap[4+MatchChoices.COUNT]' in main
assert 'overlay(previews[1],small(aligned))' in main
print("Facial landmark match, onion skin preview and constraints: OK")

entry=Path("app/src/main/java/com/frame/framematch/EntranceMatch.java").read_text(encoding="utf-8")
color=Path("app/src/main/java/com/frame/framematch/TemporalColor.java").read_text(encoding="utf-8")
assert 'EntranceMatch.choose(finalPose.get(i-1),candidates,times)' in main
assert 'smartEntrySwitch.setChecked(false)' in main
assert 'setStartPositionMs(i==0?0:entryOffsetsMs.get(i))' in main
assert 'TemporalColor.consolidate(a,b,c)' in main
assert 'max-min>.14f' in color
assert 'choice.offsetMs<=1500' in entry
print("Safe opt-in temporal entry clipping and three-sample color grade: OK")

assert 'private void renderCutPreview(int nextClip)' in main
assert 'setEndPositionMs(ends[k])' in main
assert 'new VideoView(this)' in main
assert 'previewPlayer.setOnPreparedListener' in main
assert 'if(k==1&&!(cinematicZoomSwitch.isChecked()&&clips.size()==2)&&zooms.get(idx)!=null)' in main
assert 'CinematicCutZoom.before(starts[k],durationMs(clips.get(idx)),33f)' in main
assert 'CinematicCutZoom.after(starts[k],durationMs(clips.get(idx)),33f,' in main
assert 'endFaces.get(idx-1),tracks.get(idx)' in main
assert 'endFaces.get(i-1),tracks.get(i)' in main
camera=Path('app/src/main/java/com/frame/framematch/CinematicCutZoom.java').read_text(encoding='utf-8')
# Experimental V39-target implementation: source timeline and ONE smooth recede.
# Numeric continuity, monotonicity, cut-scale parity and end frame are checked
# frame-by-frame by ZoomTimelineSelfTest.java (not by brittle source strings).
assert 'ZoomTimeline.approach(timeUs,durationUs,3_000_000L,peak)' in camera, "approach must start 3s before the cut"
assert 'ZoomTimeline.recede(timeUs,visibleDurationUs(),peak)' in camera, "camera must use a single recede trajectory"
assert 'RETURN_RATIO' not in camera and 'v37Return' not in camera, "old segmented return table was reintroduced"
assert 'alvo V39' in main, "Android UI must identify V39 as visual target"
assert 'Pixel parity with the approved desktop V39 is not yet demonstrated' in camera, "do not claim unverified desktop V39 parity"
assert 'CinematicFollowMath.translation(' in camera, "recede must account for subject position"
assert 'cinematicZoomSwitch.setChecked(false)' in main
assert '▶ PRÉVIA DESTA TRANSIÇÃO' in main
print("Cut preview: two clipped segments, color/geometry and integrated video playback OK")
