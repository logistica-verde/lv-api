# Ancoragem com o Besu fora do ar

Teste feito em 2026-11-08 no servidor da AWS, com a `main` em `9392db3` e o Besu 26.11.0 (item B3 do fechamento). Horários em UTC.

## Passos

1. No servidor, `docker compose stop besu` e conferência com `docker compose ps -a besu` (status `Exited`).
2. Na interface, cálculo de uma rota com quatro pontos (Estação Brigadeiro, Estação Paraíso, Parque da Aclimação e Estação Ana Rosa). A API criou as operações 9 (menor emissão) e 10 (menor distância) às 22:19:50.
3. Com o Besu parado, a verificação de integridade e o histórico mostram "O serviço de auditoria em blockchain está indisponível no momento".
4. `docker compose start besu` pouco antes das 22:28.
5. Conferência na interface e no banco.

## Resultado

| Tentativa | Operação 9 | Operação 10 | Origem |
|---|---|---|---|
| 1 | FAILED, 22:19:58 | FAILED, 22:19:58 | ancoragem logo depois do cálculo |
| 2 | FAILED, 22:23:10 | FAILED, 22:23:09 | reprocessamento, com o Besu ainda parado |
| 3 | ANCHORED, 22:28:14 | ANCHORED, 22:28:12 | reprocessamento, com o Besu de volta |

As duas operações entraram em blocos seguidos (10 no 123419 e 9 no 123420) e a verificação passou a mostrar "Íntegra", com os dois hashes iguais. Nenhuma operação ficou sem registro, e cada operação gastou 3 das 5 tentativas permitidas (`blockchain.retry.max-attempts`).

Com o nó parado, a API não consegue ler a blockchain, então a interface mostra "indisponível" em vez de "Aguardando registro". Quando o nó voltou, o reprocessamento ancorou as duas operações em seguida, e por isso o estado "Aguardando registro" (nó no ar, operação ainda sem âncora) não chegou a aparecer na tela.

## Saídas

Log da API (`docker compose logs api --since 20m | grep -i -E "anchor|retry"`):

```
2026-11-08T22:19:58.102Z  WARN [route-2]      BlockchainService  : Could not anchor operation 10: Could not anchor hash on Besu: besu
2026-11-08T22:19:58.249Z  WARN [route-1]      BlockchainService  : Could not anchor operation 9: Could not anchor hash on Besu: besu
2026-11-08T22:23:09.992Z  WARN [scheduling-1] BlockchainService  : Could not anchor operation 10: Could not anchor hash on Besu: besu
2026-11-08T22:23:10.025Z  WARN [scheduling-1] BlockchainService  : Could not anchor operation 9: Could not anchor hash on Besu: besu
2026-11-08T22:23:10.038Z  INFO [scheduling-1] BlockchainRetryJob : Retried 2 failed blockchain anchor(s)
2026-11-08T22:28:14.612Z  INFO [scheduling-1] BlockchainRetryJob : Retried 2 failed blockchain anchor(s)
```

Tabela `tentativas_ancoragem` (`SELECT TOP 10 id_operacao, numero, resultado, data_hora ... ORDER BY id DESC`):

```
id_operacao  numero  resultado  data_hora
          9       3  ANCHORED   2026-11-08 22:28:14.5991766 +00:00
         10       3  ANCHORED   2026-11-08 22:28:12.5262380 +00:00
          9       2  FAILED     2026-11-08 22:23:10.0281589 +00:00
         10       2  FAILED     2026-11-08 22:23:09.9989005 +00:00
          9       1  FAILED     2026-11-08 22:19:58.2518516 +00:00
         10       1  FAILED     2026-11-08 22:19:58.1072964 +00:00
          8       1  ANCHORED   2026-11-08 22:11:53.6821440 +00:00
          7       1  ANCHORED   2026-11-08 22:11:53.6282423 +00:00
          6       1  ANCHORED   2026-11-08 21:01:25.3252646 +00:00
          5       1  ANCHORED   2026-11-08 21:01:25.2997725 +00:00
```

As operações 5 a 8 mostram também o efeito da correção do nonce (#59) no servidor: em cada par, os dois critérios foram ancorados com 25 a 54 ms de diferença, ambos na primeira tentativa.
