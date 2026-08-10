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

if [ ! -f .env ]; then
    echo ".env file not found."
    echo "Create it first with:"
    echo "GEMINI_API_KEY=your_api_key"
    exit 1
fi

if ! grep -q '^GEMINI_API_KEY=' .env; then
    echo "GEMINI_API_KEY not found in .env."
    exit 1
fi

GEMINI_API_KEY=$(grep '^GEMINI_API_KEY=' .env | cut -d '=' -f2-)

echo "Creating/updating Gemini Secret..."

kubectl create secret generic gemini-secret \
    --from-literal=GEMINI_API_KEY="$GEMINI_API_KEY" \
    --dry-run=client \
    -o yaml | kubectl apply -f -

echo "Applying Kubernetes manifests..."

kubectl apply -f k8s/shared/
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/kafka-ui/
kubectl apply -f k8s/user-service/
kubectl apply -f k8s/booking-service/
kubectl apply -f k8s/audit-service/

kubectl apply -f k8s/ai-chat-service/configmap-gemini.yaml
kubectl apply -f k8s/ai-chat-service/ai-chat-deployment.yaml
kubectl apply -f k8s/ai-chat-service/ai-chat-service.yaml

kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress/

echo
echo "Kubernetes manifests applied successfully."
echo
echo "Monitor the deployment with:"
echo "    kubectl get pods -w"
