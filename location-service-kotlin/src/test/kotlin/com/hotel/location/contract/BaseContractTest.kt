package com.hotel.location.contract

import com.hotel.location.module
import com.hotel.location.resetServiceState
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.restassured.RestAssured
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

abstract class BaseContractTest {

    companion object {
        private var server: ApplicationEngine? = null

        @JvmStatic
        @BeforeAll
        fun startServer() {
            val engine = embeddedServer(Netty, port = 0) {
                module()
            }.start(wait = false)

            server = engine
            val port = kotlinx.coroutines.runBlocking { engine.resolvedConnectors() }.first().port
            RestAssured.baseURI = "http://127.0.0.1"
            RestAssured.port = port
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server?.stop(1000, 2000)
            server = null
        }
    }

    @AfterEach
    fun resetState() {
        resetServiceState()
    }
}
