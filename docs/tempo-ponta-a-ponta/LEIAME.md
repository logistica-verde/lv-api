# Tempo de ponta a ponta (A9 e B4)

Medido em 2026-11-08 com `scripts/e2e_timing.py`, com a `main` de cada repositório em lv-api `9392db3`, lv-optimizer `6bc69dd` e lv-frontend `e9a653b`, o Besu 26.11.0 e a correção do nonce (#59) incluída.

Cada execução pede o cálculo de uma rota pelos dois critérios ao mesmo tempo, como a interface faz (`POST /api/routes/calculate`, resposta 202), e mede dois intervalos por operação:

- **envio → rota pronta:** do pedido até `GET /api/routes/{id}` responder COMPLETED; inclui a fila, o traçado das ruas, a elevação e o algoritmo genético na configuração padrão (população 300, 500 gerações);
- **rota pronta → ancoragem confirmada:** de COMPLETED até `GET /api/routes/{id}/integrity` responder VALID.

Cada rodada tem N = 20 execuções (40 operações), com pontos e veículo sorteados entre os ativos. Na rodada com duas simultâneas, quatro operações correm ao mesmo tempo e quatro ancoragens disputam a mesma conta do Besu.

## Resultados

| Ambiente | Rodada | Pontos por rota | Rota pronta (p50 / p95 / máx) | Ancoragem (p50 / p95 / máx) | Falhas |
|---|---|---|---|---|---|
| Local | uma por vez | 10 a 14 | 11,89 / 14,14 / 30,48 s | 2,08 / 2,14 / 3,03 s | 0 de 40 |
| Local | duas simultâneas | 10 a 15 | 13,39 / 16,23 / 16,47 s | 1,08 / 2,11 / 2,12 s | 0 de 40 |
| AWS | uma por vez | 10 a 30 | 9,88 / 12,36 / 21,44 s | 0,53 / 2,06 / 2,08 s | 0 de 40 |
| AWS | duas simultâneas | 12 a 29 | 11,12 / 16,88 / 17,17 s | 0,55 / 2,09 / 2,14 s | 0 de 40 |

Nenhuma das 160 operações falhou: todas ficaram prontas e todas foram ancoradas e verificadas como VALID. Antes da correção do nonce, um teste rápido do mesmo script no ambiente local, com a `main` de 07/11 (2 execuções, 4 operações), deixou a operação de menor distância da primeira execução sem âncora em 120 s, enquanto a de menor emissão, pedida no mesmo instante, foi ancorada. É o defeito descrito em `docs/ancoragem-concorrencia.md`.

A ancoragem leva de 0,5 a 2,1 s porque o Besu fecha um bloco a cada 2 s (QBFT): o tempo depende de quanto falta para o próximo bloco quando a transação chega.

## Ambientes

- **Local:** Docker Desktop com WSL 2 no Windows 11, Intel Core i7-10700F, 15,9 GB de RAM, de 21:21 a 21:29 UTC. O banco local tem só 15 pontos ativos, por isso as rotas ficaram entre 10 e 15 pontos.
- **AWS:** uma instância EC2 em us-east-2 (Ohio) com 7,6 GiB de RAM, acessada por HTTPS a partir do mesmo PC, de 22:32 a 22:38 UTC. O banco tem 30 pontos ativos, e as rotas foram de 10 a 30 pontos.

Como as faixas de pontos são diferentes, os tempos de cálculo dos dois ambientes não são diretamente comparáveis. A latência de rede até a AWS (cerca de 0,5 s por requisição, ver `docs/carga/LEIAME.md`) entra nos dois intervalos, mas pesa pouco perto do tempo do algoritmo genético.

## Arquivos

- `local-2026-11-08-1-por-vez.csv` e `.txt`
- `local-2026-11-08-2-simultaneas.csv` e `.txt`
- `aws-2026-11-08-1-por-vez.csv` e `.txt`
- `aws-2026-11-08-2-simultaneas.csv` e `.txt`

Os CSV têm uma linha por operação, sem arredondamento; os `.txt` são a saída do script.

## Como repetir

```bash
export LV_E2E_PASSWORD=...   # ou deixe vazio para digitar
python scripts/e2e_timing.py --base-url http://localhost --email <administrador> -n 20 --output uma-por-vez.csv
python scripts/e2e_timing.py --base-url http://localhost --email <administrador> -n 20 --concurrency 2 --seed 2 --output duas-simultaneas.csv
```
