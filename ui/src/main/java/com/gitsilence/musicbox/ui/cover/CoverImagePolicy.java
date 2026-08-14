package com.gitsilence.musicbox.ui.cover;

import java.util.Locale;

final class CoverImagePolicy {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private CoverImagePolicy() { }

    static boolean isPngResponse(String contentType, byte[] bytes) {
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return mediaType.equals("image/png") && hasPngSignature(bytes);
    }

    private static boolean hasPngSignature(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (bytes[i] != PNG_SIGNATURE[i]) return false;
        return true;
    }
}
