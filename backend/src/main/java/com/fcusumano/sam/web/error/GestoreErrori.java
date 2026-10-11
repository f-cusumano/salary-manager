package com.fcusumano.sam.web.error;

import com.fcusumano.sam.shared.DatiNonValidiException;
import com.fcusumano.sam.shared.OperazioneNonConsentitaException;
import com.fcusumano.sam.shared.RisorsaNonTrovataException;
import com.fcusumano.sam.web.api.dto.ErroreCampoDto;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduzione delle eccezioni in Problem Details (RFC 9457) con titoli e messaggi in italiano. I messaggi restituiti
 * non contengono dettagli interni ne' dati personali (al piu' il nome di un file inviato dall'utente stesso).
 * Le eccezioni standard di Spring MVC (validazione, multipart troppo grande, metodo non consentito, ...) sono
 * gestite dalla classe base e riformulate in {@link #handleExceptionInternal}.
 * <p>
 * Nascosto a springdoc: le risposte di errore sono documentate operazione per operazione, come in
 * {@code docs/openapi.yaml}.
 */
@Hidden
@RestControllerAdvice
class GestoreErrori extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GestoreErrori.class);
    private static final String TIPO_BASE = "https://salary-manager.local/problemi/";
    private static final String PACKAGE_APPLICATIVO = "com.fcusumano.sam.";
    private static final String ERRORE_IMPREVISTO = "Si è verificato un errore imprevisto.";

    private final String messaggioUploadTroppoGrande;

    GestoreErrori(@Value(LimitiCaricamento.MAX_FILE) DataSize maxFile,
                  @Value(LimitiCaricamento.MAX_RICHIESTA) DataSize maxRichiesta) {
        this.messaggioUploadTroppoGrande = "Caricamento troppo grande: ogni PDF può essere al massimo di "
                + LimitiCaricamento.descrivi(maxFile) + " (" + LimitiCaricamento.descrivi(maxRichiesta)
                + " in totale per caricamento).";
    }

    @ExceptionHandler(RisorsaNonTrovataException.class)
    ProblemDetail risorsaNonTrovata(RisorsaNonTrovataException e) {
        return problema(HttpStatus.NOT_FOUND, "risorsa-non-trovata",
                e.risorsa() + " inesistente (id " + e.id() + ").");
    }

    @ExceptionHandler(OperazioneNonConsentitaException.class)
    ProblemDetail operazioneNonConsentita(OperazioneNonConsentitaException e) {
        return problema(HttpStatus.CONFLICT, "operazione-non-consentita", e.getMessage());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail modificaConcorrente(ConcurrencyFailureException e) {
        log.warn("Conflitto di concorrenza: {}", e.getClass().getName());
        return problema(HttpStatus.CONFLICT, "modifica-concorrente",
                "I dati sono stati modificati da un'altra operazione in corso: riprova.");
    }

    @ExceptionHandler(RichiestaNonValidaException.class)
    ProblemDetail richiestaNonValida(RichiestaNonValidaException e) {
        return problema(HttpStatus.BAD_REQUEST, "richiesta-non-valida", e.getMessage());
    }

    /**
     * Dati non validi campo per campo: 400 con l'elenco {@code errori} (percorso del campo e messaggio, mai i valori
     * inviati). Il dettaglio e' il messaggio dell'unico errore oppure "N campi da correggere".
     */
    @ExceptionHandler(DatiNonValidiException.class)
    ProblemDetail datiNonValidi(DatiNonValidiException e) {
        var problema = problema(HttpStatus.BAD_REQUEST, "richiesta-non-valida", e.getMessage());
        problema.setProperty("errori", e.errori().stream()
                .map(errore -> new ErroreCampoDto(errore.campo(), errore.messaggio()))
                .toList());
        return problema;
    }

    @ExceptionHandler(FileTroppoGrandeException.class)
    ProblemDetail fileTroppoGrande(FileTroppoGrandeException e) {
        return problema(HttpStatus.CONTENT_TOO_LARGE, "file-troppo-grande", e.getMessage());
    }

    /**
     * Valore rifiutato dalla validazione di un modulo. Il messaggio interno non viene restituito: puo' contenere
     * dettagli tecnici o valori inviati (gli input attesi sono gia' validati con messaggi dedicati).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail argomentoNonValido(IllegalArgumentException e) {
        log.debug("Valore rifiutato da un modulo: {}", e.getClass().getName());
        return problema(HttpStatus.BAD_REQUEST, "richiesta-non-valida", "Dati della richiesta non validi.");
    }

    /** Richiesta multipart malformata (le dimensioni eccessive sono gestite dalla classe base). */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipartNonValido(MultipartException e) {
        return problema(HttpStatus.BAD_REQUEST, "richiesta-non-valida",
                "Caricamento non valido: invia i PDF come multipart/form-data nel campo \"files\".");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail erroreInterno(Exception e) {
        log.error("Errore non gestito: {}", senzaMessaggi(e));
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "errore-interno", ERRORE_IMPREVISTO);
    }

    /** Riformula in italiano le risposte prodotte dalla classe base per le eccezioni standard di Spring MVC. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Errore nella gestione della richiesta: {}", senzaMessaggi(ex));
        } else {
            log.debug("Richiesta rifiutata ({}): {}", statusCode.value(), ex.getClass().getName());
        }
        var italiano = problema(statusCode, tipo(statusCode), dettaglio(ex, statusCode));
        return super.handleExceptionInternal(ex, italiano, headers, statusCode, request);
    }

    private String dettaglio(Exception ex, HttpStatusCode stato) {
        return switch (ex) {
            case MethodArgumentNotValidException e -> messaggi(e.getBindingResult().getAllErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage));
            case HandlerMethodValidationException e -> messaggi(e.getAllErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage));
            case MissingServletRequestPartException e ->
                    "Parte obbligatoria mancante nella richiesta: \"" + e.getRequestPartName() + "\".";
            case MissingServletRequestParameterException e ->
                    "Parametro obbligatorio mancante: \"" + e.getParameterName() + "\".";
            case TypeMismatchException e -> "Valore non valido per \""
                    + Objects.requireNonNullElse(e.getPropertyName(), "parametro") + "\".";
            case HttpMessageNotReadableException _ -> "Corpo della richiesta mancante o non valido (JSON atteso).";
            case MaxUploadSizeExceededException _ -> messaggioUploadTroppoGrande;
            case HttpRequestMethodNotSupportedException e ->
                    "Metodo " + e.getMethod() + " non consentito per questa risorsa.";
            case HttpMediaTypeNotSupportedException _ -> "Tipo di contenuto della richiesta non supportato.";
            case HttpMediaTypeNotAcceptableException _ -> "Formato di risposta richiesto non disponibile.";
            case NoResourceFoundException _, NoHandlerFoundException _ -> "Risorsa inesistente.";
            default -> stato.is5xxServerError() ? ERRORE_IMPREVISTO : "Richiesta non valida.";
        };
    }

    /**
     * Catena delle cause con il punto in cui ciascuna e' stata sollevata (e il primo punto del codice applicativo),
     * senza i messaggi: possono contenere dati personali, per esempio i valori di un vincolo violato riportati dal
     * database, che non vanno nei log.
     */
    static String senzaMessaggi(Throwable errore) {
        var descrizione = new StringBuilder();
        var visitate = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var corrente = errore; corrente != null && visitate.add(corrente); corrente = corrente.getCause()) {
            if (!descrizione.isEmpty()) {
                descrizione.append(" <- causato da ");
            }
            descrizione.append(corrente.getClass().getName());
            var traccia = corrente.getStackTrace();
            if (traccia.length > 0) {
                descrizione.append(" at ").append(traccia[0]);
                Arrays.stream(traccia)
                        .filter(punto -> punto.getClassName().startsWith(PACKAGE_APPLICATIVO))
                        .findFirst()
                        .filter(punto -> punto != traccia[0])
                        .ifPresent(punto -> descrizione.append(" via ").append(punto));
            }
        }
        return descrizione.toString();
    }

    private static String messaggi(Stream<String> messaggi) {
        var testo = messaggi.filter(Objects::nonNull).distinct().collect(Collectors.joining("; "));
        return testo.isEmpty() ? "Dati della richiesta non validi." : testo;
    }

    private static String tipo(HttpStatusCode stato) {
        return switch (stato.value()) {
            case 400 -> "richiesta-non-valida";
            case 404 -> "risorsa-non-trovata";
            case 405 -> "metodo-non-consentito";
            case 406 -> "formato-non-accettabile";
            case 409 -> "operazione-non-consentita";
            case 413 -> "file-troppo-grande";
            case 415 -> "tipo-non-supportato";
            default -> stato.is5xxServerError() ? "errore-interno" : "errore-richiesta";
        };
    }

    private static String titolo(HttpStatusCode stato) {
        return switch (stato.value()) {
            case 400 -> "Richiesta non valida";
            case 404 -> "Risorsa non trovata";
            case 405 -> "Metodo non consentito";
            case 406 -> "Formato non accettabile";
            case 409 -> "Operazione non consentita";
            case 413 -> "File troppo grande";
            case 415 -> "Tipo di contenuto non supportato";
            case 503 -> "Servizio non disponibile";
            default -> stato.is5xxServerError() ? "Errore interno" : "Errore nella richiesta";
        };
    }

    private static ProblemDetail problema(HttpStatusCode stato, String tipo, String dettaglio) {
        var problema = ProblemDetail.forStatusAndDetail(stato, dettaglio);
        problema.setType(URI.create(TIPO_BASE + tipo));
        problema.setTitle(titolo(stato));
        return problema;
    }
}
