"""Compares the code of a deployed AuditRegistry with the bytecode compiled from this commit.

The compiled runtime has the immutable `owner` left blank in two places (immutableReferences); the
code on the chain has the deploying account there. The check fills those places with the owner and
requires the result to be identical, byte for byte, to the code read with eth_getCode.

    python docs/contrato/verificar_bytecode.py --code codigo.json --owner 0x... [--compiled docs/contrato/solc-0.8.28-saida.json]

`codigo.json` is the eth_getCode answer ({"result": "0x..."}) or a file with the hex code alone.
"""

import argparse
import json
from pathlib import Path


def expected_runtime(compiled: dict, owner: str) -> bytes:
    runtime = bytearray.fromhex(compiled["runtimeBytecode"])
    owner_word = bytes.fromhex(owner.lower().removeprefix("0x").rjust(64, "0"))
    for references in compiled["immutableReferences"].values():
        for reference in references:
            runtime[reference["start"]:reference["start"] + reference["length"]] = owner_word
    return bytes(runtime)


def deployed_code(path: Path) -> bytes:
    text = path.read_text(encoding="utf-8").strip()
    if text.startswith("{"):
        text = json.loads(text)["result"]
    return bytes.fromhex(text.removeprefix("0x"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--code", type=Path, required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--compiled", type=Path, default=Path(__file__).with_name("solc-0.8.28-saida.json"))
    args = parser.parse_args()

    compiled = json.loads(args.compiled.read_text(encoding="utf-8"))
    expected = expected_runtime(compiled, args.owner)
    deployed = deployed_code(args.code)
    print(f"compilado: {len(expected)} bytes | implantado: {len(deployed)} bytes")
    if deployed == expected:
        print("IGUAL: o contrato implantado é o bytecode deste commit, com esse owner.")
        return
    first = next((i for i, (a, b) in enumerate(zip(expected, deployed)) if a != b), min(len(expected), len(deployed)))
    print(f"DIFERENTE a partir do byte {first}.")
    raise SystemExit(1)


if __name__ == "__main__":
    main()
