ALTER TABLE registros_blockchain ADD endereco_contrato varchar(42) NULL;

ALTER TABLE registros_blockchain ADD versao_serializacao int NOT NULL DEFAULT 1;

ALTER TABLE registros_blockchain DROP CONSTRAINT ck_registros_blockchain_status;

ALTER TABLE registros_blockchain ADD CONSTRAINT ck_registros_blockchain_status
    CHECK (status_integridade IN ('ANCHORED', 'FAILED', 'DIVERGENT', 'EXHAUSTED'));
