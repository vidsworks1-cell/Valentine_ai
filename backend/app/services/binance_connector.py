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

    @property
    def trading_venue(self) -> str:
        """Dynamically detects active venue from Scheduler, environment, or settings."""
        try:
            from app.scheduler.manager import LayeredSchedulerManager
            if LayeredSchedulerManager._instance is not None:
                return getattr(LayeredSchedulerManager._instance, "trading_venue", "SPOT").upper()
        except Exception:
            pass
        return os.getenv("TRADING_VENUE", os.getenv("BINANCE_VENUE", "SPOT")).upper()

    def get_base_url(self, mode: str, venue: Optional[str] = None) -> str:
        """Returns target endpoint URL based on mode and venue (SPOT vs FUTURES)."""
        target_venue = (venue or self.trading_venue).upper()
        mode_upper = (mode or "PAPER").upper()
        if target_venue == "FUTURES":
            if mode_upper == "LIVE":
                return "https://fapi.binance.com"
            elif mode_upper == "TESTNET":
                return "https://testnet.binancefuture.com"
            else:
                return ""
        else:
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

    @staticmethod
    def describe_binance_error(code: int, msg: str) -> Tuple[str, str]:
        """Maps Binance numeric error codes to human-readable explanations and actionable diagnostics."""
        explanations = {
            -2010: (
                "Insufficient Account Balance",
                "Account does not have enough free balance (USDT for BUY, or base asset for SELL) to execute this order. Reduce trade risk size or deposit funds."
            ),
            -1013: (
                "Filter Failure (MIN_NOTIONAL / LOT_SIZE)",
                "Order value is below the Binance minimum notional requirement (typically $5.00 - $10.00 USDT) or quantity violates step-size constraints."
            ),
            -1111: (
                "Precision Overflow",
                "Order quantity or price decimal precision exceeds the maximum allowed step precision for this trading pair."
            ),
            -1021: (
                "Timestamp Synchronization Drift",
                "Local system clock is out of sync with Binance server time by >1000ms. Synchronize system NTP clock or increase recvWindow."
            ),
            -2011: (
                "Unknown Order / Order Does Not Exist",
                "The order ID requested for cancellation or query was not found in the exchange order book."
            ),
            -1003: (
                "Rate Limit / IP Banned",
                "Too many requests sent within 1 minute window. Binance rate limit activated."
            ),
            -1100: (
                "Illegal Parameter Characters",
                "One or more query parameters contains unsupported or illegal characters."
            ),
            -2014: (
                "API-key format invalid",
                "Supplied API Key header is malformed, truncated, or invalid."
            ),
            -2015: (
                "Invalid API-key, IP, or Permissions",
                "API Key is invalid, restricted to a different IP address, or missing Spot Trading permissions in Binance account settings."
            )
        }
        if code in explanations:
            return explanations[code]
        return ("Binance Exchange Error", msg)

    async def _request(
        self,
        method: str,
        path: str,
        mode: str,
        params: Dict[str, Any] = None,
        signed: bool = False,
        venue: Optional[str] = None
    ) -> Any:
        if params is None:
            params = {}

        mode_upper = (mode or "PAPER").upper()
        if venue:
            target_venue = venue.upper()
        elif path.startswith("/fapi/"):
            target_venue = "FUTURES"
        elif path.startswith("/sapi/") or path.startswith("/api/"):
            target_venue = "SPOT"
        else:
            target_venue = self.trading_venue.upper()

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
                f"[BinanceConnector] Structured Log | Environment: PAPER | Venue: {target_venue} | Mode: {mode_upper} | "
                f"Method: {method} | Path: {path} | Endpoint: LOCAL_MOCK | Action: Executing local paper simulation"
            )
            return await self._mock_request(method, path, mode_upper, params)

        # Environment is either TESTNET or LIVE
        if mode_upper == "TESTNET":
            exec_env = "TESTNET"
            base_url = self.get_base_url("TESTNET", target_venue)
            api_key, api_secret = self.get_credentials("TESTNET")

            if target_venue == "SPOT":
                # SAFETY GUARDRAIL 1: Hard check that base_url is strictly https://testnet.binance.vision for Spot Testnet
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
            base_url = self.get_base_url("LIVE", target_venue)
            api_key, api_secret = self.get_credentials("LIVE")
        else:
            raise ValueError(f"Invalid trading mode: {mode}")

        logger.info(
            f"[BinanceConnector] Structured Log | Environment: {exec_env} | Venue: {target_venue} | Mode: {mode_upper} | "
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

        # Check Chaos Engine fault injections
        from app.trading.chaos_engine import ChaosEngine
        chaos = ChaosEngine.get_instance()
        if chaos.enabled:
            if chaos.should_inject_api_timeout():
                raise httpx.TimeoutException("Chaos Injected Binance API Timeout")
            if chaos.should_inject_http_500():
                return {
                    "error": "CHAOS_HTTP_500",
                    "status_code": 500,
                    "code": 500,
                    "msg": "Chaos injected HTTP 500 Internal Error"
                }
            fill_delay = chaos.get_fill_delay()
            if fill_delay > 0:
                await asyncio.sleep(fill_delay)

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

                # Format log data cleanly by omitting hundreds of zero balances and empty positions
                log_data = data
                if isinstance(data, dict) and "balances" in data and isinstance(data["balances"], list):
                    non_zero = [
                        b for b in data["balances"]
                        if float(b.get("free", 0.0)) > 0 or float(b.get("locked", 0.0)) > 0
                    ]
                    log_data = {
                        k: v for k, v in data.items() if k != "balances"
                    }
                    log_data["non_zero_balances"] = non_zero
                    log_data["_zero_balances_omitted"] = len(data["balances"]) - len(non_zero)
                elif isinstance(data, list) and len(data) > 0 and isinstance(data[0], dict) and "positionAmt" in data[0]:
                    active_positions = [
                        p for p in data
                        if abs(float(p.get("positionAmt", 0.0))) > 0.00001
                    ]
                    log_data = {
                        "active_futures_positions": active_positions,
                        "_zero_positions_omitted": len(data) - len(active_positions)
                    }

                logger.info(
                    f"[BinanceConnector] Response Received | Environment: {exec_env} | Mode: {mode_upper} | "
                    f"BaseURL: {base_url} | Path: {path} | HTTP Status: {response.status_code} | "
                    f"Response: {log_data}"
                )

                if response.status_code != 200:
                    binance_code = data.get("code", response.status_code) if isinstance(data, dict) else response.status_code
                    binance_msg = data.get("msg", response.text) if isinstance(data, dict) else response.text
                    err_title, err_advice = self.describe_binance_error(binance_code, str(binance_msg))
                    logger.error(
                        f"❌ [BINANCE REJECTION - HTTP {response.status_code}] {err_title} (Code {binance_code}): {binance_msg}\n"
                        f"  - Action: {method} {path} | Environment: {exec_env} | Mode: {mode_upper}\n"
                        f"  - Diagnostic Advice: {err_advice}\n"
                        f"  - Full Raw Response: {data}"
                    )
                    return {
                        "error": "BINANCE_API_ERROR",
                        "status_code": response.status_code,
                        "code": binance_code,
                        "msg": binance_msg,
                        "human_title": err_title,
                        "diagnostic_advice": err_advice,
                        "raw_response": data if isinstance(data, dict) else {"raw": response.text}
                    }

                if isinstance(data, dict) and "code" in data and isinstance(data["code"], int) and data["code"] < 0:
                    err_title, err_advice = self.describe_binance_error(data["code"], str(data.get("msg", "")))
                    logger.error(
                        f"❌ [BINANCE REJECTION in 200 OK] {err_title} (Code {data['code']}): {data.get('msg')}\n"
                        f"  - Action: {method} {path} | Environment: {exec_env} | Mode: {mode_upper}\n"
                        f"  - Diagnostic Advice: {err_advice}\n"
                        f"  - Full Raw Response: {data}"
                    )
                    return {
                        "error": "BINANCE_API_ERROR",
                        "status_code": 200,
                        "code": data["code"],
                        "msg": data.get("msg", "Unknown Binance error"),
                        "human_title": err_title,
                        "diagnostic_advice": err_advice,
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

    async def get_spot_equity_and_earn_status(self, mode: str) -> Dict[str, Any]:
        """
        Queries /api/v3/account to extract exact live Spot USDT equity and detect Binance Simple Earn (LDUSDT).
        Prevents position sizing on zero spot equity or simple earn funds.
        """
        res = await self.get_account_info(mode)
        if not isinstance(res, dict) or "balances" not in res:
            err_msg = res.get("msg") if isinstance(res, dict) else str(res)
            return {
                "error": err_msg or "Failed to retrieve /api/v3/account from Binance",
                "free_usdt": 0.0,
                "locked_usdt": 0.0,
                "total_spot_usdt": 0.0,
                "ldusdt_free": 0.0,
                "ldusdt_locked": 0.0,
                "total_ldusdt": 0.0,
                "is_earn_only": False,
                "has_simple_earn_funds": False,
                "status_message": f"Account query failed: {err_msg}"
            }

        balances_list = res.get("balances", [])
        free_usdt = 0.0
        locked_usdt = 0.0
        ldusdt_free = 0.0
        ldusdt_locked = 0.0

        for b in balances_list:
            asset = str(b.get("asset", "")).upper()
            free_val = float(b.get("free", 0.0))
            locked_val = float(b.get("locked", 0.0))

            if asset == "USDT":
                free_usdt = free_val
                locked_usdt = locked_val
            elif asset == "LDUSDT" or asset == "LENDING_USDT":
                ldusdt_free += free_val
                ldusdt_locked += locked_val

        total_spot_usdt = free_usdt + locked_usdt
        total_ldusdt = ldusdt_free + ldusdt_locked
        has_simple_earn_funds = total_ldusdt > 0.0
        is_earn_only = (free_usdt <= 0.0 and total_ldusdt > 0.0)

        if is_earn_only:
            status_message = "Funds are in Binance Simple Earn. Redeem to Spot before trading."
        elif free_usdt < 5.0:
            status_message = f"Insufficient free Spot USDT balance (${free_usdt:.2f} USDT). Minimum Binance Spot order requires at least $5.00 USDT."
        else:
            status_message = f"Verified live Binance Spot equity: ${free_usdt:.2f} USDT."

        return {
            "error": None,
            "free_usdt": free_usdt,
            "locked_usdt": locked_usdt,
            "total_spot_usdt": total_spot_usdt,
            "ldusdt_free": ldusdt_free,
            "ldusdt_locked": ldusdt_locked,
            "total_ldusdt": total_ldusdt,
            "is_earn_only": is_earn_only,
            "has_simple_earn_funds": has_simple_earn_funds,
            "status_message": status_message
        }

    async def get_exchange_info(self, mode: str) -> Dict[str, Any]:
        """Fetches /api/v3/exchangeInfo from Binance."""
        return await self._request("GET", "/api/v3/exchangeInfo", mode, signed=False)

    async def get_symbol_lot_size_filters(self, symbol: str, mode: str = "LIVE") -> Dict[str, Any]:
        """
        Dynamically extracts and caches Binance LOT_SIZE and MIN_NOTIONAL filters
        for any symbol (BTC, ETH, SOL, SUI, INJ, RENDER, LINK, AVAX, TIA, FET, NEAR, etc.).
        """
        now = time.time()
        if not hasattr(self, "_filters_cache"):
            self._filters_cache = {}
            self._filters_cache_time = 0.0

        sym_upper = symbol.upper()
        if sym_upper in self._filters_cache and (now - self._filters_cache_time) < 3600.0:
            return self._filters_cache[sym_upper]

        try:
            ex_info = await self.get_exchange_info(mode)
            symbols_list = ex_info.get("symbols", [])
            for s in symbols_list:
                s_name = s.get("symbol", "").upper()
                step_size = 0.0001
                min_qty = 0.0001
                min_notional = 5.0
                prec = 4

                price_prec = 4
                tick_size = 0.0001

                for f in s.get("filters", []):
                    f_type = f.get("filterType", "")
                    if f_type == "LOT_SIZE":
                        step_size = float(f.get("stepSize", 0.0001))
                        min_qty = float(f.get("minQty", step_size))
                        step_str = str(f.get("stepSize", "0.0001")).rstrip("0")
                        if "." in step_str:
                            prec = len(step_str.split(".")[1])
                        else:
                            prec = 0
                    elif f_type == "PRICE_FILTER":
                        tick_size = float(f.get("tickSize", 0.0001))
                        tick_str = str(f.get("tickSize", "0.0001")).rstrip("0")
                        if "." in tick_str:
                            price_prec = len(tick_str.split(".")[1])
                        else:
                            price_prec = 0
                    elif f_type in ["NOTIONAL", "MIN_NOTIONAL"]:
                        min_notional = float(f.get("minNotional", f.get("notional", 5.0)))

                self._filters_cache[s_name] = {
                    "step_size": step_size,
                    "min_qty": min_qty,
                    "min_notional": min_notional,
                    "precision": prec,
                    "price_precision": price_prec,
                    "tick_size": tick_size
                }
            self._filters_cache_time = now
            if sym_upper in self._filters_cache:
                return self._filters_cache[sym_upper]
        except Exception as e:
            logger.error(f"Error fetching exchangeInfo filters for {symbol}: {e}")

        # Fallback precision map for common dynamic universe pairs
        fallback_precisions = {
            "BTCUSDT": 5, "ETHUSDT": 4, "BNBUSDT": 3, "SOLUSDT": 2,
            "SUIUSDT": 1, "INJUSDT": 2, "RENDERUSDT": 1, "LINKUSDT": 2,
            "AVAXUSDT": 2, "TIAUSDT": 1, "FETUSDT": 1, "NEARUSDT": 1,
            "DOGEUSDT": 0, "PEPEUSDT": 0, "ADAUSDT": 1, "DOTUSDT": 2,
            "MATICUSDT": 1, "LTCUSDT": 3
        }
        p = fallback_precisions.get(sym_upper, 2)
        res = {
            "step_size": 10 ** (-p) if p > 0 else 1.0,
            "min_qty": 10 ** (-p) if p > 0 else 1.0,
            "min_notional": 5.0,
            "precision": p
        }
        self._filters_cache[sym_upper] = res
        return res

    def format_quantity_for_symbol(self, symbol: str, quantity: float, precision: int = 4) -> float:
        """Formats quantity to conform strictly to Binance LOT_SIZE stepSize without rounding up."""
        import math
        factor = 10 ** precision
        return math.floor(quantity * factor) / factor

    async def get_free_asset_balance(self, asset: str, mode: str = "LIVE") -> float:
        """Queries /api/v3/account to extract the exact live free balance of a specific base asset."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            mock_acc = await self._mock_request("GET", "/api/v3/account", mode_upper, {})
            for b in mock_acc.get("balances", []):
                if b.get("asset", "").upper() == asset.upper():
                    return float(b.get("free", 0.0))
            return 0.0

        res = await self.get_account_info(mode_upper)
        if isinstance(res, dict) and "balances" in res:
            asset_upper = asset.upper()
            for b in res.get("balances", []):
                if b.get("asset", "").upper() == asset_upper:
                    return float(b.get("free", 0.0))
        return 0.0

    async def get_dust_convertible_assets(self, mode: str = "LIVE") -> Dict[str, Any]:
        """Queries /sapi/v1/asset/dust-btc to list all assets eligible for small balance conversion to BNB."""
        return await self._request("POST", "/sapi/v1/asset/dust-btc", mode, signed=True, venue="SPOT")

    async def convert_dust_to_bnb(self, assets: List[str], mode: str = "LIVE") -> Dict[str, Any]:
        """
        Executes official Binance Dust Conversion /sapi/v1/asset/dust to convert
        specified small asset balances directly into BNB.
        """
        if not assets:
            return {"error": "NO_ASSETS_PROVIDED", "msg": "No assets specified for dust conversion."}

        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return await self._mock_request("POST", "/sapi/v1/asset/dust", mode_upper, {"asset": assets})

        params = {"asset": ",".join(a.upper() for a in assets)}
        return await self._request("POST", "/sapi/v1/asset/dust", mode_upper, params=params, signed=True, venue="SPOT")

    async def redeem_flexible_earn(self, asset: str, mode: str = "LIVE") -> Dict[str, Any]:
        """Redeems soft-staking / simple earn flexible assets back to Spot using valid productId."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {"success": True, "redeemed": asset}
        clean_asset = asset.upper().replace("USDT", "")
        product_id = f"{clean_asset}001"
        try:
            pos_info = await self._request("GET", "/sapi/v1/simple-earn/flexible/position", mode_upper, params={"asset": clean_asset}, signed=True)
            if isinstance(pos_info, dict) and "rows" in pos_info and len(pos_info["rows"]) > 0:
                product_id = pos_info["rows"][0].get("productId", product_id)
        except Exception as e:
            logger.debug(f"Failed to lookup flexible productId for {clean_asset}: {e}")

        params = {"productId": product_id, "destAccount": "SPOT", "redeemAll": "true"}
        return await self._request("POST", "/sapi/v1/simple-earn/flexible/redeem", mode_upper, params=params, signed=True)

    async def convert_asset_to_usdt(self, from_asset: str, amount: float, mode: str = "LIVE") -> Dict[str, Any]:
        """Converts any asset balance directly to USDT via official Binance Convert API (no MIN_NOTIONAL barrier)."""
        mode_upper = (mode or "PAPER").upper()
        clean = from_asset.upper().replace("USDT", "")
        if mode_upper == "PAPER":
            return {"status": "SUCCESS", "fromAsset": clean, "toAsset": "USDT", "fromAmount": amount}
        quote = await self._request("POST", "/sapi/v1/convert/getQuote", mode_upper, params={
            "fromAsset": clean,
            "toAsset": "USDT",
            "fromAmount": str(amount)
        }, signed=True)
        if isinstance(quote, dict) and "quoteId" in quote:
            qid = quote["quoteId"]
            return await self._request("POST", "/sapi/v1/convert/acceptQuote", mode_upper, params={"quoteId": qid}, signed=True)
        return quote

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
        quote_order_qty: Optional[float] = None,
        time_in_force: str = "GTC"
    ) -> Dict[str, Any]:
        """Creates an order (LIMIT or MARKET). Supports both base asset quantity, quoteOrderQty, and Post-Only Maker (GTX)."""
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

        if order_type.upper() in ["LIMIT", "LIMIT_MAKER"]:
            if price is None:
                raise ValueError("Price is required for LIMIT orders")
            if isinstance(price, float):
                sym_filters = getattr(self, "_filters_cache", {}).get(symbol.upper(), {})
                p_prec = sym_filters.get("price_precision", 4)
                params["price"] = f"{price:.{p_prec}f}"
            else:
                params["price"] = str(price)

            # Binance Spot API compatibility:
            # On Binance Spot (/api/v3/order), Post-Only Maker orders require type="LIMIT_MAKER" with NO timeInForce parameter.
            # Normal LIMIT orders accept timeInForce: GTC, IOC, FOK (GTX is invalid on Spot).
            if order_type.upper() == "LIMIT_MAKER" or str(time_in_force).upper() == "GTX":
                params["type"] = "LIMIT_MAKER"
                params.pop("timeInForce", None)
            elif order_type.upper() == "LIMIT":
                params["timeInForce"] = time_in_force if time_in_force in ["GTC", "IOC", "FOK"] else "GTC"

        return await self._request("POST", "/api/v3/order", mode, params=params, signed=True)

    async def cancel_order(self, mode: str, symbol: str, order_id: Any) -> Dict[str, Any]:
        """Cancels an existing order."""
        params = {
            "symbol": symbol.upper(),
            "orderId": str(order_id)
        }
        return await self._request("DELETE", "/api/v3/order", mode, params=params, signed=True)

    async def get_order(self, mode: str, symbol: str, order_id: Any) -> Dict[str, Any]:
        """Queries status of an existing order on Binance (/api/v3/order)."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {"status": "FILLED", "orderId": str(order_id), "symbol": symbol.upper()}
        params = {
            "symbol": symbol.upper(),
            "orderId": str(order_id)
        }
        return await self._request("GET", "/api/v3/order", mode_upper, params=params, signed=True)

    # ==========================================
    # BINANCE USDⓈ-M FUTURES TRADING ENGINE (FAPI)
    # ==========================================

    async def get_futures_account_info(self, mode: str = "LIVE") -> Dict[str, Any]:
        """Queries Binance USD(S)-M Futures /fapi/v2/account for real-time equity, margin, and position risk."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {
                "totalWalletBalance": "10000.00",
                "availableBalance": "10000.00",
                "totalUnrealizedProfit": "0.00",
                "totalMarginBalance": "10000.00",
                "positions": []
            }
        return await self._request("GET", "/fapi/v2/account", mode_upper, venue="FUTURES", signed=True)

    async def get_futures_balance(self, mode: str = "LIVE") -> List[Dict[str, Any]]:
        """Queries Binance USD(S)-M Futures /fapi/v2/balance for asset balances."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return [{"asset": "USDT", "balance": "10000.00", "availableBalance": "10000.00"}]
        res = await self._request("GET", "/fapi/v2/balance", mode_upper, venue="FUTURES", signed=True)
        return res if isinstance(res, list) else []

    async def get_futures_position_risk(self, symbol: Optional[str] = None, mode: str = "LIVE") -> List[Dict[str, Any]]:
        """Queries /fapi/v2/positionRisk to inspect active long/short contract positions, mark prices, and unrealized PnL."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return []
        params = {"symbol": symbol.upper()} if symbol else {}
        res = await self._request("GET", "/fapi/v2/positionRisk", mode_upper, params=params, venue="FUTURES", signed=True)
        return res if isinstance(res, list) else []

    async def set_futures_leverage(self, symbol: str, leverage: int = 2, mode: str = "LIVE") -> Dict[str, Any]:
        """Sets leverage on Binance USD(S)-M Futures: POST /fapi/v1/leverage (Safely capped at 2x-3x)."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {"symbol": symbol.upper(), "leverage": leverage, "maxNotionalValue": "1000000"}
        params = {
            "symbol": symbol.upper(),
            "leverage": int(leverage)
        }
        res = await self._request("POST", "/fapi/v1/leverage", mode_upper, params=params, venue="FUTURES", signed=True)
        logger.info(f"⚡ [FUTURES LEVERAGE SET] {symbol} leverage configured to {leverage}x on Binance Futures ({mode_upper}).")
        return res

    async def set_futures_margin_type(self, symbol: str, margin_type: str = "ISOLATED", mode: str = "LIVE") -> Dict[str, Any]:
        """Configures margin type to strictly ISOLATED (POST /fapi/v1/marginType) to protect entire wallet from contagion."""
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {"code": 200, "msg": "success"}
        params = {
            "symbol": symbol.upper(),
            "marginType": margin_type.upper()
        }
        res = await self._request("POST", "/fapi/v1/marginType", mode_upper, params=params, venue="FUTURES", signed=True)
        if isinstance(res, dict):
            # Error -4046 means 'No need to change margin type', confirming it is already at the target margin type
            if res.get("code") == -4046:
                return {"code": 200, "msg": f"Already {margin_type.upper()} margin"}
            # Error -4048 means 'Margin type cannot be changed if there exists position.'
            if res.get("code") == -4048:
                logger.info(f"ℹ️ [FUTURES MARGIN] Cannot change margin type for {symbol}: an open position already exists on Binance Futures.")
                return res
            if res.get("code", 200) not in [200, 0] or "error" in res:
                logger.warning(f"⚠️ [FUTURES MARGIN NOTICE] Could not set margin type for {symbol}: {res}")
                return res

        logger.info(f"🛡️ [FUTURES MARGIN CONFIGURED] {symbol} set to {margin_type.upper()} margin on Binance Futures.")
        return res

    async def get_futures_symbol_filters(self, symbol: str, mode: str = "LIVE") -> Dict[str, Any]:
        """Dynamically caches LOT_SIZE and PRICE_FILTER for symbols on Binance USD(S)-M Futures (/fapi/v1/exchangeInfo)."""
        now = time.time()
        if not hasattr(self, "_futures_filters_cache"):
            self._futures_filters_cache = {}
            self._futures_filters_cache_time = 0.0

        sym_upper = symbol.upper()
        if sym_upper in self._futures_filters_cache and (now - self._futures_filters_cache_time) < 3600.0:
            return self._futures_filters_cache[sym_upper]

        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            res = {"step_size": 0.001, "min_qty": 0.001, "precision": 3, "price_precision": 2, "tick_size": 0.01, "min_notional": 5.0}
            self._futures_filters_cache[sym_upper] = res
            return res

        try:
            ex_info = await self._request("GET", "/fapi/v1/exchangeInfo", mode_upper, venue="FUTURES", signed=False)
            symbols_list = ex_info.get("symbols", []) if isinstance(ex_info, dict) else []
            for s in symbols_list:
                s_name = s.get("symbol", "").upper()
                step_size = 0.001
                min_qty = 0.001
                min_notional = 5.0
                prec = int(s.get("quantityPrecision", 3))
                price_prec = int(s.get("pricePrecision", 2))
                tick_size = 0.01

                for f in s.get("filters", []):
                    f_type = f.get("filterType")
                    if f_type == "LOT_SIZE":
                        step_size = float(f.get("stepSize", step_size))
                        min_qty = float(f.get("minQty", min_qty))
                    elif f_type == "PRICE_FILTER":
                        tick_size = float(f.get("tickSize", tick_size))
                    elif f_type in ["MIN_NOTIONAL", "NOTIONAL"]:
                        min_notional = float(f.get("notional", 5.0))

                self._futures_filters_cache[s_name] = {
                    "step_size": step_size,
                    "min_qty": min_qty,
                    "min_notional": min_notional,
                    "precision": prec,
                    "price_precision": price_prec,
                    "tick_size": tick_size
                }
            self._futures_filters_cache_time = now
            if sym_upper in self._futures_filters_cache:
                return self._futures_filters_cache[sym_upper]
        except Exception as e:
            logger.error(f"Error querying Binance Futures exchangeInfo for {symbol}: {e}")

        # Fallback precision
        res = {"step_size": 0.001, "min_qty": 0.001, "precision": 3, "price_precision": 2, "tick_size": 0.01, "min_notional": 5.0}
        self._futures_filters_cache[sym_upper] = res
        return res

    async def create_futures_order(
        self,
        mode: str,
        symbol: str,
        side: str,
        order_type: str,
        quantity: float,
        price: Optional[float] = None,
        time_in_force: str = "GTC",
        reduce_only: bool = False
    ) -> Dict[str, Any]:
        """
        Submits an order to Binance USD(S)-M Futures (/fapi/v1/order).
        Supports:
        - Longs: side="BUY"
        - Shorts: side="SELL"
        - Exits: reduce_only=True (guarantees exit without accidentally reversing position)
        - Maker Post-Only: timeInForce="GTX"
        """
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {
                "symbol": symbol.upper(),
                "orderId": int(time.time() * 1000),
                "clientOrderId": f"paper_f_{int(time.time())}",
                "status": "FILLED",
                "side": side.upper(),
                "price": str(price or 0.0),
                "origQty": str(quantity),
                "executedQty": str(quantity)
            }

        sym_filters = await self.get_futures_symbol_filters(symbol, mode_upper)
        qty_prec = sym_filters.get("precision", 3)
        price_prec = sym_filters.get("price_precision", 2)

        params: Dict[str, Any] = {
            "symbol": symbol.upper(),
            "side": side.upper(),
            "type": order_type.upper(),
            "quantity": f"{quantity:.{qty_prec}f}"
        }

        if reduce_only:
            params["reduceOnly"] = "true"

        if order_type.upper() in ["LIMIT", "LIMIT_MAKER"]:
            if price is None:
                raise ValueError("Price is mandatory for Futures LIMIT orders")
            params["type"] = "LIMIT"
            params["price"] = f"{price:.{price_prec}f}"
            params["timeInForce"] = "GTX" if time_in_force.upper() == "GTX" else "GTC"

        logger.info(f"🚀 [BINANCE FUTURES DISPATCH] Sending {params['side']} order for {params['quantity']} {symbol} (Type: {params['type']}, ReduceOnly: {reduce_only}) to Binance Futures...")
        return await self._request("POST", "/fapi/v1/order", mode_upper, params=params, venue="FUTURES", signed=True)

    async def cancel_futures_order(self, mode: str, symbol: str, order_id: Any) -> Dict[str, Any]:
        """Cancels an existing order on Binance USD(S)-M Futures."""
        params = {"symbol": symbol.upper(), "orderId": str(order_id)}
        return await self._request("DELETE", "/fapi/v1/order", mode, params=params, venue="FUTURES", signed=True)

    async def get_futures_order(self, mode: str, symbol: str, order_id: Any) -> Dict[str, Any]:
        """Queries status of an existing order on Binance USD(S)-M Futures."""
        params = {"symbol": symbol.upper(), "orderId": str(order_id)}
        return await self._request("GET", "/fapi/v1/order", mode, params=params, venue="FUTURES", signed=True)

    async def get_futures_equity_status(self, mode: str = "LIVE") -> Dict[str, Any]:
        """Queries /fapi/v2/account to extract available USDT and total wallet equity on Binance Futures."""
        res = await self.get_futures_account_info(mode)
        if not isinstance(res, dict) or "availableBalance" not in res:
            err_msg = res.get("msg") if isinstance(res, dict) else str(res)
            return {
                "error": err_msg or "Failed to query Binance Futures account",
                "available_usdt": 0.0,
                "wallet_balance_usdt": 0.0,
                "unrealized_pnl": 0.0,
                "margin_balance_usdt": 0.0,
                "status_message": f"Futures account query error: {err_msg}"
            }
        avail_usdt = float(res.get("availableBalance", 0.0))
        wallet_balance = float(res.get("totalWalletBalance", 0.0))
        unrealized = float(res.get("totalUnrealizedProfit", 0.0))
        margin_balance = float(res.get("totalMarginBalance", 0.0))
        return {
            "error": None,
            "available_usdt": avail_usdt,
            "wallet_balance_usdt": wallet_balance,
            "unrealized_pnl": unrealized,
            "margin_balance_usdt": margin_balance,
            "status_message": f"Verified Binance Futures equity: ${avail_usdt:.2f} USDT available (Total Margin: ${margin_balance:.2f} USDT)."
        }

    async def ensure_bnb_balance_for_fees(
        self,
        min_bnb_value_usdt: float = 0.50,
        replenish_amount_usdt: float = 1.00,
        mode: str = "LIVE"
    ) -> Dict[str, Any]:
        """
        Upgrade 3: Auto-Maintain ~$1.00 in BNB (Lock in 25% Binance Fee Discount).
        Checks if free BNB balance is below min_bnb_value_usdt.
        If so, and free USDT >= replenish_amount_usdt + 5.00, converts replenish_amount_usdt
        directly to BNB via zero-fee Binance Convert API (/sapi/v1/convert).
        """
        mode_upper = (mode or "PAPER").upper()
        if mode_upper == "PAPER":
            return {"status": "SKIPPED_PAPER", "msg": "BNB fee maintenance skipped in paper mode."}
        try:
            free_bnb = await self.get_free_asset_balance("BNB", mode_upper)
            free_usdt = await self.get_free_asset_balance("USDT", mode_upper)
            
            from app.workers.price_cache import PriceCache
            cache = await PriceCache.get_instance()
            bnb_price = cache.get_price("BNBUSDT") or 600.0
            
            bnb_val_usdt = free_bnb * bnb_price
            if bnb_val_usdt < min_bnb_value_usdt:
                if free_usdt >= (replenish_amount_usdt + 5.00):
                    logger.info(f"💎 [BNB FEE REBATE ENGINE] BNB value (${bnb_val_usdt:.2f}) < threshold (${min_bnb_value_usdt:.2f}). Converting {replenish_amount_usdt} USDT to BNB to guarantee 25% fee discount...")
                    quote = await self._request("POST", "/sapi/v1/convert/getQuote", mode_upper, params={
                        "fromAsset": "USDT",
                        "toAsset": "BNB",
                        "fromAmount": str(replenish_amount_usdt)
                    }, signed=True)
                    if isinstance(quote, dict) and "quoteId" in quote:
                        res = await self._request("POST", "/sapi/v1/convert/acceptQuote", mode_upper, params={"quoteId": quote["quoteId"]}, signed=True)
                        logger.info(f"✅ [BNB FEE REBATE ENGINE] Successfully replenished BNB balance: {res}")
                        return {"status": "SUCCESS", "converted_usdt": replenish_amount_usdt, "result": res}
                    else:
                        logger.warning(f"Could not get quote for USDT->BNB convert: {quote}")
                        return {"status": "QUOTE_FAILED", "quote": quote}
                else:
                    return {"status": "INSUFFICIENT_USDT_BUFFER", "free_usdt": free_usdt, "msg": "Free USDT buffer too low to deduct 1.00 USDT without impacting $5 min order size."}
            return {"status": "SUFFICIENT_BNB", "bnb_val_usdt": bnb_val_usdt}
        except Exception as e:
            logger.error(f"Error in ensure_bnb_balance_for_fees: {e}")
            return {"status": "ERROR", "error": str(e)}

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
            elif method.upper() == "GET":
                return {
                    "symbol": params.get("symbol", "BTCUSDT"),
                    "orderId": params.get("orderId", "481516234"),
                    "price": "91500.00",
                    "origQty": "0.01",
                    "executedQty": "0.01",
                    "cummulativeQuoteQty": "915.00",
                    "status": "FILLED",
                    "type": "LIMIT",
                    "side": "BUY",
                    "fills": [{"price": "91500.00", "qty": "0.01", "commission": "0.000005", "commissionAsset": "BNB"}]
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

        elif path == "/sapi/v1/asset/dust-btc":
            return {
                "details": [
                    {"asset": "LINK", "assetFullName": "Chainlink", "amountFree": "0.00045000", "toBTC": "0.00000007", "toBNB": "0.00001050"},
                    {"asset": "SOL", "assetFullName": "Solana", "amountFree": "0.00020000", "toBTC": "0.00000004", "toBNB": "0.00000620"},
                    {"asset": "SUI", "assetFullName": "Sui", "amountFree": "0.01500000", "toBTC": "0.00000003", "toBNB": "0.00000450"}
                ],
                "totalTransferBtc": "0.00000014",
                "totalTransferBNB": "0.00002120",
                "dribbletPercentage": "0.02"
            }

        elif path == "/sapi/v1/asset/dust":
            raw_assets = params.get("asset", ["LINK", "SOL"])
            if isinstance(raw_assets, str):
                assets_conv = [a.strip() for a in raw_assets.split(",") if a.strip()]
            else:
                assets_conv = list(raw_assets)
            now_ms = int(time.time() * 1000)
            return {
                "totalServiceCharge": "0.00000042",
                "totalTransfered": "0.00002078",
                "transferResult": [
                    {
                        "amount": "0.00045000",
                        "fromAsset": a,
                        "operateTime": now_ms,
                        "serviceChargeAmount": "0.00000021",
                        "tranId": 4815162340 + i,
                        "transferedAmount": "0.00001029"
                    } for i, a in enumerate(assets_conv)
                ]
            }

        return {"error": "MOCK_UNSUPPORTED", "msg": f"Mock endpoint not defined for {path}"}
