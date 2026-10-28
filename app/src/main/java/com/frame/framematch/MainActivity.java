package com.frame.framematch;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import com.google.mlkit.vision.face.FaceDetector;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.VideoEncoderSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Standalone Android port, alpha. Uses Android Media3, not desktop Python/OpenCV. */
@UnstableApi
public class MainActivity extends Activity {
    private static final int OPEN_VIDEOS=11, SAVE_VIDEO=12, MAX_CLIPS=16;
    private final ArrayList<Uri> clips=new ArrayList<>();
    private final ArrayList<RegionMath.Plan> plans=new ArrayList<>();
    private final ArrayList<FaceTimeline> tracks=new ArrayList<>();
    private final ArrayList<ContinuityZoom> zooms=new ArrayList<>();
    // V39 uses a two-sided geometry plan: anticipation before the cut, retreat after it.
    private final ArrayList<ContinuityZoom> preZooms=new ArrayList<>();
    // Cut i (between clips i-1 and i) has five alternatives, and one explicit selection.
    private final ArrayList<RegionMath.Plan[]> alternatives=new ArrayList<>();
    private final ArrayList<Integer> selectedModes=new ArrayList<>();
    private final ArrayList<Integer> cutIntensities=new ArrayList<>();
    private final ArrayList<int[]> perCutZones=new ArrayList<>();
    // Lower resolution cached boundary frames avoid rescanning videos when changing a look.
    private final ArrayList<Bitmap> firstFrames=new ArrayList<>(), lastFrames=new ArrayList<>();
    private final ArrayList<Bitmap> earlyFrames=new ArrayList<>(), nearEndFrames=new ArrayList<>();
    private final ArrayList<Bitmap> oneSecondFrames=new ArrayList<>(), oneSecondBeforeEndFrames=new ArrayList<>();
    private final ArrayList<RegionMath.FaceBox> endFaces=new ArrayList<>();
    private final ArrayList<AlignmentMath.Pose> startPoses=new ArrayList<>(), endPoses=new ArrayList<>();
    private final ArrayList<Long> entryOffsetsMs=new ArrayList<>();
    private final ArrayList<Long> clipDurationsUs=new ArrayList<>();
    private android.widget.CheckBox smartEntrySwitch;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout clipList, container, previews;
    private TextView status, subtitle;
    private SeekBar intensity;
    private ProgressBar progress;
    private Button addButton, analyzeButton, exportButton, cancelButton;
    private android.widget.CheckBox alignSwitch, regionSwitch;
    private SeekBar faceDuration,faceStrength;
    private android.widget.CheckBox matchSizeSwitch, matchBitrateSwitch, matchFpsSwitch;
    private TextView masterInfo;
    private Button enableAllButton, batchModeButton, naturalButton;
    private QualityProfile masterProfile;
    private volatile boolean busy=false, analyzed=false;
    private volatile float analyzedStrength=1f;
    private Transformer transformer;
    private File temporaryOutput;
    private File previewOutput;
    private VideoView previewPlayer;
    private boolean previewRendering=false;
    private Uri outputUri;
    private boolean ended=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final ScrollView scroller=new ScrollView(this);
        container=new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(14),dp(16),dp(14),dp(22));scroller.addView(container);
        setContentView(scroller);
        TextView title=text("FrameMatch Android",22,true); container.addView(title);
        subtitle=text("CLIP 1 = referência de COR fixa | cortes adjacentes = movimento e enquadramento",13,false);
        container.addView(subtitle);
        addButton=button("+ ADICIONAR VÍDEOS",v->pick()); container.addView(addButton);
        clipList=new LinearLayout(this); clipList.setOrientation(LinearLayout.VERTICAL);container.addView(clipList);
        container.addView(text("Intensidade da correção (0–100%)",13,true));
        intensity=new SeekBar(this);intensity.setMax(100);intensity.setProgress(65);container.addView(intensity);
        container.addView(text("65% inicial: correção moderada. A cor permanece estável em todo o clipe; só o alinhamento é limitado aos 3s iniciais.",12,false));
        regionSwitch=new android.widget.CheckBox(this);
        regionSwitch.setText("Corrigir céu, pele, lábios e cenário separadamente (GPU)");
        regionSwitch.setChecked(true);container.addView(regionSwitch);
        alignSwitch=new android.widget.CheckBox(this);
        alignSwitch.setText("Encaixe facial automático por olhos entre clipes consecutivos (experimental)");
        alignSwitch.setChecked(false);container.addView(alignSwitch);
        smartEntrySwitch=new android.widget.CheckBox(this);
        smartEntrySwitch.setText("Buscar melhor quadro de entrada (até 1,5s; pode cortar início da fala)");
        smartEntrySwitch.setChecked(false);container.addView(smartEntrySwitch);
        smartEntrySwitch.setOnCheckedChangeListener((b,checked)->invalidateAnalysis());
        container.addView(text("Força do encaixe facial (0–100%)",12,true));
        faceStrength=new SeekBar(this);faceStrength.setMax(100);faceStrength.setProgress(70);
        container.addView(faceStrength);
        container.addView(text("Duração do retorno ao original (0,5 a 3 segundos)",12,true));
        faceDuration=new SeekBar(this);faceDuration.setMax(2500);faceDuration.setProgress(1500);
        container.addView(faceDuration);
        SeekBar.OnSeekBarChangeListener faceChanged=new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                if(fromUser)invalidateAnalysis();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        };
        faceStrength.setOnSeekBarChangeListener(faceChanged);
        faceDuration.setOnSeekBarChangeListener(faceChanged);
        regionSwitch.setOnCheckedChangeListener((b,checked)->invalidateAnalysis());
        container.addView(text("QUALIDADE DE EXPORTAÇÃO — CLIP 1 COMO REFERÊNCIA",13,true));
        masterInfo=text("Adicione o Clip 1 para ver o padrão de qualidade.",12,false);
        container.addView(masterInfo);
        matchSizeSwitch=new android.widget.CheckBox(this);
        matchSizeSwitch.setText("Igualar resolução e proporção ao Clip 1 (com limite de compatibilidade)");
        matchSizeSwitch.setChecked(true);container.addView(matchSizeSwitch);
        matchBitrateSwitch=new android.widget.CheckBox(this);
        matchBitrateSwitch.setText("Solicitar taxa de bits semelhante à do Clip 1");
        matchBitrateSwitch.setChecked(false);container.addView(matchBitrateSwitch);
        matchFpsSwitch=new android.widget.CheckBox(this);
        matchFpsSwitch.setText("Limitar FPS ao do Clip 1 quando for superior (sem inventar quadros)");
        matchFpsSwitch.setChecked(false);container.addView(matchFpsSwitch);
        enableAllButton=button("ATIVAR TODAS AS CORREÇÕES COMPATÍVEIS",v->enableEverything());
        container.addView(enableAllButton);
        container.addView(text("Ativar tudo inclui o zoom experimental. Você pode desligá-lo depois. As 6 alternativas de cor são EXCLUSIVAS; use Ajuste fino para combinar regiões.",12,false));
        alignSwitch.setOnCheckedChangeListener((b,checked)->invalidateAnalysis());
        intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean from){if(from)invalidateAnalysis();}
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        container.addView(text("O Clip 1 é a referência de cor fixa para TODOS os próximos clipes. Compare seis tentativas em cada corte e escolha a melhor.",12,false));
        analyzeButton=button("GERAR TENTATIVAS DE COR PARA CADA CORTE",v->analyze());
        container.addView(analyzeButton);
        naturalButton=button("AUTO NATURAL — ESCOLHER MELHOR TENTATIVA EM TODOS OS CORTES",
            v->autoNaturalAll());
        container.addView(naturalButton);
        batchModeButton=button("APLICAR TENTATIVA DO CORTE 1 → 2 A TODOS",v->applyModeToAll());
        container.addView(batchModeButton);
        previews=new LinearLayout(this); previews.setOrientation(LinearLayout.VERTICAL);container.addView(previews);
        container.addView(text("PRÉVIA RÁPIDA: assista ao corte com as correções antes de exportar tudo.",12,true));
        exportButton=button("UNIR E EXPORTAR MP4",v->prepareExport());container.addView(exportButton);
        cancelButton=button("CANCELAR EXPORTAÇÃO",v->cancelExport());container.addView(cancelButton);
        cancelButton.setVisibility(View.GONE);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);container.addView(progress);
        status=text("Adicione pelo menos 2 vídeos. Você pode mudar a ordem com as setas.",13,false);
        container.addView(status);
        refresh();
    }

    private int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(0,dp(7),0,dp(5));
        if(bold)t.setTypeface(null,1);return t;
    }
    private Button button(String s,View.OnClickListener action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(action);return b;}
    private void say(String s){status.setText(s);}
    private void error(String s){say("Erro: "+s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private void pick(){
        if(busy)return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,OPEN_VIDEOS);
    }
    private String label(Uri uri){
        try (Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())return c.getString(0);
        } catch(Exception ignored){}
        return uri.getLastPathSegment()==null?"vídeo":uri.getLastPathSegment();
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(req==OPEN_VIDEOS){
            ArrayList<Uri> selected=new ArrayList<>();
            if(data.getClipData()!=null){
                for(int k=0;k<data.getClipData().getItemCount();k++)selected.add(data.getClipData().getItemAt(k).getUri());
            } else if(data.getData()!=null)selected.add(data.getData());
            for(Uri uri:selected){
                if(clips.size()>=MAX_CLIPS)break;
                if(clips.contains(uri))continue;
                clips.add(uri);
                try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            }
            invalidateAnalysis();refresh();loadMasterProfile();
        } else if(req==SAVE_VIDEO){
            Uri dest=data.getData(); if(dest==null)return;
            if(clips.contains(dest)){error("O destino não pode ser um vídeo original.");return;}
            outputUri=dest;
            if(analyzed)startExport();else analyzeAndExport();
        }
    }

    private void invalidateAnalysis(){
        analyzed=false;plans.clear();tracks.clear();zooms.clear();preZooms.clear();alternatives.clear();
        selectedModes.clear();cutIntensities.clear();perCutZones.clear();firstFrames.clear();lastFrames.clear();earlyFrames.clear();
        nearEndFrames.clear();oneSecondFrames.clear();oneSecondBeforeEndFrames.clear();endFaces.clear();startPoses.clear();endPoses.clear();entryOffsetsMs.clear();clipDurationsUs.clear();previews.removeAllViews();
    }
    private void refresh(){
        clipList.removeAllViews();
        for(int i=0;i<clips.size();i++){
            final int k=i;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            TextView file=text((i+1)+". "+label(clips.get(i)),12,false);
            row.addView(file,new LinearLayout.LayoutParams(0,dp(52),1f));
            Button up=button("↑",v->{Collections.swap(clips,k,k-1);invalidateAnalysis();refresh();loadMasterProfile();});
            up.setEnabled(i>0&&!busy);row.addView(up,new LinearLayout.LayoutParams(dp(46),dp(54)));
            Button down=button("↓",v->{Collections.swap(clips,k,k+1);invalidateAnalysis();refresh();loadMasterProfile();});
            down.setEnabled(i<clips.size()-1&&!busy);row.addView(down,new LinearLayout.LayoutParams(dp(46),dp(54)));
            Button del=button("×",v->{clips.remove(k);invalidateAnalysis();refresh();loadMasterProfile();});
            del.setEnabled(!busy);row.addView(del,new LinearLayout.LayoutParams(dp(46),dp(54)));
            clipList.addView(row);
        }
        addButton.setEnabled(!busy && clips.size()<MAX_CLIPS);
        enableAllButton.setEnabled(!busy);
        batchModeButton.setEnabled(!busy && analyzed && clips.size()>=3);
        naturalButton.setEnabled(!busy && analyzed);
        matchSizeSwitch.setEnabled(!busy);
        matchBitrateSwitch.setEnabled(!busy);
        matchFpsSwitch.setEnabled(!busy);
        analyzeButton.setEnabled(!busy && clips.size()>=2);
        exportButton.setEnabled(!busy && clips.size()>=2);
        intensity.setEnabled(!busy);regionSwitch.setEnabled(!busy);alignSwitch.setEnabled(!busy);
        faceStrength.setEnabled(!busy);faceDuration.setEnabled(!busy);smartEntrySwitch.setEnabled(!busy);
        cancelButton.setVisibility(busy&&transformer!=null?View.VISIBLE:View.GONE);
    }

    private static int metadataInteger(MediaMetadataRetriever m,int key){
        try{
            String v=m.extractMetadata(key);
            return v==null?0:(int)Math.round(Double.parseDouble(v));
        }catch(Exception ignored){return 0;}
    }

    /** Obtain video stream FPS/bitrate (not container + audio bitrate) when available. */
    private int[] readVideoTrack(Uri uri){
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(this,uri,null);
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null && mime.startsWith("video/")){
                    int bitrate=f.containsKey(MediaFormat.KEY_BIT_RATE)?f.getInteger(MediaFormat.KEY_BIT_RATE):0;
                    int fps=f.containsKey(MediaFormat.KEY_FRAME_RATE)?f.getInteger(MediaFormat.KEY_FRAME_RATE):0;
                    return new int[]{bitrate,fps};
                }
            }
        }catch(Exception ignored){}finally{extractor.release();}
        return new int[]{0,0};
    }

    private QualityProfile inspectMaster(Uri uri) throws Exception {
        int[] stream=readVideoTrack(uri);
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try{
            m.setDataSource(this,uri);
            return QualityProfile.fromMaster(
                metadataInteger(m,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                metadataInteger(m,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                metadataInteger(m,MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                stream[0]>0?stream[0]:metadataInteger(m,MediaMetadataRetriever.METADATA_KEY_BITRATE),
                stream[1]>0?stream[1]:metadataInteger(m,MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));
        }finally{m.release();}
    }
    private void loadMasterProfile(){
        if(clips.isEmpty()){
            masterProfile=null;
            masterInfo.setText("Adicione o Clip 1 para ver o padrão de qualidade.");
            return;
        }
        try{
            masterProfile=inspectMaster(clips.get(0));
            masterInfo.setText("Referência: "+masterProfile.describe()+
                "\\nA qualidade técnica pode ser normalizada, mas detalhes perdidos não são recuperáveis.");
        }catch(Exception e){
            masterProfile=null;
            masterInfo.setText("Metadados do Clip 1 indisponíveis: use a resolução de segurança 540×960.");
        }
    }
    private void enableEverything(){
        if(busy)return;
        // Apply independent techniques simultaneously; color LOOKs remain exclusive.
        boolean changed=!regionSwitch.isChecked()||!alignSwitch.isChecked();
        regionSwitch.setChecked(true);
        alignSwitch.setChecked(true);
        matchSizeSwitch.setChecked(true);
        matchBitrateSwitch.setChecked(true);
        matchFpsSwitch.setChecked(true);
        if(!changed)invalidateAnalysis();
        say("Todas as correções compatíveis ativadas. Zoom é experimental e só atua no início do clipe.");
        refresh();
    }
    private void autoNaturalAll(){
        if(busy||!analyzed||selectedModes.size()<2)return;
        for(int i=1;i<selectedModes.size();i++)selectedModes.set(i,-1);
        recomputeSelected(1);
    }
    private void applyModeToAll(){
        if(busy||!analyzed||selectedModes.size()<3)return;
        int mode=selectedModes.get(1);
        int strength=cutIntensities.get(1);
        int[] zones=perCutZones.get(1).clone();
        for(int i=2;i<selectedModes.size();i++){
            selectedModes.set(i,mode);
            cutIntensities.set(i,strength);
            perCutZones.set(i,zones.clone());
        }
        recomputeSelected(1);
    }
    private void analyze(){if(clips.size()<2||busy)return;doAnalysis(false);}
    private void analyzeAndExport(){if(clips.size()<2||busy)return;doAnalysis(true);}
    private static final class MatchResult {
        final ArrayList<RegionMath.Plan> selected=new ArrayList<>();
        final ArrayList<RegionMath.Plan[]> variants=new ArrayList<>();
        final ArrayList<ContinuityZoom> alignments=new ArrayList<>();
        final ArrayList<ContinuityZoom> preAlignments=new ArrayList<>();
        final ArrayList<Bitmap[]> previewRows=new ArrayList<>();
        final ArrayList<Integer> chosenModes=new ArrayList<>();
    }

    private Bitmap compact(Bitmap bitmap) {
        if(bitmap.getWidth()<=256)return bitmap;
        int h=Math.max(2,Math.round(bitmap.getHeight()*256f/bitmap.getWidth()));
        Bitmap copy=Bitmap.createScaledBitmap(bitmap,256,h,true);
        bitmap.recycle();
        return copy;
    }

    /**
     * Clip 1 supplies the immutable color reference for EVERY subsequent clip.
     * Previous clip is consulted only for alignment and a visual side-by-side
     * preview; its selected color plan must not enter a later color calibration.
     */
    private MatchResult computeChoices(ArrayList<Bitmap> firsts,ArrayList<Bitmap> lasts,
                                       ArrayList<Bitmap> earlys,ArrayList<Bitmap> ends,
                                       ArrayList<Bitmap> laterFrames,ArrayList<Bitmap> earlierEnds,
                                       ArrayList<RegionMath.FaceBox> lastFaces,
                                       ArrayList<AlignmentMath.Pose> firstLandmarks,
                                       ArrayList<AlignmentMath.Pose> lastLandmarks,
                                       ArrayList<FaceTimeline> faceTracks,
                                       ArrayList<Long> durationsUs,
                                       ArrayList<Integer> modes,ArrayList<Integer> strengths,
                                       ArrayList<int[]> zones,float strength,
                                       boolean regional,boolean align) {
        MatchResult output=new MatchResult();
        output.selected.add(RegionMath.Plan.identity());
        output.variants.add(MatchChoices.candidates(RegionMath.Plan.identity()));
        output.alignments.add(null);
        output.preAlignments.add(null);
        output.chosenModes.add(MatchChoices.ORIGINAL);

        final Bitmap masterLast=lasts.get(0);
        final Bitmap masterNear=ends.get(0);
        final Bitmap masterEarlier=earlierEnds.get(0);
        final RegionMath.FaceBox masterFace=lastFaces.get(0);
        final MasterColorMatch.Sample masterBoundary=new MasterColorMatch.Sample(
            pixels(masterLast),masterLast.getWidth(),masterLast.getHeight(),masterFace);
        final MasterColorMatch.Sample masterNearby=new MasterColorMatch.Sample(
            pixels(masterNear),masterNear.getWidth(),masterNear.getHeight(),masterFace);
        final MasterColorMatch.Sample masterThird=new MasterColorMatch.Sample(
            pixels(masterEarlier),masterEarlier.getWidth(),masterEarlier.getHeight(),masterFace);

        for(int i=1;i<firsts.size();i++){
            // Camera motion follows clip i-1; colors ALWAYS follow clip 1.
            RegionMath.FaceBox adjacentFace=lastFaces.get(i-1);
            Bitmap target=firsts.get(i);
            FaceTimeline timeline=faceTracks.get(i);
            RegionMath.FaceBox firstFace=timeline==null?null:timeline.first();
            RegionMath.Plan calibrated=RegionMath.Plan.identity();
            if(regional){
                RegionMath.FaceBox earlyFace=timeline==null?null:timeline.at(500_000L);
                MasterColorMatch.Sample targetFirst=new MasterColorMatch.Sample(
                    pixels(target),target.getWidth(),target.getHeight(),firstFace);
                MasterColorMatch.Sample targetNear=new MasterColorMatch.Sample(
                    pixels(earlys.get(i)),earlys.get(i).getWidth(),earlys.get(i).getHeight(),earlyFace);
                Bitmap later=laterFrames.get(i);
                RegionMath.FaceBox laterFace=timeline==null?null:timeline.at(1_000_000L);
                MasterColorMatch.Sample targetThird=new MasterColorMatch.Sample(
                    pixels(later),later.getWidth(),later.getHeight(),laterFace);
                RegionMath.Plan a=RegionMath.analyze(masterBoundary.argb,masterBoundary.width,
                    masterBoundary.height,masterBoundary.face,targetFirst.argb,targetFirst.width,
                    targetFirst.height,targetFirst.face,strength);
                RegionMath.Plan b=RegionMath.analyze(masterNearby.argb,masterNearby.width,
                    masterNearby.height,masterNearby.face,targetNear.argb,targetNear.width,
                    targetNear.height,targetNear.face,strength);
                RegionMath.Plan c=RegionMath.analyze(masterThird.argb,masterThird.width,
                    masterThird.height,masterThird.face,targetThird.argb,targetThird.width,
                    targetThird.height,targetThird.face,strength);
                calibrated=TemporalColor.consolidate(a,b,c);
            }
            RegionMath.Plan[] choices=MatchChoices.candidates(calibrated,strengths.get(i),zones.get(i));
            int mode=modes.get(i);
            if(mode==-1){
                RegionMath.FaceBox earlyFace=timeline==null?null:timeline.at(500_000L);
                mode=NaturalSelector.best(masterBoundary,masterNearby,
                    new MasterColorMatch.Sample(pixels(target),target.getWidth(),target.getHeight(),firstFace),
                    new MasterColorMatch.Sample(pixels(earlys.get(i)),earlys.get(i).getWidth(),
                        earlys.get(i).getHeight(),earlyFace),choices);
            }
            if(!MatchChoices.valid(mode))throw new IllegalArgumentException("Tentativa de cor inválida");
            output.chosenModes.add(mode);
            output.variants.add(choices);
            output.selected.add(choices[mode]);

            ContinuityZoom.CutPlan cut=align?ContinuityZoom.cutPlan(
                lastLandmarks.get(i-1),firstLandmarks.get(i),
                faceStrength.getProgress()/100f,500L+faceDuration.getProgress(),
                durationsUs.get(i-1)):null;
            ContinuityZoom zoom=cut==null?null:cut.afterCut;
            output.alignments.add(zoom);
            output.preAlignments.add(cut==null?null:cut.beforeCut);

            // The two references are intentionally different: master colors vs. local cut.
            Bitmap previousPreview=i==1?lasts.get(0)
                :grade(lasts.get(i-1),output.selected.get(i-1),adjacentFace);
            Bitmap cutPrevious=previousPreview;
            ContinuityZoom beforeCut=output.preAlignments.get(i-1);
            if(beforeCut!=null)
                cutPrevious=beforeCut.previewAt(previousPreview,
                    Math.max(0L,durationsUs.get(i-1)-1L));
            Bitmap[] previews=new Bitmap[4+MatchChoices.COUNT];
            previews[0]=small(masterLast);
            previews[1]=small(cutPrevious);
            previews[2]=small(target);
            Bitmap aligned=zoom==null?target:zoom.previewAt(target,0L);
            Bitmap overlap=overlay(previews[1],small(aligned));
            previews[3]=overlap;
            for(int m=0;m<MatchChoices.COUNT;m++){
                Bitmap p=m==MatchChoices.ORIGINAL?target:grade(target,choices[m],firstFace);
                if(zoom!=null)p=zoom.preview(p);
                previews[4+m]=small(p);
                if(p!=target && p!=previews[4+m] && !p.isRecycled())p.recycle();
            }
            output.previewRows.add(previews);
            if(cutPrevious!=previousPreview&&!cutPrevious.isRecycled())cutPrevious.recycle();
            if(i>1 && !previousPreview.isRecycled())previousPreview.recycle();
        }
        return output;
    }

    private void installResult(MatchResult result) {
        selectedModes.clear();selectedModes.addAll(result.chosenModes);
        plans.clear();plans.addAll(result.selected);
        alternatives.clear();alternatives.addAll(result.variants);
        zooms.clear();zooms.addAll(result.alignments);
        preZooms.clear();preZooms.addAll(result.preAlignments);
        analyzed=true;analyzedStrength=intensity.getProgress()/100f;
        busy=false;progress.setProgress(100);
        renderPreviews(result.previewRows);refresh();
        say("Clip 1 é a referência MESTRA para todas as cenas. Escolha a correção de cada corte.");
    }

    private void chooseColor(int clipIndex,int mode){
        if(!analyzed||busy||clipIndex<1||clipIndex>=selectedModes.size()||(mode!=-1&&!MatchChoices.valid(mode)))return;
        if(selectedModes.get(clipIndex)==mode)return;
        selectedModes.set(clipIndex,mode);
        recomputeSelected(clipIndex);
    }

    private void chooseCutStrength(int clipIndex,int strength){
        if(!analyzed||busy||clipIndex<1||clipIndex>=cutIntensities.size())return;
        strength=Math.max(0,Math.min(150,strength));
        if(cutIntensities.get(clipIndex)==strength)return;
        cutIntensities.set(clipIndex,strength);
        recomputeSelected(clipIndex);
    }

    private void chooseCutZone(int clipIndex,int zone,int strength){
        if(!analyzed||busy||clipIndex<1||clipIndex>=perCutZones.size()||zone<0||zone>=4)return;
        strength=Math.max(0,Math.min(150,strength));
        int[] values=perCutZones.get(clipIndex);
        if(values[zone]==strength&&selectedModes.get(clipIndex)==MatchChoices.CUSTOM)return;
        values[zone]=strength;
        selectedModes.set(clipIndex,MatchChoices.CUSTOM);
        recomputeSelected(clipIndex);
    }

    private void recomputeSelected(int clipIndex){
        final ArrayList<Integer> selections=new ArrayList<>(selectedModes);
        final ArrayList<Integer> strengths=new ArrayList<>(cutIntensities);
        final ArrayList<int[]> zones=new ArrayList<>();
        for(int[] values:perCutZones)zones.add(values.clone());
        final float strength=intensity.getProgress()/100f;
        final boolean regional=regionSwitch.isChecked(),align=alignSwitch.isChecked();
        busy=true;refresh();say("Aplicando tentativa "+
            (selections.get(clipIndex)==-1?"AUTO NATURAL":MatchChoices.LABELS[selections.get(clipIndex)])+
            " ao corte "+clipIndex+"; referência de cor fixa no Clip 1...");
        worker.submit(()->{
            try{
                MatchResult result=computeChoices(firstFrames,lastFrames,earlyFrames,nearEndFrames,
                    oneSecondFrames,oneSecondBeforeEndFrames,endFaces,startPoses,endPoses,tracks,clipDurationsUs,
                    selections,strengths,zones,strength,regional,align);
                main.post(()->{if(!ended)installResult(result);});
            }catch(Exception e){main.post(()->{
                busy=false;analyzed=false;refresh();
                error("Não foi possível recalcular as tentativas: "+e.getMessage()+
                    ". Analise os vídeos novamente.");
            });}
        });
    }

    private void doAnalysis(boolean thenExport){
        busy=true;ended=false;refresh();progress.setProgress(0);
        say("Preparando opções por corte; os arquivos originais não serão alterados...");
        final ArrayList<Uri> selected=new ArrayList<>(clips);
        final float strength=intensity.getProgress()/100f;
        final boolean regional=regionSwitch.isChecked(),align=alignSwitch.isChecked();
        worker.submit(()->{
            FaceDetector detector=FaceTimeline.createDetector();
            try{
                ArrayList<Bitmap> firsts=new ArrayList<>(), lasts=new ArrayList<>();
                ArrayList<Bitmap> earlys=new ArrayList<>(), ends=new ArrayList<>();
                ArrayList<Bitmap> later=new ArrayList<>(), earlierEnds=new ArrayList<>();
                ArrayList<RegionMath.FaceBox> faces=new ArrayList<>();
                ArrayList<AlignmentMath.Pose> beginPose=new ArrayList<>(),finalPose=new ArrayList<>();
                ArrayList<FaceTimeline> nextTracks=new ArrayList<>();
                ArrayList<Integer> modes=new ArrayList<>();
                ArrayList<Integer> strengths=new ArrayList<>();
                ArrayList<int[]> zones=new ArrayList<>();
                ArrayList<Long> entries=new ArrayList<>();
                ArrayList<Long> durationsUs=new ArrayList<>();
                for(int i=0;i<selected.size();i++){
                    Uri uri=selected.get(i);
                    durationsUs.add(durationMs(uri)*1000L);
                    Bitmap last=frame(uri,true);
                    Bitmap first=frame(uri,false);
                    long selectedOffset=0;
                    if(i>0&&smartEntrySwitch.isChecked()){
                        long[] times={0,250,500,750,1000,1250,1500};
                        AlignmentMath.Pose[] candidates=new AlignmentMath.Pose[times.length];
                        candidates[0]=FaceTimeline.pose(detector,first);
                        for(int q=1;q<times.length;q++){
                            Bitmap candidate=frameOffset(uri,false,times[q]*1000L);
                            candidates[q]=FaceTimeline.pose(detector,candidate);
                            candidate.recycle();
                        }
                        EntranceMatch.Choice choice=EntranceMatch.choose(finalPose.get(i-1),candidates,times);
                        if(EntranceMatch.safe(choice)){
                            selectedOffset=choice.offsetMs;
                            if(selectedOffset>0){first.recycle();first=frameOffset(uri,false,selectedOffset*1000L);}
                        }
                    }
                    entries.add(selectedOffset);
                    RegionMath.FaceBox lastFace=FaceTimeline.detect(detector,last);
                    finalPose.add(FaceTimeline.pose(detector,last));
                    beginPose.add(FaceTimeline.pose(detector,first));
                    firsts.add(compact(first));
                    lasts.add(compact(last));
                    earlys.add(compact(frameOffset(uri,false,(selectedOffset+500)*1000L)));
                    ends.add(compact(frameOffset(uri,true,500_000L)));
                    later.add(compact(frameOffset(uri,false,(selectedOffset+1000)*1000L)));
                    earlierEnds.add(compact(frameOffset(uri,true,1_000_000L)));
                    faces.add(lastFace);
                    nextTracks.add(i==0?null:FaceTimeline.scan(this,uri,Long.MAX_VALUE,detector)
                        .shifted(selectedOffset*1000L));
                    modes.add(i==0?MatchChoices.ORIGINAL:-1);
                    strengths.add(100);
                    zones.add(new int[]{100,100,100,100});
                    final int progressIndex=i+1;
                    main.post(()->{progress.setProgress(progressIndex*50/selected.size());
                        say("Preparando vídeo "+progressIndex+"/"+selected.size());});
                }
                MatchResult result=computeChoices(firsts,lasts,earlys,ends,later,earlierEnds,faces,beginPose,finalPose,nextTracks,
                    durationsUs,modes,strengths,zones,strength,regional,align);
                main.post(()->{
                    if(ended)return;
                    firstFrames.clear();firstFrames.addAll(firsts);
                    lastFrames.clear();lastFrames.addAll(lasts);
                    earlyFrames.clear();earlyFrames.addAll(earlys);
                    nearEndFrames.clear();nearEndFrames.addAll(ends);
                    oneSecondFrames.clear();oneSecondFrames.addAll(later);
                    oneSecondBeforeEndFrames.clear();oneSecondBeforeEndFrames.addAll(earlierEnds);
                    endFaces.clear();endFaces.addAll(faces);
                    startPoses.clear();startPoses.addAll(beginPose);
                    entryOffsetsMs.clear();entryOffsetsMs.addAll(entries);
                    endPoses.clear();endPoses.addAll(finalPose);
                    tracks.clear();tracks.addAll(nextTracks);
                    clipDurationsUs.clear();clipDurationsUs.addAll(durationsUs);
                    selectedModes.clear();selectedModes.addAll(modes);
                    cutIntensities.clear();cutIntensities.addAll(strengths);
                    perCutZones.clear();perCutZones.addAll(zones);
                    installResult(result);
                    if(thenExport)startExport();
                });
            }catch(Exception e){main.post(()->{busy=false;refresh();
                error("Não foi possível analisar as cenas: "+e.getMessage());});
            }finally{detector.close();}
        });
    }
    /** 50/50 onion-skin comparison for adjacent frames, never inserted into the MP4. */
    private Bitmap overlay(Bitmap previous,Bitmap next){
        Bitmap layer=Bitmap.createBitmap(previous.getWidth(),previous.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(layer);canvas.drawBitmap(previous,0,0,null);
        Paint alpha=new Paint(Paint.FILTER_BITMAP_FLAG);alpha.setAlpha(128);
        canvas.drawBitmap(next,null,
            new android.graphics.Rect(0,0,layer.getWidth(),layer.getHeight()),alpha);
        return layer;
    }
    private Bitmap small(Bitmap bmp){
        int w=144,h=Math.max(2,Math.round(bmp.getHeight()*144f/bmp.getWidth()));
        return Bitmap.createScaledBitmap(bmp,w,h,true);
    }
    private Bitmap frame(Uri uri,boolean last) throws Exception {
        return frameOffset(uri,last,0L);
    }
    private Bitmap frameOffset(Uri uri,boolean last,long offsetUs) throws Exception {
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{
            retriever.setDataSource(this,uri);
            long ms=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            long durationUs=ms*1000L;
            long at=last?Math.max(0,durationUs-42000L-offsetUs):Math.min(Math.max(0,durationUs-1),offsetUs);
            Bitmap bmp=retriever.getFrameAtTime(at,MediaMetadataRetriever.OPTION_CLOSEST);
            if(bmp==null)throw new Exception("Não foi possível decodificar o quadro de "+label(uri));
            int w=Math.min(480,bmp.getWidth());
            int h=Math.max(2,Math.round(w*(float)bmp.getHeight()/bmp.getWidth()));
            return Bitmap.createScaledBitmap(bmp,w,h,true);
        }finally{retriever.release();}
    }
    private int[] pixels(Bitmap bmp){
        int[] p=new int[bmp.getWidth()*bmp.getHeight()];
        bmp.getPixels(p,0,bmp.getWidth(),0,0,bmp.getWidth(),bmp.getHeight());return p;
    }
    private Bitmap grade(Bitmap bmp,RegionMath.Plan plan,RegionMath.FaceBox face){
        int[] c=RegionMath.preview(pixels(bmp),bmp.getWidth(),bmp.getHeight(),face,plan);
        return Bitmap.createBitmap(c,bmp.getWidth(),bmp.getHeight(),Bitmap.Config.ARGB_8888);
    }
    private void renderPreviews(ArrayList<Bitmap[]> rows){
        previews.removeAllViews();
        for(int i=0;i<rows.size();i++){
            final int clipIndex=i+1;
            Bitmap[] pictures=rows.get(i);
            previews.addView(text("CORTE "+clipIndex+" → "+(clipIndex+1)+
                " | cor acompanha Clip 1; enquadramento acompanha a cena anterior",13,true));
            LinearLayout comparison=new LinearLayout(this);
            comparison.setOrientation(LinearLayout.HORIZONTAL);
            String[] labels={"CLIP 1: cores","Fim anterior","Início original"};
            for(int j=0;j<3;j++){
                LinearLayout column=new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                ImageView image=new ImageView(this);
                image.setImageBitmap(pictures[j]);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                column.addView(image,new LinearLayout.LayoutParams(-1,dp(175)));
                TextView caption=text(labels[j],11,false);caption.setGravity(Gravity.CENTER);
                column.addView(caption);
                comparison.addView(column,new LinearLayout.LayoutParams(0,-2,1f));
            }
            previews.addView(comparison);
            previews.addView(text("Ponto de entrada: "+entryOffsetsMs.get(clipIndex)+" ms"+
                (entryOffsetsMs.get(clipIndex)>0?" (corta o início do vídeo/áudio; confira a fala)":" (início preservado)"),11,true));
            previews.addView(text("Sobreposição 50/50 do último frame anterior + primeiro frame alinhado:",11,true));
            ImageView alignedOverlay=new ImageView(this);
            alignedOverlay.setImageBitmap(pictures[3]);alignedOverlay.setAdjustViewBounds(true);
            previews.addView(alignedOverlay,new LinearLayout.LayoutParams(-1,dp(210)));
            previews.addView(button("AUTO NATURAL — recomendar para este corte",
                v->chooseColor(clipIndex,-1)));
            HorizontalScrollView scroll=new HorizontalScrollView(this);
            LinearLayout variants=new LinearLayout(this);
            variants.setOrientation(LinearLayout.HORIZONTAL);
            for(int m=0;m<MatchChoices.COUNT;m++){
                final int choice=m;
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(3),dp(4),dp(3),dp(5));
                ImageView image=new ImageView(this);
                image.setImageBitmap(pictures[4+m]);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.addView(image,new LinearLayout.LayoutParams(dp(126),dp(210)));
                boolean marked=selectedModes.get(clipIndex)==m;
                Button action=button((marked?"✓ ":"")+MatchChoices.LABELS[m],
                    v->chooseColor(clipIndex,choice));
                action.setEnabled(!busy&&!marked);
                action.setTextSize(11);
                card.addView(action,new LinearLayout.LayoutParams(dp(144),dp(55)));
                variants.addView(card);
            }
            scroll.addView(variants);
            previews.addView(scroll);
            final SeekBar fineTune=new SeekBar(this);
            fineTune.setMax(150);
            fineTune.setProgress(cutIntensities.get(clipIndex));
            previews.addView(text("Força deste corte: "+cutIntensities.get(clipIndex)+
                "% (100%=padrão, 0%=sem cor, até 150%=mais intenso)",11,false));
            previews.addView(fineTune);
            fineTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar sb,int p,boolean user){}
                @Override public void onStartTrackingTouch(SeekBar sb){}
                @Override public void onStopTrackingTouch(SeekBar sb){
                    chooseCutStrength(clipIndex,sb.getProgress());
                }
            });

            if(selectedModes.get(clipIndex)==MatchChoices.CUSTOM){
                previews.addView(text("Ajuste fino só neste corte — altere cada região separadamente:",12,true));
                String[] zones={"Cenário", "Céu/azul", "Pele", "Lábios"};
                for(int z=0;z<4;z++){
                    final int zone=z;
                    final SeekBar local=new SeekBar(this);
                    local.setMax(150);
                    local.setProgress(perCutZones.get(clipIndex)[z]);
                    previews.addView(text(zones[z]+": "+perCutZones.get(clipIndex)[z]+
                        "% (0=desligado; 100=calibração; 150=mais forte)",11,false));
                    previews.addView(local);
                    local.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                        @Override public void onProgressChanged(SeekBar seek,int p,boolean fromUser){}
                        @Override public void onStartTrackingTouch(SeekBar seek){}
                        @Override public void onStopTrackingTouch(SeekBar seek){
                            chooseCutZone(clipIndex,zone,seek.getProgress());
                        }
                    });
                }
            }
            previews.addView(button("▶ PRÉVIA DESTA TRANSIÇÃO (aprox. 4 segundos)",
                v->renderCutPreview(clipIndex)));
            previews.addView(text("Selecionada: "+MatchChoices.LABELS[selectedModes.get(clipIndex)]+
                " · Referência de cor fixa no Clip 1. Outros cortes não mudam.",11,false));
        }
    }

    private long durationMs(Uri uri) throws Exception{
        MediaMetadataRetriever mm=new MediaMetadataRetriever();
        try{
            mm.setDataSource(this,uri);
            String d=mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d==null?0:Long.parseLong(d);
        }finally{mm.release();}
    }

    /** Short real Media3 export, with the same shader and alignment as the full video. */
    private void renderCutPreview(int nextClip){
        if(busy||!analyzed||nextClip<1||nextClip>=clips.size())return;
        if(Math.abs(analyzedStrength-intensity.getProgress()/100f)>.001f){
            error("Analise as cenas novamente antes de gerar a prévia.");return;
        }
        try{
            long duration=durationMs(clips.get(nextClip-1));
            long previousStart=Math.max(nextClip==1?0:entryOffsetsMs.get(nextClip-1),duration-1500);
            long nextStart=entryOffsetsMs.get(nextClip);
            long nextDuration=durationMs(clips.get(nextClip));
            long nextEnd=Math.min(nextDuration,nextStart+2500);
            if(duration<=previousStart||nextEnd<=nextStart)throw new Exception("Cenas curtas demais para prévia");
            busy=true;previewRendering=true;refresh();progress.setProgress(0);
            previewOutput=new File(getCacheDir(),"framematch_preview.mp4");
            if(previewPlayer!=null){previewPlayer.stopPlayback();previewPlayer=null;}
            if(previewOutput.exists()&&!previewOutput.delete())throw new Exception("Não foi possível substituir a prévia antiga");

            ArrayList<EditedMediaItem> edits=new ArrayList<>();
            int[] sourceIndices={nextClip-1,nextClip};
            long[] starts={previousStart,nextStart};
            long[] ends={duration,nextEnd};
            for(int k=0;k<2;k++){
                int idx=sourceIndices[k];
                MediaItem media=new MediaItem.Builder().setUri(clips.get(idx))
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(starts[k]).setEndPositionMs(ends[k]).build()).build();
                ArrayList<androidx.media3.common.Effect> effects=new ArrayList<>();
                if(idx>0){
                    if(regionSwitch.isChecked()&&selectedModes.get(idx)!=MatchChoices.ORIGINAL)
                        effects.add(new RegionEffect(plans.get(idx),tracks.get(idx)));
                    // The beginning of this preview's first clip must not re-trigger
                    // the alignment intended for its OWN preceding cut.
                    if(k==0&&preZooms.get(idx)!=null)effects.add(preZooms.get(idx));
                    if(k==1&&zooms.get(idx)!=null)effects.add(zooms.get(idx));
                }
                int[] size=masterProfile==null?new int[]{540,960}:
                    masterProfile.outputSize(matchSizeSwitch.isChecked());
                effects.add(Presentation.createForWidthAndHeight(size[0],size[1],
                    Presentation.LAYOUT_SCALE_TO_FIT));
                EditedMediaItem.Builder item=new EditedMediaItem.Builder(media)
                    .setEffects(new Effects(Collections.emptyList(),effects));
                if(masterProfile!=null&&matchFpsSwitch.isChecked()&&masterProfile.frameRateCeiling(true)>0)
                    item.setFrameRate(masterProfile.frameRateCeiling(true));
                edits.add(item.build());
            }
            Composition composition=new Composition.Builder(
                EditedMediaItemSequence.withAudioAndVideoFrom(edits)).build();
            Transformer.Builder builder=new Transformer.Builder(this);
            if(masterProfile!=null&&matchBitrateSwitch.isChecked())
                builder.setEncoderFactory(new DefaultEncoderFactory.Builder(this)
                    .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()
                        .setBitrate(masterProfile.requestedBitrate).build()).build());
            transformer=builder.setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition c,ExportResult r){
                        transformer=null;previewRendering=false;busy=false;progress.setProgress(100);
                        refresh();playCutPreview();
                    }
                    @Override public void onError(Composition c,ExportResult r,ExportException e){
                        previewRendering=false;endExport("Falha ao gerar prévia: "+e.getMessage());
                    }
                }).build();
            say("Gerando prévia curta somente deste corte, com as correções selecionadas...");
            transformer.start(composition,previewOutput.getAbsolutePath());
            trackProgress();
        }catch(Exception e){previewRendering=false;endExport("Falha na prévia: "+e.getMessage());}
    }

    private void playCutPreview(){
        if(previewOutput==null||!previewOutput.isFile()||previewOutput.length()<1000){
            error("A prévia não produziu um arquivo válido.");return;
        }
        if(previewPlayer!=null)previewPlayer.stopPlayback();
        previewPlayer=new VideoView(this);
        previews.addView(text("Prévia renderizada do corte. Assista e ajuste antes de exportar.",12,true),0);
        previews.addView(previewPlayer,1,new LinearLayout.LayoutParams(-1,dp(265)));
        previewPlayer.setVideoPath(previewOutput.getAbsolutePath());
        previewPlayer.setOnPreparedListener(mp->{mp.setLooping(true);previewPlayer.start();});
        previewPlayer.setOnErrorListener((mp,what,extra)->{
            error("Falha ao reproduzir prévia no aparelho ("+what+").");return true;
        });
        previewPlayer.requestFocus();
        say("Prévia pronta. O MP4 completo ainda não foi exportado.");
    }

    private void prepareExport(){
        if(busy||clips.size()<2)return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_TITLE,"FrameMatch_unido.mp4");
        startActivityForResult(i,SAVE_VIDEO);
    }
    private void startExport(){
        if(busy||!analyzed)return;
        if(Math.abs(analyzedStrength-intensity.getProgress()/100f)>0.001f){
            error("A intensidade mudou. Analise novamente antes de exportar.");return;
        }
        if(outputUri==null)return;
        if((matchSizeSwitch.isChecked()||matchBitrateSwitch.isChecked()||matchFpsSwitch.isChecked())
            && masterProfile==null){
            loadMasterProfile();
            if(masterProfile==null){
                error("Não foi possível ler qualidade do Clip 1. Desmarque normalização ou verifique arquivo.");
                return;
            }
        }
        busy=true;refresh();progress.setProgress(0);
        try{
            temporaryOutput=new File(getCacheDir(),"framematch_export_"+System.currentTimeMillis()+".mp4");
            if(temporaryOutput.exists())throw new IllegalStateException("Arquivo temporário já existe");
            List<EditedMediaItem> edits=new ArrayList<>();
            for(int i=0;i<clips.size();i++){
                MediaItem source=new MediaItem.Builder().setUri(clips.get(i))
                    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(i==0?0:entryOffsetsMs.get(i)).build()).build();
                EditedMediaItem.Builder item=new EditedMediaItem.Builder(source);
                ArrayList<androidx.media3.common.Effect> fx=new ArrayList<>();
                if(i>0){
                    if(regionSwitch.isChecked() && selectedModes.get(i)!=MatchChoices.ORIGINAL)
                        fx.add(new RegionEffect(plans.get(i),tracks.get(i)));
                    // Color masks must be evaluated BEFORE moving/zooming the face.
                    if(preZooms.get(i)!=null)fx.add(preZooms.get(i));
                    if(zooms.get(i)!=null)fx.add(zooms.get(i));
                }
                // Match master proportions without stretching pixels. Old 540x960 when disabled.
                int[] outputSize=masterProfile==null?new int[]{540,960}:
                    masterProfile.outputSize(matchSizeSwitch.isChecked());
                fx.add(Presentation.createForWidthAndHeight(outputSize[0],outputSize[1],
                    Presentation.LAYOUT_SCALE_TO_FIT));
                // Media3 only CAPS a higher source frame rate; it does not invent extra frames.
                if(masterProfile!=null && matchFpsSwitch.isChecked()
                    && masterProfile.frameRateCeiling(true)>0)
                    item.setFrameRate(masterProfile.frameRateCeiling(true));
                item.setEffects(new Effects(Collections.emptyList(),fx));
                edits.add(item.build());
            }
            EditedMediaItemSequence sequence=EditedMediaItemSequence.withAudioAndVideoFrom(edits);
            Composition composition=new Composition.Builder(sequence).build();
            Transformer.Builder exportBuilder=new Transformer.Builder(this);
            if(matchBitrateSwitch.isChecked() && masterProfile!=null){
                exportBuilder.setEncoderFactory(new DefaultEncoderFactory.Builder(this)
                    .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()
                        .setBitrate(masterProfile.requestedBitrate).build())
                    .build());
            }
            transformer=exportBuilder
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition c, ExportResult r) {finishExport();}
                    @Override public void onError(Composition c, ExportResult r,ExportException ex){
                        endExport("Falha ao exportar: "+ex.getMessage());
                    }
                }).build();
            say("Exportação em andamento. Não feche o aplicativo.");refresh();
            transformer.start(composition,temporaryOutput.getAbsolutePath());
            trackProgress();
        }catch(Exception e){endExport("Falha ao iniciar exportação: "+e.getMessage());}
    }
    private void trackProgress(){
        main.postDelayed(()->{
            if(!busy||transformer==null)return;
            ProgressHolder p=new ProgressHolder();
            try{
                int state=transformer.getProgress(p);
                if(state==Transformer.PROGRESS_STATE_AVAILABLE){progress.setProgress(p.progress);say("Exportando: "+p.progress+"%");}
            }catch(Exception ignored){}
            if(busy)trackProgress();
        },600);
    }
    private void finishExport(){
        transformer=null;
        final File file=temporaryOutput;final Uri dest=outputUri;
        worker.submit(()->{
            try{
                if(file==null||!file.isFile()||file.length()<1000)throw new Exception("MP4 incompleto");
                String outputDetails="";
                MediaMetadataRetriever finished=new MediaMetadataRetriever();
                try{
                    finished.setDataSource(file.getAbsolutePath());
                    int w=metadataInteger(finished,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    int h=metadataInteger(finished,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    int bitrate=metadataInteger(finished,MediaMetadataRetriever.METADATA_KEY_BITRATE);
                    if(w>0&&h>0)outputDetails=" • MP4: "+w+"×"+h+
                        (bitrate>0?" • "+(bitrate/1_000_000f)+" Mb/s":"");
                }catch(Exception ignored){}finally{finished.release();}
                final String verifiedOutput=outputDetails;
                try(InputStream in=new FileInputStream(file);
                    OutputStream out=getContentResolver().openOutputStream(dest,"w")) {
                    if(out==null)throw new Exception("Sem permissão para salvar no destino");
                    byte[] buffer=new byte[256*1024];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                file.delete();
                main.post(()->{busy=false;progress.setProgress(100);refresh();say("MP4 salvo!"+verifiedOutput+" • Originais preservados. A qualidade não cria detalhes ausentes.");});
            }catch(Exception e){
                main.post(()->endExport("Erro ao salvar: "+e.getMessage()));
            }
        });
    }
    private void cancelExport(){
        if(transformer!=null)transformer.cancel();
        previewRendering=false;
        endExport("Exportação ou prévia cancelada.");
    }
    private void endExport(String message){
        transformer=null;busy=false;refresh();
        if(temporaryOutput!=null&&temporaryOutput.exists())temporaryOutput.delete();
        say(message);
    }
    @Override protected void onDestroy(){
        ended=true;
        if(transformer!=null)transformer.cancel();
        if(previewPlayer!=null)previewPlayer.stopPlayback();
        worker.shutdownNow();
        super.onDestroy();
    }
}
