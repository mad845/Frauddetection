"""
Consumidor de Kafka: escucha el topico 'transactions-topic' publicado
por el servicio Java, evalua cada transaccion con el modelo de ML,
guarda el resultado en MongoDB y devuelve el veredicto al servicio
Java via REST para que actualice el estado en PostgreSQL.
"""
import json
import os
import time
import logging

from kafka import KafkaConsumer
from kafka.errors import NoBrokersAvailable
import requests

from fraud_detector import FraudDetector
from mongo_client import FraudAnalysisStore

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("fraud-consumer")

KAFKA_BOOTSTRAP_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
JAVA_SERVICE_URL = os.environ.get("JAVA_SERVICE_URL", "http://localhost:8080")
INTERNAL_API_KEY = os.environ.get("INTERNAL_API_KEY", "internal-secret-key-change-me")
TOPIC = "transactions-topic"


def connect_consumer(retries=15, delay=4):
    """Reintenta la conexion a Kafka porque el broker puede tardar en levantar."""
    for attempt in range(retries):
        try:
            consumer = KafkaConsumer(
                TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_deserializer=lambda v: json.loads(v.decode("utf-8")),
                auto_offset_reset="latest",
                enable_auto_commit=True,
                group_id="fraud-detection-group",
            )
            logger.info("Conectado a Kafka en %s", KAFKA_BOOTSTRAP_SERVERS)
            return consumer
        except NoBrokersAvailable:
            logger.warning("Kafka no disponible aun, reintento %d/%d...", attempt + 1, retries)
            time.sleep(delay)
    raise RuntimeError("No se pudo conectar a Kafka despues de varios intentos")


def send_verdict_to_java(verdict: dict):
    try:
        headers = {"X-Internal-Api-Key": INTERNAL_API_KEY}
        response = requests.post(f"{JAVA_SERVICE_URL}/api/alerts", json=verdict, headers=headers, timeout=5)
        response.raise_for_status()
        logger.info("Veredicto enviado a Java: %s -> %s", verdict["transactionUuid"], verdict["verdict"])
    except requests.RequestException as e:
        logger.error("No se pudo notificar al servicio Java: %s", e)


def main():
    detector = FraudDetector()
    store = FraudAnalysisStore()
    consumer = connect_consumer()

    logger.info("Escuchando transacciones en el topico '%s'...", TOPIC)
    for message in consumer:
        payload = message.value
        logger.info("Transaccion recibida: %s", payload.get("transactionUuid"))

        verdict = detector.evaluate(payload)
        store.save_result(payload, verdict)
        send_verdict_to_java(verdict)


if __name__ == "__main__":
    main()
