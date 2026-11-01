ALTER TABLE operacoes ADD CONSTRAINT fk_operacoes_base FOREIGN KEY (id_base) REFERENCES bases (id);
