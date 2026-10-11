package id.ndifix.dwichat

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class ChatSocket(baseUrl: String, token: String) {
    private val socket: Socket = IO.socket(baseUrl, IO.Options.builder().setAuth(mapOf("token" to token)).setReconnection(true).build())
    fun connect() = socket.connect()
    fun join(conversationId: String) { socket.emit("conversation:join", JSONObject().put("conversationId", conversationId)) }
    fun send(conversationId: String, body: String) { socket.emit("message:send", JSONObject().put("conversationId", conversationId).put("body", body)) }
    fun onMessage(callback: (MessageDto) -> Unit) {
        socket.on("message:new") { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@on
            callback(MessageDto(o.optString("id"), o.optString("conversationId"), o.optString("senderId"), o.optString("senderUsername"), o.optString("body"), o.optString("createdAt")))
        }
    }
    fun close() { socket.disconnect(); socket.off() }
}
