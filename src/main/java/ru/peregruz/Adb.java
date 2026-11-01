package ru.peregruz;

import android.content.Context;
import android.os.Build;
import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.adb.android.AndroidUtils;

/**
 * Built-in ADB client: the app pairs with the phone's own "Wireless debugging" once,
 * then runs shell commands (dumpsys audio) as the ADB shell user. No Shizuku needed.
 */
public final class Adb extends AbsAdbConnectionManager {

    private static Adb instance;

    public static synchronized Adb get(Context c) throws Exception {
        if (instance == null) instance = new Adb(c.getApplicationContext());
        return instance;
    }

    private final PrivateKey privateKey;
    private final Certificate certificate;

    private Adb(Context c) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        File keyFile = new File(c.getFilesDir(), "adb.key");
        File certFile = new File(c.getFilesDir(), "adb.pem");
        if (keyFile.exists() && certFile.exists()) {
            byte[] bytes = new byte[(int) keyFile.length()];
            try (InputStream is = new FileInputStream(keyFile)) {
                int off = 0;
                while (off < bytes.length) {
                    int n = is.read(bytes, off, bytes.length - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
            try (InputStream is = new FileInputStream(certFile)) {
                certificate = CertificateFactory.getInstance("X.509").generateCertificate(is);
            }
            return;
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
        KeyPair pair = gen.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        privateKey = pair.getPrivate();

        String algorithm = "SHA512withRSA";
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 3650L * 86400000L);
        X500Name name = new X500Name("CN=Peregruz");
        CertificateExtensions ext = new CertificateExtensions();
        ext.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
        ext.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
        X509CertInfo info = new X509CertInfo();
        info.set("version", new CertificateVersion(2));
        info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
        info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
        info.set("subject", new CertificateSubjectName(name));
        info.set("key", new CertificateX509Key(publicKey));
        info.set("validity", new CertificateValidity(notBefore, notAfter));
        info.set("issuer", new CertificateIssuerName(name));
        info.set("extensions", ext);
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privateKey, algorithm);
        certificate = cert;

        try (OutputStream os = new FileOutputStream(keyFile)) {
            os.write(privateKey.getEncoded());
        }
        try (OutputStream os = new FileOutputStream(certFile)) {
            os.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            os.write('\n');
            new BASE64Encoder().encode(certificate.getEncoded(), os);
            os.write('\n');
            os.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override protected PrivateKey getPrivateKey() { return privateKey; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "Peregruz"; }

    /** Connects if needed. Returns true when a connection is up. Throws AdbPairingRequiredException if never paired. */
    public synchronized boolean ensure(Context c, long timeoutMs) throws Exception {
        if (isConnected()) return true;
        try {
            connectTls(c.getApplicationContext(), timeoutMs);
        } catch (io.github.muntashirakon.adb.AdbPairingRequiredException e) {
            throw e;
        } catch (Exception ignored) {
        }
        return isConnected();
    }

    /** Runs a shell command and returns its whole output. */
    public static String run(Context c, String cmd) throws Exception {
        Adb a = get(c);
        if (!a.ensure(c, 4000)) throw new Exception("нет связи с «Отладкой по Wi-Fi»");
        StringBuilder sb = new StringBuilder();
        try (AdbStream s = a.openStream("shell:" + cmd);
             BufferedReader r = new BufferedReader(new InputStreamReader(s.openInputStream(), StandardCharsets.UTF_8))) {
            String line;
            try {
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } catch (java.io.IOException e) {
                // adbd closes the stream right after the last line; that is a normal end.
                if (sb.length() == 0) throw e;
            }
        } catch (java.io.IOException e) {
            if (sb.length() == 0) throw e;
        }
        return sb.toString();
    }

    /** Waits for the "Pair device with pairing code" dialog to be opened in Settings and returns its port. */
    public static int findPairingPort(Context c, long timeoutMs) throws InterruptedException {
        AtomicInteger port = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);
        AdbMdns mdns = new AdbMdns(c.getApplicationContext(), AdbMdns.SERVICE_TYPE_TLS_PAIRING, (host, p) -> {
            if (p > 0) { port.set(p); latch.countDown(); }
        });
        mdns.start();
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            mdns.stop();
        }
        return port.get();
    }

    public static boolean pairWith(Context c, int port, String code) throws Exception {
        return get(c).pair(AndroidUtils.getHostIpAddress(c.getApplicationContext()), port, code);
    }
}
