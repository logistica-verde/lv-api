# lv-api

API REST do sistema de Logistica Verde. Orquestra os demais modulos: recebe os pontos de entrega da interface, recupera os atributos da malha viaria no banco, delega o calculo ao `lv-optimizer`, persiste a operacao e registra o hash da rota no ledger.

Modulo do TCC *Sistema de Logistica Verde Inteligente com Otimizacao por Inteligencia Artificial e Auditoria em Blockchain* - Engenharia de Software, UNIP, 2026.

## Stack

- Java 17
- Spring Boot
- Spring Security com autenticacao JWT e controle de acesso por papeis
- Hibernate Spatial sobre SQL Server
- Web3j para comunicacao JSON-RPC com o no Besu

## Pre-requisitos

- JDK 17
- Maven 3.9 ou superior
- Ambiente do `lv-infra` em execucao (SQL Server e no Besu)

## Como rodar

```bash
mvn clean install
mvn spring-boot:run
```

A aplicacao sobe em `http://localhost:8080`.

## Configuracao

A aplicacao le variaveis de ambiente (ou, alternativamente, as propriedades equivalentes em `application-local.properties`, nao versionado):

| Variavel | Padrao | Descricao |
|---|---|---|
| `DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=logistica_verde;...` | JDBC do SQL Server |
| `DB_USERNAME` / `DB_PASSWORD` | vazio | Credenciais do banco |
| `OPTIMIZER_URL` | `http://localhost:8000` | URL do `lv-optimizer` |
| `BESU_RPC_URL` | `http://localhost:8545` | JSON-RPC do no Besu |
| `BESU_CHAIN_ID` | `1337` | Chain ID da rede Besu |
| `BESU_CONTRACT_ADDRESS` | vazio | Endereco do contrato `AuditRegistry` ja implantado |
| `BESU_PRIVATE_KEY` | vazio | Chave privada da conta que assina as transacoes de ancoragem |

## Contrato de auditoria

`contracts/AuditRegistry.sol` guarda o hash SHA-256 de cada operacao (`anchor` e `getHash`). Ele precisa ser compilado e implantado na rede Besu, e o endereco informado em `BESU_CONTRACT_ADDRESS`. Sem isso a rota e calculada normalmente, mas o registro de integridade fica com status `FAILED` e `GET /api/routes/{id}/integrity` responde `503`.

## Papeis de acesso

| Papel | Permissoes |
|---|---|
| OPERATOR | Cria e consulta rotas |
| AUDITOR | Consulta rotas e verifica integridade |
| ADMIN | Acesso total, incluindo gestao de usuarios |

## Modulos do projeto

`lv-frontend` interface web - `lv-api` este repositorio - `lv-optimizer` motor de otimizacao - `lv-infra` ambiente Docker - `lv-docs` documentacao
