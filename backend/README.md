# Lumina Algorithmic Trading FastAPI Backend

This is the secure, isolated, and scalable FastAPI backend for Lumina, engineered to manage trading audits, strategy execution, paper trading simulation accounts, and risk boundaries.

## Architecture

The backend is cleanly isolated within the `/backend` folder. It is structured into the following modules as requested:
*   `api/`: Route handlers and Pydantic validation schemas.
*   `core/`: Application settings and base settings loading.
*   `database/`: Connection engines, async session pools, and DB dependencies.
*   `models/`: SQLAlchemy 2.0 database models.
*   `services/`: Intermediate business-logic services.
*   `trading/`: Algorithmic trading execution check loops.
*   `risk/`: Real-time order validation & leverage checking.
*   `paper_trading/`: Virtual order, slippage, and fee simulators.

---

## Prerequisites (Python 3.12)

This backend requires Python 3.12+.

---

## Local Development & Setup

### 1. Create a Virtual Environment
```bash
cd backend
python3.12 -m venv venv
source venv/bin/activate
```

### 2. Install Required Dependencies
```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### 3. Database Migrations (Alembic)
The system is configured with out-of-the-box support for Alembic migrations.

**To auto-generate a new migration revision based on the SQLAlchemy models:**
```bash
alembic revision --autogenerate -m "Initial schema setup"
```

**To run the migrations and update the database scheme:**
```bash
alembic upgrade head
```

### 4. Run the API Locally
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
Once started, visit:
*   Interactive Swagger Documentation: [http://localhost:8000/docs](http://localhost:8000/docs)
*   Direct Health Check JSON: [http://localhost:8000/health](http://localhost:8000/health)

---

## Production Deployment on Contabo VPS (via Termius SSH)

Since you are managing your **Contabo VPS** from your phone using **Termius**, you can run the FastAPI backend continuously as a background service using **systemd**.

### Step 1: Install Python 3.12 & PostgreSQL/SQLite on the VPS
Connect to your VPS in Termius and run:
```bash
sudo apt update
sudo apt install python3.12 python3.12-venv python3-pip git -y
```

### Step 2: Set Up the Isolated Virtual Environment
In the repository folder:
```bash
cd Valentine_ai/backend
python3.12 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Step 3: Configure environment variables (Optional)
Create a `.env` file in `Valentine_ai/backend/`:
```bash
DATABASE_URL=sqlite+aiosqlite:///./lumina_backend.db
# Or if you use PostgreSQL in the future:
# DATABASE_URL=postgresql+asyncpg://user:password@localhost/lumina_db
```

### Step 4: Run Alembic migrations on VPS
```bash
alembic upgrade head
```

### Step 5: Keep FastAPI Running 24/7 with Systemd
To prevent FastAPI from closing when you close Termius, create a systemd service file:
```bash
sudo nano /etc/systemd/system/lumina.service
```

Paste the following configuration (replace `/path/to/your/cloned/` with your actual directory path on the VPS):
```ini
[Unit]
Description=Lumina Algorithmic FastAPI Backend
After=network.target

[Service]
User=root
WorkingDirectory=/path/to/your/cloned/Valentine_ai/backend
ExecStart=/path/to/your/cloned/Valentine_ai/backend/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
```

### Step 6: Enable and Start the Service
```bash
sudo systemctl daemon-reload
sudo systemctl enable lumina
sudo systemctl start lumina
```

To check current service logs from your phone in Termius:
```bash
sudo systemctl status lumina
# Or view continuous stream:
journalctl -u lumina -f
```

Lumina is now accessible at `http://<YOUR_VPS_STATIC_IP>:8000/health`.

---

## Running the Verification Test Suite

To verify all system features, we have developed a comprehensive automated test suite in `/backend/tests/test_trading_system.py`.

### How to Run:
Ensure you are inside the virtual environment (`source venv/bin/activate`):
```bash
pytest -v tests/
```

This test suite covers:
1. **Endpoint Health Check**: Verifies `/health` returns the correct operational telemetry.
2. **Paper Wallet Verification**: Validates isolated wallet instantiation and standard query response structures.
3. **Traceability Lifecycle**: Creates an active trade, a synchronized `AuditLog` execution trace, and establishes a strict `RiskProfile` restriction boundary—ensuring precise data normalization.
4. **Ledger Reconciliation**: Executes exactly **100 consecutive paper trades** with varying random and sequential win/loss outcomes. It programmatically re-calculates the expected account wallet balance at each step and asserts that the simulated **Expected Balance** matches the **Ledger Balance** to the absolute decimal cent. Any variance of a single penny triggers an immediate test failure.
