--liquibase formatted sql

--changeset sam:310-cedolino-tipo-dichiarato
--comment: Tipo dichiarato dall'estrazione (ORDINARIA, TREDICESIMA, QUATTORDICESIMA, FINE_RAPPORTO), distinto dal tipo
--comment: di competenza: serve a riclassificare i cedolini quando la cessazione del rapporto cambia (es. dopo
--comment: un'eliminazione una mensilita' classificata come fine rapporto torna ordinaria). Per le righe esistenti un
--comment: cedolino di fine rapporto e' dichiarato tale solo se il JSON di estrazione lo riporta, altrimenti era una
--comment: mensilita' ordinaria riclassificata.
ALTER TABLE cedolino ADD COLUMN tipo_dichiarato VARCHAR(20);
UPDATE cedolino SET tipo_dichiarato = CASE
    WHEN tipo <> 'FINE_RAPPORTO' THEN tipo
    WHEN estratto_json LIKE '%"FINE_RAPPORTO"%' THEN 'FINE_RAPPORTO'
    ELSE 'ORDINARIA'
END;
ALTER TABLE cedolino ALTER COLUMN tipo_dichiarato SET NOT NULL;
ALTER TABLE cedolino ADD CONSTRAINT ck_cedolino_tipo_dichiarato
    CHECK (tipo_dichiarato IN ('ORDINARIA', 'TREDICESIMA', 'QUATTORDICESIMA', 'FINE_RAPPORTO'));
