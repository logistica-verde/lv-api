CREATE TABLE experimentos (
    id bigint IDENTITY NOT NULL,
    data_hora datetimeoffset(7) NOT NULL,
    status varchar(20) NOT NULL,
    instancias varchar(200) NOT NULL,
    sementes varchar(200) NOT NULL,
    total_execucoes int NOT NULL,
    execucoes_concluidas int NOT NULL,
    mensagem_erro varchar(500),
    CONSTRAINT pk_experimentos PRIMARY KEY (id),
    CONSTRAINT ck_experimentos_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE experimento_resultados (
    id bigint IDENTITY NOT NULL,
    id_experimento bigint NOT NULL,
    tamanho_instancia int NOT NULL,
    semente int NOT NULL,
    criterio varchar(20) NOT NULL,
    emissao_kg float(53) NOT NULL,
    distancia_metros float(53) NOT NULL,
    tempo_ms bigint NOT NULL,
    convergencia varchar(max) NOT NULL,
    CONSTRAINT pk_experimento_resultados PRIMARY KEY (id),
    CONSTRAINT ck_experimento_resultados_criterio CHECK (criterio IN ('EMISSION', 'DISTANCE')),
    CONSTRAINT uq_experimento_resultados UNIQUE (id_experimento, tamanho_instancia, semente, criterio),
    CONSTRAINT fk_experimento_resultados_experimento FOREIGN KEY (id_experimento) REFERENCES experimentos (id)
);

CREATE INDEX ix_experimento_resultados_experimento ON experimento_resultados (id_experimento);
