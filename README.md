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

Variaveis esperadas em `application-local.properties` (nao versionado):

```
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=logistica_verde
spring.datasource.username=
spring.datasource.password=
optimizer.url=http://localhost:8000
besu.rpc.url=http://localhost:8545
jwt.secret=
```

## Papeis de acesso

| Papel | Permissoes |
|---|---|
| OPERATOR | Cria e consulta rotas |
| AUDITOR | Consulta rotas e verifica integridade |
| ADMIN | Acesso total, incluindo gestao de usuarios |

## Modulos do projeto

`lv-frontend` interface web - `lv-api` este repositorio - `lv-optimizer` motor de otimizacao - `lv-infra` ambiente Docker - `lv-docs` documentacao
