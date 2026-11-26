# Verificação do contrato AuditRegistry (item L04)

Feita em 2026-11-26, sobre o `contracts/AuditRegistry.sol` da `main` (última alteração no commit `61e81fa`, de 2026-11-01).

## 1. O bytecode do repositório é o código-fonte compilado

O `AuditRegistry.sol` foi compilado de novo com o solc 0.8.28 (`0.8.28+commit.7893614a`, via `npx solc@0.8.28 --standard-json`, sem otimizador, como no `README.md`). Resultado:

| Item | Resultado |
|---|---|
| Bytecode de criação contra `src/main/resources/contracts/AuditRegistry.bin` | Idêntico (1.426 bytes) |
| ABI contra `src/main/resources/contracts/AuditRegistry.abi` | Idêntica |
| Bytecode de execução (runtime) | 1.330 bytes, com o `owner` (variável `immutable`) em duas posições, bytes 200 e 236 |

A entrada e a saída do compilador estão em `solc-entrada.json` e `solc-0.8.28-saida.json`.

## 2. O contrato da AWS é esse bytecode

O código do contrato implantado na AWS foi lido no servidor com `eth_getCode` em 2026-11-26 (`codigo-aws-2026-11-26.json`):

- **Endereço:** `0x982868164f12f24073e21cddd066c97dd7876f01`
- **Transação de deploy:** `0x23235c4fd8e657e36b736f946b44e5844a22aa6b378cbde48030dc409d6c88f3`, bloco 66
- **Conta que implantou (`owner`):** `0x67a7f5b32b0075a315d0258c366f2b4b848b0dd7`

Preenchendo as duas posições do `owner` no runtime compilado com essa conta, o resultado é **idêntico, byte a byte, aos 1.330 bytes do contrato da AWS**, inclusive o hash dos metadados no fim do código, que identifica o código-fonte e as configurações de compilação.

```bash
python docs/contrato/verificar_bytecode.py --code docs/contrato/codigo-aws-2026-11-26.json --owner 0x67a7f5b32b0075a315d0258c366f2b4b848b0dd7
```

O contrato do Docker local (`0x42699a…210b`, de 2026-10-18) tem 919 bytes: é a versão anterior ao controle de `owner`, que entrou em 2026-11-01, e por isso não é comparável.

## 3. As regras de gravação funcionam na rede de teste

O teste `AuditRegistryAuthorizationBesuIT` implanta o mesmo `AuditRegistry.bin` no Besu 26.11.0 local (chain ID 1337) com duas contas descartáveis, geradas na hora e nunca gravadas, e tenta três gravações. Antes de cada envio, a mesma chamada é simulada com `eth_call`, que devolve o motivo da recusa. Os recibos estão em `rede-de-teste-2026-11-26.json`.

| Tentativa | Conta | Motivo na simulação | Recibo |
|---|---|---|---|
| Gravação autorizada | `owner` | — | status 0x1, bloco 109237, 1 evento `HashAnchored` |
| Segunda gravação da mesma operação | `owner` | `already anchored` | status 0x0 (revertida), bloco 109238 |
| Gravação por conta sem permissão | outra conta | `not the owner` | status 0x0 (revertida), bloco 109239 |

Depois das três tentativas, o contrato guarda o primeiro hash da operação gravada e nenhum hash para a operação da conta sem permissão. O runtime desse contrato de teste também confere com o compilado (`verificar_bytecode.py` com o `owner` de teste).

```bash
docker run -d --rm --name besu-rpc-forward --network lastro_default -p 127.0.0.1:18545:8545 \
  alpine/socat TCP-LISTEN:8545,fork,reuseaddr TCP:besu:8545
LV_BESU_IT_RPC_URL=http://127.0.0.1:18545 LV_BESU_IT_EVIDENCE=recibos.json \
  ./mvnw test -Dtest=AuditRegistryAuthorizationBesuIT -Dsurefire.failIfNoSpecifiedTests=false
docker rm -f besu-rpc-forward
```

Nenhuma chave privada aparece nos arquivos: só endereços, hashes de transação e recibos, que são públicos na própria rede.
