# Cobertura de testes

Medida em 2026-11-07, de 20:44 a 20:51 UTC, sobre a `main` de cada repositório: lv-api `b729226`, lv-optimizer `c008c72` e lv-frontend `e9a653b`. Os merges da A1 (#57, #46, #16) já estavam incluídos; a A2 (#59) e a A3 (#60) ainda não. Na API, use sempre `clean`, porque sem ele classes e relatórios de outras branches ficam em `target/` e entram na contagem. O critério do TCC é cobertura mínima de 80%.

| Repositório | Comando | Testes | Instruções | Linhas | Ramos |
|---|---|---|---|---|---|
| lv-api (JaCoCo) | `./mvnw clean test` (relatório em `target/site/jacoco`) | 460 aprovados, 0 falhas | 96,4% | 96,3% | 83,2% |
| lv-optimizer (pytest-cov) | `python -m pytest --cov=app --cov-branch --cov-report=term-missing` | 222 aprovados, 0 falhas | — | 96,1% | 91,4% |
| lv-frontend (vitest v8) | `npm run test:coverage` | 864 aprovados, 0 falhas | 94,2% | 94,4% | 88,6% |

Total: 1.546 testes aprovados e nenhuma falha. Os relatórios JUnit desta execução geraram a `docs/matriz-requisitos.md`.

## lv-api por pacote

| Pacote | Instruções | Linhas | Ramos |
|---|---|---|---|
| api | 100,0% | 100,0% | 100,0% |
| auth | 99,8% | 99,4% | 97,6% |
| base | 95,4% | 91,8% | **66,7%** |
| blockchain | 95,2% | 93,7% | 85,2% |
| deliverypoint | 100,0% | 100,0% | 100,0% |
| execution | 93,7% | 95,4% | 82,5% |
| experiment | 99,4% | 99,1% | 91,7% |
| geo | 99,3% | 98,7% | 85,9% |
| geocode | 95,7% | 95,5% | 86,4% |
| optimizer | 97,5% | 98,6% | **77,8%** |
| progress | 96,9% | 95,7% | **73,1%** |
| route | 93,9% | 94,8% | **78,4%** |
| settings | 100,0% | 100,0% | 100,0% |
| vehicle | 100,0% | 100,0% | 100,0% |

O pacote raiz (classe `main`) tem 37,5% e não é lógica de negócio.

Em ramos, quatro pacotes ficam abaixo de 80%, embora o total passe:
- `base` (66,7%): o cadastro de bases é coberto pelo `BaseControllerTest`, que exercita a API, mas a entidade `Base` tem regras de transição sem teste de unidade.
- `optimizer` (77,8%): faltam os ramos de erro do streaming do otimizador, como um evento sem tipo ou um resultado vazio.
- `progress` (73,1%): faltam os ramos de assinatura inválida do WebSocket (token nulo, mais de 10 operações, id não positivo) e o envio para uma sessão fechada.
- `route` (78,4%): os ramos que faltam são de validação de entrada e de combinação base/origem já cobertos por outros caminhos; o fluxo principal tem cobertura de linhas de 94,8%.

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
