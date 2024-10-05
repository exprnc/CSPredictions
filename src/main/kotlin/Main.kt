import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

fun main() {
    val client = OkHttpClient()

    val request = Request.Builder().url("wss://scorebot-lb.hltv.org/socket.io/?EIO=3&transport=websocket&sid=7a1ea9c3-5c88-4de2-b6d8-8eaf46de73f8").build()

    val webSocket = client.newWebSocket(request, MyWebSocketListener())

    client.dispatcher.executorService.shutdown()
}

class MyWebSocketListener : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        println("WebSocket открыт")
        webSocket.send("Привет, WebSocket!")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        println("Получено сообщение: $text")
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        println("Получены байты: ${bytes.hex()}")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        println("Закрытие WebSocket: код $code, причина $reason")
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        println("Ошибка: ${t.message}")
    }
}