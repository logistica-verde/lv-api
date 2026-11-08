"""Mede o tempo de ponta a ponta do cálculo de rotas do Lastro, como a interface faz.

Para cada execução, envia o cálculo pelos dois critérios (POST /api/routes/calculate, 202),
consulta GET /api/routes/{id} até COMPLETED ou FAILED e depois GET /api/routes/{id}/integrity
até VALID ou até desistir. Registra, por operação, os tempos envio -> rota pronta e
rota pronta -> ancoragem confirmada, grava um CSV e imprime p50, p95, máximo e falhas.

Só usa a biblioteca padrão. A senha vem de LV_E2E_PASSWORD ou é pedida no terminal;
nunca é passada na linha de comando nem gravada no CSV.

Exemplo:
    LV_E2E_PASSWORD=... python scripts/e2e_timing.py --base-url http://localhost \\
        --email admin@exemplo.com -n 20 --concurrency 1 --output e2e-local.csv
"""

import argparse
import csv
import getpass
import json
import os
import random
import ssl
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, dataclass

CRITERIA = ("EMISSION", "DISTANCE")


@dataclass
class Measurement:
    execution: int
    criterion: str
    operation_id: int | None
    points: int
    route_status: str
    integrity_status: str
    submit_to_ready_s: float | None
    ready_to_anchored_s: float | None
    error: str


class Api:
    def __init__(self, base_url: str, insecure: bool):
        self.base_url = base_url.rstrip("/")
        self.token = ""
        self.context = ssl._create_unverified_context() if insecure else None

    def call(self, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None]:
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        if self.token:
            request.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(request, timeout=60, context=self.context) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as failure:
            raw = failure.read()
            try:
                return failure.code, json.loads(raw) if raw else None
            except json.JSONDecodeError:
                return failure.code, None

    def login(self, email: str, password: str) -> None:
        status, body = self.call("POST", "/api/auth/login", {"email": email, "senha": password})
        if status != 200 or not isinstance(body, dict):
            sys.exit(f"Login falhou (HTTP {status}).")
        self.token = body["token"]


def wait_for_route(api: Api, operation_id: int, timeout_s: float) -> str:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        status, body = api.call("GET", f"/api/routes/{operation_id}")
        if status == 200 and isinstance(body, dict) and body.get("status") in ("COMPLETED", "FAILED"):
            return body["status"]
        time.sleep(0.5)
    return "TIMEOUT"


def wait_for_anchor(api: Api, operation_id: int, timeout_s: float) -> str:
    deadline = time.monotonic() + timeout_s
    last = "NOT_ANCHORED"
    while time.monotonic() < deadline:
        status, body = api.call("GET", f"/api/routes/{operation_id}/integrity")
        if status == 200 and isinstance(body, dict):
            last = body.get("status", last)
            if last in ("VALID", "TAMPERED", "ANCHOR_FAILED"):
                return last
        elif status == 503:
            last = "LEDGER_UNAVAILABLE"
        time.sleep(1.0)
    return last


def run_execution(api: Api, execution: int, vehicle_id: int, point_ids: list[int], args) -> list[Measurement]:
    def one(criterion: str) -> Measurement:
        started = time.monotonic()
        status, body = api.call("POST", "/api/routes/calculate", {
            "vehicleId": vehicle_id, "deliveryPointIds": point_ids, "criterion": criterion,
        })
        if status != 202 or not isinstance(body, dict):
            return Measurement(execution, criterion, None, len(point_ids), "REJECTED", "-", None, None,
                               f"HTTP {status}")
        operation_id = body["id"]
        route_status = wait_for_route(api, operation_id, args.route_timeout)
        ready = time.monotonic()
        if route_status != "COMPLETED":
            return Measurement(execution, criterion, operation_id, len(point_ids), route_status, "-",
                               round(ready - started, 3), None, "")
        integrity = wait_for_anchor(api, operation_id, args.anchor_timeout)
        anchored = time.monotonic()
        return Measurement(execution, criterion, operation_id, len(point_ids), route_status, integrity,
                           round(ready - started, 3),
                           round(anchored - ready, 3) if integrity == "VALID" else None, "")

    with ThreadPoolExecutor(max_workers=len(CRITERIA)) as both:
        return list(both.map(one, CRITERIA))


def seconds(value: float | None, prefix: str = "") -> str:
    return "-" if value is None else f"{prefix}{value:.1f} s"


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round(fraction * (len(ordered) - 1))))
    return ordered[index]


def describe(label: str, values: list[float]) -> str:
    if not values:
        return f"{label}: sem medições"
    return (f"{label}: p50 {percentile(values, 0.5):.2f} s · p95 {percentile(values, 0.95):.2f} s · "
            f"máx {max(values):.2f} s · média {statistics.mean(values):.2f} s (n = {len(values)})")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True, help="ex.: http://localhost ou https://servidor")
    parser.add_argument("--email", required=True, help="conta com perfil Administrador (calcula e verifica)")
    parser.add_argument("-n", "--executions", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=1, help="rotas calculadas ao mesmo tempo")
    parser.add_argument("--min-points", type=int, default=10)
    parser.add_argument("--max-points", type=int, default=30)
    parser.add_argument("--route-timeout", type=float, default=180.0)
    parser.add_argument("--anchor-timeout", type=float, default=120.0)
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--output", default="e2e-timing.csv")
    parser.add_argument("--insecure", action="store_true", help="aceita certificado inválido (só para testes)")
    args = parser.parse_args()

    password = os.environ.get("LV_E2E_PASSWORD") or getpass.getpass("Senha: ")
    api = Api(args.base_url, args.insecure)
    api.login(args.email, password)

    _, vehicles = api.call("GET", "/api/vehicles")
    _, points = api.call("GET", "/api/delivery-points")
    active_vehicles = [v["id"] for v in vehicles or [] if v.get("ativo")]
    active_points = [p["id"] for p in points or [] if p.get("ativo")]
    if not active_vehicles or len(active_points) < 2:
        sys.exit("É preciso ao menos um veículo ativo e dois pontos de entrega ativos.")
    highest = min(args.max_points, len(active_points), 30)
    lowest = min(args.min_points, highest)
    if highest < args.max_points:
        print(f"Aviso: só há {len(active_points)} pontos ativos; as rotas terão de {lowest} a {highest} pontos.")

    rng = random.Random(args.seed)
    plans = [(index + 1, rng.choice(active_vehicles), rng.sample(active_points, rng.randint(lowest, highest)))
             for index in range(args.executions)]

    lock = threading.Lock()
    measurements: list[Measurement] = []
    started = time.monotonic()

    def execute(plan):
        result = run_execution(api, *plan, args)
        with lock:
            measurements.extend(result)
            for item in result:
                print(f"#{item.execution:>3} {item.criterion:<8} {item.points:>2} pontos  "
                      f"{item.route_status:<9} {item.integrity_status:<12} "
                      f"pronta {seconds(item.submit_to_ready_s)}  ancorada {seconds(item.ready_to_anchored_s, '+')} {item.error}")

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        list(pool.map(execute, plans))

    with open(args.output, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(asdict(measurements[0]).keys()))
        writer.writeheader()
        writer.writerows(asdict(item) for item in sorted(measurements, key=lambda m: (m.execution, m.criterion)))

    ready = [m.submit_to_ready_s for m in measurements if m.route_status == "COMPLETED"]
    anchored = [m.ready_to_anchored_s for m in measurements if m.ready_to_anchored_s is not None]
    failures = [m for m in measurements if m.route_status != "COMPLETED" or m.integrity_status != "VALID"]
    print()
    print(f"{len(measurements)} operações em {args.executions} execuções, {args.concurrency} ao mesmo tempo, "
          f"{time.monotonic() - started:.1f} s no total")
    print(describe("Envio -> rota pronta", ready))
    print(describe("Rota pronta -> ancoragem confirmada", anchored))
    print(f"Falhas: {len(failures)}" + "".join(
        f"\n  #{m.execution} {m.criterion}: rota {m.route_status}, integridade {m.integrity_status} {m.error}"
        for m in failures))
    print(f"CSV: {args.output}")


if __name__ == "__main__":
    main()
