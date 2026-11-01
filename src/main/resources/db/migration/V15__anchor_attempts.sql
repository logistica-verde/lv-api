CREATE TABLE tentativas_ancoragem (
    id bigint IDENTITY(1,1) NOT NULL,
    id_operacao bigint NOT NULL,
    numero int NOT NULL,
    resultado varchar(20) NOT NULL,
    tx_hash varchar(66) NULL,
    mensagem_erro varchar(500) NULL,
    data_hora datetimeoffset NOT NULL,
    CONSTRAINT pk_tentativas_ancoragem PRIMARY KEY (id),
    CONSTRAINT uq_tentativas_ancoragem_numero UNIQUE (id_operacao, numero),
    CONSTRAINT ck_tentativas_ancoragem_resultado CHECK (resultado IN ('ANCHORED', 'RECOVERED', 'FAILED')),
    CONSTRAINT fk_tentativas_ancoragem_operacao FOREIGN KEY (id_operacao) REFERENCES operacoes (id)
);
