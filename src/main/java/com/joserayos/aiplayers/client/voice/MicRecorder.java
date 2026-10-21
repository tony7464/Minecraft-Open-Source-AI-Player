package com.joserayos.aiplayers.client.voice;

import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/** Records 16 kHz mono 16-bit PCM from the default microphone on a background thread. */
final class MicRecorder {
    static final AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

    private final int maxBytes;
    private volatile boolean running;
    private TargetDataLine line;
    private Thread thread;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    MicRecorder(int maxSeconds) {
        this.maxBytes = maxSeconds * 16_000 * 2;
    }

    void start() throws LineUnavailableException {
        line = AudioSystem.getTargetDataLine(FORMAT);
        line.open(FORMAT);
        line.start();
        running = true;
        thread = Thread.ofVirtual().name("aiplayers-mic").start(() -> {
            byte[] chunk = new byte[3200];
            while (running && buffer.size() < maxBytes) {
                int n = line.read(chunk, 0, chunk.length);
                if (n > 0) synchronized (buffer) { buffer.write(chunk, 0, n); }
            }
        });
    }

    boolean isFull() { return buffer.size() >= maxBytes; }

    byte[] stop() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (buffer) { return buffer.toByteArray(); }
    }
}
