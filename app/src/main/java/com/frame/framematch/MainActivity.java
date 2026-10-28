package com.frame.framematch;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout clipList, container, previews;
    private TextView status, subtitle;
    private SeekBar intensity;
    private ProgressBar progress;
    private Button addButton, analyzeButton, exportButton, cancelButton;
    private android.widget.CheckBox alignSwitch, regionSwitch;
    private volatile boolean busy=false, analyzed=false;
    private volatile float analyzedStrength=1f;
    private Transformer transformer;
    private File temporaryOutput;
    private Uri outputUri;
    private boolean ended=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final ScrollView scroller=new ScrollView(this);
        container=new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(14),dp(16),dp(14),dp(22));scroller.addView(container);
        setContentView(scroller);
        TextView title=text("FrameMatch Android",22,true); container.addView(title);
        subtitle=text("Último quadro → primeiro quadro • correção de cor automática • unir clipes",13,false);
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
        alignSwitch.setText("Reenquadramento nos 3s iniciais (experimental; pode causar zoom)");
        alignSwitch.setChecked(false);container.addView(alignSwitch);
        regionSwitch.setOnCheckedChangeListener((b,checked)->invalidateAnalysis());
        alignSwitch.setOnCheckedChangeListener((b,checked)->invalidateAnalysis());
        intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean from){if(from)invalidateAnalysis();}
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        analyzeButton=button("ANALISAR CORES E MOSTRAR COMPARAÇÃO",v->analyze());
        container.addView(analyzeButton);
        previews=new LinearLayout(this); previews.setOrientation(LinearLayout.VERTICAL);container.addView(previews);
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
            invalidateAnalysis();refresh();
        } else if(req==SAVE_VIDEO){
            Uri dest=data.getData(); if(dest==null)return;
            if(clips.contains(dest)){error("O destino não pode ser um vídeo original.");return;}
            outputUri=dest;
            if(analyzed)startExport();else analyzeAndExport();
        }
    }

    private void invalidateAnalysis(){analyzed=false;plans.clear();tracks.clear();zooms.clear();previews.removeAllViews();}
    private void refresh(){
        clipList.removeAllViews();
        for(int i=0;i<clips.size();i++){
            final int k=i;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            TextView file=text((i+1)+". "+label(clips.get(i)),12,false);
            row.addView(file,new LinearLayout.LayoutParams(0,dp(52),1f));
            Button up=button("↑",v->{Collections.swap(clips,k,k-1);invalidateAnalysis();refresh();});
            up.setEnabled(i>0&&!busy);row.addView(up,new LinearLayout.LayoutParams(dp(46),dp(54)));
            Button down=button("↓",v->{Collections.swap(clips,k,k+1);invalidateAnalysis();refresh();});
            down.setEnabled(i<clips.size()-1&&!busy);row.addView(down,new LinearLayout.LayoutParams(dp(46),dp(54)));
            Button del=button("×",v->{clips.remove(k);invalidateAnalysis();refresh();});
            del.setEnabled(!busy);row.addView(del,new LinearLayout.LayoutParams(dp(46),dp(54)));
            clipList.addView(row);
        }
        addButton.setEnabled(!busy && clips.size()<MAX_CLIPS);
        analyzeButton.setEnabled(!busy && clips.size()>=2);
        exportButton.setEnabled(!busy && clips.size()>=2);
        intensity.setEnabled(!busy);regionSwitch.setEnabled(!busy);alignSwitch.setEnabled(!busy);
        cancelButton.setVisibility(busy&&transformer!=null?View.VISIBLE:View.GONE);
    }
    private void analyze(){if(clips.size()<2||busy)return;doAnalysis(false);}
    private void analyzeAndExport(){if(clips.size()<2||busy)return;doAnalysis(true);}
    private void doAnalysis(boolean thenExport){
        busy=true;ended=false;refresh();progress.setProgress(0);
        say("Analisando cenário, rosto e movimento — sem alterar originais...");
        final ArrayList<Uri> selected=new ArrayList<>(clips);
        final float strength=intensity.getProgress()/100f;
        final boolean regional=regionSwitch.isChecked(),align=alignSwitch.isChecked();
        worker.submit(()->{
            FaceDetector detector=FaceTimeline.createDetector();
            try{
                ArrayList<RegionMath.Plan> nextPlans=new ArrayList<>();
                ArrayList<FaceTimeline> nextTracks=new ArrayList<>();
                ArrayList<ContinuityZoom> nextZooms=new ArrayList<>();
                nextPlans.add(RegionMath.Plan.identity());
                nextTracks.add(null);nextZooms.add(null);
                ArrayList<Bitmap[]> triplets=new ArrayList<>();
                Bitmap previousLast=frame(selected.get(0),true);
                RegionMath.FaceBox lastFace=FaceTimeline.detect(detector,previousLast);
                for(int i=1;i<selected.size();i++){
                    Bitmap first=frame(selected.get(i),false);
                    Bitmap last=previousLast;
                    // A second close-by observation reduces influence of one anomalous boundary frame.
                    // Do not use frames beyond 3 s for continuity calibration.
                    Bitmap referenceNearEnd=frameOffset(selected.get(i-1),true,500_000L);
                    if(i>1)referenceNearEnd=grade(referenceNearEnd,nextPlans.get(i-1),lastFace);
                    Bitmap targetEarly=frameOffset(selected.get(i),false,500_000L);
                    // Scan entire target at bounded sampling, keep a stable region plan for every frame.
                    FaceTimeline timeline=FaceTimeline.scan(this,selected.get(i),Long.MAX_VALUE,detector);
                    RegionMath.FaceBox targetFirst=timeline.first();
                    RegionMath.Plan match=regional ? RegionMath.analyze(
                        pixels(last),last.getWidth(),last.getHeight(),lastFace,
                        pixels(first),first.getWidth(),first.getHeight(),targetFirst,strength)
                        : RegionMath.Plan.identity();
                    if(regional){
                        RegionMath.FaceBox earlyFace=timeline.at(500_000L);
                        RegionMath.Plan earlyMatch=RegionMath.analyze(
                            pixels(referenceNearEnd),referenceNearEnd.getWidth(),referenceNearEnd.getHeight(),lastFace,
                            pixels(targetEarly),targetEarly.getWidth(),targetEarly.getHeight(),earlyFace,strength);
                        match=RegionMath.blendStable(match,earlyMatch);
                    }
                    nextPlans.add(match);nextTracks.add(timeline);
                    ContinuityZoom zoom=align?ContinuityZoom.plan(timeline,lastFace):null;
                    nextZooms.add(zoom);
                    Bitmap corrected=grade(first,match,targetFirst);
                    if(zoom!=null)corrected=zoom.preview(corrected);
                    triplets.add(new Bitmap[]{small(last),small(first),small(corrected)});
                    Bitmap finalFrame=frame(selected.get(i),true);
                    RegionMath.FaceBox finalFace=FaceTimeline.detect(detector,finalFrame);
                    previousLast=grade(finalFrame,match,finalFace);
                    lastFace=finalFace;
                    final int ix=i;
                    main.post(()->{progress.setProgress(ix*100/(selected.size()-1));
                        say("Analisado corte "+ix+" → "+(ix+1));});
                }
                main.post(()->{
                    if(ended)return;
                    plans.clear();plans.addAll(nextPlans);
                    tracks.clear();tracks.addAll(nextTracks);
                    zooms.clear();zooms.addAll(nextZooms);
                    analyzed=true;analyzedStrength=strength;
                    busy=false;progress.setProgress(100);renderPreviews(triplets);refresh();
                    int aligned=0;for(ContinuityZoom z:zooms)if(z!=null)aligned++;
                    say("Análise concluída. Cortes: "+(nextPlans.size()-1)+
                        ", reenquadramentos confiáveis: "+aligned+". Veja a prévia e exporte.");
                    if(thenExport)startExport();
                });
            }catch(Exception e){main.post(()->{busy=false;refresh();
                error(e.getMessage()==null?"falha ao analisar quadros":e.getMessage());});
            }finally{detector.close();}
        });
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
    private void renderPreviews(ArrayList<Bitmap[]> triplets){
        previews.removeAllViews();
        for(int i=0;i<triplets.size();i++){
            previews.addView(text("Corte "+(i+1)+" → "+(i+2)+" | correção por regiões",12,true));
            LinearLayout line=new LinearLayout(this);
            Bitmap[] trio=triplets.get(i);
            String[] names={"Último anterior","Primeiro original","Primeiro corrigido"};
            for(int j=0;j<3;j++){
                LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
                ImageView view=new ImageView(this);view.setImageBitmap(trio[j]);view.setAdjustViewBounds(true);
                view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                column.addView(view,new LinearLayout.LayoutParams(-1,dp(142)));
                TextView caption=text(names[j],10,false);caption.setGravity(Gravity.CENTER);
                column.addView(caption);line.addView(column,new LinearLayout.LayoutParams(0,-2,1f));
            }
            previews.addView(line);
        }
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
        busy=true;refresh();progress.setProgress(0);
        try{
            temporaryOutput=new File(getCacheDir(),"framematch_export_"+System.currentTimeMillis()+".mp4");
            if(temporaryOutput.exists())throw new IllegalStateException("Arquivo temporário já existe");
            List<EditedMediaItem> edits=new ArrayList<>();
            for(int i=0;i<clips.size();i++){
                EditedMediaItem.Builder item=new EditedMediaItem.Builder(MediaItem.fromUri(clips.get(i)));
                ArrayList<androidx.media3.common.Effect> fx=new ArrayList<>();
                if(i>0){
                    if(regionSwitch.isChecked())fx.add(new RegionEffect(plans.get(i),tracks.get(i)));
                    // Color masks must be evaluated BEFORE moving/zooming the face.
                    if(zooms.get(i)!=null)fx.add(zooms.get(i));
                }
                // Common aspect ratio: retain proportions without stretching faces.
                fx.add(Presentation.createForWidthAndHeight(540,960,Presentation.LAYOUT_SCALE_TO_FIT));
                item.setEffects(new Effects(Collections.emptyList(),fx));
                edits.add(item.build());
            }
            EditedMediaItemSequence sequence=EditedMediaItemSequence.withAudioAndVideoFrom(edits);
            Composition composition=new Composition.Builder(sequence).build();
            transformer=new Transformer.Builder(this)
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
                try(InputStream in=new FileInputStream(file);
                    OutputStream out=getContentResolver().openOutputStream(dest,"w")) {
                    if(out==null)throw new Exception("Sem permissão para salvar no destino");
                    byte[] buffer=new byte[256*1024];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                file.delete();
                main.post(()->{busy=false;progress.setProgress(100);refresh();say("MP4 salvo! Abra em Arquivos. Arquivos originais preservados.");});
            }catch(Exception e){
                main.post(()->endExport("Erro ao salvar: "+e.getMessage()));
            }
        });
    }
    private void cancelExport(){
        if(transformer!=null)transformer.cancel();
        endExport("Exportação cancelada.");
    }
    private void endExport(String message){
        transformer=null;busy=false;refresh();
        if(temporaryOutput!=null&&temporaryOutput.exists())temporaryOutput.delete();
        say(message);
    }
    @Override protected void onDestroy(){
        ended=true;
        if(transformer!=null)transformer.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }
}
