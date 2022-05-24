package com.example.fp_test.net

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import kotlin.system.exitProcess

class Client(private val serverAddress: InetSocketAddress) {
    private val clientSock = Socket(serverAddress.address, serverAddress.port)
    private val inputStream = clientSock.getInputStream()
    private val outputStream = clientSock.getOutputStream()

    /**
     * Send the given command to the server, storing the response in `response` if not null.
     * @param command The command to send to the server.
     * @param response The buffer to store the server response in (or null).
     * @return The server response, or null if an error occurred.
     */
    fun sendCommand(msg: ServerMessage): ServerResponse? {
        val cmdString = msg.toRawMessage()
        outputStream.write(cmdString)

        val response = ByteArray(MAX_CMD_LENGTH);
        val responseLen = inputStream.read(response)

        if (responseLen <= 0)
            return null

        if (msg.command == ServerCommand.QUIT)
            close()

        return ServerResponse(response.copyOfRange(0, responseLen))
    }

    val isClosed: Boolean
        get() = clientSock.isClosed

    /**
     * Close the connection between the client and the server.
     */
    fun close() = clientSock.close()

    fun reconnect() {
        clientSock.close();
        clientSock.connect(serverAddress)
    }

    companion object {
        private const val MAX_CMD_LENGTH = 20

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