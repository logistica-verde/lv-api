#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
DATA_DIR="data/besu"
IMAGE="hyperledger/besu:latest"
CHAIN_ID="${BESU_CHAIN_ID:-1337}"

if [ -f "$DATA_DIR/genesis.json" ] && [ -f "$DATA_DIR/key" ]; then
  echo "A rede ja foi gerada em $DATA_DIR"
  exit 0
fi

rm -rf "$DATA_DIR"
mkdir -p "$DATA_DIR"

cat > "$DATA_DIR/config.json" <<EOF
{
  "genesis": {
    "config": {
      "chainId": $CHAIN_ID,
      "berlinBlock": 0,
      "londonBlock": 0,
      "shanghaiTime": 0,
      "zeroBaseFee": true,
      "contractSizeLimit": 2147483647,
      "qbft": {
        "blockperiodseconds": 2,
        "epochlength": 30000,
        "requesttimeoutseconds": 4
      }
    },
    "nonce": "0x0",
    "timestamp": "0x0",
    "gasLimit": "0x1fffffffffffff",
    "difficulty": "0x1",
    "mixHash": "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
    "coinbase": "0x0000000000000000000000000000000000000000",
    "alloc": {}
  },
  "blockchain": {
    "nodes": {
      "generate": true,
      "count": 1
    }
  }
}
EOF

docker run --rm --user "$(id -u):$(id -g)" -v "$PWD/$DATA_DIR:/work" "$IMAGE" \
  operator generate-blockchain-config \
  --config-file=/work/config.json \
  --to=/work/network \
  --private-key-file-name=key

cp "$DATA_DIR/network/genesis.json" "$DATA_DIR/genesis.json"
cp "$(find "$DATA_DIR/network/keys" -name key | head -n 1)" "$DATA_DIR/key"
chmod 644 "$DATA_DIR/genesis.json" "$DATA_DIR/key"

echo "Pronto: rede Besu QBFT com chain ID $CHAIN_ID em $DATA_DIR"
