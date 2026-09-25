package com.example.fp_test.net

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import kotlin.system.exitProcess

class Client(private val serverAddress: InetSocketAddress) {
    // The socket is a var because a closed Socket can never be reopened; reconnect() must replace it.
    private var clientSock = openSocket()
    // Input stream of the current socket (replaced together with the socket on reconnect).
    private var inputStream = clientSock.getInputStream()
    // Output stream of the current socket (replaced together with the socket on reconnect).
    private var outputStream = clientSock.getOutputStream()

    /**
     * Open a new socket to the server, giving up if the server does not answer in time.
     * @return A connected socket whose reads time out instead of blocking forever.
     */
    private fun openSocket(): Socket {
        val sock = Socket() // create an unconnected socket so timeouts can be set before connecting
        sock.soTimeout = READ_TIMEOUT_MS // reads throw SocketTimeoutException instead of hanging forever
        sock.connect(serverAddress, CONNECT_TIMEOUT_MS) // connect, but give up after the timeout
        return sock // hand the connected socket to the caller
    }

    /**
     * Read one whole response from the server: a length byte, then the rest of the message.
     * A single `read` call may return only part of a message, so keep reading until it is complete.
     * @return The raw response bytes (including the length byte), or null if the response is invalid.
     */
    private fun readResponse(): ByteArray? {
        val length = inputStream.read() // first byte is the total length; -1 means the server closed the connection
        if (length < MIN_RSP_LENGTH || length > MAX_CMD_LENGTH) return null // reject closed, empty, or oversized responses

        val response = ByteArray(length) // buffer for exactly the announced number of bytes
        response[0] = length.toByte() // keep the length byte, because ServerResponse expects it
        var received = 1 // one byte (the length) is already in the buffer

        while (received < length) { // keep reading until the whole message has arrived
            val n = inputStream.read(response, received, length - received) // read the missing bytes
            if (n < 0) return null // the connection closed before the message was complete
            received += n // count the bytes that just arrived
        }

        return response // the complete raw response
    }

    /**
     * Send the given command to the server, storing the response in `response` if not null.
     * @param command The command to send to the server.
     * @param response The buffer to store the server response in (or null).
     * @return The server response, or null if an error occurred.
     * @throws java.io.IOException if the network fails or the server does not answer in time.
     */
    fun sendCommand(msg: ServerMessage): ServerResponse? {
        val cmdString = msg.toRawMessage()
        outputStream.write(cmdString)

        val raw = readResponse() // wait (with a timeout) for the complete response

        if (msg.command == ServerCommand.QUIT)
            close() // QUIT ends the session even if the server sent nothing back

        if (raw == null)
            return null // no usable response arrived

        return try {
            ServerResponse(raw) // parse the response
        } catch (e: IllegalArgumentException) {
            null // malformed response: report "no response" instead of crashing the app
        } catch (e: IllegalStateException) {
            null // unknown status code or wrong data length: same handling
        }
    }

    val isClosed: Boolean
        get() = clientSock.isClosed

    /**
     * Close the connection between the client and the server.
     */
    fun close() = clientSock.close()

    /**
     * Close the current connection and open a fresh one to the same server.
     * @throws java.io.IOException if the new connection cannot be made.
     */
    fun reconnect() {
        clientSock.close() // release the old, broken socket
        clientSock = openSocket() // a closed socket cannot be reused, so make a new one
        inputStream = clientSock.getInputStream() // streams belong to the old socket, so fetch new ones
        outputStream = clientSock.getOutputStream() // same for the output stream
    }

    companion object {
        private const val MAX_CMD_LENGTH = 20
        private const val MIN_RSP_LENGTH = 2 // a response is at least a length byte and a status byte
        private const val CONNECT_TIMEOUT_MS = 5000 // give up connecting after 5 seconds
        private const val READ_TIMEOUT_MS = 5000 // give up waiting for a reply after 5 seconds

        private fun handleConnection(client: Client) {
            println("Enter command to send:")
            println("1) Get temp             7) Dispense water")
            println("2) Get humidity         8) Quit")
            println("3) Get light level      ")
            println("4) Get water level      ")
            println("5) Get moisture level   ")
            println("6) Get fertilizer level ")

            while (!client.isClosed) {
                var selection: Int?

                do {
                    selection = readLine()?.toInt()
                } while (selection == null)

                val command = when (selection) {
                    1 -> ServerCommand.GET_TEMP
                    2 -> ServerCommand.GET_HUMIDITY
                    3 -> ServerCommand.GET_LIGHT
                    4 -> ServerCommand.GET_WATER
                    5 -> ServerCommand.GET_MOISTURE
                    6 -> ServerCommand.GET_FERTILIZER
                    7 -> ServerCommand.DISPENSE_WATER
                    else -> {
                        client.close()
                        return
                    }
                }

                print("Enter data to send with command: ")
                System.out.flush()

                val qty = readLine()?.toByteOrNull();
                val msg = ServerMessage(command, qty)
                val response: ServerResponse?

                try {
                    response = client.sendCommand(msg)
                } catch (e: IllegalStateException) {
                    println("Failed to send command; stopping client.")
                    client.close()
                    return
                }

                if (response == null) {
                    println("Failed to get response; stopping client.")
                    client.close()
                    return
                }

                println("Sent $command to server. Response=$response")
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                // initialize server connection
                val serverAddr = InetAddress.getByName(args[0])
                val serverPort = args[1].toInt()
                val client = Client(InetSocketAddress(serverAddr, serverPort))
                println("Client connected to server at $serverAddr:$serverPort")
                handleConnection(client)
            } catch (e: IOException) {
                e.printStackTrace()
                exitProcess(1)
            }
        }
    }
}
