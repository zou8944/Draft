package com.github.zou8944.io.bio

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket

const val PORT = 9090

fun main() {

  val serverSocket = ServerSocket(PORT)

  while (true) {
    val socket = serverSocket.accept()
    val inputStream = socket.getInputStream()
    val outputStream = socket.getOutputStream()

    val reader = BufferedReader(InputStreamReader(inputStream))
    val writer = PrintWriter(OutputStreamWriter(outputStream))

    val line = reader.readLine()
    writer.println(line)
    writer.flush()

    writer.close()
    socket.close()
  }

}