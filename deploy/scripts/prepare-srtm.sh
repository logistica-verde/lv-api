#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="data/srtm30m"
TILES="${TILES:-S24W047}"
BASE_URL="${SRTM_BASE_URL:-https://step.esa.int/auxdata/dem/SRTMGL1}"

mkdir -p "$DATA_DIR"

for tile in $TILES; do
  if [ ! -f "$DATA_DIR/$tile.hgt" ]; then
    echo "Baixando o bloco $tile"
    curl -fL "$BASE_URL/$tile.SRTMGL1.hgt.zip" -o "$DATA_DIR/$tile.zip"
    unzip -o "$DATA_DIR/$tile.zip" -d "$DATA_DIR"
    rm -f "$DATA_DIR/$tile.zip"
  fi
done

echo "Pronto: $(ls "$DATA_DIR" | wc -l) arquivo(s) em $DATA_DIR"
