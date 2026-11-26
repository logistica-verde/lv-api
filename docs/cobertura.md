# Cobertura de testes

Medida em 2026-11-08, de 21:13 a 21:18 UTC, sobre a `main` de cada repositório: lv-api `9392db3`, lv-optimizer `6bc69dd` e lv-frontend `e9a653b`. Todos os PRs do fechamento já estavam incluídos: na API, a correção do nonce (#59), o Besu fixo (#60), os testes de ramos (#61) e os scripts (#62); no otimizador, a rodada padrão (#18), a referência do MEET (#19) e o pacote de reprodução (#20). Na API, use sempre `clean`, porque sem ele classes e relatórios de outras branches ficam em `target/` e entram na contagem. O critério do TCC é cobertura mínima de 80%.

| Repositório | Comando | Testes | Instruções | Linhas | Ramos |
|---|---|---|---|---|---|
| lv-api (JaCoCo) | `./mvnw clean test` (relatório em `target/site/jacoco`) | 480 aprovados, 0 falhas | 97,4% | 97,2% | 87,2% |
| lv-optimizer (pytest-cov) | `python -m pytest --cov=app --cov-branch --cov-report=term-missing` | 224 aprovados, 11 pulados, 0 falhas | — | 96,1% | 91,4% |
| lv-frontend (vitest v8) | `npm run test:coverage` | 864 aprovados, 0 falhas | 94,2% | 94,4% | 88,6% |

Total: 1.568 testes aprovados e nenhuma falha. Os 11 pulados são os casos do teste de referência do MEET (`tests/test_meet_reference.py`), que só rodam quando a planilha `docs/meet-referencia.xlsx` estiver no repositório (item A4). Os relatórios JUnit desta execução geraram a `docs/matriz-requisitos.md`.

Em relação à medição de 2026-11-07, a API passou de 460 para 480 testes (19 do #61 e 1 do #59) e de 83,2% para 87,2% dos ramos. O otimizador ganhou os 2 testes do resumo da rodada padrão e os 11 casos do MEET, ainda pulados. O front não mudou.

O teste da ancoragem contra o Besu real (`BesuLedgerBesuIT`) fica fora dessa contagem: o Surefire, na configuração padrão, não roda classes terminadas em `IT`, e esse teste também exige `LV_BESU_IT_RPC_URL` definido. O resultado dele está em `docs/ancoragem-concorrencia.md`.

## lv-api por pacote

| Pacote | Instruções | Linhas | Ramos |
|---|---|---|---|
| api | 100,0% | 100,0% | — |
| auth | 99,8% | 99,4% | 97,6% |
| base | 100,0% | 100,0% | 100,0% |
| blockchain | 95,2% | 93,8% | 85,2% |
| deliverypoint | 100,0% | 100,0% | — |
| execution | 93,7% | 95,4% | 82,5% |
| experiment | 99,4% | 99,1% | 91,7% |
| geo | 99,3% | 98,7% | 85,9% |
| geocode | 95,7% | 95,5% | 86,4% |
| optimizer | 97,5% | 98,6% | 88,9% |
| progress | 98,3% | 96,7% | 90,4% |
| route | 97,1% | 97,7% | 84,3% |
| settings | 100,0% | 100,0% | — |
| vehicle | 100,0% | 100,0% | 100,0% |

"—" indica pacote sem desvios condicionais. O pacote raiz (classe `main`) tem 37,5% e não é lógica de negócio.

Todos os pacotes passam de 80% em ramos. Na medição anterior, quatro ficavam abaixo (`base` 66,7%, `optimizer` 77,8%, `progress` 73,1% e `route` 78,4%); o #61 cobriu os ramos que faltavam sem mudar código de produção.

## lv-optimizer por módulo

Só os módulos abaixo de 100% (linhas e ramos combinados):

| Módulo | Cobertura |
|---|---|
| `app/api/optimize.py` | 92% |
| `app/optimization/fitness/base.py` | 69% |
| `app/optimization/process_pool.py` | 91% |
| `app/optimization/progress.py` | 96% |
| `app/optimization/route_optimizer.py` | 95% |

## lv-frontend

| Métrica | Cobertura |
|---|---|
| Instruções | 94,19% (2.532 de 2.688) |
| Ramos | 88,58% (1.753 de 1.979) |
| Funções | 92,31% (829 de 898) |
| Linhas | 94,38% (2.406 de 2.549) |
