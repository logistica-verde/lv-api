CREATE TABLE bases (
    id bigint IDENTITY NOT NULL,
    nome varchar(120) NOT NULL,
    endereco varchar(255),
    latitude float(53) NOT NULL,
    longitude float(53) NOT NULL,
    ativo bit NOT NULL DEFAULT 1,
    id_ponto bigint NOT NULL,
    CONSTRAINT pk_bases PRIMARY KEY (id),
    CONSTRAINT fk_bases_ponto FOREIGN KEY (id_ponto) REFERENCES pontos_entrega (id)
);

ALTER TABLE operacoes ADD id_base bigint NULL;

ALTER TABLE operacoes ADD retorna_base bit NOT NULL DEFAULT 0;
