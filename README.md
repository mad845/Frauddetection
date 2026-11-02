# Sistema Distribuido de Detección de Fraude en Transacciones

Proyecto avanzado que combina **Java** (núcleo transaccional) y **Python** (motor de
Machine Learning) comunicados por red, con dos bases de datos distintas y
mensajería asíncrona.

## Arquitectura

![Diagrama de arquitectura](docs/architecture-diagram.svg)

```
Dashboard (HTML/JS)
        │  REST + WebSocket
        ▼
┌─────────────────────┐        Kafka topic         ┌──────────────────────┐
│   Java Service       │ ───────────────────────▶  │   Python Service      │
│   Spring Boot         │   "transactions-topic"    │   Isolation Forest    │
│   PostgreSQL          │                            │   MongoDB              │
│   Puerto 8080          │ ◀───────────────────────  │   (consume + analiza) │
└─────────────────────┘   REST /api/alerts          └──────────────────────┘
```

1. El **cliente** (dashboard) envía una transacción por REST al servicio Java.
2. Java guarda la transacción en **PostgreSQL** (estado `PENDING`) y la publica
   en **Kafka**.
3. El servicio **Python** consume el mensaje, calcula un score de riesgo con
   un modelo de **Isolation Forest** (detección de anomalías no supervisada),
   guarda el resultado en **MongoDB** y devuelve el veredicto a Java por REST.
4. Java actualiza el estado de la transacción y notifica al dashboard en
   tiempo real por **WebSocket**.

## Requisitos

- Docker y Docker Compose instalados
- Puertos libres: 5432, 8080, 9092, 27017

## Cómo levantar todo el sistema

```bash
cd fraud-detection-system
docker compose up --build
```

Esto levanta, en orden:
- `postgres` (con las tablas `accounts` y `transactions` ya creadas y datos de prueba)
- `mongo`
- `zookeeper` + `kafka`
- `java-service` (Spring Boot, expone `http://localhost:8080`)
- `python-service` (entrena el modelo automáticamente la primera vez y luego
  se queda escuchando Kafka)

La primera vez, `python-service` genera datos sintéticos y entrena el modelo
(`generate_training_data.py` + `train_model.py`) antes de arrancar el consumidor.

## Probar el sistema

### Opción 1: Dashboard visual
Abre `dashboard/index.html` en el navegador (doble clic o `Live Server`).
Desde ahí podés enviar transacciones y ver las alertas llegar en tiempo real.

### Opción 2: cURL
```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC-0001",
    "amount": 150,
    "merchant": "Tienda Online",
    "merchantCountry": "AR",
    "channel": "ONLINE"
  }'
```

Cuentas de prueba disponibles: `ACC-0001`, `ACC-0002`, `ACC-0003`, `ACC-0004`
(ver `init-db/init.sql`).

Para simular una transacción sospechosa (monto alto, país extranjero):
```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC-0003",
    "amount": 9500,
    "merchant": "Casino Internacional",
    "merchantCountry": "RU",
    "channel": "ONLINE"
  }'
```

### Ver transacciones recientes / marcadas
```bash
curl http://localhost:8080/api/transactions/recent
curl http://localhost:8080/api/transactions/flagged
```

## Autenticación (JWT)

El servicio Java protege sus endpoints con **JWT**. Hay dos flujos distintos:

- **Usuarios humanos** (dashboard, cURL manual): inician sesión en
  `/api/auth/login` y usan el token Bearer devuelto en cada request.
- **Servicio Python → Java** (`/api/alerts`): en vez de pedirle un JWT de
  usuario, se valida con una **API key interna compartida** (`X-Internal-Api-Key`),
  ya que es comunicación servicio-a-servicio dentro de la red de Docker.

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'
```
Devuelve `{"token": "...", "tokenType": "Bearer"}`. Usuarios de demo:
`admin/admin123` y `analyst/analyst123` (ver `AuthController.java`; en un
entorno real vendrían de una tabla `users` con contraseñas hasheadas).

### Usar el token
```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer TU_TOKEN_AQUI" \
  -H "Content-Type: application/json" \
  -d '{"accountNumber": "ACC-0001", "amount": 150, "merchant": "Tienda", "merchantCountry": "AR", "channel": "ONLINE"}'
```

El dashboard (`dashboard/index.html`) ya incluye un formulario de login que
guarda el token y lo adjunta automáticamente a las siguientes peticiones.

En producción cambiá `JWT_SECRET` e `INTERNAL_API_KEY` (definidas en
`docker-compose.yml`) por valores generados de forma segura.

## Tests automatizados

### Java (JUnit 5 + Mockito)
```bash
cd java-service
mvn test
```
Cubre:
- `TransactionServiceTest`: creación de transacciones, cálculo de features
  (`isForeignCountry`, conteo de tx recientes), aplicación de veredictos de
  fraude, y manejo de errores (cuenta o transacción inexistente).
- `JwtUtilTest`: generación/validación de tokens, rechazo de tokens
  malformados, firmados con otra clave, o expirados.

### Python (pytest)
```bash
cd python-service
pip install -r requirements-dev.txt --break-system-packages
pytest tests/ -v
```
Los tests entrenan un modelo real (con semilla fija) en un directorio
temporal y validan que:
- transacciones normales se aprueban (`risk_score` bajo)
- transacciones con comportamiento extremo se rechazan
- el `risk_score` siempre cae en el rango `[0, 1]`
- el UUID de la transacción se preserva en el resultado

> Nota: esta suite fue validada manualmente ejecutando la misma lógica
> línea por línea contra el modelo entrenado (ver historial de desarrollo);
> los 5 casos pasaron correctamente antes de incluirse en el repo.

## Estructura del proyecto

```
fraud-detection-system/
├── docker-compose.yml
├── docs/architecture-diagram.svg    # diagrama de arquitectura
├── init-db/init.sql                 # esquema PostgreSQL + datos de prueba
├── java-service/                    # núcleo transaccional (Spring Boot)
│   ├── src/main/java/com/frauddetect/
│   │   ├── controller/               # TransactionController, AlertController, AuthController
│   │   ├── service/                  # lógica de negocio + Kafka producer + WebSocket
│   │   ├── security/                 # JwtUtil, JwtAuthFilter, InternalApiKeyFilter, SecurityConfig
│   │   ├── model/                    # entidades JPA (Account, Transaction)
│   │   ├── repository/               # Spring Data JPA
│   │   ├── dto/
│   │   └── config/                   # Kafka + WebSocket config
│   ├── src/test/java/com/frauddetect/
│   │   ├── service/TransactionServiceTest.java
│   │   └── security/JwtUtilTest.java
│   └── pom.xml
├── python-service/                  # motor de detección de fraude
│   ├── generate_training_data.py    # genera dataset sintético
│   ├── train_model.py               # entrena Isolation Forest
│   ├── fraud_detector.py            # aplica el modelo a cada transacción
│   ├── kafka_consumer.py            # consume Kafka, guarda en Mongo, notifica a Java
│   ├── mongo_client.py
│   ├── tests/test_fraud_detector.py
│   ├── requirements.txt
│   └── requirements-dev.txt
└── dashboard/index.html             # panel en tiempo real (login + REST + WebSocket)
```

## Por qué estas decisiones técnicas

- **PostgreSQL** para transacciones: necesitas consistencia fuerte (ACID) en
  datos financieros.
- **MongoDB** para resultados de análisis: estructura flexible, útil para
  guardar el payload completo + metadata variable de cada análisis, ideal
  para auditoría y reentrenamiento futuro del modelo.
- **Kafka** en vez de llamada REST directa Java→Python: desacopla ambos
  servicios, permite que el análisis sea asíncrono y escalable (podrías
  tener varios workers Python consumiendo el mismo tópico).
- **Isolation Forest**: no requiere transacciones etiquetadas como "fraude
  confirmado" (que en la vida real son escasas y difíciles de conseguir);
  aprende la forma de los datos normales y detecta desviaciones.
- **WebSocket (STOMP)** para el dashboard: las alertas llegan en tiempo real
  sin necesidad de hacer polling.
- **JWT para usuarios + API key para servicios**: separar ambos mecanismos
  evita forzar al servicio Python a manejar un flujo de login de usuario
  que no le corresponde, y mantiene el endpoint interno simple de rotar.

## Posibles extensiones (para llevar el proyecto más lejos)

- Reemplazar Isolation Forest por un modelo supervisado (XGBoost) si en el
  futuro se cuenta con transacciones etiquetadas.
- Agregar autenticación JWT en la API Java.
- Escalar el consumidor Python con múltiples particiones de Kafka.
- Agregar un feedback loop: si un analista humano revisa una transacción
  `FLAGGED` y la confirma como fraude real, esos datos alimentan el
  reentrenamiento periódico del modelo.
- Contenerizar el dashboard y servirlo con Nginx.
