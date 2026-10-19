CREATE TABLE execucoes (
    id bigint IDENTITY NOT NULL,
    id_operacao bigint NOT NULL,
    status varchar(20) NOT NULL,
    agendada_para datetimeoffset(7),
    criada_em datetimeoffset(7) NOT NULL,
    iniciada_em datetimeoffset(7),
    finalizada_em datetimeoffset(7),
    CONSTRAINT pk_execucoes PRIMARY KEY (id),
    CONSTRAINT ck_execucoes_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'FINISHED', 'CANCELLED')),
    CONSTRAINT fk_execucoes_operacao FOREIGN KEY (id_operacao) REFERENCES operacoes (id)
);

CREATE INDEX ix_execucoes_status_agendada ON execucoes (status, agendada_para);
