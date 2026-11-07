"""Gera docs/matriz-requisitos.md a partir dos relatórios JUnit das três suítes.

Cada requisito aponta para os testes que o cobrem; o resultado vem da última execução, lida dos
relatórios. Rode as suítes na main antes, gerando os relatórios nos caminhos padrão:

    lv-api:        ./mvnw clean test                        (target/surefire-reports/TEST-*.xml)
    lv-optimizer:  python -m pytest --junitxml=reports/junit.xml
    lv-frontend:   npx vitest run --reporter=default --reporter=junit --outputFile.junit=reports/junit.xml

Uso (a partir da pasta que contém os três repositórios):
    python lv-api/scripts/matriz_requisitos.py --root . --output lv-api/docs/matriz-requisitos.md
"""

import argparse
import glob
import os
import subprocess
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone

UNIT, INTEGRATION, E2E, UI = "unidade", "integração", "ponta a ponta", "interface"


@dataclass(frozen=True)
class Ref:
    repo: str
    selector: str
    kind: str


def api(selector: str, kind: str = UNIT) -> Ref:
    return Ref("lv-api", selector, kind)


def opt(selector: str, kind: str = UNIT) -> Ref:
    return Ref("lv-optimizer", selector, kind)


def front(selector: str) -> Ref:
    return Ref("lv-frontend", selector, UI)


REQUIREMENTS: list[tuple[str, str, str, list[Ref]]] = [
    ("Casos de uso (Quadro 5)", "UC01", "Operador cadastra pontos de entrega", [
        api("DeliveryPointControllerTest"), api("SecurityRulesTest#operatorsCanRegisterDeliveryPoints", INTEGRATION),
        api("DeliveryPointRepositoryTest", INTEGRATION), front("src/pages/PlanningPage/AddPoint/AddPointDialog.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC02", "Operador calcula a rota de menor emissão", [
        api("RouteControllerTest"), api("RouteServiceTest"), api("RouteProcessorTest", INTEGRATION),
        api("RouteFlowIntegrationTest#calculatesRouteAsynchronouslyAndExposesResultByPolling", INTEGRATION),
        opt("test_optimize_api.py::test_emission_criterion_avoids_slow_uphill_legs_that_distance_ignores", INTEGRATION),
        opt("test_fitness.py"), front("src/pages/PlanningPage/PlanningPage.test.tsx"),
        front("src/pages/PlanningPage/Calculation/CalculatingDialog.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC03", "Operador calcula a rota de menor distância", [
        api("RouteControllerTest"), api("RouteProcessorTest", INTEGRATION),
        opt("test_optimize_api.py::test_distance_criterion_finds_the_shortest_open_route_on_a_small_instance", INTEGRATION),
        opt("test_fitness.py::test_distance_fitness_depends_on_visit_order"),
        front("src/pages/PlanningPage/PlanningPage.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC04", "Operador compara as emissões das duas rotas", [
        api("RouteComparisonTest"), api("RouteControllerTest#comparisonReturnsBothRoutesWithTheirExecutionTimes"),
        front("src/pages/ComparisonPage/ComparisonPage.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC05", "Operador vê a rota no mapa interativo", [
        api("RouteControllerTest#returnsTheStreetGeometryOfEachSegmentAsLatitudeAndLongitude"),
        api("RoadNetworkSegmentSourceTest#carriesTheStreetGeometryOfEachRoute"),
        front("src/components/Map/MapView.test.tsx"), front("src/components/Map/RoutePolyline.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC06", "Auditor consulta o registro na blockchain pelo ID da operação", [
        api("IntegrityControllerTest"), api("SecurityRulesTest#keepsTheIntegrityReportToAuditorsAndAdministrators", INTEGRATION),
        front("src/pages/VerificationPage/VerificationPage.test.tsx"), front("src/pages/VerificationPage/VerdictCard.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC07", "Auditor verifica se os dados foram alterados depois do registro", [
        api("IntegrityVerifierTest"), api("HashGeneratorTest"),
        api("RouteFlowIntegrationTest#detectsEveryKindOfAlterationMadeDirectlyInTheDatabase", INTEGRATION),
        api("RouteFlowIntegrationTest#anchorsTheCompletedRouteAndReportsItAsValid", INTEGRATION),
    ]),
    ("Casos de uso (Quadro 5)", "UC08", "Administrador cadastra veículos com a massa", [
        api("VehicleControllerTest"), api("VehicleRepositoryTest", INTEGRATION),
        api("SecurityRulesTest#onlyAdministratorsRegisterVehicles", INTEGRATION),
        front("src/pages/AdminPage/AdminPage.test.tsx"),
    ]),
    ("Casos de uso (Quadro 5)", "UC09", "Administrador gerencia usuários e perfis", [
        api("UserAdminServiceTest"), api("UserAdminControllerTest"), api("UserAdminIntegrationTest", INTEGRATION),
        api("SecurityRulesTest#administratorsListAndChangeUsersThroughTheApi", INTEGRATION),
    ]),
    ("Casos de uso (Quadro 5)", "UC10", "Administrador configura os parâmetros do AG", [
        api("GaSettingsServiceTest", INTEGRATION), api("GaSettingsControllerTest"),
        api("RouteProcessorTest#sendsTheConfiguredGeneticAlgorithmParametersToTheOptimizer"),
        opt("test_optimize_ga_params.py", INTEGRATION),
    ]),
    ("Segurança", "S01", "Sem token, qualquer endpoint da API responde 401", [
        api("SecurityRulesTest#everyApiEndpointRequiresAToken", INTEGRATION),
        api("SecurityRulesTest#routeCalculationRequiresAToken", INTEGRATION),
    ]),
    ("Segurança", "S02", "Token expirado, adulterado, de outro segredo ou sem perfil responde 401", [
        api("SecurityRulesTest#rejectsAnExpiredToken", INTEGRATION), api("SecurityRulesTest#rejectsATamperedToken", INTEGRATION),
        api("SecurityRulesTest#rejectsATokenSignedWithAnotherSecret", INTEGRATION),
        api("SecurityRulesTest#rejectsASignedTokenThatHasNoValidProfile", INTEGRATION),
    ]),
    ("Segurança", "S03", "Perfil sem permissão responde 403 (Operador na administração, Auditor no cálculo)", [
        api("SecurityRulesTest#auditorsCannotRegisterDeliveryPointsOrCalculateRoutes", INTEGRATION),
        api("SecurityRulesTest#keepsUserAndSettingsAdministrationToAdministrators", INTEGRATION),
        api("SecurityRulesTest#onlyAdministratorsChangeVehicleStatus", INTEGRATION),
        api("ExecutionControllerTest#anAuditorCannotCreateOrChangeExecutions"),
        api("BaseControllerTest#anOperatorCannotRegisterOrChangeABase"),
    ]),
    ("Segurança", "S04", "Perfil correto acessa (fluxo do login à chamada protegida)", [
        api("SecurityRulesTest#allowsTheFullFlowFromLoginToAProtectedCall", INTEGRATION),
        api("SecurityRulesTest#operatorsAndAdministratorsReachTheRouteCalculation", INTEGRATION),
        api("AuthServiceTest"), api("AuthControllerTest"), front("src/pages/LoginPage/LoginPage.test.tsx"),
        front("src/router.test.tsx"),
    ]),
    ("Segurança", "S05", "Senha errada não revela se o e-mail existe", [
        api("SecurityRulesTest#refusesTheWrongPasswordAndHidesWhetherTheEmailExists", INTEGRATION),
        api("AuthServiceTest#rejectsAnUnknownEmailWithTheSameErrorAsAWrongPassword"),
    ]),
    ("Segurança", "S06", "Só origens permitidas (CORS); token só no cabeçalho", [api("CorsTest", INTEGRATION)]),
    ("Ancoragem", "A01", "Rota concluída tem o hash ancorado (ANCHORED) com txHash e bloco", [
        api("BlockchainServiceTest#anchorsTheOperationHashAndStoresTheReceipt"), api("BesuLedgerTest"),
    ]),
    ("Ancoragem", "A02", "Hash já no contrato após timeout é recuperado sem reenviar (RECOVERED)", [
        api("BlockchainServiceTest#recoversTheRecordWithoutResendingWhenTheHashIsAlreadyOnTheLedger"),
    ]),
    ("Ancoragem", "A03", "Falha de envio fica FAILED e é reprocessada no mesmo registro", [
        api("BlockchainServiceTest#recordsFailureWhenTheLedgerIsUnavailable"),
        api("BlockchainServiceTest#logsEveryAttemptAndRetriesAFailedAnchorOnTheSameRecord"),
        api("BlockchainServiceTest#keepsTheRecordFailedWhenTheRetryFailsToo"),
    ]),
    ("Ancoragem", "A04", "Hash diferente no contrato vira DIVERGENT, sem reenvio nem reprocessamento", [
        api("BlockchainServiceTest#flagsADivergenceInsteadOfResendingWhenTheLedgerHoldsAnotherHash"),
        api("BlockchainServiceTest#tellsWhenTheLedgerHoldsTheHashSentEarlierButTheOperationChangedSince"),
        api("BlockchainServiceTest#neverRetriesOrReanchorsADivergentOperation"),
    ]),
    ("Ancoragem", "A05", "Depois do limite de tentativas o registro vira EXHAUSTED", [
        api("BlockchainServiceTest#givesUpAfterTheMaximumNumberOfAttemptsAndStopsRetrying"),
        api("IntegrityVerifierTest#reportsAFailedAnchorOnceTheAttemptsRunOut"),
    ]),
    ("Ancoragem", "A06", "Cada registro guarda o contrato e a versão da serialização", [
        api("BlockchainServiceTest#recordsTheContractAndTheSerializationVersionOfEveryRecord"),
        api("HashGeneratorTest#producesTheKnownSha256ForTheCanonicalJson"),
        api("HashGeneratorTest#producesTheSameHashWhateverTheTimeZoneOfTheServer"),
    ]),
    ("Ancoragem", "A07", "Só a conta da API grava no contrato; segunda gravação é recusada", [
        api("AuditRegistryArtifactTest#onlyTheAccountThatDeployedTheContractCanAnchor"),
    ]),
    ("Ancoragem", "A08", "Ancoragens simultâneas não reaproveitam o nonce", [
        api("BesuLedgerConcurrencyTest"),
    ]),
    ("Verificação de integridade", "V01", "Dados iguais ao contrato: VALID", [
        api("IntegrityVerifierTest#reportsValidWhenTheDatabaseMatchesTheLedger"),
        front("src/pages/VerificationPage/VerdictCard.test.tsx"),
    ]),
    ("Verificação de integridade", "V02", "Dados alterados no banco: TAMPERED, mesmo sem o registro local", [
        api("IntegrityVerifierTest#reportsTamperedWhenStoredDataChangesAfterAnchoring"),
        api("IntegrityVerifierTest#stillDetectsTamperingWhenTheLocalRecordWasAlsoDeleted"),
        api("RouteFlowIntegrationTest#detectsEveryKindOfAlterationMadeDirectlyInTheDatabase", INTEGRATION),
    ]),
    ("Verificação de integridade", "V03", "Sem hash no contrato: NOT_ANCHORED (ancoragem pendente)", [
        api("IntegrityVerifierTest#reportsNotAnchoredWhenTheLedgerHasNoHash"),
        api("OperationHistoryServiceTest#reportsTheIntegrityOfEachOperationAndSkipsTheLedgerForUnanchoredOnes", INTEGRATION),
    ]),
    ("Verificação de integridade", "V04", "Besu indisponível: 503 com mensagem clara", [
        api("IntegrityControllerTest#mapsLedgerFailureToServiceUnavailable"),
        api("OperationHistoryControllerTest#mapsAnUnavailableLedgerTo503"),
    ]),
    ("Otimizador", "O01", "Modelo MEET segue as equações A10, A11 e A14 e as tabelas de Hickman et al. (1999)", [
        opt("test_meet_emission_model.py"),
    ]),
    ("Otimizador", "O02", "Operadores do AG geram permutações válidas (OX, inversão, elitismo, torneio)", [
        opt("test_crossover.py"), opt("test_mutation.py"), opt("test_chromosome.py"),
        opt("test_genetic_algorithm.py"),
    ]),
    ("Otimizador", "O03", "Mesma semente e parâmetros dão a mesma rota", [
        opt("test_genetic_algorithm.py::test_same_seed_gives_same_result"),
        opt("test_optimize_api.py::test_same_seed_gives_same_sequence", INTEGRATION),
        api("RouteProcessorTest#sendsAFixedSeedSoTheSameStopsGiveTheSameRoute"),
    ]),
    ("Otimizador", "O04", "30 pontos dentro do orçamento de tempo (KPI-1)", [
        opt("test_optimize_api.py::test_thirty_point_instance_stays_within_the_time_budget", INTEGRATION),
        opt("test_genetic_algorithm.py::test_time_limit_stops_evolution_early"),
    ]),
    ("Otimizador", "O05", "Contrato da API do otimizador versionado", [opt("test_contract.py", INTEGRATION)]),
    ("Otimizador", "O06", "Experimento: regra de Δ ≥ 5% em 4 de 5 instâncias e linha de base aleatória", [
        opt("test_experiment.py"),
    ]),
    ("Otimizador", "O07", "Progresso do AG transmitido durante o cálculo", [
        opt("test_optimize_stream.py", INTEGRATION),
    ]),
]

UNTESTED = [
    ("Valor de referência do MEET calculado fora do código (planilha)", "A4: falta a planilha de vocês; o teste já lê o arquivo quando ele existir."),
    ("Tempo de ponta a ponta envio → rota pronta → ancoragem confirmada (F08)", "Medido pelo scripts/e2e_timing.py, fora da suíte automática."),
    ("Carga com 10 usuários (KPI-5)", "Script de carga do §5.4, fora da suíte automática."),
    ("Ancoragem simultânea contra o Besu real", "BesuLedgerBesuIT só roda com LV_BESU_IT_RPC_URL; fora da suíte padrão."),
    ("Fluxo no navegador de ponta a ponta", "Não há teste automatizado de navegador (Playwright/Cypress); a interface é testada por componente."),
]


def read_cases(pattern: str) -> list[tuple[str, str, str]]:
    cases = []
    for path in glob.glob(pattern, recursive=True):
        for case in ET.parse(path).getroot().iter("testcase"):
            failed = any(child.tag in ("failure", "error") for child in case)
            skipped = any(child.tag == "skipped" for child in case)
            outcome = "falhou" if failed else ("pulado" if skipped else "aprovado")
            cases.append((case.get("classname", ""), case.get("name", ""), outcome))
    return cases


def matches(ref: Ref, classname: str, name: str) -> bool:
    target, _, test = ref.selector.partition("#" if ref.repo == "lv-api" else "::")
    if ref.repo == "lv-api":
        return classname.split(".")[-1] == target and (not test or name.split("(")[0].split("[")[0] == test)
    if ref.repo == "lv-optimizer":
        module = "tests." + target.removesuffix(".py")
        return classname.endswith(module) and (not test or name.split("[")[0] == test)
    return classname.replace("\\", "/").endswith(target) and (not test or test in name)


def commit(root: str, repo: str) -> str:
    return subprocess.run(["git", "-C", os.path.join(root, repo), "rev-parse", "--short", "HEAD"],
                          capture_output=True, text=True).stdout.strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", default="lv-api/docs/matriz-requisitos.md")
    args = parser.parse_args()

    cases = {
        "lv-api": read_cases(os.path.join(args.root, "lv-api/target/surefire-reports/TEST-*.xml")),
        "lv-optimizer": read_cases(os.path.join(args.root, "lv-optimizer/reports/junit.xml")),
        "lv-frontend": read_cases(os.path.join(args.root, "lv-frontend/reports/junit.xml")),
    }
    lines = [
        "# Matriz requisito → caso de teste → resultado",
        "",
        f"Gerada em {datetime.now(timezone.utc):%Y-%m-%d %H:%M} UTC por `scripts/matriz_requisitos.py`, "
        "a partir dos relatórios JUnit da última execução das três suítes.",
        "",
        "| Repositório | Commit | Casos executados | Aprovados | Falharam |",
        "|---|---|---|---|---|",
    ]
    for repo, found in cases.items():
        lines.append(f"| {repo} | `{commit(args.root, repo)}` | {len(found)} | "
                     f"{sum(o == 'aprovado' for *_, o in found)} | {sum(o == 'falhou' for *_, o in found)} |")
    missing = []
    for area in dict.fromkeys(row[0] for row in REQUIREMENTS):
        lines += ["", f"## {area}", "", "| ID | Requisito | Testes | Tipo | Resultado |", "|---|---|---|---|---|"]
        for row_area, key, requirement, refs in REQUIREMENTS:
            if row_area != area:
                continue
            described, kinds, passed, total = [], [], 0, 0
            for ref in refs:
                hits = [o for c, n, o in cases[ref.repo] if matches(ref, c, n)]
                if not hits:
                    missing.append(f"{key}: {ref.repo} {ref.selector}")
                passed += sum(o == "aprovado" for o in hits)
                total += len(hits)
                described.append(f"{ref.repo}: `{ref.selector}`" + (f" ({len(hits)})" if len(hits) > 1 else ""))
                kinds.append(ref.kind)
            if total == 0:
                result = "sem execução"
            elif passed == total:
                result = f"{passed}/{total} aprovados"
            else:
                result = f"**{passed}/{total} aprovados**"
            lines.append(f"| {key} | {requirement} | {'<br>'.join(described)} | "
                         f"{', '.join(dict.fromkeys(kinds))} | {result} |")
    lines += ["", "## Requisitos sem teste automatizado", "", "| Requisito | Situação |", "|---|---|"]
    lines += [f"| {item} | {why} |" for item, why in UNTESTED]
    if missing:
        lines += ["", "## Referências sem resultado na última execução", ""]
        lines += [f"- {item}" for item in missing]
    with open(args.output, "w", encoding="utf-8", newline="\n") as file:
        file.write("\n".join(lines) + "\n")
    print(f"{args.output}: {len(REQUIREMENTS)} requisitos, {len(missing)} referências sem resultado")


if __name__ == "__main__":
    main()
