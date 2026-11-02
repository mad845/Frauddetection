#!/bin/sh
set -e

if [ ! -f "fraud_model.joblib" ]; then
    echo "Modelo no encontrado. Generando datos y entrenando..."
    python generate_training_data.py
    python train_model.py
fi

echo "Iniciando consumidor de Kafka..."
exec python kafka_consumer.py
