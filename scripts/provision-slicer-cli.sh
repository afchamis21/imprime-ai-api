#!/usr/bin/env bash
set -euo pipefail

# Provisiona e verifica o fatiador headless (Bambu Studio / OrcaSlicer ou Docker) para validação server-side.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "[imprime-slicer] Verificando binários headless instalados..."
if command -v bambu-studio >/dev/null 2>&1; then
  echo "[imprime-slicer] bambu-studio encontrado em: $(command -v bambu-studio)"
  bambu-studio --help | head -n 3 || true
  exit 0
fi

if command -v orca-slicer >/dev/null 2>&1; then
  echo "[imprime-slicer] orca-slicer encontrado em: $(command -v orca-slicer)"
  echo "[imprime-slicer] Defina SLICING_CLI_PROVIDER=ORCA_SLICER e SLICING_CLI_BINARY=$(command -v orca-slicer)"
  exit 0
fi

echo "[imprime-slicer] Nenhum binário local encontrado no PATH."
if command -v docker >/dev/null 2>&1; then
  echo "[imprime-slicer] Construindo imagem isolada imprimeai/slicer-cli:latest..."
  docker build -f "${PROJECT_ROOT}/docker/slicer/Dockerfile" -t imprimeai/slicer-cli:latest "${PROJECT_ROOT}"
  echo "[imprime-slicer] Concluído. Defina SLICING_CLI_PROVIDER=DOCKER na VM."
  exit 0
fi

echo "[imprime-slicer] ERRO: nem bambu-studio/orca-slicer nem docker foram encontrados." >&2
echo "[imprime-slicer] Instale OrcaSlicer/Bambu Studio ou Docker para habilitar validação CLI headless." >&2
exit 1
