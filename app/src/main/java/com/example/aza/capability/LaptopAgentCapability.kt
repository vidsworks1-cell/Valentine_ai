package com.example.aza.capability

import com.example.data.api.DataExchangePayload
import com.example.data.api.LaptopAgentClient
import com.example.data.database.LaptopDocumentEntity
import com.example.data.database.LaptopExchangeRecord
import com.example.data.repository.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class LaptopAgentCapability : Capability {
    override val name = "Laptop Agent Capability"
    override val description = "Bidirectional local network interface for secure document retrieval, sync status orchestration, and unified file exchange with the Laptop Host agent."
    override val supportedActions = listOf("PING_HOST", "RETRIEVE_DOCUMENTS", "EXCHANGE_DATA", "GET_SYNC_STATUS", "DOWNLOAD_DOCUMENT")
    override val requiredPermissions = listOf(PermissionLevel.READ, PermissionLevel.WRITE, PermissionLevel.EXECUTE)

    override suspend fun executeAction(
        action: String,
        parameters: Map<String, Any>,
        missionId: Int?,
        repository: AppRepository,
        onThoughtLog: suspend (String) -> Unit
    ): CapabilityResult {
        onThoughtLog("🔄 LAPTOP AGENT CAPABILITY: Dispatching action '$action'")
        val state = repository.getCapabilityStateByNameDirect(name)
        val mode = state?.operatingMode ?: "MOCK"
        val isLive = mode == "LIVE"

        // Retrieve dynamic Base URL from secure API keys stored in capability state
        val rawBaseUrl = state?.apiKey ?: ""
        val baseUrl = if (rawBaseUrl.isNotBlank()) rawBaseUrl else "192.168.1.100:8080"

        onThoughtLog("🔌 LAPTOP AGENT: Operating mode is $mode. Target Base URL: '$baseUrl'")

        if (isLive) {
            if (rawBaseUrl.isBlank()) {
                val err = "API Key / Client ID is empty. In LIVE mode, please provide the Laptop Agent IP/URL (e.g. 192.168.1.100:8080) in the secure credential manager."
                onThoughtLog("🚨 LAPTOP AGENT: $err")
                return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
            }

            // Increment API usage metric
            if (state != null) {
                val nextCount = state.apiUsageCount + 1
                repository.updateCapabilityState(state.copy(
                    apiUsageCount = nextCount,
                    rateLimitStatus = "${(100 - (nextCount % 100)).coerceAtLeast(1)}/100"
                ))
            }
        }

        return when (action.uppercase()) {
            "PING_HOST" -> {
                if (isLive) {
                    try {
                        onThoughtLog("📡 LAPTOP AGENT: Sending HTTP GET /api/ping to $baseUrl...")
                        val service = LaptopAgentClient.buildService(baseUrl)
                        val response = service.ping()
                        onThoughtLog("✅ LAPTOP AGENT: Pong received from Host: $response")
                        CapabilityResult(
                            success = true,
                            outputData = mapOf("status" to "CONNECTED", "response" to response, "mode" to "LIVE")
                        )
                    } catch (e: Exception) {
                        val err = "Ping failed: ${e.localizedMessage}"
                        onThoughtLog("❌ LAPTOP AGENT: $err")
                        CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
                    }
                } else {
                    delay(300)
                    onThoughtLog("✅ LAPTOP AGENT: Mock Host pong. Connection state: STABLE.")
                    CapabilityResult(
                        success = true,
                        outputData = mapOf("status" to "CONNECTED", "response" to mapOf("message" to "mock_pong"), "mode" to "MOCK")
                    )
                }
            }

            "RETRIEVE_DOCUMENTS" -> {
                if (isLive) {
                    try {
                        onThoughtLog("📡 LAPTOP AGENT: Fetching documents from $baseUrl/api/documents...")
                        val service = LaptopAgentClient.buildService(baseUrl)
                        val apiDocs = service.getDocuments()
                        onThoughtLog("✅ LAPTOP AGENT: Retrieved ${apiDocs.size} documents from Host.")

                        val entities = apiDocs.map {
                            LaptopDocumentEntity(
                                id = it.id,
                                title = it.title,
                                content = it.content,
                                category = it.category,
                                sizeBytes = it.sizeBytes,
                                lastModified = it.lastModified,
                                retrievedAt = System.currentTimeMillis()
                            )
                        }

                        repository.insertLaptopDocuments(entities)

                        val exchangeRecord = LaptopExchangeRecord(
                            deviceName = "Laptop-Host-Agent",
                            direction = "PULL",
                            dataCount = apiDocs.size,
                            timestamp = System.currentTimeMillis(),
                            status = "SUCCESS",
                            message = "Retrieved ${apiDocs.size} documents from laptop agent REST API."
                        )
                        repository.insertLaptopExchangeRecord(exchangeRecord)

                        CapabilityResult(
                            success = true,
                            outputData = mapOf("retrieved_count" to apiDocs.size, "documents" to apiDocs, "mode" to "LIVE")
                        )
                    } catch (e: Exception) {
                        val err = "Document retrieval failed: ${e.localizedMessage}. Falling back to cached database documents."
                        onThoughtLog("⚠️ LAPTOP AGENT: $err")
                        
                        val exchangeRecord = LaptopExchangeRecord(
                            deviceName = "Laptop-Host-Agent",
                            direction = "PULL",
                            dataCount = 0,
                            timestamp = System.currentTimeMillis(),
                            status = "FAILED",
                            message = err
                        )
                        repository.insertLaptopExchangeRecord(exchangeRecord)

                        CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
                    }
                } else {
                    // MOCK Retrieval: create elegant sample records
                    delay(800)
                    onThoughtLog("📦 LAPTOP AGENT: Synthesizing mock documents from local file system...")
                    val sampleDocs = listOf(
                        LaptopDocumentEntity(
                            id = UUID.randomUUID().toString().take(8),
                            title = "Quantitative Trading Alpha Research.pdf",
                            content = "Abstract: This document details our new Mean-Reversion strategy utilizing GARCH models. Historical backtest indicates a Sharpe ratio of 2.14 over the past 4 years.",
                            category = "RESEARCH",
                            sizeBytes = 2048500,
                            lastModified = System.currentTimeMillis() - 86400000,
                            retrievedAt = System.currentTimeMillis()
                        ),
                        LaptopDocumentEntity(
                            id = UUID.randomUUID().toString().take(8),
                            title = "YouTube Growth Blueprint & Content Ideas.docx",
                            content = "Draft Ideas for multi-agent systems overview video. Keywords: Jetpack Compose, autonomous agents, kotlin backend. Video duration target: 12 minutes.",
                            category = "CONTENT",
                            sizeBytes = 452000,
                            lastModified = System.currentTimeMillis() - 3600000 * 4,
                            retrievedAt = System.currentTimeMillis()
                        ),
                        LaptopDocumentEntity(
                            id = UUID.randomUUID().toString().take(8),
                            title = "Freelance Project Spec - API Integration.md",
                            content = "Technical specifications for the client API gateway. High throughput Ktor engine, JWT token authentication, rate limit 500 requests/min.",
                            category = "PROJECTS",
                            sizeBytes = 12400,
                            lastModified = System.currentTimeMillis(),
                            retrievedAt = System.currentTimeMillis()
                        )
                    )

                    repository.insertLaptopDocuments(sampleDocs)

                    val exchangeRecord = LaptopExchangeRecord(
                        deviceName = "Laptop-Mock-Host",
                        direction = "PULL",
                        dataCount = sampleDocs.size,
                        timestamp = System.currentTimeMillis(),
                        status = "SUCCESS",
                        message = "Successfully retrieved ${sampleDocs.size} mock documents from simulated host."
                    )
                    repository.insertLaptopExchangeRecord(exchangeRecord)

                    onThoughtLog("✅ LAPTOP AGENT: Populated local database with synthesized research and plan documents.")
                    CapabilityResult(
                        success = true,
                        outputData = mapOf("retrieved_count" to sampleDocs.size, "documents" to sampleDocs, "mode" to "MOCK")
                    )
                }
            }

            "EXCHANGE_DATA" -> {
                // Collect local statistics to exchange
                val assets = repository.portfolioAssets.firstOrNull() ?: emptyList()
                val activeContractsCount = assets.size
                val messages = repository.chatMessages.firstOrNull() ?: emptyList()
                
                val telemetryData = mapOf(
                    "active_assets_count" to activeContractsCount.toString(),
                    "total_chat_history_messages" to messages.size.toString(),
                    "sync_client_time" to System.currentTimeMillis().toString()
                )

                if (isLive) {
                    try {
                        onThoughtLog("📡 LAPTOP AGENT: Uploading telemetry exchange package to $baseUrl/api/exchange...")
                        val service = LaptopAgentClient.buildService(baseUrl)
                        val payload = DataExchangePayload(
                            deviceId = "NexusHub-AndroidClient",
                            clientVersion = "1.0.0",
                            dataPayload = telemetryData,
                            timestamp = System.currentTimeMillis()
                        )
                        val syncResponse = service.exchangeData(payload)
                        onThoughtLog("✅ LAPTOP AGENT: Exchange success. Status: ${syncResponse.status}. Synced ${syncResponse.recordsSynced} records.")

                        val exchangeRecord = LaptopExchangeRecord(
                            deviceName = syncResponse.deviceName,
                            direction = "PUSH",
                            dataCount = syncResponse.recordsSynced,
                            timestamp = System.currentTimeMillis(),
                            status = "SUCCESS",
                            message = "Exchanged data payload with laptop agent. Status: ${syncResponse.status}."
                        )
                        repository.insertLaptopExchangeRecord(exchangeRecord)

                        CapabilityResult(
                            success = true,
                            outputData = mapOf(
                                "status" to syncResponse.status,
                                "synced_records" to syncResponse.recordsSynced,
                                "deviceName" to syncResponse.deviceName,
                                "mode" to "LIVE"
                            )
                        )
                    } catch (e: Exception) {
                        val err = "Data exchange failed: ${e.localizedMessage}"
                        onThoughtLog("❌ LAPTOP AGENT: $err")

                        val exchangeRecord = LaptopExchangeRecord(
                            deviceName = "Laptop-Host-Agent",
                            direction = "PUSH",
                            dataCount = 0,
                            timestamp = System.currentTimeMillis(),
                            status = "FAILED",
                            message = err
                        )
                        repository.insertLaptopExchangeRecord(exchangeRecord)

                        CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
                    }
                } else {
                    delay(600)
                    onThoughtLog("✅ LAPTOP AGENT: Synthesizing offline exchange transaction...")
                    
                    val exchangeRecord = LaptopExchangeRecord(
                        deviceName = "Laptop-Mock-Host",
                        direction = "PUSH",
                        dataCount = telemetryData.size,
                        timestamp = System.currentTimeMillis(),
                        status = "SUCCESS",
                        message = "Pushed telemetry metrics to simulated host. Records synchronized: ${telemetryData.size}."
                    )
                    repository.insertLaptopExchangeRecord(exchangeRecord)

                    CapabilityResult(
                        success = true,
                        outputData = mapOf(
                            "status" to "SYNCED_OFFLINE",
                            "synced_records" to telemetryData.size,
                            "deviceName" to "Laptop-Mock-Host",
                            "mode" to "MOCK"
                        )
                    )
                }
            }

            "GET_SYNC_STATUS" -> {
                if (isLive) {
                    try {
                        onThoughtLog("📡 LAPTOP AGENT: Retrieving sync telemetry from $baseUrl/api/sync-status...")
                        val service = LaptopAgentClient.buildService(baseUrl)
                        val syncStatus = service.getSyncStatus()
                        CapabilityResult(
                            success = true,
                            outputData = mapOf(
                                "status" to syncStatus.status,
                                "deviceName" to syncStatus.deviceName,
                                "recordsSynced" to syncStatus.recordsSynced,
                                "mode" to "LIVE"
                            )
                        )
                    } catch (e: Exception) {
                        val err = "Failed to fetch sync status: ${e.localizedMessage}"
                        onThoughtLog("❌ LAPTOP AGENT: $err")
                        CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
                    }
                } else {
                    delay(200)
                    val history = repository.getAllLaptopExchangeRecordsDirect()
                    val lastRecord = history.firstOrNull()
                    CapabilityResult(
                        success = true,
                        outputData = mapOf(
                            "status" to (lastRecord?.status ?: "STANDBY"),
                            "deviceName" to (lastRecord?.deviceName ?: "Laptop-Mock-Host"),
                            "recordsSynced" to (lastRecord?.dataCount ?: 0),
                            "mode" to "MOCK"
                        )
                    )
                }
            }

            "DOWNLOAD_DOCUMENT" -> {
                val docId = parameters["id"]?.toString() ?: ""
                if (docId.isBlank()) {
                    return CapabilityResult(success = false, outputData = emptyMap(), errorMessage = "Missing required parameter: 'id'")
                }

                if (isLive) {
                    try {
                        onThoughtLog("📡 LAPTOP AGENT: Pulling detailed document content for ID '$docId'...")
                        val service = LaptopAgentClient.buildService(baseUrl)
                        val doc = service.getDocumentById(docId)
                        
                        // Update local cache
                        repository.insertLaptopDocuments(listOf(
                            LaptopDocumentEntity(
                                id = doc.id,
                                title = doc.title,
                                content = doc.content,
                                category = doc.category,
                                sizeBytes = doc.sizeBytes,
                                lastModified = doc.lastModified,
                                retrievedAt = System.currentTimeMillis()
                            )
                        ))

                        CapabilityResult(success = true, outputData = mapOf("document" to doc, "mode" to "LIVE"))
                    } catch (e: Exception) {
                        val err = "Download document failed: ${e.localizedMessage}"
                        onThoughtLog("❌ LAPTOP AGENT: $err")
                        CapabilityResult(success = false, outputData = emptyMap(), errorMessage = err)
                    }
                } else {
                    delay(300)
                    val cachedDocs = repository.getAllLaptopDocumentsDirect()
                    val match = cachedDocs.find { it.id == docId }
                    if (match != null) {
                        CapabilityResult(success = true, outputData = mapOf("document" to match, "mode" to "MOCK"))
                    } else {
                        CapabilityResult(success = false, outputData = emptyMap(), errorMessage = "Document with ID '$docId' not found.")
                    }
                }
            }

            else -> {
                CapabilityResult(success = false, outputData = emptyMap(), errorMessage = "Action '$action' is not supported by Laptop Agent Capability.")
            }
        }
    }
}
