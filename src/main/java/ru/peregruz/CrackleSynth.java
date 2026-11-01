package ru.peregruz;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Generates an actual crackle sound and plays it on top of the music:
 * dry clicks, harsh electrical cracks (often in bursts, like a loose plug) and low speaker pops.
 */
public final class CrackleSynth implements Runnable {

    private static final int SR = 48000;
    private static final int VOICES = 12;

    /** 0..1: how much crackle (density and loudness). */
    public volatile float amount = 0f;
    /** Play only while music is playing. */
    public volatile boolean active = false;
    private volatile boolean running = true;
    private Thread thread;

    private final Random rnd = new Random();
    // voices
    private final float[] amp = new float[VOICES], dec = new float[VOICES], phase = new float[VOICES],
            freq = new float[VOICES], pan = new float[VOICES];
    private final int[] kind = new int[VOICES];
    private float lastNoise = 0f;
    private int burstLeft = 0, burstWait = 0;
    private float level = 0f; // smoothed on/off so starting and stopping does not click on its own

    public void start() {
        thread = new Thread(this, "crackle-synth");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        AudioTrack track = null;
        try {
            int min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, SR / 25 * 2 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            int frames = 480; // 10 ms
            float[] buf = new float[frames * 2];
            while (running) {
                fill(buf, frames);
                track.write(buf, 0, buf.length, AudioTrack.WRITE_BLOCKING);
            }
        } catch (Throwable t) {
            Fx.lastIssue = "Треск: " + Fx.short_(t);
        } finally {
            if (track != null) {
                try { track.stop(); } catch (Throwable ignored) {}
                track.release();
            }
        }
    }

    private float noise() { return rnd.nextFloat() * 2f - 1f; }

    private void spawn(int k, float strength) {
        int v = -1;
        for (int i = 0; i < VOICES; i++) if (amp[i] < 0.002f) { v = i; break; }
        if (v < 0) v = rnd.nextInt(VOICES);
        kind[v] = k;
        pan[v] = rnd.nextFloat() * 2f - 1f;
        phase[v] = 0f;
        float ms;
        switch (k) {
            case 0: // dry click
                amp[v] = strength * (0.25f + 0.75f * (float) Math.sqrt(rnd.nextFloat()));
                ms = 0.15f + rnd.nextFloat() * 1.2f;
                break;
            case 1: // harsh electrical crack
                amp[v] = strength * (0.6f + 0.4f * rnd.nextFloat());
                ms = 0.8f + rnd.nextFloat() * 5f;
                break;
            default: // low speaker pop
                amp[v] = strength * (0.5f + 0.5f * rnd.nextFloat());
                ms = 6f + rnd.nextFloat() * 18f;
                freq[v] = (float) (2 * Math.PI * (45 + rnd.nextFloat() * 110) / SR);
                break;
        }
        dec[v] = (float) Math.exp(-1.0 / (SR * ms / 1000.0));
    }

    private void fill(float[] buf, int frames) {
        float a = amount;
        float target = active && a > 0f ? 1f : 0f;
        // events per second: from a few to a dense, aggressive crackle
        double rate = 2 + Math.pow(a, 1.4) * 900;
        double p = rate / SR;
        float strength = 0.35f + 0.65f * a;
        for (int n = 0; n < frames; n++) {
            level += (target - level) * 0.0005f;
            if (level > 0.001f) {
                if (burstLeft > 0) {
                    if (--burstWait <= 0) {
                        spawn(rnd.nextFloat() < 0.6f ? 1 : 0, strength);
                        burstLeft--;
                        burstWait = 60 + rnd.nextInt(700);
                    }
                } else if (rnd.nextDouble() < p) {
                    float r = rnd.nextFloat();
                    if (r < 0.55f) spawn(0, strength);
                    else if (r < 0.88f) {
                        spawn(1, strength);
                        if (rnd.nextFloat() < 0.25f + 0.5f * a) { burstLeft = 2 + rnd.nextInt(6); burstWait = 40 + rnd.nextInt(400); }
                    } else spawn(2, strength);
                }
            }
            float wn = noise();
            float hp = wn - lastNoise; // bright, papery noise for clicks
            lastNoise = wn;
            float l = 0f, r = 0f;
            for (int i = 0; i < VOICES; i++) {
                if (amp[i] < 0.002f) continue;
                float s;
                if (kind[i] == 0) s = hp * 0.7f;
                else if (kind[i] == 1) s = Math.signum(wn) * (float) Math.pow(Math.abs(wn), 0.25); // gritty, square-ish
                else { phase[i] += freq[i]; s = (float) Math.sin(phase[i]) + wn * 0.15f; }
                s *= amp[i];
                amp[i] *= dec[i];
                l += s * (1f - Math.max(0f, pan[i]) * 0.6f);
                r += s * (1f + Math.min(0f, pan[i]) * 0.6f);
            }
            // dirty saturation, like it all goes through a blown speaker
            buf[2 * n] = (float) Math.tanh(l * 2.2f) * 0.8f * level;
            buf[2 * n + 1] = (float) Math.tanh(r * 2.2f) * 0.8f * level;
        }
    }
}
