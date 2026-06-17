#!/usr/bin/env sh
#
# Container start entrypoint: runtime-decrypt the encrypted application secrets,
# then exec the Spring Boot application pointing at the decrypted file.
#
# Design goals (SECRET-ENC-02b):
#   - Fail fast if the age key or encrypted source is missing.
#   - SOPS/age-decrypt to a runtime-only path, never the working tree.
#   - Never print secret values or decrypted content; only non-secret diagnostics.
#
# Configuration (env, all with safe defaults):
#   SOPS_AGE_KEY       - required: the age private key used by SOPS to decrypt.
#   SECRETS_ENC_FILE   - encrypted source YAML (default: /app/application-secrets.enc.yaml).
#   SECRETS_OUT_FILE   - decrypted runtime-only target (default: /tmp/application-secrets.yaml).
#   APP_JAR            - application jar to run (default: /app/app.jar).

set -eu

SECRETS_ENC_FILE="${SECRETS_ENC_FILE:-/app/application-secrets.enc.yaml}"
SECRETS_OUT_FILE="${SECRETS_OUT_FILE:-/tmp/application-secrets.yaml}"
APP_JAR="${APP_JAR:-/app/app.jar}"

fail() {
    # Emit a non-secret diagnostic to stderr and stop the container.
    echo "docker-entrypoint: $1" >&2
    exit 1
}

log() {
    # Emit a non-secret positive diagnostic to stderr. Callers must pass only
    # safe booleans, key names, or paths -- never secret values or decrypted
    # content.
    echo "docker-entrypoint: $1" >&2
}

log "bootstrap started=true"

# --- Preconditions (fail fast, no secret output) ---------------------------

command -v sops >/dev/null 2>&1 || fail "sops binary not found on PATH"

# SOPS_AGE_KEY must be present but is never echoed.
if [ -z "${SOPS_AGE_KEY:-}" ]; then
    fail "SOPS_AGE_KEY is not set; cannot decrypt application secrets"
fi
export SOPS_AGE_KEY
log "SOPS_AGE_KEY present=true"

[ -f "$SECRETS_ENC_FILE" ] || fail "encrypted secrets file not found: $SECRETS_ENC_FILE"
log "encrypted config file present=true path=$SECRETS_ENC_FILE"

# --- Decrypt to a runtime-only path ----------------------------------------

# Restrict permissions on anything we create during decryption.
umask 077

# Decrypt directly to the output file. Suppress sops stdout/stderr so no
# decrypted content or secret material can leak into container logs; on failure
# we surface only our own non-secret diagnostic.
if ! sops --decrypt --output "$SECRETS_OUT_FILE" "$SECRETS_ENC_FILE" >/dev/null 2>&1; then
    rm -f "$SECRETS_OUT_FILE"
    fail "failed to decrypt $SECRETS_ENC_FILE (check SOPS_AGE_KEY)"
fi
log "sops decrypt success=true"

[ -s "$SECRETS_OUT_FILE" ] || fail "decrypted secrets file is empty: $SECRETS_OUT_FILE"
log "decrypted runtime config file created=true path=$SECRETS_OUT_FILE"

# --- Launch the application ------------------------------------------------

[ -f "$APP_JAR" ] || fail "application jar not found: $APP_JAR"

# Point Spring at the decrypted file via an additional config import.
# `exec` so the JVM becomes PID 1 and receives container signals directly.
log "Spring config import attempted=true path=$SECRETS_OUT_FILE"
exec java \
    "-Dspring.config.import=optional:file:${SECRETS_OUT_FILE}" \
    -jar "$APP_JAR" "$@"
