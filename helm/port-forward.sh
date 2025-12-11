#!/bin/bash

# Script to create port-forwards for BankApp services
# Usage: ./port-forward.sh

echo "Starting port-forwards for BankApp services..."

# Kill existing port-forwards
pkill -f "kubectl port-forward.*bankapp"

# Wait a bit
sleep 2

# Create port-forwards
kubectl port-forward -n bankapp svc/bankapp-front-ui 8080:8080 > /tmp/pf-front-ui.log 2>&1 &
echo "✅ Front-UI: http://localhost:8080"

kubectl port-forward -n bankapp svc/bankapp-keycloak 8180:8080 > /tmp/pf-keycloak.log 2>&1 &
echo "✅ Keycloak: http://localhost:8180"

kubectl port-forward -n bankapp svc/bankapp-accounts 8081:8081 > /tmp/pf-accounts.log 2>&1 &
echo "✅ Accounts: http://localhost:8081"

kubectl port-forward -n bankapp svc/bankapp-cash 8082:8082 > /tmp/pf-cash.log 2>&1 &
echo "✅ Cash: http://localhost:8082"

kubectl port-forward -n bankapp svc/bankapp-transfer 8083:8083 > /tmp/pf-transfer.log 2>&1 &
echo "✅ Transfer: http://localhost:8083"

kubectl port-forward -n bankapp svc/bankapp-exchange 8084:8084 > /tmp/pf-exchange.log 2>&1 &
echo "✅ Exchange: http://localhost:8084"

kubectl port-forward -n bankapp svc/bankapp-exchange-generator 8085:8085 > /tmp/pf-exchange-generator.log 2>&1 &
echo "✅ Exchange Generator: http://localhost:8085"

kubectl port-forward -n bankapp svc/bankapp-notifications 8087:8087 > /tmp/pf-notifications.log 2>&1 &
echo "✅ Notifications: http://localhost:8087"

kubectl port-forward -n bankapp svc/bankapp-blocker 8086:8086 > /tmp/pf-blocker.log 2>&1 &
echo "✅ Blocker: http://localhost:8086"

echo ""
echo "All port-forwards are running in the background."
echo "Logs are available in /tmp/pf-*.log"
echo ""
echo "To stop all port-forwards, run:"
echo "  pkill -f 'kubectl port-forward.*bankapp'"

