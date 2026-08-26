#!/usr/bin/env bash
set -e

echo "================================================================="
echo "   ⚠️   INVEST TRACKER - DATABASE RESET UTILITY                   "
echo "================================================================="
echo "WARNING: This will permanently wipe all portfolios, accounts,    "
echo "transactions, imported data, and price observations!             "
echo "================================================================="
read -r -p "Type 'RESET' to confirm database reset: " CONFIRM

if [[ "$CONFIRM" != "RESET" && "$CONFIRM" != "reset" ]]; then
  echo "❌ Reset cancelled. No data was modified."
  exit 0
fi

echo "🧹 Resetting database..."

# Check if containers are running
if docker compose ps postgres | grep -q "Up"; then
  echo "🔄 Calling backend system reset API..."
  curl -s -X POST http://localhost:8080/api/v1/system/reset-database \
    -H "Content-Type: application/json" \
    -d '{"confirmation":"RESET"}' || true
  echo ""
  echo "✅ Application database reset complete!"
else
  echo "🔄 Resetting via Docker volumes..."
  docker compose down -v
  echo "✅ Docker volume removed. Starting fresh containers..."
  ./start.sh
fi

echo "================================================================="
echo "   🎉 Database reset successfully completed!                    "
echo "================================================================="
