"""
Entrena un modelo Isolation Forest para deteccion de anomalias
en transacciones bancarias y lo guarda en disco (fraud_model.joblib).

Isolation Forest es ideal aqui porque no necesita etiquetas de "fraude
confirmado" (que en la vida real son escasas): aprende la forma de los
datos normales y asigna un puntaje de anomalia a cada nueva observacion.
"""
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import joblib
import os

FEATURES = ["amount", "tx_last_hour_same_account", "is_foreign_country", "balance_ratio"]

def train():
    if not os.path.exists("training_data.csv"):
        raise FileNotFoundError("Ejecuta primero generate_training_data.py")

    df = pd.read_csv("training_data.csv")
    X = df[FEATURES]

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # contamination = proporcion esperada de anomalias en los datos
    model = IsolationForest(
        n_estimators=200,
        contamination=0.1,
        random_state=42,
    )
    model.fit(X_scaled)

    # Guardamos el rango real de decision_function sobre los datos de
    # entrenamiento para poder calibrar el risk_score (0-1) correctamente
    # en vez de usar un umbral fijo arbitrario.
    train_scores = model.decision_function(X_scaled)
    score_bounds = {
        "min": float(train_scores.min()),
        "max": float(train_scores.max()),
    }

    joblib.dump(model, "fraud_model.joblib")
    joblib.dump(scaler, "fraud_scaler.joblib")
    joblib.dump(score_bounds, "fraud_score_bounds.joblib")
    print("Modelo entrenado y guardado: fraud_model.joblib / fraud_scaler.joblib / fraud_score_bounds.joblib")
    print(f"Rango de decision_function observado: {score_bounds}")

if __name__ == "__main__":
    train()
