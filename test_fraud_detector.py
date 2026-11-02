"""
Tests del motor de deteccion de fraude.

Entrena un modelo real (con semilla fija) en un directorio temporal para
no depender de artefactos pre-generados, y valida que:
- transacciones normales se aprueban
- transacciones con comportamiento extremo se rechazan o marcan
- el risk_score siempre queda en el rango [0, 1]
"""
import os
import sys
import subprocess
import pytest

SERVICE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, SERVICE_DIR)


@pytest.fixture(scope="module", autouse=True)
def trained_model(tmp_path_factory):
    """Genera datos y entrena el modelo una sola vez para todos los tests."""
    workdir = tmp_path_factory.mktemp("model_artifacts")
    original_cwd = os.getcwd()
    os.chdir(workdir)

    subprocess.run([sys.executable, os.path.join(SERVICE_DIR, "generate_training_data.py")], check=True)
    subprocess.run([sys.executable, os.path.join(SERVICE_DIR, "train_model.py")], check=True)

    yield

    os.chdir(original_cwd)


@pytest.fixture
def detector():
    from fraud_detector import FraudDetector
    return FraudDetector()


def test_transaccion_normal_se_aprueba(detector):
    payload = {
        "transactionUuid": "t-normal",
        "amount": 150,
        "balance": 5000,
        "txLastHourSameAccount": 1,
        "isForeignCountry": False,
    }
    result = detector.evaluate(payload)
    assert result["verdict"] == "APPROVED"
    assert result["riskScore"] < 0.55


def test_transaccion_extrema_se_rechaza(detector):
    payload = {
        "transactionUuid": "t-extrema",
        "amount": 9500,
        "balance": 5000,
        "txLastHourSameAccount": 10,
        "isForeignCountry": True,
    }
    result = detector.evaluate(payload)
    assert result["verdict"] == "REJECTED"
    assert result["riskScore"] >= 0.80


def test_transaccion_intermedia_se_marca_para_revision(detector):
    payload = {
        "transactionUuid": "t-media",
        "amount": 4000,
        "balance": 5000,
        "txLastHourSameAccount": 6,
        "isForeignCountry": True,
    }
    result = detector.evaluate(payload)
    assert result["verdict"] in ("FLAGGED", "REJECTED")
    assert result["riskScore"] >= 0.55


@pytest.mark.parametrize("amount,tx_last_hour,is_foreign,balance", [
    (10, 0, False, 5000),
    (5000, 5, True, 200),
    (999999, 50, True, 100),
    (0.01, 0, False, 100000),
])
def test_risk_score_siempre_en_rango_valido(detector, amount, tx_last_hour, is_foreign, balance):
    payload = {
        "transactionUuid": "t-fuzz",
        "amount": amount,
        "balance": balance,
        "txLastHourSameAccount": tx_last_hour,
        "isForeignCountry": is_foreign,
    }
    result = detector.evaluate(payload)
    assert 0.0 <= result["riskScore"] <= 1.0
    assert result["verdict"] in ("APPROVED", "FLAGGED", "REJECTED")


def test_resultado_conserva_el_uuid_de_la_transaccion(detector):
    payload = {
        "transactionUuid": "uuid-especifico-123",
        "amount": 100,
        "balance": 1000,
        "txLastHourSameAccount": 0,
        "isForeignCountry": False,
    }
    result = detector.evaluate(payload)
    assert result["transactionUuid"] == "uuid-especifico-123"
