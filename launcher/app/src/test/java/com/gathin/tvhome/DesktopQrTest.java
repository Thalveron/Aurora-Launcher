package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;import com.google.zxing.*;import com.google.zxing.common.*;
public class DesktopQrTest{
 @Test public void completeAddressRoundTripsWithoutCloudService()throws Exception{
  for(String url:new String[]{"http://192.168.1.10:8899/","http://192.168.1.20:9000/my-tv/"}){
   BitMatrix qr=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,650,650);int[] rgb=new int[650*650];for(int y=0;y<650;y++)for(int x=0;x<650;x++)rgb[y*650+x]=qr.get(x,y)?0xff000000:0xffffffff;
   assertEquals(url,new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(650,650,rgb)))).getText());
  }
 }
}
