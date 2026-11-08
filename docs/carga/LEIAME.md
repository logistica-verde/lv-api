# Carga na nuvem (B4)

Medida em 2026-11-08, de 22:38 a 22:41 UTC, contra o servidor da AWS (EC2 em us-east-2, Ohio), com a `main` em lv-api `9392db3`, lv-optimizer `6bc69dd` e lv-frontend `e9a653b`. O script é o `scripts/load_test.py`, com a mesma carga do §5.4 do TCC: 10 usuários virtuais, uma thread por usuário, 120 s, intervalo de 0,5 s entre requisições. A cada dez iterações o usuário pede o cálculo de uma rota com quatro pontos sorteados; nas outras, consulta uma rota pronta, a verificação de integridade dela ou a comparação entre os dois critérios. O tempo do algoritmo genético fica fora, porque o cálculo responde 202 e segue em segundo plano.

## Resultado

1.036 requisições em 121 s, nenhuma falha.

| Endpoint | Requisições | 2xx | p50 | p95 | Média | Máximo |
|---|---|---|---|---|---|---|
| `POST /api/routes/calculate` | 100 | 100 | 584 ms | 699 ms | 590 ms | 769 ms |
| `GET /api/routes/{id}` | 311 | 311 | 704 ms | 778 ms | 712 ms | 837 ms |
| `GET /api/routes/{id}/integrity` | 310 | 310 | 554 ms | 628 ms | 563 ms | 668 ms |
| `GET /api/routes/comparison` | 315 | 315 | 712 ms | 797 ms | 721 ms | 907 ms |

## Rede

A carga saiu de um PC no Brasil, e cada requisição abre uma conexão HTTPS nova (o `urllib` não reaproveita conexões). Medida com `curl` logo depois da carga, em 5 requisições a um endpoint que só responde 401:

| Etapa | Média |
|---|---|
| Conexão TCP | 174 ms |
| TCP + TLS | 354 ms |
| Primeira resposta | 515 ms |

Cerca de 515 ms de cada tempo da tabela são rede. Descontando isso, o servidor gastou de 40 a 200 ms por requisição na mediana. Por isso estes números não se comparam diretamente com os do §5.4, que foram medidos na mesma máquina do servidor.

## Servidor logo depois da carga

`docker stats --no-stream`:

```
NAME                 CPU %     MEM USAGE / LIMIT     MEM %
lastro-web-1         0.00%     4.27MiB / 7.594GiB    0.05%
lastro-api-1         0.14%     777.1MiB / 7.594GiB   9.99%
lastro-besu-1        1.51%     361MiB / 7.594GiB     4.64%
lastro-optimizer-1   0.15%     145.1MiB / 7.594GiB   1.87%
lastro-caddy-1       0.00%     17.38MiB / 7.594GiB   0.22%
lastro-elevation-1   0.03%     92.26MiB / 7.594GiB   1.19%
lastro-osrm-1        0.00%     294.8MiB / 7.594GiB   3.79%
lastro-db-1          0.88%     872.6MiB / 7.594GiB   11.22%
```

`free -h`:

```
               total        used        free      shared  buff/cache   available
Mem:           7.6Gi       3.3Gi       1.1Gi       4.1Mi       3.5Gi       4.3Gi
Swap:          4.0Gi        52Ki       4.0Gi
```

Os 100 cálculos pedidos durante a carga (rotas de quatro pontos) já tinham terminado quando o registro foi feito, por isso a CPU aparece quase parada. A memória em uso pelos oito containers soma cerca de 2,5 GiB, e o servidor ficou com 4,3 GiB disponíveis.

## Arquivos

- `aws-2026-11-08.json`: o resultado por endpoint, como o script grava
- `aws-2026-11-08.txt`: a saída do script

## Como repetir

```bash
export LV_E2E_PASSWORD=...   # ou deixe vazio para digitar
python scripts/load_test.py --base-url https://<servidor> --email <administrador> --output carga.json
```
