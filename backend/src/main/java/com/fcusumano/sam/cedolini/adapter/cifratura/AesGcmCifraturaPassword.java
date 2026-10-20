package com.fcusumano.sam.cedolini.adapter.cifratura;

import com.fcusumano.sam.cedolini.application.CifraturaPassword;
import com.fcusumano.sam.shared.WorkspaceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link CifraturaPassword} con AES-256-GCM: formato {@code versione (1 byte) | nonce (12 byte) | testo cifrato + tag},
 * con l'id del workspace come dati autenticati (una password cifrata non vale in un altro workspace).
 * <p>
 * La chiave e' {@code salary-manager.password-pdf.chiave} (base64 di 32 byte) oppure, se vuota, quella del file
 * {@code salary-manager.password-pdf.file-chiave}, creato al primo uso con una chiave casuale (permessi 600 dove il file
 * system li supporta). Chiave e password non sono mai loggate.
 */
@Component
class AesGcmCifraturaPassword implements CifraturaPassword {

    private static final Logger log = LoggerFactory.getLogger(AesGcmCifraturaPassword.class);

    private static final byte VERSIONE = 1;
    private static final int BYTE_CHIAVE = 32;
    private static final int BYTE_NONCE = 12;
    private static final int BIT_TAG = 128;
    private static final String AAD = "salary-manager/password-pdf/workspace/";

    private final CifraturaProperties properties;
    private final SecureRandom casuale = new SecureRandom();
    private volatile SecretKey chiave;

    AesGcmCifraturaPassword(CifraturaProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] cifra(WorkspaceId workspace, String password) {
        var nonce = new byte[BYTE_NONCE];
        casuale.nextBytes(nonce);
        try {
            var cifrario = Cipher.getInstance("AES/GCM/NoPadding");
            cifrario.init(Cipher.ENCRYPT_MODE, chiave(), new GCMParameterSpec(BIT_TAG, nonce));
            cifrario.updateAAD(aad(workspace));
            var cifrato = cifrario.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + BYTE_NONCE + cifrato.length).put(VERSIONE).put(nonce).put(cifrato).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cifratura AES-GCM non disponibile", e);
        }
    }

    @Override
    public String decifra(WorkspaceId workspace, byte[] cifrata) {
        if (cifrata == null || cifrata.length <= 1 + BYTE_NONCE || cifrata[0] != VERSIONE) {
            throw new IllegalArgumentException("Password cifrata in un formato sconosciuto");
        }
        try {
            var cifrario = Cipher.getInstance("AES/GCM/NoPadding");
            cifrario.init(Cipher.DECRYPT_MODE, chiave(), new GCMParameterSpec(BIT_TAG, cifrata, 1, BYTE_NONCE));
            cifrario.updateAAD(aad(workspace));
            var chiaro = cifrario.doFinal(cifrata, 1 + BYTE_NONCE, cifrata.length - 1 - BYTE_NONCE);
            return new String(chiaro, StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new IllegalArgumentException("Password cifrata non valida con la chiave attuale");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decifratura AES-GCM non disponibile", e);
        }
    }

    private static byte[] aad(WorkspaceId workspace) {
        return (AAD + workspace.valore()).getBytes(StandardCharsets.US_ASCII);
    }

    private SecretKey chiave() {
        var letta = chiave;
        if (letta == null) {
            synchronized (this) {
                letta = chiave;
                if (letta == null) {
                    letta = new SecretKeySpec(caricaChiave(), "AES");
                    chiave = letta;
                }
            }
        }
        return letta;
    }

    private byte[] caricaChiave() {
        if (properties.chiave() != null && !properties.chiave().isBlank()) {
            return decodifica(properties.chiave(), "salary-manager.password-pdf.chiave (SAM_PASSWORD_PDF_CHIAVE)");
        }
        var file = properties.fileChiave();
        if (file == null) {
            throw new IllegalStateException("Chiave delle password dei PDF non configurata: impostare "
                    + "SAM_PASSWORD_PDF_CHIAVE o salary-manager.password-pdf.file-chiave");
        }
        try {
            if (!Files.exists(file)) {
                creaFile(file);
            }
            return decodifica(Files.readString(file, StandardCharsets.US_ASCII), "il file della chiave " + file);
        } catch (IOException e) {
            throw new UncheckedIOException("File della chiave delle password dei PDF non leggibile: " + file, e);
        }
    }

    private void creaFile(Path file) throws IOException {
        var nuova = new byte[BYTE_CHIAVE];
        casuale.nextBytes(nuova);
        var cartella = file.toAbsolutePath().getParent();
        if (cartella != null) {
            Files.createDirectories(cartella);
        }
        try {
            try {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException e) {
                // Windows: nessun permesso POSIX, il file eredita quelli della cartella
                Files.createFile(file);
            }
            Files.writeString(file, Base64.getEncoder().encodeToString(nuova) + System.lineSeparator(),
                    StandardCharsets.US_ASCII);
            log.info("Creato il file della chiave delle password dei PDF ({}): conservarlo insieme al database",
                    file.toAbsolutePath());
        } catch (FileAlreadyExistsException e) {
            // creato nel frattempo da un altro processo: si usa quello
        }
    }

    private static byte[] decodifica(String base64, String origine) {
        byte[] chiave;
        try {
            chiave = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Chiave delle password dei PDF non in base64: " + origine);
        }
        if (chiave.length != BYTE_CHIAVE) {
            throw new IllegalStateException("La chiave delle password dei PDF deve essere di " + BYTE_CHIAVE
                    + " byte in base64: " + origine);
        }
        return chiave;
    }
}
