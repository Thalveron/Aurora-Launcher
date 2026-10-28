package com.gathin.tvhome;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One continuous AudioTrack: decoder and file boundaries never restart the audio output. */
final class PcmLoopPlayer {
    private static final Object CACHE_LOCK=new Object();
    private final Context context;
    private final String selected;
    private final boolean overlap;
    private final Runnable ready,failed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile AudioTrack track;
    private final Object playbackLock=new Object();
    private final Object completionLock=new Object();
    private boolean terminated;
    private Runnable stopped;
    private int rate,channels;
    private long loops,written;
    private volatile long introFrames,cycleFrames;
    long positionMs(){AudioTrack t=track;if(t==null||rate<=0||cycleFrames<=0)return 0;try{long played=Integer.toUnsignedLong(t.getPlaybackHeadPosition());long frame=played<introFrames?played:introFrames+(played-introFrames)%cycleFrames;return frame*1000/rate;}catch(Exception e){return 0;}}
    private Thread worker;
    private boolean playbackStarted;
    PcmLoopPlayer(Context c,String id,boolean fade,Runnable ready,Runnable failed){context=c;selected=id;overlap=fade;this.ready=ready;this.failed=failed;}
    void start(){worker=new Thread(this::run,"DesktopPcmLoop");worker.start();}
    void stop(Runnable completion){
        synchronized(completionLock){if(terminated)main.post(completion);else stopped=completion;}
        cancelled.set(true);
        synchronized(playbackLock){AudioTrack t=track;if(t!=null)try{t.setVolume(0f);t.pause();t.flush();}catch(IllegalStateException ignored){}}
        if(worker!=null)worker.interrupt();
    }
    private void check()throws IOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");}
    private void run(){
        try{synchronized(CACHE_LOCK){if(!cancelled.get())runLocked();}}
        finally{synchronized(completionLock){terminated=true;if(stopped!=null){main.post(stopped);stopped=null;}}}
    }
    private void runLocked(){
        File dir=new File(context.getCacheDir(),"ambient-pcm");dir.mkdirs();
        File raw=new File(dir,"audio.pcm"),cycle=new File(dir,"cycle.pcm");
        try{
            File source=AmbientMusic.byId(context,selected);
            String key=(source==null?"builtin":source.getName()+":"+source.length()+":"+source.lastModified())+":pcm-v1";
            File metadata=new File(dir,"metadata");boolean cached=false;
            try(DataInputStream in=new DataInputStream(new FileInputStream(metadata))){cached=in.readUTF().equals(key);rate=in.readInt();channels=in.readInt();cached=cached&&raw.length()>0;}catch(IOException ignored){}
            if(!cached){
                metadata.delete();raw.delete();decode(raw,source);check();
                try(DataOutputStream out=new DataOutputStream(new FileOutputStream(metadata))){out.writeUTF(key);out.writeInt(rate);out.writeInt(channels);}
            }
            int frame=channels*2;long frames=raw.length()/frame;
            int fadeFrames=overlap?(int)Math.min(rate*3L,frames/5):0;
            if(fadeFrames<rate/20)fadeFrames=0;
            buildCycle(raw,cycle,fadeFrames);introFrames=fadeFrames;cycleFrames=cycle.length()/(channels*2);check();
            int mask=channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
            int minimum=AudioTrack.getMinBufferSize(rate,mask,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IOException("Unsupported PCM output");
            AudioTrack output=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(mask).build())
                .setBufferSizeInBytes(Math.max(minimum*4,rate*frame/4)).setTransferMode(AudioTrack.MODE_STREAM).build();
            track=output;check();if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("PCM output unavailable");
            byte[] buffer=new byte[8192-(8192%frame)];
            // The first pass starts at the original beginning. All later passes share the same stream.
            try(RandomAccessFile original=new RandomAccessFile(raw,"r");RandomAccessFile loop=new RandomAccessFile(cycle,"r")){
                long head=(long)fadeFrames*frame;
                pump(original,head,buffer,output);
                while(!cancelled.get()){
                    loop.seek(0);pump(loop,loop.length(),buffer,output);loops++;
                    state(output);
                }
            }
        }catch(Exception error){if(!cancelled.get()){android.util.Log.w("TvHomeAmbient","Continuous PCM music failed",error);main.post(()->{if(!cancelled.get())failed.run();});}}
        finally{AudioTrack t=track;track=null;if(t!=null){try{t.pause();t.flush();}catch(Exception ignored){}t.release();}}
    }
    private void pump(RandomAccessFile in,long remaining,byte[] buffer,AudioTrack out)throws IOException{
        long last=0;
        while(remaining>0){check();int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException();int at=0;
            while(at<n){check();int count=out.write(buffer,at,n-at,AudioTrack.WRITE_BLOCKING);if(count<=0)throw new IOException("Audio write "+count);at+=count;written+=count;
                if(!playbackStarted){synchronized(playbackLock){check();out.play();playbackStarted=true;}main.post(()->{if(!cancelled.get())ready.run();});}
            }
            remaining-=n;
            long now=SystemClock.elapsedRealtime();if(now-last>1000){last=now;state(out);}
        }
    }
    private void state(AudioTrack out){try{String value="engine=continuous-pcm playing="+(out.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)+" loops="+loops+" frames="+written/(channels*2)+" rate="+rate+" underruns="+out.getUnderrunCount();java.nio.file.Files.write(new File(context.getExternalFilesDir(null),"ambient-loop-state.txt").toPath(),value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}}
    private void buildCycle(File raw,File cycle,int fadeFrames)throws IOException{
        int frame=channels*2;long head=(long)fadeFrames*frame;
        try(RandomAccessFile in=new RandomAccessFile(raw,"r");FileOutputStream out=new FileOutputStream(cycle)){
            byte[] buffer=new byte[8192];in.seek(head);long remaining=raw.length()-head*2;
            while(remaining>0){check();int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException();out.write(buffer,0,n);remaining-=n;}
            if(fadeFrames>0){
                byte[] first=new byte[(int)head],last=new byte[(int)head];in.seek(0);in.readFully(first);in.seek(raw.length()-head);in.readFully(last);
                for(int f=0;f<fadeFrames;f++){float mix=f/(float)Math.max(1,fadeFrames-1);for(int ch=0;ch<channels;ch++){
                    int at=(f*channels+ch)*2;int a=(short)((last[at]&255)|(last[at+1]<<8)),b=(short)((first[at]&255)|(first[at+1]<<8));
                    int value=Math.round(a*(1-mix)+b*mix);last[at]=(byte)value;last[at+1]=(byte)(value>>8);
                }}out.write(last);
            }
        }
    }
    private void decode(File target,File source)throws Exception{
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try{
            if(source!=null)extractor.setDataSource(source.getPath());
            else try(android.content.res.AssetFileDescriptor asset=context.getResources().openRawResourceFd(R.raw.ambient_piano)){extractor.setDataSource(asset.getFileDescriptor(),asset.getStartOffset(),asset.getLength());}
            MediaFormat format=null;for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){format=f;extractor.selectTrack(i);break;}}
            if(format==null)throw new IOException("No audio track");rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);validateFormat();
            String mime=format.getString(MediaFormat.KEY_MIME);
            try(FileOutputStream out=new FileOutputStream(target)){
                if("audio/raw".equals(mime)){
                    ByteBuffer data=ByteBuffer.allocate(256*1024);while(true){check();data.clear();int n=extractor.readSampleData(data,0);if(n<0)break;byte[] bytes=new byte[n];data.position(0);data.get(bytes);out.write(bytes);extractor.advance();limit(target);}return;
                }
                String decoder=null;
                for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())if(!info.isEncoder())for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase(mime)&&(info.getName().startsWith("OMX.google.")||info.getName().startsWith("c2.android.")))decoder=info.getName();
                codec=decoder==null?MediaCodec.createDecoderByType(mime):MediaCodec.createByCodecName(decoder);
                format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);codec.configure(format,null,null,0);codec.start();
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnd=false,outputEnd=false;int encoding=AudioFormat.ENCODING_PCM_16BIT;long lastOutput=SystemClock.elapsedRealtime();
                while(!outputEnd){check();
                    if(!inputEnd){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer data=codec.getInputBuffer(index);data.clear();int n=extractor.readSampleData(data,0);if(n<0){inputEnd=true;codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);}else{codec.queueInputBuffer(index,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
                    int index=codec.dequeueOutputBuffer(info,10000);
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);validateFormat();if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=f.getInteger(MediaFormat.KEY_PCM_ENCODING);}
                    else if(index>=0){lastOutput=SystemClock.elapsedRealtime();ByteBuffer data=codec.getOutputBuffer(index);data.position(info.offset);data.limit(info.offset+info.size);
                        if(encoding==AudioFormat.ENCODING_PCM_16BIT){byte[] bytes=new byte[info.size];data.get(bytes);out.write(bytes);}
                        else if(encoding==AudioFormat.ENCODING_PCM_FLOAT){data.order(ByteOrder.nativeOrder());while(data.remaining()>=4){float f=data.getFloat();int sample=(int)(Math.max(-1,Math.min(1,f))*32767);out.write(sample&255);out.write((sample>>8)&255);}}
                        else throw new IOException("Unsupported decoder PCM encoding");
                        outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);limit(target);
                    }
                    if(SystemClock.elapsedRealtime()-lastOutput>15000)throw new IOException("Decoder stalled");
                }
            }
            if(target.length()<channels*2)throw new IOException("Empty audio");
        }finally{extractor.release();if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}}
    }
    private void validateFormat()throws IOException{if(channels<1||channels>2||rate<8000||rate>192000)throw new IOException("Unsupported audio format");}
    private void limit(File file)throws IOException{if(file.length()>384L*1024*1024||file.getUsableSpace()<32L*1024*1024)throw new IOException("PCM cache limit exceeded");}
}
