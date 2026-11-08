# Matriz requisito → caso de teste → resultado

Gerada em 2026-11-07 20:54 UTC por `scripts/matriz_requisitos.py`, a partir dos relatórios JUnit da última execução das três suítes.

| Repositório | Commit | Casos executados | Aprovados | Falharam |
|---|---|---|---|---|
| lv-api | `b729226` | 460 | 460 | 0 |
| lv-optimizer | `c008c72` | 222 | 222 | 0 |
| lv-frontend | `e9a653b` | 864 | 864 | 0 |

## Casos de uso (Quadro 5)

| ID | Requisito | Testes | Tipo | Resultado |
|---|---|---|---|---|
| UC01 | Operador cadastra pontos de entrega | lv-api: `DeliveryPointControllerTest` (4)<br>lv-api: `SecurityRulesTest#operatorsCanRegisterDeliveryPoints`<br>lv-api: `DeliveryPointRepositoryTest` (2)<br>lv-frontend: `src/pages/PlanningPage/AddPoint/AddPointDialog.test.tsx` (30) | unidade, integração, interface | 37/37 aprovados |
| UC02 | Operador calcula a rota de menor emissão | lv-api: `RouteControllerTest` (18)<br>lv-api: `RouteServiceTest` (7)<br>lv-api: `RouteProcessorTest` (18)<br>lv-api: `RouteFlowIntegrationTest#calculatesRouteAsynchronouslyAndExposesResultByPolling`<br>lv-optimizer: `test_optimize_api.py::test_emission_criterion_avoids_slow_uphill_legs_that_distance_ignores`<br>lv-optimizer: `test_fitness.py` (6)<br>lv-frontend: `src/pages/PlanningPage/PlanningPage.test.tsx` (28)<br>lv-frontend: `src/pages/PlanningPage/Calculation/CalculatingDialog.test.tsx` (3) | unidade, integração, interface | 82/82 aprovados |
| UC03 | Operador calcula a rota de menor distância | lv-api: `RouteControllerTest` (18)<br>lv-api: `RouteProcessorTest` (18)<br>lv-optimizer: `test_optimize_api.py::test_distance_criterion_finds_the_shortest_open_route_on_a_small_instance`<br>lv-optimizer: `test_fitness.py::test_distance_fitness_depends_on_visit_order`<br>lv-frontend: `src/pages/PlanningPage/PlanningPage.test.tsx` (28) | unidade, integração, interface | 66/66 aprovados |
| UC04 | Operador compara as emissões das duas rotas | lv-api: `RouteComparisonTest` (7)<br>lv-api: `RouteControllerTest#comparisonReturnsBothRoutesWithTheirExecutionTimes`<br>lv-frontend: `src/pages/ComparisonPage/ComparisonPage.test.tsx` (26) | unidade, interface | 34/34 aprovados |
| UC05 | Operador vê a rota no mapa interativo | lv-api: `RouteControllerTest#returnsTheStreetGeometryOfEachSegmentAsLatitudeAndLongitude`<br>lv-api: `RoadNetworkSegmentSourceTest#carriesTheStreetGeometryOfEachRoute`<br>lv-frontend: `src/components/Map/MapView.test.tsx` (8)<br>lv-frontend: `src/components/Map/RoutePolyline.test.tsx` | unidade, interface | 11/11 aprovados |
| UC06 | Auditor consulta o registro na blockchain pelo ID da operação | lv-api: `IntegrityControllerTest` (3)<br>lv-api: `SecurityRulesTest#keepsTheIntegrityReportToAuditorsAndAdministrators`<br>lv-frontend: `src/pages/VerificationPage/VerificationPage.test.tsx` (14)<br>lv-frontend: `src/pages/VerificationPage/VerdictCard.test.tsx` (12) | unidade, integração, interface | 30/30 aprovados |
| UC07 | Auditor verifica se os dados foram alterados depois do registro | lv-api: `IntegrityVerifierTest` (7)<br>lv-api: `HashGeneratorTest` (8)<br>lv-api: `RouteFlowIntegrationTest#detectsEveryKindOfAlterationMadeDirectlyInTheDatabase`<br>lv-api: `RouteFlowIntegrationTest#anchorsTheCompletedRouteAndReportsItAsValid` | unidade, integração | 17/17 aprovados |
| UC08 | Administrador cadastra veículos com a massa | lv-api: `VehicleControllerTest` (14)<br>lv-api: `VehicleRepositoryTest` (5)<br>lv-api: `SecurityRulesTest#onlyAdministratorsRegisterVehicles`<br>lv-frontend: `src/pages/AdminPage/AdminPage.test.tsx` (30) | unidade, integração, interface | 50/50 aprovados |
| UC09 | Administrador gerencia usuários e perfis | lv-api: `UserAdminServiceTest` (12)<br>lv-api: `UserAdminControllerTest` (7)<br>lv-api: `UserAdminIntegrationTest` (4)<br>lv-api: `SecurityRulesTest#administratorsListAndChangeUsersThroughTheApi` | unidade, integração | 24/24 aprovados |
| UC10 | Administrador configura os parâmetros do AG | lv-api: `GaSettingsServiceTest` (3)<br>lv-api: `GaSettingsControllerTest` (16)<br>lv-api: `RouteProcessorTest#sendsTheConfiguredGeneticAlgorithmParametersToTheOptimizer`<br>lv-optimizer: `test_optimize_ga_params.py` (11) | integração, unidade | 31/31 aprovados |

## Segurança

| ID | Requisito | Testes | Tipo | Resultado |
|---|---|---|---|---|
| S01 | Sem token, qualquer endpoint da API responde 401 | lv-api: `SecurityRulesTest#everyApiEndpointRequiresAToken`<br>lv-api: `SecurityRulesTest#routeCalculationRequiresAToken` | integração | 2/2 aprovados |
| S02 | Token expirado, adulterado, de outro segredo ou sem perfil responde 401 | lv-api: `SecurityRulesTest#rejectsAnExpiredToken`<br>lv-api: `SecurityRulesTest#rejectsATamperedToken`<br>lv-api: `SecurityRulesTest#rejectsATokenSignedWithAnotherSecret`<br>lv-api: `SecurityRulesTest#rejectsASignedTokenThatHasNoValidProfile` | integração | 4/4 aprovados |
| S03 | Perfil sem permissão responde 403 (Operador na administração, Auditor no cálculo) | lv-api: `SecurityRulesTest#auditorsCannotRegisterDeliveryPointsOrCalculateRoutes`<br>lv-api: `SecurityRulesTest#keepsUserAndSettingsAdministrationToAdministrators`<br>lv-api: `SecurityRulesTest#onlyAdministratorsChangeVehicleStatus`<br>lv-api: `ExecutionControllerTest#anAuditorCannotCreateOrChangeExecutions`<br>lv-api: `BaseControllerTest#anOperatorCannotRegisterOrChangeABase` | integração, unidade | 5/5 aprovados |
| S04 | Perfil correto acessa (fluxo do login à chamada protegida) | lv-api: `SecurityRulesTest#allowsTheFullFlowFromLoginToAProtectedCall`<br>lv-api: `SecurityRulesTest#operatorsAndAdministratorsReachTheRouteCalculation`<br>lv-api: `AuthServiceTest` (5)<br>lv-api: `AuthControllerTest` (4)<br>lv-frontend: `src/pages/LoginPage/LoginPage.test.tsx` (16)<br>lv-frontend: `src/router.test.tsx` (18) | integração, unidade, interface | 45/45 aprovados |
| S05 | Senha errada não revela se o e-mail existe | lv-api: `SecurityRulesTest#refusesTheWrongPasswordAndHidesWhetherTheEmailExists`<br>lv-api: `AuthServiceTest#rejectsAnUnknownEmailWithTheSameErrorAsAWrongPassword` | integração, unidade | 2/2 aprovados |
| S06 | Só origens permitidas (CORS); token só no cabeçalho | lv-api: `CorsTest` (7) | integração | 7/7 aprovados |

## Ancoragem

| ID | Requisito | Testes | Tipo | Resultado |
|---|---|---|---|---|
| A01 | Rota concluída tem o hash ancorado (ANCHORED) com txHash e bloco | lv-api: `BlockchainServiceTest#anchorsTheOperationHashAndStoresTheReceipt`<br>lv-api: `BesuLedgerTest` (9) | unidade | 10/10 aprovados |
| A02 | Hash já no contrato após timeout é recuperado sem reenviar (RECOVERED) | lv-api: `BlockchainServiceTest#recoversTheRecordWithoutResendingWhenTheHashIsAlreadyOnTheLedger` | unidade | 1/1 aprovados |
| A03 | Falha de envio fica FAILED e é reprocessada no mesmo registro | lv-api: `BlockchainServiceTest#recordsFailureWhenTheLedgerIsUnavailable`<br>lv-api: `BlockchainServiceTest#logsEveryAttemptAndRetriesAFailedAnchorOnTheSameRecord`<br>lv-api: `BlockchainServiceTest#keepsTheRecordFailedWhenTheRetryFailsToo` | unidade | 3/3 aprovados |
| A04 | Hash diferente no contrato vira DIVERGENT, sem reenvio nem reprocessamento | lv-api: `BlockchainServiceTest#flagsADivergenceInsteadOfResendingWhenTheLedgerHoldsAnotherHash`<br>lv-api: `BlockchainServiceTest#tellsWhenTheLedgerHoldsTheHashSentEarlierButTheOperationChangedSince`<br>lv-api: `BlockchainServiceTest#neverRetriesOrReanchorsADivergentOperation` | unidade | 3/3 aprovados |
| A05 | Depois do limite de tentativas o registro vira EXHAUSTED | lv-api: `BlockchainServiceTest#givesUpAfterTheMaximumNumberOfAttemptsAndStopsRetrying`<br>lv-api: `IntegrityVerifierTest#reportsAFailedAnchorOnceTheAttemptsRunOut` | unidade | 2/2 aprovados |
| A06 | Cada registro guarda o contrato e a versão da serialização | lv-api: `BlockchainServiceTest#recordsTheContractAndTheSerializationVersionOfEveryRecord`<br>lv-api: `HashGeneratorTest#producesTheKnownSha256ForTheCanonicalJson`<br>lv-api: `HashGeneratorTest#producesTheSameHashWhateverTheTimeZoneOfTheServer` | unidade | 3/3 aprovados |
| A07 | Só a conta da API grava no contrato; segunda gravação é recusada | lv-api: `AuditRegistryArtifactTest#onlyTheAccountThatDeployedTheContractCanAnchor` | unidade | 1/1 aprovados |
| A08 | Ancoragens simultâneas não reaproveitam o nonce | lv-api: `BesuLedgerConcurrencyTest` | unidade | sem execução |

## Verificação de integridade

| ID | Requisito | Testes | Tipo | Resultado |
|---|---|---|---|---|
| V01 | Dados iguais ao contrato: VALID | lv-api: `IntegrityVerifierTest#reportsValidWhenTheDatabaseMatchesTheLedger`<br>lv-frontend: `src/pages/VerificationPage/VerdictCard.test.tsx` (12) | unidade, interface | 13/13 aprovados |
| V02 | Dados alterados no banco: TAMPERED, mesmo sem o registro local | lv-api: `IntegrityVerifierTest#reportsTamperedWhenStoredDataChangesAfterAnchoring`<br>lv-api: `IntegrityVerifierTest#stillDetectsTamperingWhenTheLocalRecordWasAlsoDeleted`<br>lv-api: `RouteFlowIntegrationTest#detectsEveryKindOfAlterationMadeDirectlyInTheDatabase` | unidade, integração | 3/3 aprovados |
| V03 | Sem hash no contrato: NOT_ANCHORED (ancoragem pendente) | lv-api: `IntegrityVerifierTest#reportsNotAnchoredWhenTheLedgerHasNoHash`<br>lv-api: `OperationHistoryServiceTest#reportsTheIntegrityOfEachOperationAndSkipsTheLedgerForUnanchoredOnes` | unidade, integração | 2/2 aprovados |
| V04 | Besu indisponível: 503 com mensagem clara | lv-api: `IntegrityControllerTest#mapsLedgerFailureToServiceUnavailable`<br>lv-api: `OperationHistoryControllerTest#mapsAnUnavailableLedgerTo503` | unidade | 2/2 aprovados |

## Otimizador

| ID | Requisito | Testes | Tipo | Resultado |
|---|---|---|---|---|
| O01 | Modelo MEET segue as equações A10, A11 e A14 e as tabelas de Hickman et al. (1999) | lv-optimizer: `test_meet_emission_model.py` (18) | unidade | 18/18 aprovados |
| O02 | Operadores do AG geram permutações válidas (OX, inversão, elitismo, torneio) | lv-optimizer: `test_crossover.py` (54)<br>lv-optimizer: `test_mutation.py` (54)<br>lv-optimizer: `test_chromosome.py` (3)<br>lv-optimizer: `test_genetic_algorithm.py` (12) | unidade | 123/123 aprovados |
| O03 | Mesma semente e parâmetros dão a mesma rota | lv-optimizer: `test_genetic_algorithm.py::test_same_seed_gives_same_result`<br>lv-optimizer: `test_optimize_api.py::test_same_seed_gives_same_sequence`<br>lv-api: `RouteProcessorTest#sendsAFixedSeedSoTheSameStopsGiveTheSameRoute` | unidade, integração | 3/3 aprovados |
| O04 | 30 pontos dentro do orçamento de tempo (KPI-1) | lv-optimizer: `test_optimize_api.py::test_thirty_point_instance_stays_within_the_time_budget`<br>lv-optimizer: `test_genetic_algorithm.py::test_time_limit_stops_evolution_early` | integração, unidade | 2/2 aprovados |
| O05 | Contrato da API do otimizador versionado | lv-optimizer: `test_contract.py` | integração | 1/1 aprovados |
| O06 | Experimento: regra de Δ ≥ 5% em 4 de 5 instâncias e linha de base aleatória | lv-optimizer: `test_experiment.py` (12) | unidade | 12/12 aprovados |
| O07 | Progresso do AG transmitido durante o cálculo | lv-optimizer: `test_optimize_stream.py` (7) | integração | 7/7 aprovados |

## Requisitos sem teste automatizado

| Requisito | Situação |
|---|---|
| Valor de referência do MEET calculado fora do código (planilha) | A4: falta a planilha de vocês; o teste já lê o arquivo quando ele existir. |
| Tempo de ponta a ponta envio → rota pronta → ancoragem confirmada (F08) | Medido pelo scripts/e2e_timing.py, fora da suíte automática. |
| Carga com 10 usuários (KPI-5) | Script de carga do §5.4, fora da suíte automática. |
| Ancoragem simultânea contra o Besu real | BesuLedgerBesuIT só roda com LV_BESU_IT_RPC_URL; fora da suíte padrão. |
| Fluxo no navegador de ponta a ponta | Não há teste automatizado de navegador (Playwright/Cypress); a interface é testada por componente. |

## Referências sem resultado na última execução

- A08: lv-api BesuLedgerConcurrencyTest
