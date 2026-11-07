# Ancoragens simultâneas e o nonce da conta

Investigação feita em 2026-11-07 a partir da branch `fix/anchor-nonce`, com o Besu do Docker Compose local (imagem `hyperledger/besu` 26.10.0, QBFT, chain ID 1337, gás zero).

## Hipótese

O `BesuLedger` criava um `RawTransactionManager` a cada ancoragem, e o web3j buscava o nonce da conta no nó (`eth_getTransactionCount` com `pending`) logo antes de assinar e enviar. A API ancora os dois critérios de uma rota quase ao mesmo tempo. Se as duas ancoragens buscam o nonce antes de qualquer uma ser enviada, as duas assinam com o mesmo nonce. Com preço de gás zero, o Besu aceita a segunda como substituta da primeira no pool, sem devolver erro. A primeira nunca entra em bloco, e a espera pelo recibo termina em 30 s com "Transaction receipt was not generated after 30 seconds". É a mesma mensagem das 20 falhas da cópia de 01/11, todas cerca de 28 a 29 s depois de outra ancoragem confirmada da mesma conta.

## Como foi testado

1. `BesuLedgerConcurrencyTest` (roda sempre, sem rede): um nó falso simula o pool do Besu. O nonce pendente é o número de transações no pool, e um envio com nonce repetido substitui o anterior sem erro. São disparadas 10 ancoragens ao mesmo tempo.
2. `BesuLedgerBesuIT` (só roda com a variável `LV_BESU_IT_RPC_URL`): contra o Besu real. Publica um `AuditRegistry` novo com uma conta descartável e dispara 2 e depois 10 ancoragens ao mesmo tempo, com o mesmo `BesuLedger` da API.

Como a porta do Besu não é publicada fora da rede do Compose, o teste real usou um encaminhamento temporário, só na máquina local:

```bash
docker run -d --rm --name besu-rpc-forward --network lastro_default -p 127.0.0.1:18545:8545 \
  alpine/socat TCP-LISTEN:8545,fork,reuseaddr TCP:besu:8545
LV_BESU_IT_RPC_URL=http://127.0.0.1:18545 ./mvnw test -Dtest=BesuLedgerBesuIT -Dsurefire.failIfNoSpecifiedTests=false
docker rm -f besu-rpc-forward
```

## Resultado

| Ancoragens ao mesmo tempo | Antes (código da `main`) | Depois (envio serializado) |
|---|---|---|
| 2 | 1 confirmada, 1 falhou | 2 confirmadas |
| 10 | 2 confirmadas, 8 falharam | 10 confirmadas |

Todas as falhas do "antes" foram "Transaction receipt was not generated after 30 seconds", o mesmo erro das falhas históricas. No nó falso, o "antes" também falha: ao menos uma ancoragem não confirma em 10 s, porque a transação dela foi substituída no pool.

**A hipótese se confirmou.** As 20 falhas registradas não foram instabilidade do Besu; foram transações da própria API substituídas por outra com o mesmo nonce.

## Correção

O `BesuLedger` passa a serializar o trecho "buscar o nonce e enviar" (`synchronized` em volta do `sendTransaction`). A espera pelo recibo continua fora do bloqueio, então as ancoragens seguem em paralelo depois do envio. Quando uma ancoragem termina de enviar, a transação dela já está no pool, e a próxima busca do nonce pendente devolve o número seguinte.

O reprocessamento (`BlockchainRetryJob`) passa pelo mesmo `BesuLedger`, que é um único bean, e por isso também fica coberto.

Limite: o bloqueio vale para um processo. Se um dia houver mais de uma instância da API usando a mesma conta, será preciso controlar o nonce em um lugar só (por exemplo, uma conta por instância).
