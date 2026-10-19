package com.capstone.steam.cover;

import java.util.Optional;

/** Formatos de portada admitidos. El tipo sale de los bytes del archivo, nunca de su nombre ni de su Content-Type. */
public enum ImageType {
    JPEG, PNG, WEBP;

    /** Cuántos bytes iniciales hacen falta para reconocer cualquiera de los formatos. */
    public static final int HEADER_SIZE = 12;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /** Detecta el formato por los bytes mágicos; vacío si no es JPEG, PNG ni WebP. */
    public static Optional<ImageType> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (startsWith(head, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        // WebP: "RIFF" + 4 bytes de tamaño + "WEBP"
        if (startsWith(head, 0, ascii("RIFF")) && startsWith(head, 8, ascii("WEBP"))) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] magic) {
        if (data.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** Content-Type con el que se manda el archivo a Cloudinary. */
    public String mediaType() {
        return switch (this) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case WEBP -> "image/webp";
        };
    }
}
