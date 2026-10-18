CREATE TABLE parametros_ag (
    id bigint NOT NULL,
    populacao int NOT NULL,
    geracoes int NOT NULL,
    taxa_cruzamento float(53) NOT NULL,
    taxa_mutacao float(53) NOT NULL,
    CONSTRAINT pk_parametros_ag PRIMARY KEY (id),
    CONSTRAINT ck_parametros_ag_unico CHECK (id = 1),
    CONSTRAINT ck_parametros_ag_populacao CHECK (populacao BETWEEN 10 AND 500),
    CONSTRAINT ck_parametros_ag_geracoes CHECK (geracoes BETWEEN 10 AND 1000),
    CONSTRAINT ck_parametros_ag_cruzamento CHECK (taxa_cruzamento BETWEEN 0 AND 1),
    CONSTRAINT ck_parametros_ag_mutacao CHECK (taxa_mutacao BETWEEN 0 AND 1)
);

INSERT INTO parametros_ag (id, populacao, geracoes, taxa_cruzamento, taxa_mutacao)
VALUES (1, 100, 200, 0.8, 0.05);
