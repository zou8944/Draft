package com.github.zou8944.io.aio

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit

fun main() {
  val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(9090))

  val obj: CompletionHandler<AsynchronousSocketChannel, Any?> = object : CompletionHandler<AsynchronousSocketChannel, Any?> {
    override fun completed(channel: AsynchronousSocketChannel, attachment: Any?) {

      server.accept(null as Any?, this)

      val buffer = ByteBuffer.allocate(1024)
      channel.read(buffer).get(100, TimeUnit.MILLISECONDS)
      buffer.flip()
      channel.write(buffer)
      channel.close()
    }

    override fun failed(exc: Throwable?, attachment: Any?) {

    }
  }

  server.accept(null as Any?, obj)

  Thread.sleep(1000000000)

}