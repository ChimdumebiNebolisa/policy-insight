#!/usr/bin/env bash
set -euo pipefail

HOST=""
SSH_KEY=""
USER_NAME="opc"
IMAGE_REF="ghcr.io/chimdumebinebolisa/policy-insight:latest"
CONTAINER_NAME="policyinsight-web"
ENV_FILE_PATH="/opt/policyinsight/web.env"
APP_PORT="8080"
DRY_RUN="false"
VERIFY_ROUTES="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST="$2"
      shift 2
      ;;
    --ssh-key)
      SSH_KEY="$2"
      shift 2
      ;;
    --user)
      USER_NAME="$2"
      shift 2
      ;;
    --image-ref)
      IMAGE_REF="$2"
      shift 2
      ;;
    --container-name)
      CONTAINER_NAME="$2"
      shift 2
      ;;
    --env-file)
      ENV_FILE_PATH="$2"
      shift 2
      ;;
    --app-port)
      APP_PORT="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    --verify-routes)
      VERIFY_ROUTES="true"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$HOST" || -z "$SSH_KEY" ]]; then
  echo "Usage: $0 --host <host> --ssh-key <path> [--user opc] [--image-ref <ref>] [--container-name <name>] [--env-file <path>] [--app-port <port>] [--dry-run] [--verify-routes]" >&2
  exit 2
fi

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH key not found: $SSH_KEY" >&2
  exit 1
fi

if ! command -v ssh >/dev/null 2>&1; then
  echo "ssh client is required but not found on PATH" >&2
  exit 1
fi

REMOTE="${USER_NAME}@${HOST}"
read -r -d '' REMOTE_CMD <<EOF || true
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is not installed on target host" >&2
  exit 1
fi

sudo mkdir -p /opt/policyinsight

if [ ! -f "$ENV_FILE_PATH" ]; then
  echo "missing env file: $ENV_FILE_PATH" >&2
  exit 1
fi

sudo docker pull "$IMAGE_REF"
sudo docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
sudo docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --env-file "$ENV_FILE_PATH" \
  -p "$APP_PORT:8080" \
  "$IMAGE_REF"

sudo docker ps --filter name="$CONTAINER_NAME" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
EOF

echo "Deploy target: $REMOTE"
echo "Image: $IMAGE_REF"
echo "Container: $CONTAINER_NAME"
echo "Env file: $ENV_FILE_PATH"
echo "Port mapping: $APP_PORT -> 8080"

if [[ "$DRY_RUN" == "true" ]]; then
  echo "Dry run enabled; remote command not executed."
  echo "$REMOTE_CMD"
  exit 0
fi

ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$REMOTE" "$REMOTE_CMD"

if [[ "$VERIFY_ROUTES" == "true" ]]; then
  if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required for --verify-routes but was not found on PATH" >&2
    exit 1
  fi

  BASE_URL="http://${HOST}:${APP_PORT}"
  for path in /health /readiness /sample-report /sample-pdf; do
    url="${BASE_URL}${path}"
    status="$(curl -s -o /dev/null -w '%{http_code}' "$url")"
    if [[ "$status" != "200" ]]; then
      echo "Route verification failed for ${url}: HTTP ${status}" >&2
      exit 1
    fi
    echo "Verified ${url} -> 200"
  done

  echo "Post-deploy route verification passed."
fi
