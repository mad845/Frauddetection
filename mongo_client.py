"""
Cliente MongoDB: guarda cada resultado de analisis de fraude
(payload original + veredicto) para auditoria y futuro reentrenamiento.
"""
import os
from datetime import datetime
from pymongo import MongoClient

MONGO_URI = os.environ.get("MONGO_URI", "mongodb://localhost:27017")


class FraudAnalysisStore:
    def __init__(self):
        self.client = MongoClient(MONGO_URI)
        self.db = self.client["frauddetect"]
        self.collection = self.db["analysis_results"]

    def save_result(self, transaction_payload: dict, verdict: dict):
        document = {
            "transaction": transaction_payload,
            "verdict": verdict,
            "analyzed_at": datetime.utcnow().isoformat(),
        }
        self.collection.insert_one(document)

    def get_recent(self, limit=50):
        return list(
            self.collection.find({}, {"_id": 0}).sort("analyzed_at", -1).limit(limit)
        )
