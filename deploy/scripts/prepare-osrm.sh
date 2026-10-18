#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="data/osrm"
IMAGE="ghcr.io/project-osrm/osrm-backend:latest"
PBF_URL="${PBF_URL:-https://download.bbbike.org/osm/bbbike/SaoPaulo/SaoPaulo.osm.pbf}"

mkdir -p "$DATA_DIR"

if [ ! -f "$DATA_DIR/region.osm.pbf" ]; then
  echo "Baixando o mapa: $PBF_URL"
  curl -fL "$PBF_URL" -o "$DATA_DIR/region.osm.pbf"
fi

docker run --rm -v "$PWD/$DATA_DIR:/data" "$IMAGE" osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -v "$PWD/$DATA_DIR:/data" "$IMAGE" osrm-partition /data/region.osrm
docker run --rm -v "$PWD/$DATA_DIR:/data" "$IMAGE" osrm-customize /data/region.osrm

echo "Pronto: $DATA_DIR/region.osrm"
