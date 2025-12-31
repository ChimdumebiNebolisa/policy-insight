#!/bin/bash
# Bash script to run PolicyInsight with Datadog agent
# Prerequisites:
#   1. Datadog agent running (docker-compose -f docker-compose.datadog.yml up -d datadog-agent)
#   2. DD_API_KEY environment variable set (optional for local testing)
#   3. Java 21 installed
#   4. Maven wrapper (./mvnw) available

set -e

DD_API_KEY=${DD_API_KEY:-}
DD_SERVICE=${DD_SERVICE:-policy-insight}
DD_ENV=${DD_ENV:-local}
DD_VERSION=${DD_VERSION:-dev}
DD_AGENT_HOST=${DD_AGENT_HOST:-localhost}

echo "Starting PolicyInsight with Datadog observability..."
echo "  DD_SERVICE: $DD_SERVICE"
echo "  DD_ENV: $DD_ENV"
echo "  DD_VERSION: $DD_VERSION"
echo "  DD_AGENT_HOST: $DD_AGENT_HOST"

# Download dd-java-agent if not present
DD_AGENT_PATH=".dd-java-agent/dd-java-agent.jar"
if [ ! -f "$DD_AGENT_PATH" ]; then
    echo "Downloading dd-java-agent..."
    mkdir -p .dd-java-agent
    curl -L -o "$DD_AGENT_PATH" "https://dtdg.co/latest-java-tracer" || {
        echo "Failed to download dd-java-agent"
        echo "Please download manually from: https://dtdg.co/latest-java-tracer"
        exit 1
    }
    echo "Downloaded dd-java-agent to $DD_AGENT_PATH"
fi

# Set environment variables
export DATADOG_ENABLED=true
export DD_SERVICE=$DD_SERVICE
export DD_ENV=$DD_ENV
export DD_VERSION=$DD_VERSION
export DD_AGENT_HOST=$DD_AGENT_HOST
export DD_LOGS_INJECTION=true
export DD_TRACE_SAMPLE_RATE=1.0
export DD_PROFILING_ENABLED=false

if [ -n "$DD_API_KEY" ]; then
    export DD_API_KEY=$DD_API_KEY
fi

# Get absolute path to agent
ABSOLUTE_AGENT_PATH=$(cd "$(dirname "$DD_AGENT_PATH")" && pwd)/$(basename "$DD_AGENT_PATH")

# Run Spring Boot with dd-java-agent
echo ""
echo "Starting application with dd-java-agent..."
./mvnw spring-boot:run \
    -Dspring-boot.run.jvmArguments="-javaagent:$ABSOLUTE_AGENT_PATH -Ddd.service=$DD_SERVICE -Ddd.env=$DD_ENV -Ddd.version=$DD_VERSION -Ddd.agent.host=$DD_AGENT_HOST -Ddd.logs.injection=true" \
    -Dspring-boot.run.profiles=datadog

