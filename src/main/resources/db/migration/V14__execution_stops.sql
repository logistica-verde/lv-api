CREATE TABLE execucao_paradas (
    id bigint IDENTITY NOT NULL,
    id_execucao bigint NOT NULL,
    id_ponto bigint NOT NULL,
    ordem int NOT NULL,
    status varchar(20) NOT NULL,
    motivo varchar(500),
    atualizada_em datetimeoffset(7),
    CONSTRAINT pk_execucao_paradas PRIMARY KEY (id),
    CONSTRAINT ck_execucao_paradas_status CHECK (status IN ('PENDING', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT fk_execucao_paradas_execucao FOREIGN KEY (id_execucao) REFERENCES execucoes (id),
    CONSTRAINT fk_execucao_paradas_ponto FOREIGN KEY (id_ponto) REFERENCES pontos_entrega (id),
    CONSTRAINT uq_execucao_paradas UNIQUE (id_execucao, id_ponto)
);

CREATE TABLE execucao_revisoes (
    id bigint IDENTITY NOT NULL,
    id_execucao bigint NOT NULL,
    id_operacao bigint NOT NULL,
    criada_em datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_execucao_revisoes PRIMARY KEY (id),
    CONSTRAINT fk_execucao_revisoes_execucao FOREIGN KEY (id_execucao) REFERENCES execucoes (id),
    CONSTRAINT fk_execucao_revisoes_operacao FOREIGN KEY (id_operacao) REFERENCES operacoes (id)
);

ALTER TABLE execucoes ADD revisao int NOT NULL DEFAULT 0;

ALTER TABLE execucoes ADD mensagem_replanejamento varchar(500) NULL;

ALTER TABLE execucoes DROP CONSTRAINT ck_execucoes_status;

ALTER TABLE execucoes ADD CONSTRAINT ck_execucoes_status
    CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'REPLANNING', 'FINISHED', 'CANCELLED'));
