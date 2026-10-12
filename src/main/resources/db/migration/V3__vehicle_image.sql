ALTER TABLE veiculos ADD imagem varchar(20) NOT NULL DEFAULT 'TRUCK';

ALTER TABLE veiculos ADD CONSTRAINT ck_veiculos_imagem
    CHECK (imagem IN ('VAN', 'PICKUP', 'VUC', 'TRUCK', 'SEMI', 'MOTORCYCLE'));
