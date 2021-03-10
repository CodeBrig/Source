package com.sourceplusplus.sourcemarker.discover

import com.sourceplusplus.protocol.SourceMarkerServices.Provider.LOCAL_TRACING
import com.sourceplusplus.protocol.SourceMarkerServices.Provider.LOG_COUNT_INDICATOR
import io.vertx.core.*
import io.vertx.core.eventbus.impl.EventBusImpl
import io.vertx.core.eventbus.impl.MessageImpl
import io.vertx.core.http.impl.headers.HeadersMultiMap
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetClient
import io.vertx.core.net.NetSocket
import io.vertx.ext.bridge.BridgeEventType
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.servicediscovery.Record
import io.vertx.servicediscovery.spi.ServiceDiscoveryBackend
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * todo: description.
 *
 * @since 0.2.1
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
class TCPServiceDiscoveryBackend : ServiceDiscoveryBackend {

    companion object {
        private val log = LoggerFactory.getLogger(TCPServiceDiscoveryBackend::class.java)
    }

    private lateinit var vertx: Vertx
    private lateinit var client: NetClient
    private lateinit var socket: NetSocket
    private val setupPromise = Promise.promise<Void>()
    private val setupFuture = setupPromise.future()
    private val replyHandlers = ConcurrentHashMap<String, (JsonObject) -> Unit>()

    override fun init(vertx: Vertx, config: JsonObject) {
        this.vertx = vertx

        GlobalScope.launch(vertx.dispatcher()) {
            client = vertx.createNetClient()
            socket = client.connect(5455, "localhost").await()
            val parser = FrameParser { parse: AsyncResult<JsonObject> ->
                val frame = parse.result()
                if (replyHandlers.containsKey(frame.getString("address"))) {
                    if (frame.getString("type") == "err") {
                        val err = JsonObject().put("error", true)
                            .put("rawFailure", JsonObject(frame.getString("rawFailure")))
                        replyHandlers.remove(frame.getString("address"))!!.invoke(err)
                    } else {
                        replyHandlers.remove(frame.getString("address"))!!.invoke(frame.getJsonObject("body"))
                    }
                } else if (frame.containsKey("headers")) {
                    val headers = frame.getJsonObject("headers")
                    if (headers.containsKey("sm.reply")) {
                        replyHandlers.remove(headers.getString("sm.reply"))!!.invoke(frame.getJsonObject("body"))
                    }
                }
            }
            socket.handler(parser)

            setupHandler(vertx, "get-records")
            setupHandler(vertx, LOCAL_TRACING)
            setupHandler(vertx, LOG_COUNT_INDICATOR)

            setupPromise.complete()
        }
    }

    private fun setupHandler(vertx: Vertx, address: String) {
        vertx.eventBus().consumer<JsonObject>(address) { resultHandler ->
            val replyAddress = UUID.randomUUID().toString()
            replyHandlers[replyAddress] = {
                if (it.map.keys.size == 1 && it.containsKey("value")) {
                    resultHandler.reply(it.getValue("value"))
                } else if (it.map.keys.size == 2 && it.containsKey("error")) {
                    resultHandler.fail(500, it.getString("rawFailure"))
                } else {
                    val message = MessageImpl(
                        address,
                        HeadersMultiMap.httpHeaders(),
                        it,
                        (resultHandler as MessageImpl<JsonObject, JsonObject>).codec(),
                        true,
                        vertx.eventBus() as EventBusImpl
                    )
                    message.setReplyAddress(resultHandler.replyAddress())
                    message.reply(it)
                }
            }
            val headers = JsonObject()
            headers.put("sm.reply", replyAddress)
            for ((key, value) in resultHandler.headers().entries()) {
                headers.put(key, value)
            }
            FrameHelper.sendFrame(
                BridgeEventType.SEND.name.toLowerCase(),
                address, replyAddress, headers, true, resultHandler.body(), socket
            )
        }
    }

    override fun store(record: Record, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun remove(record: Record, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun remove(uuid: String, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun update(record: Record, resultHandler: Handler<AsyncResult<Void>>) {
        TODO("Not yet implemented")
    }

    override fun getRecords(resultHandler: Handler<AsyncResult<MutableList<Record>>>) {
        //todo: cache getRecords() result and return to any calls that happen in the same second
        if (setupFuture.isComplete) {
            vertx.eventBus().request<JsonObject>("get-records", null) {
                resultHandler.handle(Future.succeededFuture(mutableListOf(Record(it.result().body()))))
            }
        } else {
            setupFuture.onComplete {
                vertx.eventBus().request<JsonArray>("get-records", null) {
                    val records = mutableListOf<Record>()
                    it.result().body().forEach { record ->
                        records.add(Record(record as JsonObject))
                    }
                    resultHandler.handle(Future.succeededFuture(records))
                }
            }
        }
    }

    override fun getRecord(uuid: String, resultHandler: Handler<AsyncResult<Record>>) {
        TODO("Not yet implemented")
    }

    override fun name() = "tcp-service-discovery"
}