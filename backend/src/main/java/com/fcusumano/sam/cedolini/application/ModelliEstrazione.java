package com.fcusumano.sam.cedolini.application;

/**
 * Identificativi dei lettori registrati in {@code cedolino.modello_estrazione} che non provengono da un adapter di
 * lettura.
 */
public final class ModelliEstrazione {

    /** Cedolino inserito a mano da un file in errore (versione del formato del modulo). */
    public static final String MODELLO_INSERIMENTO_MANUALE = "inserimento-manuale-1";

    private static final String PREFISSO_INSERIMENTO_MANUALE = "inserimento-manuale";

    private ModelliEstrazione() {
    }

    /** True se il modello registrato e' l'inserimento a mano (di qualunque versione). */
    public static boolean inseritoAMano(String modello) {
        return modello != null && modello.startsWith(PREFISSO_INSERIMENTO_MANUALE);
    }
}
