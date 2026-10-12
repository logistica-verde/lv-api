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
| `JWT_SECRET` | sem padrao (obrigatoria) | Segredo HS256 dos tokens, com pelo menos 32 caracteres. A aplicacao nao sobe sem ele |
| `JWT_EXPIRATION` | `8h` | Validade do token (formato de duracao, por exemplo `30m`) |
| `LV_ADMIN_EMAIL` / `LV_ADMIN_PASSWORD` | vazio | Se a tabela de usuarios estiver vazia, cria o primeiro administrador com esses dados |
| `LV_ADMIN_NAME` | `Administrador` | Nome do primeiro administrador |

## Banco de dados e migrations

O schema e versionado com Flyway em `src/main/resources/db/migration` (arquivos `V<numero>__<descricao>.sql`, escritos para SQL Server). Ao subir, a aplicacao aplica as migrations pendentes e o Hibernate apenas valida (`ddl-auto=validate`).

- Banco novo: a `V1__initial_schema.sql` cria todas as tabelas.
- Banco que ja existe com as tabelas atuais: o Flyway marca a versao 1 como aplicada (`baseline-on-migrate`) e executa so as migrations seguintes.
- Nunca edite uma migration ja publicada; crie uma nova.
- Os testes usam H2 em modo SQL Server. A `SchemaMigrationTest` roda as migrations nele e confere que as entidades validam contra o resultado. O H2 nao substitui o SQL Server: antes de publicar uma migration nova, rode-a tambem num SQL Server de verdade.

## Contrato de auditoria

`contracts/AuditRegistry.sol` guarda o hash SHA-256 de cada operacao (`anchor` e `getHash`). Ele precisa ser compilado e implantado na rede Besu, e o endereco informado em `BESU_CONTRACT_ADDRESS`. Sem isso a rota e calculada normalmente, mas o registro de integridade fica com status `FAILED` e `GET /api/routes/{id}/integrity` responde `503`.

## Autenticacao e papeis de acesso

`POST /api/auth/login` recebe `{"email", "senha"}` e devolve `{"token", "usuario": {"id", "nome", "email", "perfil"}}`. O token e um JWT e vai em `Authorization: Bearer <token>` nas demais chamadas. Credenciais invalidas respondem `401` (a mesma resposta para senha errada e e-mail desconhecido) e usuario desativado responde `403`. Sem token, ou com token invalido, expirado ou sem perfil valido, a API responde `401`; com um perfil sem permissao, `403`.

Para criar o primeiro administrador, suba a aplicacao uma vez com `LV_ADMIN_EMAIL` e `LV_ADMIN_PASSWORD` definidos. Depois disso as duas variaveis podem ser removidas.

| Papel | Permissoes |
|---|---|
| OPERATOR | Le veiculos, pontos e rotas; cadastra pontos de entrega e calcula rotas |
| AUDITOR | Le veiculos, pontos e rotas; verifica integridade (`GET /api/routes/{id}/integrity`) |
| ADMIN | Acesso total, incluindo cadastro e status de veiculos, usuarios e configuracoes |

## Modulos do projeto

`lv-frontend` interface web - `lv-api` este repositorio - `lv-optimizer` motor de otimizacao - `lv-infra` ambiente Docker - `lv-docs` documentacao
