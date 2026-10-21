package com.joserayos.aiplayers.voice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minimal multipart/form-data body builder. */
final class Multipart {
    final String boundary = "----aiplayers" + UUID.randomUUID().toString().replace("-", "");
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    Multipart field(String name, String value) {
        write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
        return this;
    }

    Multipart file(String name, String filename, String contentType, byte[] data) {
        write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n");
        out.writeBytes(data);
        write("\r\n");
        return this;
    }

    byte[] build() {
        write("--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    String contentType() { return "multipart/form-data; boundary=" + boundary; }

    private void write(String s) { out.writeBytes(s.getBytes(StandardCharsets.UTF_8)); }
}
