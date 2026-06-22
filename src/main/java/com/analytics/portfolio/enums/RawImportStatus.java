package com.analytics.portfolio.enums;

public enum RawImportStatus {
    /** Linha gravada em raw, ainda não mapeada */
    PENDING,
    /** Mapeada e persistida com sucesso nas entidades canónicas */
    MAPPED,
    /** Detetada como duplicata pelo fingerprint */
    DUPLICATE,
    /** Erro de parse ou mapeamento */
    FAILED
}
