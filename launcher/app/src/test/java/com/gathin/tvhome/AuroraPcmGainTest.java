package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;import java.nio.*;import androidx.media3.common.C;import androidx.media3.common.audio.AudioProcessor;
public class AuroraPcmGainTest {
 private AuroraPcmSpectrum processor(float gain)throws Exception{AuroraPcmSpectrum p=new AuroraPcmSpectrum(()->1000L);p.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT));p.flush();p.gain(gain);return p;}
 private ByteBuffer samples(int frames){ByteBuffer b=ByteBuffer.allocateDirect(frames*4).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<frames*2;i++)b.putShort((short)12000);b.flip();return b;}
 @Test public void zeroProducesActualSilence()throws Exception{AuroraPcmSpectrum p=processor(0);p.queueInput(samples(2048));ByteBuffer out=p.getOutput().order(ByteOrder.LITTLE_ENDIAN);while(out.hasRemaining())assertEquals(0,out.getShort());assertEquals(0,p.outputRms,0);}
 @Test public void requestedGainIsAppliedToSamples()throws Exception{AuroraPcmSpectrum p=processor(.25f);p.queueInput(samples(12000));ByteBuffer out=p.getOutput().order(ByteOrder.LITTLE_ENDIAN);out.position(out.limit()-4);assertEquals(3000,out.getShort(),2);assertEquals(3000,out.getShort(),2);p.gain(0);p.queueInput(samples(32));out=p.getOutput().order(ByteOrder.LITTLE_ENDIAN);while(out.hasRemaining())assertEquals(0,out.getShort());}
}
