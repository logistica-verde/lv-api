# Deploy com Docker

Sobe o Lastro inteiro com `docker compose`: front, API, SQL Server, nó Besu, OSRM e o serviço de elevação. O `lv-optimizer` ainda não está no compose, porque o repositório dele não tem Dockerfile. Sem ele a API responde, mas o cálculo de rotas termina como `FAILED`.

## O que precisa existir

- Docker 24 ou superior com o plugin `docker compose`
- Os repositórios `lv-api` e `lv-frontend` lado a lado, na mesma pasta (o compose usa `../../lv-frontend`)
- 8 GB de memória livre (o SQL Server pede pelo menos 2 GB) e 30 GB de disco

## Primeira vez

```bash
cd lv-api/deploy
cp .env.example .env
```

Preencha no `.env` pelo menos `DB_PASSWORD`, `JWT_SECRET`, `LV_ADMIN_EMAIL` e `LV_ADMIN_PASSWORD`.

**1. Dados do mapa e da altitude** (uma vez só):

```bash
./scripts/prepare-srtm.sh
./scripts/prepare-osrm.sh
./scripts/prepare-besu.sh
```

O primeiro baixa o bloco SRTM da cidade de São Paulo (`S24W047`, cerca de 25 MB). O segundo baixa o mapa da cidade e prepara o OSRM, o que leva alguns minutos. Para outra região, mude `TILES` e `PBF_URL`. Se a URL de download de algum deles mudar, o script falha na hora com a mensagem do `curl`.

O terceiro gera a rede privada do Besu (QBFT, um validador, chain ID `1337`). Se você trocar o chain ID depois, apague a pasta `data/besu` e os volumes (`docker compose down -v`).

**2. Banco e rede Besu:**

```bash
docker compose up -d db db-init besu
```

**3. Implantar o contrato de auditoria** (uma vez só):

```bash
docker compose --profile tools run --rm deploy-contract
```

A saída termina com `BESU_CONTRACT_ADDRESS=0x...`. Copie esse valor para o `.env`.

**4. Subir o resto:**

```bash
docker compose up -d --build
```

Abra `http://localhost` e entre com o e-mail e a senha de `LV_ADMIN_EMAIL` e `LV_ADMIN_PASSWORD`.

## Conferir

```bash
docker compose ps
docker compose logs -f api
```

Na subida a API roda as migrations (Flyway) e avisa se o contrato não está configurado. `curl http://localhost/api/vehicles` sem token deve responder `401`.

## Servidor na nuvem (AWS EC2, DigitalOcean etc.)

1. Máquina Ubuntu 22.04 com 8 GB de memória (na AWS, `t3.large`) e 40 GB de disco.
2. Grupo de segurança liberando só as portas `22` (seu IP), `80` e `443`. Nenhuma outra porta, principalmente a do Besu.
3. Instale o Docker:

   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER
   ```

4. Clone `lv-api` e `lv-frontend` lado a lado e siga os passos de "Primeira vez".
5. Para HTTPS, aponte um domínio para o IP da máquina e, no `.env`, defina `DOMAIN=seu.dominio`, `PUBLIC_URL=https://seu.dominio` e `WEB_PORT=8081`. Suba com:

   ```bash
   docker compose --profile https up -d --build
   ```

   O Caddy emite e renova o certificado sozinho. Feche a porta `8081` no grupo de segurança.

## Observações

- A conta de teste do Besu em `.env.example` é pública. Só serve porque a porta do Besu não é publicada fora da rede do compose.
- O Besu roda uma rede privada QBFT com um validador (chain ID `1337`, gás zero). A imagem está em `latest`; fixe uma versão antes da defesa, para o ambiente não mudar sozinho.
- Os dados do banco e do Besu ficam em volumes do Docker. `docker compose down` mantém; `docker compose down -v` apaga tudo.
- O serviço de elevação (Open-Topo-Data) não tem imagem pública; o compose a constrói a partir do código oficial, o que leva alguns minutos na primeira vez.
- O OSRM e o serviço de elevação são pesados só na preparação dos dados. Rodando, usam pouca memória para uma cidade.
- Para usar a tabela `malha_viaria` em vez do OSRM, defina `GEO_PROVIDER=database`.
