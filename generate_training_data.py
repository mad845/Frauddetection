"""
Genera un dataset sintetico de transacciones para entrenar el modelo
de deteccion de anomalias (Isolation Forest).

La mayoria de las transacciones son "normales". Se inyecta un pequeno
porcentaje de transacciones con patrones atipicos (montos muy altos,
muchas transacciones en poco tiempo, pais extranjero) para que el
modelo aprenda a distinguir el comportamiento raro.
"""
import numpy as np
import pandas as pd

np.random.seed(42)

N_NORMAL = 4500
N_ANOMALOUS = 500

def generate_normal(n):
    return pd.DataFrame({
        "amount": np.random.gamma(shape=2.0, scale=80, size=n),        # montos moderados
        "tx_last_hour_same_account": np.random.poisson(1, size=n),     # pocas tx recientes
        "is_foreign_country": np.random.choice([0, 1], size=n, p=[0.9, 0.1]),
        "balance_ratio": np.random.uniform(0.01, 0.4, size=n),         # % del saldo usado
    })

def generate_anomalous(n):
    return pd.DataFrame({
        "amount": np.random.gamma(shape=5.0, scale=900, size=n),       # montos muy altos
        "tx_last_hour_same_account": np.random.poisson(8, size=n),     # muchas tx seguidas
        "is_foreign_country": np.random.choice([0, 1], size=n, p=[0.3, 0.7]),
        "balance_ratio": np.random.uniform(0.5, 1.5, size=n),          # usa casi todo el saldo
    })

if __name__ == "__main__":
    normal = generate_normal(N_NORMAL)
    anomalous = generate_anomalous(N_ANOMALOUS)
    dataset = pd.concat([normal, anomalous], ignore_index=True)
    dataset = dataset.sample(frac=1, random_state=42).reset_index(drop=True)
    dataset.to_csv("training_data.csv", index=False)
    print(f"Dataset generado: {len(dataset)} filas -> training_data.csv")
