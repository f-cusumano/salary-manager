package com.fcusumano.sam.cedolini.adapter.cifratura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fcusumano.sam.shared.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cifratura delle password ricordate dei PDF: chiave configurata o dal file creato al primo uso. */
class AesGcmCifraturaPasswordTest {

    private static final String CHIAVE = Base64.getEncoder().encodeToString(new byte[32]);
    private static final WorkspaceId WS = WorkspaceId.DEFAULT;

    private static AesGcmCifraturaPassword conChiave(String chiave) {
        return new AesGcmCifraturaPassword(new CifraturaProperties(chiave, null));
    }

    @Test
    void cifraEDecifraUnaPasswordSenzaLasciarlaInChiaro() {
        var cifratura = conChiave(CHIAVE);

        var cifrata = cifratura.cifra(WS, "Pa$sw0rd è segreta");

        assertThat(new String(cifrata, StandardCharsets.ISO_8859_1)).doesNotContain("Pa$sw0rd");
        assertThat(cifratura.decifra(WS, cifrata)).isEqualTo("Pa$sw0rd è segreta");
        assertThat(cifratura.cifra(WS, "Pa$sw0rd è segreta")).as("nonce casuale").isNotEqualTo(cifrata);
    }

    @Test
    void unaPasswordCifrataNonValeInUnAltroWorkspaceNeConUnAltraChiave() {
        var cifrata = conChiave(CHIAVE).cifra(WS, "segreta");
        var altraChiave = new byte[32];
        altraChiave[0] = 1;

        assertThatThrownBy(() -> conChiave(CHIAVE).decifra(new WorkspaceId(2), cifrata))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conChiave(Base64.getEncoder().encodeToString(altraChiave)).decifra(WS, cifrata))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conChiave(CHIAVE).decifra(WS, new byte[] {9, 1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void senzaChiaveConfigurataLaCreaNelFileELaRiusa(@TempDir Path cartella) throws IOException {
        var file = cartella.resolve("dati").resolve("chiave.key");

        var cifrata = new AesGcmCifraturaPassword(new CifraturaProperties(" ", file)).cifra(WS, "segreta");

        assertThat(Base64.getDecoder().decode(Files.readString(file).strip())).hasSize(32);
        assertThat(new AesGcmCifraturaPassword(new CifraturaProperties(null, file)).decifra(WS, cifrata))
                .as("dopo un riavvio la chiave e' la stessa").isEqualTo("segreta");
    }

    @Test
    void unaChiaveNonValidaBloccaLaCifraturaConUnMessaggioChiaro() {
        assertThatThrownBy(() -> conChiave("non-base64!").cifra(WS, "x"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
        assertThatThrownBy(() -> conChiave(Base64.getEncoder().encodeToString(new byte[16])).cifra(WS, "x"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 byte");
    }
}
