import hmac
import hashlib
import os
import time
import urllib.parse
from typing import Dict, Any, List, Optional, Union
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

    @property
    def api_key(self) -> str:
        return os.environ.get("BINANCE_API_KEY") or getattr(settings, "BINANCE_API_KEY", "") or ""

    @property
    def api_secret(self) -> str:
        return os.environ.get("BINANCE_API_SECRET") or getattr(settings, "BINANCE_API_SECRET", "") or ""

    @classmethod
    def get_instance(cls) -> 'BinanceConnector':
        if cls._instance is None:
            cls._instance = BinanceConnector()
        return cls._instance

    def get_base_url(self, mode: str) -> str:
        if mode == "LIVE":
            return "https://api.binance.com"
        else:
            return "https://testnet.binance.vision"

    def has_keys(self) -> bool:
        return bool(self.api_key and self.api_secret)

    def _sign_query_string(self, query_string: str) -> str:
        return hmac.new(
            self.api_secret.encode('utf-8'),
            query_string.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()

    async def _request(self, method: str, path: str, mode: str, params: Dict[str, Any] = None, signed: bool = False) -> Any:
        if params is None:
            params = {}
            
        exec_type = "MOCK" if mode in ["OFF", "PAPER"] else "LIVE"
        base_url = self.get_base_url(mode)
        
        logger.info(
            f"[BinanceConnector] Request Initiated | Mode: {mode} | ExecType: {exec_type} | "
            f"Method: {method} | Path: {path} | BaseURL: {base_url} | HasKeys: {self.has_keys()}"
        )

        if mode in ["OFF", "PAPER"]:
            logger.info(f"[BinanceConnector] Using high-fidelity mock request in {mode} mode for {path}.")
            return await self._mock_request(method, path, mode, params)

        # LIVE or TESTNET mode - strictly NEVER call _mock_request()
        if not self.has_keys():
            logger.error(f"[BinanceConnector] Cannot execute request in {mode} mode: API key or secret is missing.")
            return {
                "error": "MISSING_KEYS",
                "status_code": 401,
                "code": -2008,
                "msg": "Binance API key or secret is not configured in environment settings."
            }

        headers = {
            "X-MBX-APIKEY": self.api_key,
            "Content-Type": "application/x-www-form-urlencoded"
        }

        req_params = dict(params)
        if signed:
            if "recvWindow" not in req_params:
                req_params["recvWindow"] = 5000
            req_params["timestamp"] = int(time.time() * 1000)
            
            # Construct deterministic query string and sign IT directly
            query_string = urllib.parse.urlencode(req_params)
            signature = self._sign_query_string(query_string)
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

                logger.info(
                    f"[BinanceConnector] Response Received | Mode: {mode} | ExecType: {exec_type} | "
                    f"Path: {path} | HTTP Status: {response.status_code}"
                )

                try:
                    data = response.json()
                except Exception as json_err:
                    logger.error(
                        f"[BinanceConnector] Non-JSON response | Mode: {mode} | Status: {response.status_code} | "
                        f"Body: {response.text[:200]}"
                    )
                    return {
                        "error": "HTTP_ERROR",
                        "status_code": response.status_code,
                        "code": response.status_code,
                        "msg": response.text
                    }

                if response.status_code != 200:
                    binance_code = data.get("code", response.status_code) if isinstance(data, dict) else response.status_code
                    binance_msg = data.get("msg", response.text) if isinstance(data, dict) else response.text
                    logger.error(
                        f"[BinanceConnector] Binance API Error | Mode: {mode} | ExecType: {exec_type} | "
                        f"BaseURL: {base_url} | Path: {path} | HTTP Status: {response.status_code} | "
                        f"Binance Code: {binance_code} | Binance Msg: {binance_msg}"
                    )
                    return {
                        "error": "BINANCE_API_ERROR",
                        "status_code": response.status_code,
                        "code": binance_code,
                        "msg": binance_msg
                    }

                if isinstance(data, dict) and "code" in data and isinstance(data["code"], int) and data["code"] < 0:
                    logger.error(
                        f"[BinanceConnector] Error object in 200 OK | Mode: {mode} | ExecType: {exec_type} | "
                        f"Path: {path} | Code: {data['code']} | Msg: {data.get('msg')}"
                    )
                    return {
                        "error": "BINANCE_API_ERROR",
                        "status_code": 200,
                        "code": data["code"],
                        "msg": data.get("msg", "Unknown Binance error")
                    }

                return data
            except Exception as e:
                logger.error(
                    f"[BinanceConnector] Exception during request | Mode: {mode} | ExecType: {exec_type} | "
                    f"Path: {path} | Error: {e}"
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

        # Do NOT silently swallow errors - return the error object if account info call failed
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

    async def create_order(self, mode: str, symbol: str, side: str, order_type: str, quantity: float, price: Optional[float] = None) -> Dict[str, Any]:
        """Creates an order (LIMIT or MARKET)."""
        params = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "type": order_type.upper(),
            "quantity": str(quantity)
        }
        if order_type.upper() == "LIMIT":
            if price is None:
                raise ValueError("Price is required for LIMIT orders")
            params["price"] = str(price)
            params["timeInForce"] = "GTC" # Good Till Cancelled
            
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
        if mode in ["LIVE", "TESTNET"]:
            raise ValueError(f"CRITICAL ERROR: _mock_request was invoked while in {mode} mode! Mock execution is strictly forbidden in LIVE/TESTNET modes.")
        logger.info(f"Using high-fidelity mockup for {method} {path} in {mode} mode.")
        
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
