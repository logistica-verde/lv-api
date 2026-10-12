CREATE TABLE veiculos (
    id bigint IDENTITY NOT NULL,
    placa varchar(10) NOT NULL,
    massa_kg float(53) NOT NULL,
    tipo_combustivel varchar(30) NOT NULL,
    ativo bit NOT NULL,
    CONSTRAINT pk_veiculos PRIMARY KEY (id)
);

CREATE TABLE pontos_entrega (
    id bigint IDENTITY NOT NULL,
    nome varchar(120) NOT NULL,
    endereco varchar(255),
    geom geometry NOT NULL,
    ativo bit NOT NULL,
    CONSTRAINT pk_pontos_entrega PRIMARY KEY (id)
);

CREATE TABLE malha_viaria (
    id bigint IDENTITY NOT NULL,
    geom geometry NOT NULL,
    velocidade_media float(53) NOT NULL,
    declividade float(53) NOT NULL,
    distancia_metros float(53) NOT NULL,
    CONSTRAINT pk_malha_viaria PRIMARY KEY (id)
);

CREATE TABLE operacoes (
    id bigint IDENTITY NOT NULL,
    id_veiculo bigint NOT NULL,
    criterio varchar(20) NOT NULL,
    data_hora datetimeoffset(7) NOT NULL,
    status varchar(20) NOT NULL,
    emissao_total_kg float(53),
    mensagem_erro varchar(500),
    CONSTRAINT pk_operacoes PRIMARY KEY (id),
    CONSTRAINT ck_operacoes_criterio CHECK (criterio IN ('EMISSION', 'DISTANCE')),
    CONSTRAINT ck_operacoes_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT fk_operacoes_veiculo FOREIGN KEY (id_veiculo) REFERENCES veiculos (id)
);

CREATE TABLE operacao_pontos (
    id_operacao bigint NOT NULL,
    id_ponto bigint NOT NULL,
    ordem_visita int NOT NULL,
    CONSTRAINT pk_operacao_pontos PRIMARY KEY (id_operacao, id_ponto),
    CONSTRAINT fk_operacao_pontos_operacao FOREIGN KEY (id_operacao) REFERENCES operacoes (id),
    CONSTRAINT fk_operacao_pontos_ponto FOREIGN KEY (id_ponto) REFERENCES pontos_entrega (id)
);

CREATE TABLE segmentos_rota (
    id bigint IDENTITY NOT NULL,
    id_operacao bigint NOT NULL,
    id_ponto_origem bigint NOT NULL,
    id_ponto_destino bigint NOT NULL,
    ordem int NOT NULL,
    geom geometry NOT NULL,
    velocidade_media float(53) NOT NULL,
    declividade float(53) NOT NULL,
    emissao_kg_co2 float(53) NOT NULL,
    distancia_metros float(53) NOT NULL,
    CONSTRAINT pk_segmentos_rota PRIMARY KEY (id),
    CONSTRAINT fk_segmentos_rota_operacao FOREIGN KEY (id_operacao) REFERENCES operacoes (id),
    CONSTRAINT fk_segmentos_rota_origem FOREIGN KEY (id_ponto_origem) REFERENCES pontos_entrega (id),
    CONSTRAINT fk_segmentos_rota_destino FOREIGN KEY (id_ponto_destino) REFERENCES pontos_entrega (id)
);

CREATE TABLE registros_blockchain (
    id bigint IDENTITY NOT NULL,
    id_operacao bigint NOT NULL,
    hash_sha256 varchar(64) NOT NULL,
    tx_hash varchar(66),
    bloco_ledger bigint,
    timestamp_registro datetimeoffset(7) NOT NULL,
    status_integridade varchar(20) NOT NULL,
    mensagem_erro varchar(500),
    CONSTRAINT pk_registros_blockchain PRIMARY KEY (id),
    CONSTRAINT uq_registros_blockchain_operacao UNIQUE (id_operacao),
    CONSTRAINT ck_registros_blockchain_status CHECK (status_integridade IN ('ANCHORED', 'FAILED')),
    CONSTRAINT fk_registros_blockchain_operacao FOREIGN KEY (id_operacao) REFERENCES operacoes (id)
);
