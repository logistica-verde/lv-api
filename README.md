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
| `LV_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origens do front autorizadas a chamar a API, separadas por virgula |
| `JWT_SECRET` | sem padrao (obrigatoria) | Segredo HS256 dos tokens, com pelo menos 32 caracteres. A aplicacao nao sobe sem ele |
| `JWT_EXPIRATION` | `8h` | Validade do token (formato de duracao, por exemplo `30m`) |
| `LV_ADMIN_EMAIL` / `LV_ADMIN_PASSWORD` | vazio | Se a tabela de usuarios estiver vazia, cria o primeiro administrador com esses dados |
| `LV_ADMIN_NAME` | `Administrador` | Nome do primeiro administrador |
| `GEO_PROVIDER` | `database` | De onde vem distancia, velocidade e inclinacao dos trechos: `database` (tabela `malha_viaria`) ou `osrm` (rotas do OSRM e altitudes) |
| `OSRM_URL` | `http://localhost:5000` | URL do OSRM, usada com `GEO_PROVIDER=osrm` |
| `ELEVATION_URL` / `ELEVATION_DATASET` | `http://localhost:5001` / `srtm30m` | Servico de altitude no formato Open-Topo-Data |
| `LV_GEOCODER_URL` | `https://nominatim.openstreetmap.org/search` | Provedor da busca de enderecos (formato Nominatim) |
| `LV_GEOCODER_USER_AGENT` | `lv-api/1.0 (Logistica Verde Inteligente)` | Identificacao enviada ao provedor; troque pelo nome e contato reais do projeto |

## Rede viaria, altitude e inclinacao

Com `GEO_PROVIDER=osrm`, a API monta os trechos entre os pontos pela rede viaria real, em vez de usar a linha reta:

- **distancia e velocidade media** vem da rota do OSRM (`/route/v1/driving`): a distancia pelas ruas e a velocidade como distancia dividida pela duracao, limitada entre 5 e 90 km/h
- **inclinacao** sai do perfil de altitude da geometria da rota: o trajeto e amostrado a cada 100 m, a altitude de cada amostra vem do servico de elevacao (SRTM de 30 m), as pontas sao suavizadas em tres amostras e a inclinacao liquida e `(altitude final - inicial) / distancia`, limitada a 15%. O sinal segue o sentido da viagem, entao A para B e B para A tem inclinacoes opostas. Trechos com menos de 50 m ficam com 0, porque o SRTM nao resolve distancias tao curtas
- as altitudes ja consultadas ficam em memoria, em uma grade de cerca de 11 m

O modo `database` continua o padrao. Como subir o OSRM e o servico de altitude esta em [`deploy/README.md`](deploy/README.md).

## Busca de enderecos

`GET /api/geocode?q=<endereco>` (qualquer usuario autenticado) faz a busca no provedor configurado e devolve a lista no formato do Nominatim, `[{"display_name", "lat", "lon"}]`, limitada a 5 resultados no Brasil. A API faz o papel de intermediaria porque o navegador nao consegue enviar o `User-Agent` que o Nominatim publico exige, e para respeitar a politica de uso dele:

- no maximo uma consulta por segundo ao provedor (`lv.geocoder.min-interval`, padrao 1100 ms): consultas seguidas esperam a sua vez
- cache em memoria das buscas repetidas, ignorando maiusculas e espacos extras (`lv.geocoder.cache-ttl`, padrao 24 h, ate 500 buscas)
- `q` com 3 a 200 caracteres; fora disso, `400`
- provedor fora do ar, lento (`lv.geocoder.timeout`, padrao 5 s) ou respondendo erro: `503` ("The address search service is unavailable.")

Trocar de provedor (por exemplo um Nominatim proprio) e so mudar `LV_GEOCODER_URL`; o front nao muda.

## Banco de dados e migrations

O schema e versionado com Flyway em `src/main/resources/db/migration` (arquivos `V<numero>__<descricao>.sql`, escritos para SQL Server). Ao subir, a aplicacao aplica as migrations pendentes e o Hibernate apenas valida (`ddl-auto=validate`).

- Banco novo: a `V1__initial_schema.sql` cria todas as tabelas.
- Banco que ja existe com as tabelas atuais: o Flyway marca a versao 1 como aplicada (`baseline-on-migrate`) e executa so as migrations seguintes.
- Nunca edite uma migration ja publicada; crie uma nova.
- Os testes usam H2 em modo SQL Server. A `SchemaMigrationTest` roda as migrations nele e confere que as entidades validam contra o resultado. O H2 nao substitui o SQL Server: antes de publicar uma migration nova, rode-a tambem num SQL Server de verdade.

## Contrato de auditoria

`contracts/AuditRegistry.sol` guarda o hash SHA-256 de cada operacao (`anchor` e `getHash`). Ele precisa estar implantado na rede Besu, com o endereco informado em `BESU_CONTRACT_ADDRESS`. Sem isso a rota e calculada normalmente, mas o registro de integridade fica com status `FAILED`, `GET /api/routes/{id}/integrity` responde `503` e a aplicacao registra um aviso na subida.

O contrato ja vem compilado em `src/main/resources/contracts/AuditRegistry.bin` (e o ABI em `AuditRegistry.abi`), gerado com o solc 0.8.28. Um teste confere que o bytecode atende as funcoes que o `BesuLedger` chama.

### Implantar o contrato

Com o no Besu no ar e uma conta com fundos (ou com `BESU_GAS_PRICE=0`, o padrao), rode:

```bash
BESU_RPC_URL=http://localhost:8545 BESU_CHAIN_ID=1337 BESU_PRIVATE_KEY=<chave da conta> \
  ./mvnw spring-boot:run -Dspring-boot.run.main-class=br.com.greenvrp.blockchain.AuditRegistryDeployCli
```

Se der certo, a saida traz a linha `BESU_CONTRACT_ADDRESS=0x...`; use esse valor na configuracao da aplicacao. Se falhar, a mensagem explica o motivo (chave ausente, transacao recusada ou revertida, rede indisponivel) e o Maven termina com `BUILD FAILURE`. O comando nao usa o banco de dados.

### Recompilar o contrato

Se alterar `contracts/AuditRegistry.sol`, recompile e substitua os dois arquivos em `src/main/resources/contracts`:

```bash
npx solc@0.8.28 --bin --abi -o build contracts/AuditRegistry.sol
```

O solc-js nomeia os arquivos com o caminho de origem; renomeie para `AuditRegistry.bin` e `AuditRegistry.abi`. Contratos ja implantados nao mudam: uma alteracao exige novo deploy e novo `BESU_CONTRACT_ADDRESS`.

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
