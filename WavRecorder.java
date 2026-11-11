package com.routine.watch;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Records the microphone into a plain WAV file (16 kHz, mono, 16 bit) - the Pi can play that with aplay. */
final class WavRecorder {
    static final int RATE = 16000;
    static final int MAX_SECONDS = 300;                       // 5 minutes = about 10 MB

    private final File out;
    private AudioRecord rec;
    private Thread th;
    private volatile boolean run;
    volatile long bytes;                                      // sound bytes written so far
    volatile int peak;                                        // loudest sample in the last moment (0..32767)
    volatile int loudest;                                     // loudest sample of the whole recording
    volatile boolean capped;                                  // stopped by itself at MAX_SECONDS
    volatile String failed;                                   // the recording thread died: why

    WavRecorder(File out) {
        this.out = out;
    }

    /** The 44 byte WAV header for [dataLen] bytes of sound. */
    static byte[] header(long dataLen) {
        long byteRate = (long) RATE * 2;
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        le32(h, 4, 36 + dataLen);
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        le32(h, 16, 16);
        le16(h, 20, 1);                                        // PCM
        le16(h, 22, 1);                                        // mono
        le32(h, 24, RATE);
        le32(h, 28, byteRate);
        le16(h, 32, 2);                                        // bytes per frame
        le16(h, 34, 16);                                       // bits
        put(h, 36, "data");
        le32(h, 40, dataLen);
        return h;
    }

    private static void put(byte[] h, int at, String s) {
        for (int i = 0; i < s.length(); i++) h[at + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] h, int at, long v) {
        h[at] = (byte) v;
        h[at + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] h, int at, long v) {
        le16(h, at, v);
        le16(h, at + 2, v >> 16);
    }

    /** Start. Throws IOException with words for the person if the microphone cannot be used. */
    void start() throws IOException {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IOException("This watch cannot record sound.");
        AudioRecord r = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE * 2));
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            throw new IOException("The microphone is not available. Is another app using it?");
        }
        final RandomAccessFile raf = new RandomAccessFile(out, "rw");
        raf.setLength(0);
        raf.write(new byte[44]);                                // the real header is written at the end
        rec = r;
        bytes = 0;
        peak = 0;
        loudest = 0;
        capped = false;
        failed = null;
        run = true;
        final AudioRecord ar = r;
        th = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[4096];
                try {
                    ar.startRecording();
                    long max = (long) RATE * 2 * MAX_SECONDS;
                    while (run) {
                        int n = ar.read(buf, 0, buf.length);
                        if (n <= 0) {
                            if (n < 0) failed = "The microphone stopped working.";
                            break;
                        }
                        raf.write(buf, 0, n);
                        bytes += n;
                        int pk = 0;
                        for (int i = 0; i + 1 < n; i += 2) {
                            int v = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
                            if (v < 0) v = -v;
                            if (v > pk) pk = v;
                        }
                        peak = pk;
                        if (pk > loudest) loudest = pk;
                        if (bytes >= max) {
                            capped = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    failed = "Recording stopped: " + e.getMessage();
                } finally {
                    try {
                        ar.stop();
                    } catch (Exception ignored) {
                    }
                    ar.release();
                    try {
                        raf.seek(0);
                        raf.write(header(bytes));
                        raf.close();
                    } catch (IOException e) {
                        if (failed == null) failed = "Could not finish the sound file.";
                    }
                    run = false;
                }
            }
        }, "wav-record");
        th.start();
    }

    boolean isRunning() {
        return th != null && th.isAlive();
    }

    /** Stop and wait until the file is complete. */
    void stop() {
        run = false;
        Thread t = th;
        if (t != null) {
            try {
                t.join(3000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    double seconds() {
        return bytes / (double) (RATE * 2);
    }
}
