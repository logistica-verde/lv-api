#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="data/osrm"
IMAGE="ghcr.io/project-osrm/osrm-backend:latest"
PBF_URL="${PBF_URL:-https://download.geofabrik.de/south-america/brazil/sudeste-latest.osm.pbf}"
BBOX="${BBOX:--46.95,-24.10,-46.25,-23.25}"

mkdir -p "$DATA_DIR"

if [ ! -f "$DATA_DIR/region.osm.pbf" ]; then
  if ! command -v osmium > /dev/null; then
    echo "Falta o osmium-tool. No Ubuntu: sudo apt install -y osmium-tool" >&2
    exit 1
  fi
  if [ ! -f "$DATA_DIR/source.osm.pbf" ]; then
    echo "Baixando o mapa: $PBF_URL"
    curl -fL "$PBF_URL" -o "$DATA_DIR/source.osm.pbf"
  fi
  echo "Recortando a regiao $BBOX"
  osmium extract -b "$BBOX" "$DATA_DIR/source.osm.pbf" -o "$DATA_DIR/region.osm.pbf" --overwrite
  rm -f "$DATA_DIR/source.osm.pbf"
fi

docker run --rm -v "$PWD/$DATA_DIR:/data" "$IMAGE" osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -v "$PWD/$DATA_DIR:/data" "$IMAGE" osrm-partition /data/region.osrm
docker run --rm -v "$PWD/$DATA_DIR:/data" "$IMAGE" osrm-customize /data/region.osrm

echo "Pronto: $DATA_DIR/region.osrm"
