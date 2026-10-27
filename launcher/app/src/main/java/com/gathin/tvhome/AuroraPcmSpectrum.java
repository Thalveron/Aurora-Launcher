package com.gathin.tvhome;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.audio.AudioProcessor;
import java.nio.ByteBuffer;
/** PCM gain is applied before vendor audio output. Spectrum observes the original decoded samples. */
final class AuroraPcmSpectrum extends BaseAudioProcessor {
    private final double[] window=new double[1024];private int count,rate,channels;private long measured;
    private static final double[] HANN=new double[1024];static{for(int i=0;i<HANN.length;i++)HANN[i]=.5-.5*Math.cos(2*Math.PI*i/(HANN.length-1));}
    private volatile float targetGain;private float appliedGain;
    volatile double inputRms,outputRms;volatile long renderedSamples;
    void gain(float value){targetGain=Math.max(0,Math.min(1,value));}
    float requestedGain(){return targetGain;}
    volatile double[] bands=new double[32];volatile long updated;
    @Override protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat f)throws AudioProcessor.UnhandledAudioFormatException{rate=f.sampleRate;channels=f.channelCount;if(f.encoding!=C.ENCODING_PCM_16BIT)throw new AudioProcessor.UnhandledAudioFormatException(f);return f;}
    @Override public void queueInput(ByteBuffer input){int length=input.remaining();if(length==0)return;ByteBuffer view=input.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN);int stride=Math.max(1,channels)*2;
        long now=android.os.SystemClock.elapsedRealtime();if(now-measured>=100){for(int p=view.position();p+stride<=view.limit();p+=stride){double sample=0;for(int ch=0;ch<channels;ch++)sample+=view.getShort(p+ch*2)/32768.0;window[count++]=sample/Math.max(1,channels);if(count==window.length){measure();count=0;measured=now;updated=now;break;}}}
        ByteBuffer out=replaceOutputBuffer(length).order(java.nio.ByteOrder.LITTLE_ENDIAN);float target=targetGain;double before=0,after=0;int samples=0;float smoothing=1f/Math.max(1,rate*.01f);
        input.order(java.nio.ByteOrder.LITTLE_ENDIAN);while(input.remaining()>=2){if(samples%Math.max(1,channels)==0){if(target==0)appliedGain=0;else appliedGain+=(target-appliedGain)*smoothing;}
            short sample=input.getShort();short scaled=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(sample*appliedGain)));
            out.putShort(scaled);before+=(double)sample*sample;after+=(double)scaled*scaled;samples++;
        }input.position(input.limit());out.flip();inputRms=Math.sqrt(before/Math.max(1,samples))/32768.0;outputRms=Math.sqrt(after/Math.max(1,samples))/32768.0;renderedSamples+=samples;
    }
    private void measure(){double[] result=new double[32];for(int i=0;i<32;i++){double frequency=40*Math.pow(Math.min(16000,rate*.45)/40.0,i/31.0),coefficient=2*Math.cos(2*Math.PI*frequency/rate),s1=0,s2=0;for(int n=0;n<window.length;n++){double sample=window[n]*HANN[n],s=sample+coefficient*s1-s2;s2=s1;s1=s;}double amplitude=Math.sqrt(Math.max(0,s1*s1+s2*s2-coefficient*s1*s2))*4/window.length;result[i]=amplitude<.0001?0:Math.min(1,Math.log10(1+amplitude*100)/2);}bands=result;}
    @Override protected void onFlush(){count=0;bands=new double[32];updated=0;}
}
