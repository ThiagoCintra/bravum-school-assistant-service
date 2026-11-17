package br.com.bravum.schoolassistant.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FingerprintTest {
    private final UUID connection = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void sameVisibleContentProducesSameFingerprint() {
        String a = Fingerprint.communication(connection, "CLIPESCOLA", null,
                "Comunicado", "Texto", "GERAL", null);
        String b = Fingerprint.communication(connection, "CLIPESCOLA", null,
                "Comunicado", "Texto", "GERAL", null);
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void externalIdTakesPriorityOverChangedText() {
        String a = Fingerprint.communication(connection, "CLIPESCOLA", "id-123",
                "Título A", "Texto A", "GERAL", null);
        String b = Fingerprint.communication(connection, "CLIPESCOLA", "id-123",
                "Título B", "Texto B", "GERAL", null);
        assertEquals(a, b);
    }

    @Test
    void distinctConnectionOrContentProducesDistinctFingerprint() {
        String a = Fingerprint.communication(connection, "CLIPESCOLA", null,
                "A", "B", null, null);
        String b = Fingerprint.communication(connection, "CLIPESCOLA", null,
                "A", "C", null, null);
        String c = Fingerprint.communication(UUID.randomUUID(), "CLIPESCOLA", null,
                "A", "B", null, null);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }
}
