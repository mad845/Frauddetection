"""
Motor de deteccion de fraude.
Recibe el payload de una transaccion (dict), calcula el score de riesgo
usando el modelo entrenado y decide un veredicto.
"""
import joblib
import numpy as np
import os

FEATURES = ["amount", "tx_last_hour_same_account", "is_foreign_country", "balance_ratio"]

MODEL_PATH = "fraud_model.joblib"
SCALER_PATH = "fraud_scaler.joblib"
BOUNDS_PATH = "fraud_score_bounds.joblib"


class FraudDetector:
    def __init__(self):
        if not (os.path.exists(MODEL_PATH) and os.path.exists(SCALER_PATH) and os.path.exists(BOUNDS_PATH)):
            raise FileNotFoundError(
                "No se encontro el modelo entrenado. Ejecuta generate_training_data.py y train_model.py primero."
            )
        self.model = joblib.load(MODEL_PATH)
        self.scaler = joblib.load(SCALER_PATH)
        bounds = joblib.load(BOUNDS_PATH)
        # Ampliamos un poco el rango observado en entrenamiento para dar
        # margen a transacciones aun mas extremas en produccion.
        span = bounds["max"] - bounds["min"]
        self.score_min = bounds["min"] - 0.15 * span
        self.score_max = bounds["max"] + 0.15 * span

    def _extract_features(self, payload: dict):
        amount = float(payload.get("amount", 0))
        balance = float(payload.get("balance", 1)) or 1.0
        tx_last_hour = float(payload.get("txLastHourSameAccount", 0))
        is_foreign = 1.0 if payload.get("isForeignCountry") else 0.0
        balance_ratio = amount / balance

        return np.array([[amount, tx_last_hour, is_foreign, balance_ratio]])

    def evaluate(self, payload: dict) -> dict:
        raw_features = self._extract_features(payload)
        scaled = self.scaler.transform(raw_features)

        # decision_function: valores mas bajos = mas anomalo, mas altos = mas normal
        raw_score = self.model.decision_function(scaled)[0]

        # Normalizamos usando el rango real observado en entrenamiento,
        # invertido para que 1.0 = maximo riesgo y 0.0 = comportamiento normal
        risk_score = float(
            np.clip((self.score_max - raw_score) / (self.score_max - self.score_min), 0, 1)
        )

        if risk_score >= 0.80:
            verdict = "REJECTED"
            reason = "Patron altamente anomalo: monto y comportamiento fuera de lo normal"
        elif risk_score >= 0.55:
            verdict = "FLAGGED"
            reason = "Transaccion marcada para revision manual por comportamiento inusual"
        else:
            verdict = "APPROVED"
            reason = "Transaccion dentro de patrones normales"

        return {
            "transactionUuid": payload.get("transactionUuid"),
            "riskScore": round(risk_score, 4),
            "verdict": verdict,
            "reason": reason,
        }
