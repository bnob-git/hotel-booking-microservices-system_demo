#!/usr/bin/env bash

set -e

echo "========================================"
echo "Deploying Hotel Booking System with Helm"
echo "========================================"

# Check that Minikube is running
if ! minikube status >/dev/null 2>&1; then
    echo "Minikube is not running."
    echo "Start it first with:"
    echo "    minikube start"
    exit 1
fi

CHART="./helm/hotel-booking"
RELEASE="hotel-booking"

case "$1" in

    --ollama)
        echo "Deploying with Ollama AI..."

        helm upgrade --install "$RELEASE" "$CHART" \
            --set ai.enabled=true \
            --set ai.provider=ollama
        ;;

    --gemini)
        echo "Deploying with Gemini AI..."

        if [ ! -f .env ]; then
            echo ".env file not found."
            echo "Create .env with:"
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

        helm upgrade --install "$RELEASE" "$CHART" \
            --set ai.enabled=true \
            --set ai.provider=gemini
        ;;

    *)
        echo "Deploying without AI..."

        helm upgrade --install "$RELEASE" "$CHART"
        ;;

esac

echo
echo "Helm release deployed successfully."
echo
echo "Monitor the deployment with:"
echo "    kubectl get pods -w"
echo
echo "When all pods are Running (or Completed for one-time Jobs),"
echo "press Ctrl+C to stop watching."
