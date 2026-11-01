# Cobertura de testes

Medida em 2026-11-01 sobre a `main` de cada repositório (lv-api `27de912`, lv-optimizer `f9ee0f4`, lv-frontend `ed71155`). Na API, usar sempre `clean`: sem ele, classes e relatórios de outras branches ficam em `target/` e entram na contagem. Critério do TCC: mínimo de 80%.

| Repositório | Comando | Testes | Cobertura |
|---|---|---|---|
| lv-api (JaCoCo) | `./mvnw clean test` (relatório em `target/site/jacoco`) | 404 aprovados | instruções 98,1%, ramos 87,6% |
| lv-optimizer (pytest-cov) | `python -m pytest --cov=app` | 199 aprovados | linhas 98% |
| lv-frontend (vitest v8) | `npm run test:coverage` | 854 aprovados | linhas 94,3%, ramos 88,4% |

## lv-api por pacote

| Pacote | Instruções | Ramos |
|---|---|---|
| api | 100,0% | 100,0% |
| auth | 99,8% | 97,6% |
| blockchain | 97,1% | 88,3% |
| deliverypoint | 100,0% | 100,0% |
| execution | 96,8% | 87,5% |
| experiment | 99,4% | 91,7% |
| geo | 99,3% | 85,6% |
| geocode | 95,7% | 86,4% |
| optimizer | 100,0% | 100,0% |
| route | 96,9% | 83,2% |
| settings | 100,0% | 100,0% |
| vehicle | 100,0% | 100,0% |

O pacote raiz (classe `main`) tem 37,5% e não é lógica de negócio.
