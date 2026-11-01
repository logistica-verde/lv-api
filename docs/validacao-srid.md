# Validação de SRID e unidade de distância

Executada em 2026-11-01 no SQL Server do Docker Compose (`lastro-db-1`), dentro de uma transação desfeita ao final.

## Situação encontrada

- As colunas `geom` de `pontos_entrega` e `malha_viaria` são do tipo `geometry`, com SRID 4326 (WGS 84, graus).
- Em `geometry`, `STDistance` calcula no plano cartesiano e devolve **graus**, não metros.
- A consulta `SpatialRepository.findNearestSegment` ordenava por esse valor. Na latitude de São Paulo (−23,5°), um grau de longitude mede cerca de 92% de um grau de latitude, então a ordenação podia escolher o trecho errado.
- Escopo do impacto: essa consulta só é usada com `lv.geo.provider=database`. O sistema implantado usa `GEO_PROVIDER=osrm` (distâncias do OSRM, em metros), e a tabela `malha_viaria` está vazia. Os resultados do experimento não passaram por essa consulta.

## Correção

A ordenação passou a converter a geometria para `geography` (`geography::STGeomFromWKB(geom.STAsBinary(), 4326)`), que calcula a distância geodésica no elipsoide WGS 84 e devolve **metros**.

## Evidência

Ponto de consulta: `POINT(-46.63 -23.55)`. Dois trechos inseridos só para o teste:

| Trecho | Posição | Distância real (geography) | Distância em `geometry` |
|---|---|---|---|
| A (40 km/h) | 0,010° a leste | 1.021,0 m | 0,0100 |
| B (30 km/h) | 0,0095° ao norte | 1.052,1 m | 0,0095 |

| Consulta | Trecho escolhido | Valor devolvido |
|---|---|---|
| Antes (`geometry`) | B, o mais distante | 0,0095 (graus) |
| Depois (`geography`) | A, o mais próximo | 1.021,0 m |

Distância de referência entre a Praça da Sé (−23,5505, −46,6333) e o MASP (−23,5614, −46,6559):

| Método | Resultado |
|---|---|
| SQL Server `geography` (elipsoide WGS 84) | 2.604,1 m |
| Haversine da API (`DistanceCalculator`, esfera de raio médio) | 2.603,0 m |
| SQL Server `geometry` | 0,0251 (graus) |

A diferença entre haversine e `geography` é de 0,04%. O teste `DistanceCalculatorTest.matchesTheGeodesicDistanceSqlServerReportsBetweenPracaDaSeAndMasp` fixa essa referência com tolerância de 0,5%.
