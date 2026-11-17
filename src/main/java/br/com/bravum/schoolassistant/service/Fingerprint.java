package br.com.bravum.schoolassistant.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Chave determinística interna, não um ID oficial do ClipEscola. */
public final class Fingerprint {
    private Fingerprint() {}

    public static String communication(UUID connectionId, String source, String externalId,
                                        String title, String body, String category, String publishedAt) {
        String stableInput = hasText(externalId)
                ? "external-id:" + externalId.trim()
                : "visible-content:" + norm(title) + "\u0000" + norm(body) + "\u0000"
                    + norm(category) + "\u0000" + norm(publishedAt);
        return sha256(connectionId + "\u0000" + norm(source) + "\u0000" + stableInput);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    private static String norm(String value) { return value == null ? "" : value.trim(); }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
