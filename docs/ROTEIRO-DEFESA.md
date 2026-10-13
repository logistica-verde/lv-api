# Roteiro de explicação técnica: Logística Verde Inteligente

Material de estudo para a defesa. Cada seção traz o que dizer, o porquê da decisão, os limites e as perguntas que a banca costuma fazer. Onde algo é limitação ou ainda não foi verificado, está escrito assim de propósito: admitir o limite com clareza pesa mais a seu favor do que ser pego em uma afirmação exagerada.

## 1. A ideia em 30 segundos

O sistema calcula rotas de entrega para um veículo e compara dois critérios: a rota de **menor distância** e a de **menor emissão de CO₂**. Cada cálculo é registrado e tem seu resumo criptográfico (hash) gravado em uma blockchain privada, de modo que qualquer alteração posterior no banco de dados seja detectável. A tela de verificação mostra se o registro continua íntegro.

Peças do sistema:

| Peça | Tecnologia | Papel |
|---|---|---|
| Front | React, TypeScript, Vite | Telas, mapa (Leaflet), gráficos |
| API (`lv-api`) | Spring Boot, JPA, SQL Server | Regras de negócio, autenticação, persistência, integração |
| Otimizador (`lv-optimizer`) | Python, FastAPI | Algoritmo genético que escolhe a ordem de visita |
| Blockchain | Hyperledger Besu (rede privada) + contrato `AuditRegistry` | Guarda o hash de cada operação |

Fluxo de uma rota: o operador escolhe veículo e pontos, o front chama `POST /api/routes/calculate` (uma vez por critério), a API responde `202` na hora e processa em segundo plano (monta os trechos entre os pontos, chama o otimizador, grava o resultado, calcula o hash e ancora na blockchain). O front consulta `GET /api/routes/{id}` até a operação ficar `COMPLETED`.

**Por que assíncrono:** o algoritmo genético demora. Responder `202` e consultar depois evita travar a requisição HTTP e dá para mostrar o andamento ao usuário.

## 2. Por que JWT

**O que é:** depois do login (`POST /api/auth/login`) a API devolve um token assinado que contém o id do usuário, o perfil (`OPERATOR`, `AUDITOR` ou `ADMIN`) e a validade. O front envia o token em `Authorization: Bearer ...` em cada chamada.

**Por que escolhemos:**
- **Sem estado no servidor.** A API valida a assinatura e lê o perfil do próprio token, sem tabela de sessões. É simples e escala sem compartilhar sessão entre instâncias.
- **Combina com SPA + API separadas.** O front é um aplicativo estático em outro endereço; cookies de sessão exigiriam tratar CSRF e domínios.
- **Perfis no token** permitem a API aplicar as regras por endpoint em um único lugar (`SecurityConfig`): administração só para `ADMIN`, verificação de integridade para `AUDITOR` e `ADMIN`, cálculo de rota para `OPERATOR` e `ADMIN`.

**Como está feito:**
- Assinatura HS256 com segredo de pelo menos 32 caracteres (`JWT_SECRET`). A aplicação **nem sobe** sem ele.
- Expiração configurável, padrão 8 horas.
- Senhas guardadas com BCrypt, nunca em texto.
- O login responde igual para "senha errada" e "e-mail inexistente" (`401`) e confere a senha mesmo quando o e-mail não existe, para não vazar por tempo de resposta quais e-mails existem.
- Token inválido, expirado, adulterado, assinado com outro segredo ou sem perfil válido: `401`. Perfil sem permissão: `403`. Os testes cobrem cada um desses casos.
- O primeiro administrador nasce de variáveis de ambiente, não de uma senha fixa no repositório.

**Limites (diga você antes que perguntem):**
- Um JWT **não pode ser revogado** antes de expirar. Desativar um usuário impede novos logins, mas o token já emitido vale até vencer. A saída profissional seria tokens curtos com refresh token ou uma lista de revogação.
- O token fica em `sessionStorage` no navegador: some ao fechar a aba, mas qualquer falha de XSS poderia lê-lo. A alternativa é cookie `HttpOnly`, que traz o problema de CSRF. Foi uma troca consciente.
- HS256 usa um segredo compartilhado; com vários serviços verificando o token, o ideal seria RS256 (chave pública e privada).

**Perguntas prováveis:**
- *Por que não sessão com cookie?* Resposta acima: API e front separados, sem estado, mais simples de escalar.
- *E se o token vazar?* Vale até expirar; por isso a expiração é limitada e a evolução natural é refresh token.
- *Como o front protege as telas?* Rotas protegidas redirecionam para o login e escondem o que o perfil não acessa, mas **a segurança de verdade está na API**: o front só melhora a experiência.

## 3. Por que Flyway

**O problema:** sem controle, o banco de cada pessoa e de cada ambiente fica diferente, e ninguém sabe qual script rodou. Antes, o schema dependia de criação manual.

**O que o Flyway faz:** o schema é descrito em migrations numeradas (`V1__initial_schema.sql`, `V2__users.sql`, ...) em `src/main/resources/db/migration`. Ao subir, a aplicação aplica só as que faltam, em ordem, e registra o que foi aplicado. O Hibernate ficou em `validate`: ele não altera nada, só confere se as entidades batem com o banco.

**Decisões:**
- A `V1` foi **gerada pelo próprio Hibernate no dialeto do SQL Server** a partir das entidades, para bater exatamente com o que o `validate` espera.
- **Baseline.** Um banco que já existia com as tabelas atuais é marcado como "já está na versão 1" e recebe só as migrations seguintes; um banco novo é criado pela V1.
- Migration publicada **nunca se edita**; qualquer mudança é uma migration nova. As migrations desta entrega: usuários (V2), imagem do veículo (V3), parâmetros do algoritmo (V4), tempo de execução (V5), histórico e índices (V6), experimentos (V7).

**Como foi testado e o limite:** os testes rodam as migrations em H2 no modo de compatibilidade com SQL Server e conferem que as entidades validam contra o resultado (isso já pegou um erro de tipo de coluna durante o desenvolvimento). **H2 não é SQL Server**: as migrations nunca foram executadas num SQL Server de verdade. Esse é o próximo passo antes de produção.

**Pergunta provável:** *por que não deixar o Hibernate criar as tabelas (`ddl-auto=update`)?* Porque não deixa histórico, não é seguro em produção, pode perder dados e não permite revisar a mudança antes de aplicar.

## 4. Verificação de integridade

**Objetivo:** provar que o resultado de um cálculo (rota, emissão, trechos) **não foi alterado depois** de calculado, mesmo por alguém com acesso ao banco.

**Como funciona, em quatro passos:**

1. **Resumo canônico.** Quando uma operação termina, o `HashGenerator` monta um JSON em ordem e formato fixos e calcula o SHA-256 dele. Entram: id da operação, veículo, critério, data de criação, emissão total, a lista de pontos com a ordem de visita e cada trecho (origem, destino, distância, velocidade média, declividade e emissão). Os números são arredondados em 6 casas para o resultado ser sempre o mesmo. Se qualquer um desses valores mudar, o hash muda.
2. **Ancoragem.** O hash é gravado no contrato `AuditRegistry` (função `anchor(operationId, hash)`), numa rede Besu privada. O contrato **recusa** hash vazio e **recusa** sobrescrever um id já registrado: o que entrou não pode ser trocado.
3. **Registro local.** A API guarda o recibo (hash da transação, bloco, data) na tabela `registros_blockchain`.
4. **Verificação.** Para verificar, a API **recalcula o hash a partir do que está no banco agora** e lê o hash guardado no contrato (`getHash`). Se forem iguais, o status é `VALID`; se diferentes, `TAMPERED` (adulterado); se o contrato não tem nada para aquele id, `NOT_ANCHORED`.

**Por que uma blockchain e não só um hash no banco?** Quem altera o banco também alteraria o hash guardado ao lado. O hash na blockchain fica fora do alcance de quem controla o banco de dados e não pode ser reescrito: a blockchain é a "testemunha" independente.

**O que isso garante e o que NÃO garante (importante):**
- Garante que, se o banco for mexido nos campos que entram no hash, a diferença é detectada.
- **Não** protege campos que ficam fora do hash: por exemplo o nome e as coordenadas dos pontos de entrega e a massa do veículo não entram no resumo. Se a banca perguntar, a resposta é: o hash cobre o resultado do cálculo; ampliar a cobertura é só incluir mais campos na montagem do JSON canônico.
- **Não** impede a alteração, só a revela. E prova que o dado não mudou desde a ancoragem, não que o cálculo original estava correto.
- Se o hash for ancorado depois de o dado já estar errado, a blockchain registra o errado. Por isso a ancoragem acontece logo após o cálculo.

**Rede privada e custo:** como é uma rede privada, o preço do gás pode ser zero; em rede pública cada ancoragem custaria dinheiro. O custo por registro é um dos motivos para guardar só o hash (32 bytes) e não os dados.

**Comportamento quando a blockchain falha:** a rota é calculada normalmente; o registro fica como falha de ancoragem e a verificação responde `503` com mensagem clara. O sistema não depende da blockchain para funcionar, só para auditar.

**O que ainda não foi comprovado:** o contrato está compilado e o comando de deploy testado contra uma rede simulada, mas **ainda não foi implantado num Besu real**. Diga isso com franqueza e mostre que o teste de ponta a ponta (implantar, calcular, verificar) é o próximo passo.

## 5. As duas rotas e a comparação

- Cada cálculo gera **duas operações** com os mesmos pontos e o mesmo veículo: uma otimizando emissão, outra otimizando distância.
- A API valida que a comparação faz sentido: critérios corretos, mesmo veículo e mesmo conjunto de pontos (a ordem em que foram informados não importa).
- A tela mostra lado a lado emissão, distância, tempo de execução e a diferença percentual, com o selo de integridade de cada uma.
- A emissão de cada trecho depende da massa do veículo, velocidade média e declividade; por isso a rota mais curta nem sempre é a que menos emite.

## 6. Decisões de qualidade que valem citar

- **Camadas claras:** controller recebe e valida, service aplica a regra, repositório acessa o banco; classes pequenas com injeção por construtor.
- **Erros padronizados:** um único tratador converte exceções de negócio em respostas HTTP no formato `ProblemDetail` (`400`, `401`, `403`, `404`, `409`, `503`), e o front mostra mensagens em português.
- **Testes automatizados:** 324 no back e 655 no front rodam a cada alteração, incluindo as regras de acesso por perfil de ponta a ponta.
- **Busca de endereço pela API:** o navegador não consegue identificar a aplicação para o Nominatim; a API faz isso, limita a uma consulta por segundo e guarda cache.
- **Sem rolagem vertical** e layout de 375 a 1280 px, com tema claro e escuro.

## 7. Limitações honestas

1. **O otimizador ainda não está implementado** neste repositório (só tem o esqueleto com `/health`). O contrato de comunicação está definido e testado do lado da API, mas o algoritmo genético em si é a parte pendente.
2. As migrations nunca rodaram em SQL Server real; o contrato nunca rodou em um Besu real.
3. JWT sem revogação e `sessionStorage` (seção 2).
4. O filtro de integridade do histórico é feito em memória, com limite de 1000 operações; consultar o ledger em cada linha limita o tamanho da página.
5. O experimento de convergência usa instâncias **sintéticas** (pontos sorteados em São Paulo, de forma reproduzível), não pontos reais.

## 8. Perguntas difíceis e respostas curtas

- **"Por que não guardar tudo na blockchain?"** Caro, lento e dados de negócio não devem ficar expostos; o hash basta para provar integridade.
- **"E se alguém alterar o hash no banco também?"** Não adianta: a verificação compara com o hash do contrato, que o dono do banco não consegue alterar.
- **"E se o nó Besu cair?"** O cálculo continua; só a ancoragem e a verificação ficam indisponíveis (`503`), e o registro mostra a falha.
- **"Por que Spring Boot e React?"** Ecossistema maduro, tipagem forte nos dois lados, boa testabilidade; o contrato entre eles é validado no front com esquemas (zod) e coberto por testes.
- **"Como garantem que o front e a API conversam certo?"** O front valida cada resposta contra um esquema; se a API mudar o formato, o erro aparece na hora e não em dados silenciosamente errados. Os formatos estão descritos nas issues e PRs.
- **"O que faria com mais tempo?"** Implementar e medir o otimizador, rodar tudo num ambiente real (SQL Server e Besu), refresh token, ampliar o hash e testes de ponta a ponta com navegador.

## 9. Como demonstrar

1. Login como **operador**: planejar uma rota (mapa, pontos, calcular) e abrir a comparação.
2. Login como **auditor**: histórico com o selo de integridade e a tela de verificação.
3. Mostrar um registro **adulterado** (no ambiente simulado há uma operação adulterada de propósito) e explicar o porquê da diferença entre os hashes.
4. Login como **administrador**: veículos com imagem, usuários e perfis, parâmetros do algoritmo.

## 10. Uso de ferramentas

Se a instituição exigir declaração do uso de ferramentas de apoio, inclua um parágrafo na metodologia descrevendo o que foi usado e como. Confirme com o orientador o que a regra do curso pede; declarar com clareza é sempre o caminho mais seguro.
