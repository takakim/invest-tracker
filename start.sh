#!/usr/bin/env bash
set -e

# Change directory to the script's directory
cd "$(dirname "$0")"

echo "================================================================="
echo "   🚀 Starting Invest Tracker (Full Stack on Docker)            "
echo "================================================================="

# Step 1: Ensure .env file exists
if [ ! -f .env ]; then
  echo "📄 Creating .env from .env.example..."
  cp .env.example .env
fi

# Step 2: Build, launch, and wait for healthy services
echo "🐳 Building and starting containers (Postgres, Backend, Frontend)..."
docker compose up --build -d --wait

echo ""
echo "================================================================="
echo "   ✅ Invest Tracker is up and running!                          "
echo "================================================================="
echo "   🌐 Web Application (Frontend):  http://localhost:3000        "
echo "   🔌 REST API (Backend):          http://localhost:8080/api/v1 "
echo "   🩺 Actuator Health:             http://localhost:8080/actuator/health"
echo "   🗄️ PostgreSQL Database:         127.0.0.1:5432               "
echo "================================================================="
echo "   To view logs:     docker compose logs -f                      "
echo "   To stop services: docker compose down                         "
echo "================================================================="
