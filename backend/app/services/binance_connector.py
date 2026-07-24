import hmac
import hashlib
import os
import time
import urllib.parse
from typing import Dict, Any, List, Optional, Union, Tuple
import httpx
import logging
from app.core.config import settings

logger = logging.getLogger("lumina.services.binance_connector")

class BinanceConnector:
    """Service to connect directly to Binance endpoints (Testnet and Live).
    Implements:
    - Account Info
    - Wallet Balances
    - Open Positions
    - Open Orders
    - Market/Limit Orders
    - Order Cancellation
    """
    _instance: Optional['BinanceConnector'] = None

    def __init__(self):
        pass

    @classmethod
    def get_instance(cls) -> 'BinanceConnector':
        if cls._instance is None:
            cls._instance = BinanceConnector()
        return cls._instance

    def get_credentials(self, mode: str) -> Tuple[str, str]:
        """Returns (api_key, api_secret) strictly based on execution mode."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "TESTNET":
            key = (
                os.environ.get("BINANCE_TESTNET_API_KEY") or
                getattr(settings, "BINANCE_TESTNET_API_KEY", "") or
                ""
            ).strip()
            secret = (
                os.environ.get("BINANCE_TESTNET_SECRET_KEY") or
                os.environ.get("BINANCE_TESTNET_API_SECRET") or
                getattr(settings, "BINANCE_TESTNET_SECRET_KEY", "") or
                getattr(settings, "BINANCE_TESTNET_API_SECRET", "") or
                ""
            ).strip()
            return key, secret
        elif mode_upper == "LIVE":
            key = (
                os.environ.get("BINANCE_API_KEY") or
                getattr(settings, "BINANCE_API_KEY", "") or
                ""
            ).strip()
            secret = (
                os.environ.get("BINANCE_API_SECRET") or
                getattr(settings, "BINANCE_API_SECRET", "") or
                ""
            ).strip()
            return key, secret
        else:
            return "", ""

    @property
    def api_key(self) -> str:
        key, _ = self.get_credentials("LIVE")
        return key

    @property
    def api_secret(self) -> str:
        _, secret = self.get_credentials("LIVE")
        return secret

    def get_base_url(self, mode: str) -> str:
        """Returns target endpoint URL based on mode."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "LIVE":
            return "https://api.binance.com"
        elif mode_upper == "TESTNET":
            return "https://testnet.binance.vision"
        else:
            return ""

    def has_keys(self, mode: Optional[str] = None) -> bool:
        """Checks if API key and secret are present for the given mode."""
        if mode is None or not str(mode).strip():
            k_live, s_live = self.get_credentials("LIVE")
            k_test, s_test = self.get_credentials("TESTNET")
            return bool((k_live and s_live) or (k_test and s_test))

        mode_upper = mode.strip().upper()
        if mode_upper == "PAPER":
            return True
        if mode_upper == "OFF":
            return False

        key, secret = self.get_credentials(mode_upper)
        return bool(key and secret)

    def _sign_query_string(self, query_string: str, secret: str) -> str:
        return hmac.new(
            secret.encode('utf-8'),
            query_string.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()

    async def _request(self, method: str, path: str, mode: str, params: Dict[str, Any] = None, signed: bool = False) -> Any:
        if params is None:
            params = {}

        mode_upper = (mode or "PAPER").upper()

        if mode_upper == "OFF":
            logger.info("[BinanceConnector] Request rejected: Trading mode is OFF.")
            return {
                "error": "TRADING_DISABLED",
                "status_code": 400,
                "code": -1000,
                "msg": "Trading mode is OFF."
            }

        if mode_upper == "PAPER":
            logger.info(
                f"[BinanceConnector] Structured Log | Environment: PAPER | Mode: {mode_upper} | "
                f"Method: {method} | Path: {path} | Endpoint: LOCAL_MOCK | Action: Executing local paper simulation"
            )
            return await self._mock_request(method, path, mode_upper, params)

        # Environment is either TESTNET or LIVE
        if mode_upper == "TESTNET":
            exec_env = "TESTNET"
            base_url = "https://testnet.binance.vision"
            api_key, api_secret = self.get_credentials("TESTNET")

            # SAFETY GUARDRAIL 1: Hard check that base_url is strictly https://testnet.binance.vision
            if "api.binance.com" in base_url or base_url != "https://testnet.binance.vision":
                logger.critical("[BinanceConnector] SECURITY VIOLATION: LIVE endpoint targeted in TESTNET mode!")
                raise RuntimeError("CRITICAL SECURITY VIOLATION: Production endpoint detected while TESTNET mode is active!")

            # SAFETY GUARDRAIL 2: Check that live keys are NOT being used for TESTNET
            prod_key, prod_secret = self.get_credentials("LIVE")
            if (prod_key and api_key and prod_key == api_key) or (prod_secret and api_secret and prod_secret == api_secret):
                logger.error("[BinanceConnector] SECURITY VIOLATION: Production API keys supplied for TESTNET mode!")
                return {
                    "error": "INVALID_TESTNET_KEYS",
                    "status_code": 400,
                    "code": -2008,
                    "msg": "LIVE API credentials cannot be used for Binance Spot Testnet. Please supply dedicated BINANCE_TESTNET_API_KEY and BINANCE_TESTNET_SECRET_KEY."
                }

        elif mode_upper == "LIVE":
            exec_env = "LIVE"
            base_url = "https://api.binance.com"
            api_key, api_secret = self.get_credentials("LIVE")
        else:
            raise ValueError(f"Invalid trading mode: {mode}")

        logger.info(
            f"[BinanceConnector] Structured Log | Environment: {exec_env} | Mode: {mode_upper} | "
            f"Method: {method} | Path: {path} | BaseURL: {base_url} | Key: {api_key[:6] if api_key else 'NONE'}... | Signed: {signed}"
        )

        if not (api_key and api_secret):
            key_names = "BINANCE_TESTNET_API_KEY / BINANCE_TESTNET_SECRET_KEY" if mode_upper == "TESTNET" else "BINANCE_API_KEY / BINANCE_API_SECRET"
            logger.error(f"[BinanceConnector] Cannot execute request in {mode_upper} ({exec_env}) environment: API key or secret is missing ({key_names}).")
            return {
                "error": "MISSING_KEYS",
                "status_code": 401,
                "code": -2008,
                "msg": f"Binance {exec_env} API key or secret is not configured in environment settings ({key_names})."
            }

        headers = {
            "X-MBX-APIKEY": api_key
        }
        if method.upper() in ["POST", "PUT"]:
            headers["Content-Type"] = "application/x-www-form-urlencoded"

        req_params = dict(params)
        if signed:
            if "recvWindow" not in req_params:
                req_params["recvWindow"] = 60000
            req_params["timestamp"] = int(time.time() * 1000)

            query_string = urllib.parse.urlencode(req_params)
            signature = self._sign_query_string(query_string, api_secret)
            query_string = f"{query_string}&signature={signature}"
            url = f"{base_url}{path}?{query_string}"
        else:
            if req_params:
                query_string = urllib.parse.urlencode(req_params)
                url = f"{base_url}{path}?{query_string}"
            else:
                url = f"{base_url}{path}"

        async with httpx.AsyncClient(timeout=10.0) as client:
            try:
                if method.upper() == "GET":
                    response = await client.get(url, headers=headers)
                elif method.upper() == "POST":
                    response = await client.post(url, headers=headers)
                elif method.upper() == "DELETE":
                    response = await client.delete(url, headers=headers)
                else:
                    raise ValueError(f"Unsupported HTTP method: {method}")

                try:
                    data = response.json()
                except Exception as json_err:
                    logger.error(
                        f"[BinanceConnector] Non-JSON response | Environment: {exec_env} | Mode: {mode_upper} | "
                        f"BaseURL: {base_url} | Path: {path} | Status: {response.status_code} | Body: {response.text[:200]}"
                    )
                    return {
                        "error": "HTTP_ERROR",
                        "status_code": response.status_code,
                        "code": response.status_code,
                        "msg": response.text
                    }

                logger.info(
                    f"[BinanceConnector] Response Received | Environment: {exec_env} | Mode: {mode_upper} | "
                    f"BaseURL: {base_url} | Path: {path} | HTTP Status: {response.status_code} | "
                    f"Full Binance JSON Response: {data}"
                )

                if response.status_code != 200:
                    binance_code = data.get("code", response.status_code) if isinstance(data, dict) else response.status_code
                    binance_msg = data.get("msg", response.text) if isinstance(data, dict) else response.text
                    logger.error(
                        f"[BinanceConnector] Binance API Error | Environment: {exec_env} | Mode: {mode_upper} | "
                        f"BaseURL: {base_url} | Path: {path} | HTTP Status: {response.status_code} | "
                        f"Binance Code: {binance_code} | Binance Msg: {binance_msg} | Full Response: {data}"
                    )
                    return {
                        "error": "BINANCE_API_ERROR",
                        "status_code": response.status_code,
                        "code": binance_code,
                        "msg": binance_msg,
                        "raw_response": data if isinstance(data, dict) else {"raw": response.text}
                    }

                if isinstance(data, dict) and "code" in data and isinstance(data["code"], int) and data["code"] < 0:
                    logger.error(
                        f"[BinanceConnector] Error object in 200 OK | Environment: {exec_env} | Mode: {mode_upper} | "
                        f"BaseURL: {base_url} | Path: {path} | Code: {data['code']} | Msg: {data.get('msg')}"
                    )
                    return {
                        "error": "BINANCE_API_ERROR",
                        "status_code": 200,
                        "code": data["code"],
                        "msg": data.get("msg", "Unknown Binance error"),
                        "raw_response": data
                    }

                return data
            except Exception as e:
                logger.error(
                    f"[BinanceConnector] Exception during request | Environment: {exec_env} | Mode: {mode_upper} | "
                    f"BaseURL: {base_url} | Path: {path} | Error: {e}"
                )
                return {
                    "error": "EXCEPTION",
                    "status_code": 500,
                    "code": 500,
                    "msg": str(e)
                }

    async def get_account_info(self, mode: str) -> Dict[str, Any]:
        """Fetches account details from Binance."""
        return await self._request("GET", "/api/v3/account", mode, signed=True)

    async def get_wallet_balances(self, mode: str) -> Union[List[Dict[str, Any]], Dict[str, Any]]:
        """Fetches non-zero asset balances or returns error dictionary."""
        res = await self.get_account_info(mode)
        if isinstance(res, dict) and "balances" in res:
            balances = []
            for b in res["balances"]:
                free = float(b.get("free", 0.0))
                locked = float(b.get("locked", 0.0))
                if free > 0 or locked > 0:
                    balances.append({
                        "asset": b["asset"],
                        "free": free,
                        "locked": locked
                    })
            return balances

        if isinstance(res, dict):
            return res
        return {
            "error": "UNKNOWN_ERROR",
            "status_code": 500,
            "code": -1,
            "msg": f"Unexpected response structure from Binance: {res}"
        }

    async def get_open_orders(self, mode: str, symbol: Optional[str] = None) -> Union[List[Dict[str, Any]], Dict[str, Any]]:
        """Gets all open orders or returns error dictionary."""
        params = {}
        if symbol:
            params["symbol"] = symbol.upper()
        res = await self._request("GET", "/api/v3/openOrders", mode, params=params, signed=True)
        if isinstance(res, list):
            return res
        if isinstance(res, dict):
            return res
        return {
            "error": "UNKNOWN_ERROR",
            "status_code": 500,
            "code": -1,
            "msg": f"Unexpected response structure from Binance: {res}"
        }

    async def create_order(
        self,
        mode: str,
        symbol: str,
        side: str,
        order_type: str,
        quantity: Optional[float] = None,
        price: Optional[float] = None,
        quote_order_qty: Optional[float] = None
    ) -> Dict[str, Any]:
        """Creates an order (LIMIT or MARKET). Supports both base asset quantity and quoteOrderQty (USDT amount)."""
        params = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "type": order_type.upper(),
        }
        if quantity is not None and quantity > 0.0:
            params["quantity"] = f"{quantity:.8f}".rstrip("0").rstrip(".")
        elif quote_order_qty is not None and quote_order_qty > 0.0:
            params["quoteOrderQty"] = f"{quote_order_qty:.8f}".rstrip("0").rstrip(".")
        else:
            raise ValueError("Either quantity or quote_order_qty must be provided and > 0")

        if order_type.upper() == "LIMIT":
            if price is None:
                raise ValueError("Price is required for LIMIT orders")
            params["price"] = str(price)
            params["timeInForce"] = "GTC"

        return await self._request("POST", "/api/v3/order", mode, params=params, signed=True)

    async def cancel_order(self, mode: str, symbol: str, order_id: Any) -> Dict[str, Any]:
        """Cancels an existing order."""
        params = {
            "symbol": symbol.upper(),
            "orderId": str(order_id)
        }
        return await self._request("DELETE", "/api/v3/order", mode, params=params, signed=True)

    async def _mock_request(self, method: str, path: str, mode: str, params: Dict[str, Any]) -> Any:
        """High-fidelity simulation fallback when in PAPER mode."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper in ["LIVE", "TESTNET"]:
            raise ValueError(f"CRITICAL SECURITY ERROR: _mock_request was invoked while in {mode_upper} mode! Mock execution is strictly forbidden in LIVE/TESTNET modes.")
        logger.info(f"[BinanceConnector] Structured Log | Environment: PAPER | Mode: {mode_upper} | Using high-fidelity mockup for {method} {path}")

        if path == "/api/v3/account":
            return {
                "makerCommission": 15,
                "takerCommission": 15,
                "buyerCommission": 0,
                "sellerCommission": 0,
                "canTrade": True,
                "canWithdraw": True,
                "canDeposit": True,
                "updateTime": int(time.time() * 1000),
                "accountType": "SPOT",
                "balances": [
                    {"asset": "BTC", "free": "0.15000000", "locked": "0.00000000"},
                    {"asset": "ETH", "free": "1.24000000", "locked": "0.00000000"},
                    {"asset": "USDT", "free": "48150.25000000", "locked": "0.00000000"},
                    {"asset": "BNB", "free": "5.50000000", "locked": "0.00000000"},
                    {"asset": "SOL", "free": "12.00000000", "locked": "0.00000000"}
                ]
            }

        elif path == "/api/v3/openOrders":
            return [
                {
                    "symbol": "BTCUSDT",
                    "orderId": 481516234,
                    "orderListId": -1,
                    "clientOrderId": "lumina_mock_btc",
                    "price": "91500.00000000",
                    "origQty": "0.01000000",
                    "executedQty": "0.00000000",
                    "cummulativeQuoteQty": "0.00000000",
                    "status": "NEW",
                    "timeInForce": "GTC",
                    "type": "LIMIT",
                    "side": "BUY",
                    "stopPrice": "0.00000000",
                    "icebergQty": "0.00000000",
                    "time": int(time.time() * 1000) - 60000,
                    "updateTime": int(time.time() * 1000) - 60000,
                    "isWorking": True,
                    "workingTime": int(time.time() * 1000) - 60000,
                    "origQuoteOrderQty": "0.00000000"
                }
            ]

        elif path == "/api/v3/order":
            if method.upper() == "DELETE":
                return {
                    "symbol": params.get("symbol", "BTCUSDT"),
                    "origClientOrderId": "lumina_mock_btc",
                    "orderId": params.get("orderId", "481516234"),
                    "orderListId": -1,
                    "clientOrderId": "lumina_mock_cancel",
                    "price": "91500.00",
                    "origQty": "0.01",
                    "executedQty": "0.00",
                    "cummulativeQuoteQty": "0.00",
                    "status": "CANCELED",
                    "timeInForce": "GTC",
                    "type": "LIMIT",
                    "side": "BUY"
                }
            else: # POST
                order_id = int(time.time() * 1000) % 1000000000
                qty = params.get("quantity", "0.01")
                price = params.get("price", "92500.00")
                return {
                    "symbol": params.get("symbol", "BTCUSDT"),
                    "orderId": order_id,
                    "orderListId": -1,
                    "clientOrderId": f"lumina_{order_id}",
                    "transactTime": int(time.time() * 1000),
                    "price": price,
                    "origQty": qty,
                    "executedQty": qty if params.get("type") == "MARKET" else "0.00",
                    "cummulativeQuoteQty": "0.00",
                    "status": "FILLED" if params.get("type") == "MARKET" else "NEW",
                    "timeInForce": "GTC",
                    "type": params.get("type", "MARKET"),
                    "side": params.get("side", "BUY"),
                    "workingTime": int(time.time() * 1000),
                    "fills": []
                }

        return {"error": "MOCK_UNSUPPORTED", "msg": f"Mock endpoint not defined for {path}"}
