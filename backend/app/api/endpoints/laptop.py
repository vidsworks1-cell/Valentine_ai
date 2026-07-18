import time
from typing import List
from fastapi import APIRouter, HTTPException, status
from app.api.schemas import (
    LaptopDocumentResponse, 
    DataExchangePayload, 
    SyncResponse
)

router = APIRouter()

# In-memory database of documents
DOCUMENTS_DB = [
    {
        "id": "qta-001",
        "title": "Quantitative Trading Alpha Research.pdf",
        "content": "Abstract: This document details our new Mean-Reversion strategy utilizing GARCH models. Historical backtest indicates a Sharpe ratio of 2.14 over the past 4 years.",
        "category": "RESEARCH",
        "sizeBytes": 2048500,
        "lastModified": int(time.time() * 1000) - 86400000
    },
    {
        "id": "ytb-002",
        "title": "YouTube Growth Blueprint & Content Ideas.docx",
        "content": "Draft Ideas for multi-agent systems overview video. Keywords: Jetpack Compose, autonomous agents, kotlin backend. Video duration target: 12 minutes.",
        "category": "CONTENT",
        "sizeBytes": 452000,
        "lastModified": int(time.time() * 1000) - 14400000
    },
    {
        "id": "fps-003",
        "title": "Freelance Project Spec - API Integration.md",
        "content": "Technical specifications for the client API gateway. High throughput Ktor engine, JWT token authentication, rate limit 500 requests/min.",
        "category": "PROJECTS",
        "sizeBytes": 12400,
        "lastModified": int(time.time() * 1000)
    },
    {
        "id": "lrm-004",
        "title": "Lumina System Risk Management Policy.md",
        "content": "Official risk assessment guidelines for Lumina. Max Drawdown Limit: 10%. Position Sizing Rule: strictly 1.5% max risk per trade. Leverages above 10x are automatically blocked by the local Risk Engine.",
        "category": "POLICY",
        "sizeBytes": 18200,
        "lastModified": int(time.time() * 1000) - 172800000
    }
]

# Simple in-memory sync log
EXCHANGE_HISTORY = []

@router.get("/ping")
async def ping_laptop():
    """Verify connectivity between the mobile client and the local laptop agent."""
    return {"message": "pong", "status": "stable", "timestamp": int(time.time() * 1000)}

@router.get("/documents", response_model=List[LaptopDocumentResponse])
async def get_documents():
    """Retrieve secure local knowledge packages synchronized from Laptop Agent."""
    return DOCUMENTS_DB

@router.get("/documents/{doc_id}", response_model=LaptopDocumentResponse)
async def get_document_by_id(doc_id: str):
    """Fetch detailed content structure of a specific knowledge package document."""
    for doc in DOCUMENTS_DB:
        if doc["id"] == doc_id:
            return doc
    raise HTTPException(
        status_code=status.HTTP_404_NOT_FOUND,
        detail=f"Document with ID {doc_id} not found"
    )

@router.post("/exchange", response_model=SyncResponse)
async def exchange_data(payload: DataExchangePayload):
    """Accept and synchronize mobile telemetry metrics with the laptop host."""
    records_synced = len(payload.dataPayload)
    response_data = {
        "status": "SUCCESS",
        "timestamp": int(time.time() * 1000),
        "recordsSynced": records_synced,
        "deviceName": f"Laptop-Host-Agent (PID {payload.deviceId})"
    }
    EXCHANGE_HISTORY.append(response_data)
    return response_data

@router.get("/sync-status", response_model=SyncResponse)
async def get_sync_status():
    """Retrieve the overall sync and connection statistics of the laptop synchronizer."""
    if EXCHANGE_HISTORY:
        return EXCHANGE_HISTORY[-1]
    return {
        "status": "STANDBY",
        "timestamp": int(time.time() * 1000),
        "recordsSynced": 0,
        "deviceName": "Laptop-Host-Agent"
    }
