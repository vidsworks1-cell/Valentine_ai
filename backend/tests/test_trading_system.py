import unittest
import asyncio
import random
from datetime import datetime
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from app.main import app
from app.database.base import Base
from app.database.session import get_db
from app.models.trading import Trade
from app.models.paper_trading import PaperWallet
from app.models.risk import RiskProfile
from app.models.audit import AuditLog

# Use isolated in-memory SQLite database for fast execution
TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

test_engine = create_async_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = async_sessionmaker(bind=test_engine, class_=AsyncSession, expire_on_commit=False)

# Override FastAPI get_db dependency to point to our test database sessionmaker
async def override_get_db():
    async with TestingSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()

app.dependency_overrides[get_db] = override_get_db


class TestTradingSystem(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        """Create all tables before each test."""
        async with test_engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)

    async def asyncTearDown(self):
        """Tear down tables after each test."""
        async with test_engine.begin() as conn:
            await conn.run_sync(Base.metadata.drop_all)

    async def test_endpoint_health(self):
        """Test 2: Verifies that GET /health returns 200 OK and accurate JSON."""
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "healthy")
        self.assertIn("version", data)

    async def test_paper_wallet_lifecycle(self):
        """Test 3: Create a paper wallet and retrieve it; balance matches perfectly."""
        account_id = "test_acc_88"
        initial_balance = 55000.50

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # Create wallet
            create_res = await client.post(
                f"/api/v1/paper-trading/{account_id}?initial_balance={initial_balance}"
            )
            self.assertEqual(create_res.status_code, 201)
            self.assertEqual(create_res.json()["balance"], initial_balance)

            # Retrieve wallet
            get_res = await client.get(f"/api/v1/paper-trading/{account_id}")
            self.assertEqual(get_res.status_code, 200)
            self.assertEqual(get_res.json()["balance"], initial_balance)

    async def test_single_trade_lifecycle(self):
        """Test 4: Insert trade, verify Trade, AuditLog, and RiskProfile update properly."""
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # 1. Establish RiskProfile first
            risk_profile_payload = {
                "strategy_name": "AZA_MOMENTUM_BREAKOUT",
                "max_drawdown": 0.08,
                "max_risk_per_trade": 0.015
            }
            risk_res = await client.post("/api/v1/risk", json=risk_profile_payload)
            self.assertEqual(risk_res.status_code, 201)
            self.assertEqual(risk_res.json()["max_drawdown"], 0.08)

            # 2. Insert the paper trade with reproducibility data (ATR, indicators, etc.)
            trade_payload = {
                "symbol": "ETHUSDT",
                "side": "BUY",
                "entry_price": 3200.0,
                "position_size": 2.5,
                "stop_loss": 3150.0,
                "take_profit": 3350.0,
                "strategy_used": "AZA_MOMENTUM_BREAKOUT",
                "market_regime": "TRENDING_UP",
                "confidence_score": 0.92,
                "entry_reason": "Moving Average 50 cross on 4h timeframe",
                "indicators": '{"rsi": 62, "macd": "bullish_cross"}',
                "atr": 45.2,
                "risk_amount": 125.0,
                "risk_pct": 0.0125
            }
            trade_res = await client.post("/api/v1/trading", json=trade_payload)
            self.assertEqual(trade_res.status_code, 201)
            trade_data = trade_res.json()
            self.assertEqual(trade_data["symbol"], "ETHUSDT")
            self.assertEqual(trade_data["entry_reason"], "Moving Average 50 cross on 4h timeframe")
            self.assertEqual(trade_data["atr"], 45.2)

            # 3. Create AuditLog representing system telemetry related to this trade
            audit_payload = {
                "level": "INFO",
                "category": "TRADING",
                "message": "Executed order ETHUSDT at 3200.00 successfully",
                "details": '{"slippage": 0.001, "latency_ms": 140}',
                "trade_id": trade_data["id"]
            }
            audit_res = await client.post("/api/v1/audit", json=audit_payload)
            self.assertEqual(audit_res.status_code, 201)
            self.assertEqual(audit_res.json()["trade_id"], trade_data["id"])

            # 4. Fetch audit log list and verify update
            all_logs_res = await client.get("/api/v1/audit")
            self.assertEqual(all_logs_res.status_code, 200)
            self.assertGreaterEqual(len(all_logs_res.json()), 1)

    async def test_reproducibility_ledger_reconciliation(self):
        """Test 7: Simulate 100 paper trades, verify that Expected balance == Ledger balance down to the penny."""
        account_id = "reconciliation_acc"
        initial_balance = 100000.00
        expected_balance = initial_balance

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # Create Paper Wallet
            await client.post(
                f"/api/v1/paper-trading/{account_id}?initial_balance={initial_balance}"
            )

            # We execute 100 simulated trades
            for i in range(100):
                # Simulate a win or loss outcome randomly or sequentially
                # Win: +$250.75, Loss: -$110.35
                is_win = (i % 3 != 0) # ~66% win-rate simulation
                pnl = 250.75 if is_win else -110.35
                
                # Post a trade record for traceability
                trade_payload = {
                    "symbol": "BTCUSDT",
                    "side": "BUY" if is_win else "SELL",
                    "entry_price": 60000.0 + i,
                    "position_size": 0.5,
                    "stop_loss": 59800.0,
                    "take_profit": 60500.0,
                    "strategy_used": "SCALPER_100",
                    "market_regime": "RANGING",
                    "confidence_score": 0.85,
                    "entry_reason": f"Scalp execution trade round {i}",
                    "indicators": f'{{"step": {i}}}',
                    "atr": 150.0,
                    "risk_amount": 110.35,
                    "risk_pct": 0.0011
                }
                trade_res = await client.post("/api/v1/trading", json=trade_payload)
                self.assertEqual(trade_res.status_code, 201)
                trade_id = trade_res.json()["id"]

                # Adjust the wallet balance on the ledger
                adjust_res = await client.post(
                    f"/api/v1/paper-trading/{account_id}/adjust?amount_change={pnl}"
                )
                self.assertEqual(adjust_res.status_code, 200)
                
                # Record exit update to the trade to mark reproducibility of outcome
                update_payload = {
                    "exit_price": 60500.0 if is_win else 59779.3,
                    "profit_loss": pnl,
                    "exit_time": datetime.utcnow().isoformat(),
                    "outcome": "WIN" if is_win else "LOSS"
                }
                await client.patch(f"/api/v1/trading/{trade_id}", json=update_payload)

                # Log audit trail event for full system traceability
                await client.post("/api/v1/audit", json={
                    "level": "INFO",
                    "category": "SIMULATOR",
                    "message": f"Trade {trade_id} exited with profit/loss: ${pnl}",
                    "details": f'{{"outcome": "{"WIN" if is_win else "LOSS"}", "pnl": {pnl}}}',
                    "trade_id": trade_id
                })

                # Track expected balance
                expected_balance += pnl

            # Retrieve final wallet balance
            final_wallet_res = await client.get(f"/api/v1/paper-trading/{account_id}")
            self.assertEqual(final_wallet_res.status_code, 200)
            final_balance = final_wallet_res.json()["balance"]

            # Assert Expected balance matches Ledger balance down to the exact cent
            self.assertLess(abs(final_balance - expected_balance), 0.001, f"Balance mismatch! Expected {expected_balance}, got {final_balance}")

    async def test_laptop_ping(self):
        """Test Laptop Agent /api/ping connectivity endpoint."""
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/api/ping")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["message"], "pong")
        self.assertEqual(data["status"], "stable")
        self.assertIn("timestamp", data)

    async def test_laptop_documents(self):
        """Test Laptop Agent document retrieval and detailed query endpoints."""
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # 1. Get all documents
            docs_res = await client.get("/api/documents")
            self.assertEqual(docs_res.status_code, 200)
            docs = docs_res.json()
            self.assertGreaterEqual(len(docs), 3)
            self.assertEqual(docs[0]["category"], "RESEARCH")

            # 2. Get document by ID
            first_doc_id = docs[0]["id"]
            doc_res = await client.get(f"/api/documents/{first_doc_id}")
            self.assertEqual(doc_res.status_code, 200)
            doc = doc_res.json()
            self.assertEqual(doc["title"], docs[0]["title"])

            # 3. Request invalid ID
            err_res = await client.get("/api/documents/non_existent_id")
            self.assertEqual(err_res.status_code, 404)

    async def test_laptop_exchange(self):
        """Test Laptop Agent telemetry metrics push/pull sync endpoints."""
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # 1. Push exchange payload
            payload = {
                "deviceId": "Test-Client-Device",
                "clientVersion": "1.0.0",
                "dataPayload": {
                    "active_assets_count": "5",
                    "total_chat_history_messages": "12"
                },
                "timestamp": 1234567890
            }
            exchange_res = await client.post("/api/exchange", json=payload)
            self.assertEqual(exchange_res.status_code, 200)
            exchange_data = exchange_res.json()
            self.assertEqual(exchange_data["status"], "SUCCESS")
            self.assertEqual(exchange_data["recordsSynced"], 2)
            self.assertIn("Test-Client-Device", exchange_data["deviceName"])

            # 2. Check sync status
            status_res = await client.get("/api/sync-status")
            self.assertEqual(status_res.status_code, 200)
            status_data = status_res.json()
            self.assertEqual(status_data["status"], "SUCCESS")
            self.assertEqual(status_data["recordsSynced"], 2)

    async def test_binance_testnet_connector(self):
        """Test BinanceConnector base URL, credential separation, and key checking."""
        import os
        from app.services.binance_connector import BinanceConnector

        connector = BinanceConnector.get_instance()

        # 1. Base URL verification
        self.assertEqual(connector.get_base_url("TESTNET"), "https://testnet.binance.vision")
        self.assertEqual(connector.get_base_url("LIVE"), "https://api.binance.com")

        # 2. Credential separation
        os.environ["BINANCE_TESTNET_API_KEY"] = "testnet_key_123"
        os.environ["BINANCE_TESTNET_SECRET_KEY"] = "testnet_secret_456"
        os.environ["BINANCE_API_KEY"] = "live_key_789"
        os.environ["BINANCE_API_SECRET"] = "live_secret_000"

        try:
            self.assertEqual(connector.get_credentials("TESTNET"), ("testnet_key_123", "testnet_secret_456"))
            self.assertEqual(connector.get_credentials("LIVE"), ("live_key_789", "live_secret_000"))
            self.assertTrue(connector.has_keys("TESTNET"))
            self.assertTrue(connector.has_keys("LIVE"))

            # Testnet keys must never match live keys
            key_t, secret_t = connector.get_credentials("TESTNET")
            key_l, secret_l = connector.get_credentials("LIVE")
            self.assertNotEqual(key_t, key_l)
            self.assertNotEqual(secret_t, secret_l)
        finally:
            os.environ.pop("BINANCE_TESTNET_API_KEY", None)
            os.environ.pop("BINANCE_TESTNET_SECRET_KEY", None)
            os.environ.pop("BINANCE_API_KEY", None)
            os.environ.pop("BINANCE_API_SECRET", None)

    async def test_binance_testnet_missing_keys_structured_error(self):
        """Test that endpoints return structured errors when TESTNET keys are missing."""
        import os
        os.environ.pop("BINANCE_TESTNET_API_KEY", None)
        os.environ.pop("BINANCE_TESTNET_SECRET_KEY", None)
        os.environ.pop("BINANCE_TESTNET_API_SECRET", None)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # 1. Status endpoint
            status_res = await client.get("/api/v1/trading/binance/status?mode=TESTNET")
            self.assertEqual(status_res.status_code, 200)
            res_data = status_res.json()
            self.assertFalse(res_data["is_authenticated"])
            self.assertEqual(res_data["mode"], "TESTNET")
            self.assertEqual(res_data["execution_mode"], "TESTNET")
            self.assertEqual(res_data["execution_url"], "https://testnet.binance.vision")
            self.assertEqual(res_data["http_status"], 401)
            self.assertEqual(res_data["binance_code"], -2008)

            # 2. Balances endpoint missing keys
            bal_res = await client.get("/api/v1/trading/binance/balances?mode=TESTNET")
            self.assertEqual(bal_res.status_code, 401)
            self.assertEqual(bal_res.json()["detail"]["error"], "MISSING_KEYS")

            # 3. Orders endpoint missing keys
            orders_res = await client.get("/api/v1/trading/binance/orders?mode=TESTNET")
            self.assertEqual(orders_res.status_code, 401)
            self.assertEqual(orders_res.json()["detail"]["error"], "MISSING_KEYS")

            # 4. Execute order endpoint missing keys
            order_payload = {
                "symbol": "BTCUSDT",
                "side": "BUY",
                "order_type": "MARKET",
                "usdt_amount": 10.0,
                "mode": "TESTNET"
            }
            exec_res = await client.post("/api/v1/trading/execute-live", json=order_payload)
            self.assertEqual(exec_res.status_code, 401)
            self.assertEqual(exec_res.json()["detail"]["error"], "MISSING_KEYS")


