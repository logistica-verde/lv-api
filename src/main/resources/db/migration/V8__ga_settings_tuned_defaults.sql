UPDATE parametros_ag
SET populacao = 300, geracoes = 1000, taxa_cruzamento = 0.9, taxa_mutacao = 0.4
WHERE id = 1
  AND populacao = 100 AND geracoes = 200 AND taxa_cruzamento = 0.8 AND taxa_mutacao = 0.05;
