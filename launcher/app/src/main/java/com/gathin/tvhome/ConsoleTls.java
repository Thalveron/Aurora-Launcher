package com.gathin.tvhome;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.*;
import java.util.Calendar;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

final class ConsoleTls {
    private static final String ALIAS = "aurora-console-https";
    private static SSLServerSocketFactory verified;
    static synchronized SSLServerSocketFactory factory() throws GeneralSecurityException, java.io.IOException {
        if(verified!=null)return verified;
        try {
            verified=checked(ALIAS,false);
        } catch(GeneralSecurityException | java.io.IOException incompatible) {
            verified=checked(ALIAS+"-ec",true);
        }
        return verified;
    }
    private static SSLServerSocketFactory checked(String alias,boolean ec) throws GeneralSecurityException, java.io.IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(alias)) {
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 10);
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ec?"EC":"RSA", "AndroidKeyStore");
            KeyGenParameterSpec.Builder spec=new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests("NONE","SHA-256", "SHA-384", "SHA-512")
                
                .setCertificateSubject(new X500Principal("CN=Aurora Launcher"))
                .setCertificateSerialNumber(new BigInteger(128, new SecureRandom()).setBit(0))
                .setCertificateNotAfter(end.getTime());
            if(ec)spec.setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1"));
            else spec.setKeySize(2048).setSignaturePaddings("PKCS1","PSS");
            generator.initialize(spec.build());
            generator.generateKeyPair();
        }
        final PrivateKey key = (PrivateKey) store.getKey(alias, null);
        final java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) store.getCertificate(alias);
        X509KeyManager manager = new X509KeyManager() {
            public String chooseServerAlias(String type, Principal[] issuers, java.net.Socket socket) {return key.getAlgorithm().equalsIgnoreCase(type) ? alias : null;}
            public String[] getServerAliases(String type, Principal[] issuers) {return key.getAlgorithm().equalsIgnoreCase(type) ? new String[]{alias} : null;}
            public PrivateKey getPrivateKey(String alias) {return alias.equals(alias) ? key : null;}
            public java.security.cert.X509Certificate[] getCertificateChain(String alias) {return alias.equals(alias) ? new java.security.cert.X509Certificate[]{cert} : null;}
            public String chooseClientAlias(String[] types, Principal[] issuers, java.net.Socket socket) {return null;}
            public String[] getClientAliases(String type, Principal[] issuers) {return null;}
        };
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(new KeyManager[]{manager}, null, null);
        SSLServerSocketFactory factory=tls.getServerSocketFactory();
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);trust.setCertificateEntry("console",cert);
        TrustManagerFactory managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext client=SSLContext.getInstance("TLS");
        client.init(null,managers.getTrustManagers(),null);
        java.net.InetAddress local=java.net.InetAddress.getByName("127.0.0.1");
        try(SSLServerSocket listener=(SSLServerSocket)factory.createServerSocket(0,1,local)){
            listener.setSoTimeout(5000);
            java.util.concurrent.FutureTask<Void> test=new java.util.concurrent.FutureTask<>(()->{
                try(SSLSocket peer=(SSLSocket)listener.accept()){
                    peer.setSoTimeout(5000);peer.startHandshake();peer.getOutputStream().write(1);
                }
                return null;
            });
            new Thread(test,"console-tls-check").start();
            try(SSLSocket peer=(SSLSocket)client.getSocketFactory().createSocket(local,listener.getLocalPort())){
                peer.setSoTimeout(5000);peer.setEnabledProtocols(new String[]{"TLSv1.2"});
                peer.startHandshake();
                if(peer.getInputStream().read()!=1)throw new java.io.IOException("HTTPS 握手检查失败");
            }
        }
        return factory;
    }
}
