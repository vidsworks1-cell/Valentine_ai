import asyncio
import logging
import json
import os
import urllib.request
import urllib.error
from typing import Dict, Any, Optional
from datetime import datetime, timezone
from app.core.config import settings

logger = logging.getLogger("lumina.trading.telegram")

class TelegramNotifier:
    """
    Real-Time Telegram Telemetry & Runner Alert Dispatcher for Lumina.
    
    Operates an asynchronous non-blocking worker queue:
    - Never blocks high-frequency trade evaluation or order placement.
    - Sends rich HTML alerts for:
      1. 🚀 Breakout & Squeeze Cascade Detections
      2. ⚡ Live Order ACK & Fill Notifications
      3. 🛡️ Auto-Breakeven Ratchets (+0.35% Fee & Profit Lock)
      4. 🏃 Uncapped Runner TP Extensions & Chandelier Trailing Stops
      5. 🏁 Position Closures with Net Realized PnL & Duration
      6. ⚠️ Circuit Breakers & Systemic Contagion Alerts
    - Fallback: Previews clean message in logs and registers with ProductionMonitoringEngine if token is not yet configured.
    """
    _instance: Optional['TelegramNotifier'] = None

    def __init__(self):
        self._queue: asyncio.Queue = asyncio.Queue()
        self._worker_task: Optional[asyncio.Task] = None
        self._is_running = False
        self._sent_alerts_count = 0
        self._last_sent_at: Optional[str] = None

    @classmethod
    def get_instance(cls) -> 'TelegramNotifier':
        if cls._instance is None:
            cls._instance = TelegramNotifier()
            cls._instance._start_worker_if_needed()
        return cls._instance

    def _start_worker_if_needed(self):
        if not self._is_running:
            try:
                loop = asyncio.get_running_loop()
                self._is_running = True
                self._worker_task = loop.create_task(self._worker_loop())
            except RuntimeError:
                # No running event loop yet (e.g. during sync imports)
                pass

    def _ensure_worker_running(self):
        if not self._is_running or (self._worker_task and self._worker_task.done()):
            try:
                loop = asyncio.get_running_loop()
                self._is_running = True
                self._worker_task = loop.create_task(self._worker_loop())
            except RuntimeError:
                pass

    async def _worker_loop(self):
        """Background worker consuming messages and dispatching them asynchronously."""
        while self._is_running:
            try:
                msg_payload = await self._queue.get()
                text = msg_payload.get("text", "")
                category = msg_payload.get("category", "GENERAL")
                title = msg_payload.get("title", "Lumina Alert")

                token = getattr(settings, "TELEGRAM_BOT_TOKEN", "") or os.getenv("TELEGRAM_BOT_TOKEN", "")
                chat_id = getattr(settings, "TELEGRAM_CHAT_ID", "") or os.getenv("TELEGRAM_CHAT_ID", "")
                enabled = getattr(settings, "TELEGRAM_ENABLED", True)

                if token and chat_id and enabled:
                    await self._send_http_request(token, chat_id, text)
                else:
                    # Clean console preview & production monitoring registration
                    clean_preview = text.replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")
                    logger.info(f"📱 [TELEGRAM ALERT PREVIEW]\n{clean_preview}")

                # Register in ProductionMonitoringEngine
                try:
                    from app.trading.production_monitoring import ProductionMonitoringEngine
                    clean_short = text.replace("<b>", "").replace("</b>", "").replace("<i>", "").replace("</i>", "")[:120]
                    await ProductionMonitoringEngine.get_instance().dispatch_alert(
                        level="INFO" if category != "RISK" else "WARNING",
                        title=title,
                        message=clean_short
                    )
                except Exception:
                    pass

                self._sent_alerts_count += 1
                self._last_sent_at = datetime.now(timezone.utc).isoformat()
                self._queue.task_done()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in Telegram worker loop: {e}")
                await asyncio.sleep(1.0)

    async def _send_http_request(self, token: str, chat_id: str, html_text: str):
        """Performs non-blocking HTTP post to Telegram API."""
        url = f"https://api.telegram.org/bot{token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": html_text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True
        }
        data = json.dumps(payload).encode("utf-8")

        def _sync_post():
            req = urllib.request.Request(
                url,
                data=data,
                headers={"Content-Type": "application/json", "User-Agent": "LuminaTradingEngine/1.0"}
            )
            try:
                with urllib.request.urlopen(req, timeout=5) as resp:
                    return resp.read()
            except urllib.error.HTTPError as he:
                logger.warning(f"Telegram API HTTP error {he.code}: {he.reason}")
            except Exception as ex:
                logger.warning(f"Telegram connection failed: {ex}")

        try:
            await asyncio.to_thread(_sync_post)
        except Exception as e:
            logger.error(f"Failed to post alert to Telegram: {e}")

    def enqueue_message(self, text: str, category: str = "GENERAL", title: str = "Lumina Alert"):
        """Non-blocking submission of an alert to the background queue."""
        self._ensure_worker_running()
        try:
            self._queue.put_nowait({"text": text, "category": category, "title": title})
        except Exception as e:
            logger.error(f"Failed to enqueue Telegram message: {e}")

    # ==================== High-Utility Trade Event Dispatchers ====================

    def send_breakout_alert(
        self,
        symbol: str,
        side: str,
        price: float,
        squeeze_ratio: float,
        rvol: float,
        is_veb: bool = True,
        liquidation_skew: Optional[str] = None
    ):
        """Fires when a Volatility Expansion Breakout or Liquidation Squeeze is detected."""
        headline = "🚀 <b>VOLATILITY EXPANSION BREAKOUT</b>" if is_veb else "⚡ <b>BREAKOUT CASCADE DETECTED</b>"
        text = (
            f"{headline}\n\n"
            f"• <b>Symbol:</b> <code>{symbol}</code> ({side.upper()})\n"
            f"• <b>Trigger Price:</b> ${price:,.4f}\n"
            f"• <b>Empirical Squeeze:</b> <code>{squeeze_ratio:.3f}</code> (BB vs Keltner)\n"
            f"• <b>Relative Volume (RVOL):</b> <code>{rvol:.2f}x</code> Velocity Surge\n"
        )
        if liquidation_skew:
            text += f"• <b>Counterparty Flow:</b> {liquidation_skew}\n"
        text += f"• <b>Timestamp:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"

        self.enqueue_message(text, category="BREAKOUT", title=f"Breakout Alert: {symbol}")

    def send_order_filled(
        self,
        symbol: str,
        side: str,
        qty: float,
        price: float,
        stop_loss: float,
        tp1: float,
        strategy: str,
        trading_mode: str,
        routing: str = "MARKET"
    ):
        """Fires when an order ACK is received and filled."""
        risk_pct = abs(price - stop_loss) / price * 100.0 if price > 0 else 0.0
        text = (
            f"🟢 <b>ORDER EXECUTED ({trading_mode})</b>\n\n"
            f"• <b>Symbol:</b> <code>{symbol}</code> | {side.upper()}\n"
            f"• <b>Entry Price:</b> ${price:,.4f}\n"
            f"• <b>Quantity:</b> {qty}\n"
            f"• <b>Routing:</b> <code>{routing}</code>\n"
            f"• <b>Stop-Loss:</b> ${stop_loss:,.4f} (-{risk_pct:.2f}%)\n"
            f"• <b>Take-Profit 1:</b> ${tp1:,.4f}\n"
            f"• <b>Strategy:</b> <i>{strategy}</i>\n"
            f"• <b>Time:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
        )
        self.enqueue_message(text, category="EXECUTION", title=f"Order Filled: {symbol}")

    def send_tp_ratchet(
        self,
        symbol: str,
        current_price: float,
        be_level: float,
        locked_profit_pct: float,
        max_gain_pct: float
    ):
        """Fires when Auto-Breakeven Ratchet locks in net profit."""
        text = (
            f"🛡️ <b>BREAKEVEN PROFIT LOCK ACTIVE</b>\n\n"
            f"• <b>Symbol:</b> <code>{symbol}</code>\n"
            f"• <b>Peak Gain:</b> +{max_gain_pct:.2f}%\n"
            f"• <b>New Stop-Loss:</b> ${be_level:,.4f}\n"
            f"• <b>Locked Net Profit:</b> +{locked_profit_pct:.2f}% (Exchange Fees 100% Covered)\n"
            f"• <b>Status:</b> Free-Roll Active (Zero Downside Risk)\n"
            f"• <b>Time:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
        )
        self.enqueue_message(text, category="MANAGEMENT", title=f"Breakeven Ratchet: {symbol}")

    def send_chandelier_ratchet(
        self,
        symbol: str,
        side: str,
        highest_or_lowest: float,
        new_sl: float,
        locked_r: float,
        peak_r: float,
        regime: str,
        reason: str
    ):
        """Fires when Adaptive Volatility Chandelier Engine ratchets the trailing stop."""
        text = (
            f"⚡ <b>CHANDELIER VOLATILITY RATCHET</b>\n\n"
            f"• <b>Symbol:</b> <code>{symbol}</code> ({side.upper()})\n"
            f"• <b>Peak Progress:</b> <b>+{peak_r:.2f}R</b> (Watermark: ${highest_or_lowest:,.4f})\n"
            f"• <b>Ratcheted Stop-Loss:</b> ${new_sl:,.4f}\n"
            f"• <b>Locked Equity:</b> <b>+{locked_r:.2f}R Locked</b>\n"
            f"• <b>Volatility Regime:</b> <code>{regime}</code>\n"
            f"• <b>Rationale:</b> <i>{reason}</i>\n"
            f"• <b>Time:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
        )
        self.enqueue_message(text, category="CHANDELIER", title=f"Chandelier Ratchet: {symbol} (+{locked_r:.2f}R)")

    def send_runner_extension(
        self,
        symbol: str,
        stage: int,
        new_tp: float,
        trailing_stop: float,
        current_gain_pct: float
    ):
        """Fires when an uncapped runner extends its take profit target."""
        text = (
            f"🏃 <b>RUNNER EXPANSION (STAGE {stage})</b>\n\n"
            f"• <b>Symbol:</b> <code>{symbol}</code>\n"
            f"• <b>Unrealized Gain:</b> <b>+{current_gain_pct:.2f}%</b>\n"
            f"• <b>Extended Target:</b> ${new_tp:,.4f} (+1.6x ATR)\n"
            f"• <b>Chandelier Trailing SL:</b> ${trailing_stop:,.4f}\n"
            f"• <b>Momentum:</b> Accelerated Volatility Expansion\n"
            f"• <b>Time:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
        )
        self.enqueue_message(text, category="RUNNER", title=f"Runner Extension Stage {stage}: {symbol}")

    def send_trade_closed(
        self,
        symbol: str,
        side: str,
        entry_price: float,
        exit_price: float,
        pnl_usd: float,
        pnl_pct: float,
        duration_mins: float,
        outcome_type: str
    ):
        """Fires when a position is finalized and closed."""
        is_win = pnl_usd >= 0
        emoji = "🏆 <b>TRADE CLOSED (WIN)</b>" if is_win else "🛑 <b>TRADE CLOSED (LOSS)</b>"
        pnl_sign = "+" if is_win else ""
        text = (
            f"{emoji}\n\n"
            f"• <b>Symbol:</b> <code>{symbol}</code> ({side.upper()})\n"
            f"• <b>Realized PnL:</b> <b>{pnl_sign}${pnl_usd:,.2f} ({pnl_sign}{pnl_pct:.2f}%)</b>\n"
            f"• <b>Entry:</b> ${entry_price:,.4f} ➔ <b>Exit:</b> ${exit_price:,.4f}\n"
            f"• <b>Hold Duration:</b> {duration_mins:.1f} minutes\n"
            f"• <b>Exit Reason:</b> <code>{outcome_type}</code>\n"
            f"• <b>Time:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
        )
        self.enqueue_message(text, category="CLOSE", title=f"Trade Closed: {symbol} ({pnl_sign}${pnl_usd:.2f})")

    def send_risk_alert(self, title: str, details: str, severity: str = "WARNING"):
        """Fires on circuit breakers, systemic contagion, or rate limit throttle."""
        emoji = "🚨" if severity == "CRITICAL" else "⚠️"
        text = (
            f"{emoji} <b>RISK ALERT: {title}</b>\n\n"
            f"{details}\n\n"
            f"• <b>Timestamp:</b> {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
        )
        self.enqueue_message(text, category="RISK", title=f"Risk Alert: {title}")
