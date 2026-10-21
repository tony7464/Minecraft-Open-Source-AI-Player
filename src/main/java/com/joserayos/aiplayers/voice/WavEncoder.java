package com.joserayos.aiplayers.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Wraps raw little-endian PCM in a WAV (RIFF) header. */
public final class WavEncoder {
    private WavEncoder() {}

    public static byte[] encode(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels).putInt(sampleRate).putInt(byteRate)
                .putShort((short) (channels * bitsPerSample / 8)).putShort((short) bitsPerSample);
        b.put("data".getBytes()).putInt(pcm.length).put(pcm);
        return b.array();
    }

    /** Root-mean-square level of 16-bit PCM in [0, 1]; used to skip silent recordings. */
    public static double rms16(byte[] pcm) {
        if (pcm.length < 2) return 0;
        double sum = 0;
        int n = pcm.length / 2;
        for (int i = 0; i < n; i++) {
            short s = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            sum += (double) s * s;
        }
        return Math.sqrt(sum / n) / 32768.0;
    }
}
