"""Teste de carga da API do Lastro (§5.4 do TCC).

Repete a carga usada em 2026-10-19: usuários virtuais simultâneos, um por thread, com
intervalo fixo entre requisições. A cada dez iterações, o usuário pede o cálculo de uma rota
com quatro pontos sorteados; nas outras, consulta uma rota pronta, a verificação de
integridade dela ou a comparação entre os dois critérios. O tempo do algoritmo genético
fica fora da medição, porque o cálculo é assíncrono (resposta 202).

Só usa a biblioteca padrão. A senha vem de LV_E2E_PASSWORD ou é pedida no terminal;
nunca vai na linha de comando.

    python scripts/load_test.py --base-url https://servidor --email admin@exemplo.com \\
        --output carga.json
"""

import argparse
import getpass
import json
import os
import random
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request

ENDPOINTS = ("calculate", "route", "integrity", "comparison")


class Api:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.token = ""

    def call(self, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None, float]:
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base_url + path, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        if self.token:
            request.add_header("Authorization", "Bearer " + self.token)
        started = time.perf_counter()
        status, payload = 0, None
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                status = response.status
                raw = response.read()
                payload = json.loads(raw) if raw else None
        except urllib.error.HTTPError as failure:
            status = failure.code
        except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
            status = 0
        return status, payload, (time.perf_counter() - started) * 1000

    def login(self, email: str, password: str) -> None:
        status, body, _ = self.call("POST", "/api/auth/login", {"email": email, "senha": password})
        if status != 200 or not isinstance(body, dict):
            sys.exit(f"Login falhou (HTTP {status}).")
        self.token = body["token"]


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(fraction * (len(ordered) - 1))))
    return ordered[index]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True, help="ex.: http://localhost ou https://servidor")
    parser.add_argument("--email", required=True, help="conta com perfil Administrador")
    parser.add_argument("--users", type=int, default=10)
    parser.add_argument("--duration", type=float, default=120.0, help="segundos de carga")
    parser.add_argument("--think", type=float, default=0.5, help="intervalo entre requisições de um usuário")
    parser.add_argument("--post-every", type=int, default=10, help="uma requisição de cálculo a cada N iterações")
    parser.add_argument("--origin", default="-23.5614,-46.6559", help="latitude,longitude de saída das rotas")
    parser.add_argument("--output", default="carga.json")
    args = parser.parse_args()

    password = os.environ.get("LV_E2E_PASSWORD") or getpass.getpass("Senha: ")
    api = Api(args.base_url)
    api.login(args.email, password)

    _, vehicles, _ = api.call("GET", "/api/vehicles")
    _, points, _ = api.call("GET", "/api/delivery-points")
    vehicle_id = next(v["id"] for v in vehicles or [] if v.get("ativo"))
    point_ids = [p["id"] for p in points or [] if p.get("ativo")]
    if len(point_ids) < 5:
        sys.exit("É preciso ao menos cinco pontos de entrega ativos.")
    latitude, longitude = (float(part) for part in args.origin.split(","))
    origin = {"latitude": latitude, "longitude": longitude}

    reference = {}
    for criterion in ("EMISSION", "DISTANCE"):
        status, accepted, _ = api.call("POST", "/api/routes/calculate", {
            "vehicleId": vehicle_id, "deliveryPointIds": point_ids[:5], "criterion": criterion, "origin": origin,
        })
        if status != 202 or not isinstance(accepted, dict):
            sys.exit(f"O par de referência não foi aceito (HTTP {status}).")
        reference[criterion] = accepted["id"]
    for operation_id in reference.values():
        while True:
            _, route, _ = api.call("GET", f"/api/routes/{operation_id}")
            if isinstance(route, dict) and route.get("status") in ("COMPLETED", "FAILED"):
                break
            time.sleep(0.4)
    time.sleep(3)
    emission_id, distance_id = reference["EMISSION"], reference["DISTANCE"]
    print(f"Par de referência: operações {emission_id} e {distance_id}", flush=True)

    samples = {name: [] for name in ENDPOINTS}
    statuses = {name: [] for name in ENDPOINTS}
    lock = threading.Lock()
    deadline = time.time() + args.duration

    def record(name: str, status: int, elapsed: float) -> None:
        with lock:
            samples[name].append(elapsed)
            statuses[name].append(status)

    def user(seed: int) -> None:
        rng = random.Random(seed)
        iteration = 0
        while time.time() < deadline:
            iteration += 1
            if iteration % args.post_every == 0:
                status, _, elapsed = api.call("POST", "/api/routes/calculate", {
                    "vehicleId": vehicle_id, "deliveryPointIds": rng.sample(point_ids, 4),
                    "criterion": rng.choice(["EMISSION", "DISTANCE"]), "origin": origin,
                })
                record("calculate", status, elapsed)
            else:
                kind = iteration % 3
                if kind == 0:
                    status, _, elapsed = api.call("GET", f"/api/routes/{emission_id}")
                    record("route", status, elapsed)
                elif kind == 1:
                    status, _, elapsed = api.call("GET", f"/api/routes/{emission_id}/integrity")
                    record("integrity", status, elapsed)
                else:
                    status, _, elapsed = api.call(
                        "GET", f"/api/routes/comparison?emissionId={emission_id}&distanceId={distance_id}")
                    record("comparison", status, elapsed)
            time.sleep(args.think)

    threads = [threading.Thread(target=user, args=(seed,)) for seed in range(args.users)]
    started = time.time()
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    total = time.time() - started

    result = {
        "base_url": args.base_url, "users": args.users, "duration_s": round(total, 1), "think_s": args.think,
        "post_every": args.post_every, "reference_operations": [emission_id, distance_id], "endpoints": {},
    }
    print(f"\n{args.users} usuários, {total:.1f} s, intervalo de {args.think} s")
    print(f"{'endpoint':<11} {'requisições':>11} {'2xx':>6} {'falhas':>6} {'p50 ms':>8} {'p95 ms':>8} {'média':>8} {'máx':>8}")
    for name in ENDPOINTS:
        values = samples[name]
        if not values:
            continue
        ok = sum(1 for status in statuses[name] if 200 <= status < 300)
        summary = {
            "requests": len(values), "ok": ok, "failures": len(values) - ok,
            "p50": round(percentile(values, 0.5), 1), "p95": round(percentile(values, 0.95), 1),
            "mean": round(statistics.fmean(values), 1), "max": round(max(values), 1),
        }
        result["endpoints"][name] = summary
        print(f"{name:<11} {summary['requests']:>11} {ok:>6} {summary['failures']:>6} {summary['p50']:>8} "
              f"{summary['p95']:>8} {summary['mean']:>8} {summary['max']:>8}")
    with open(args.output, "w", encoding="utf-8") as file:
        json.dump(result, file, indent=2, ensure_ascii=False)
    print(f"JSON: {args.output}")


if __name__ == "__main__":
    main()
