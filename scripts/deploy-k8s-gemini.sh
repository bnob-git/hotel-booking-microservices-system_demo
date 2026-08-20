#!/usr/bin/env bash

set -e

echo "========================================"
echo "Deploying Hotel Booking System"
echo "Kubernetes Manifests (Gemini)"
echo "========================================"

# Check that Minikube is running
if ! minikube status >/dev/null 2>&1; then
    echo "Minikube is not running."
    echo "Start it first with:"
    echo "    minikube start"
    exit 1
fi

# Check that .env exists
if [ ! -f .env ]; then
    echo ".env file not found."
    echo "Create it from .env.example first."
    exit 1
fi

# Read a value from .env without executing the file.
get_env_value() {
    grep "^${1}=" .env | head -n 1 | cut -d '=' -f2-
}

POSTGRES_USER=$(get_env_value "POSTGRES_USER")
POSTGRES_PASSWORD=$(get_env_value "POSTGRES_PASSWORD")
JWT_SECRET=$(get_env_value "JWT_SECRET")
JWT_EXPIRATION_MINUTES=$(get_env_value "JWT_EXPIRATION_MINUTES")
AI_ENABLED=$(get_env_value "AI_ENABLED")
AI_SERVICE_TOKEN=$(get_env_value "AI_SERVICE_TOKEN")
GEMINI_API_KEY=$(get_env_value "GEMINI_API_KEY")

# Validate required values
required_vars=(
    POSTGRES_USER
    POSTGRES_PASSWORD
    JWT_SECRET
    JWT_EXPIRATION_MINUTES
    AI_ENABLED
    AI_SERVICE_TOKEN
    GEMINI_API_KEY
)

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "$var is missing or empty in .env."
        exit 1
    fi
done

# Gemini deployment requires AI to be enabled.
if [ "$AI_ENABLED" != "true" ]; then
    echo "AI_ENABLED must be true for the Gemini deployment."
    echo "Set AI_ENABLED=true in .env and run this script again."
    exit 1
fi

echo "Creating/updating PostgreSQL Secret..."

kubectl create secret generic postgres-secret \
    --from-literal=POSTGRES_USER="$POSTGRES_USER" \
    --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    --dry-run=client \
    -o yaml | kubectl apply -f -

echo "Creating/updating application Secret..."

kubectl create secret generic app-secret \
    --from-literal=JWT_SECRET="$JWT_SECRET" \
    --from-literal=AI_SERVICE_TOKEN="$AI_SERVICE_TOKEN" \
    --dry-run=client \
    -o yaml | kubectl apply -f -

echo "Creating/updating Gemini Secret..."

kubectl create secret generic gemini-secret \
    --from-literal=GEMINI_API_KEY="$GEMINI_API_KEY" \
    --dry-run=client \
    -o yaml | kubectl apply -f -

echo "Applying Kubernetes manifests..."

kubectl apply -f k8s/postgres/
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/kafka-ui/
kubectl apply -f k8s/user-service/
kubectl apply -f k8s/booking-service/
kubectl apply -f k8s/audit-service/

echo "Applying Gemini AI Chat Service..."

kubectl apply -f k8s/ai-chat-service/configmap-gemini.yaml
kubectl apply -f k8s/ai-chat-service/deployment-gemini.yaml
kubectl apply -f k8s/ai-chat-service/ai-chat-service.yaml

echo "Applying API Gateway ConfigMap..."

kubectl apply -f k8s/api-gateway/api-gateway-configmap.yaml

echo "Updating API Gateway configuration..."

kubectl patch configmap api-gateway-config \
    --type merge \
    -p "{\"data\":{\"AI_ENABLED\":\"$AI_ENABLED\",\"JWT_EXPIRATION_MINUTES\":\"$JWT_EXPIRATION_MINUTES\"}}"

echo "Applying API Gateway..."

kubectl apply -f k8s/api-gateway/api-gateway-deployment.yaml
kubectl apply -f k8s/api-gateway/api-gateway-service.yaml

kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress/

echo
echo "Kubernetes manifests applied successfully."
echo
echo "Gemini AI is enabled."
echo
echo "Monitor the deployment with:"
echo "    kubectl get pods -w"
echo
echo "When all pods are Running (or Completed for one-time Jobs),"
echo "press Ctrl+C to stop watching."
