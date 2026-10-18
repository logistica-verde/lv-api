UPDATE operacoes
SET distancia_total_metros = (
    SELECT SUM(s.distancia_metros) FROM segmentos_rota s WHERE s.id_operacao = operacoes.id
)
WHERE status = 'COMPLETED';

CREATE INDEX ix_operacoes_data_hora ON operacoes (data_hora);
CREATE INDEX ix_operacoes_veiculo ON operacoes (id_veiculo);
CREATE INDEX ix_segmentos_rota_operacao ON segmentos_rota (id_operacao);
