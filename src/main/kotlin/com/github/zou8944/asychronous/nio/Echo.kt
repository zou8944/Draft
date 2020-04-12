package com.github.zou8944.asychronous.nio

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * 这里使用了两个Selector，其实也完全可以仅仅使用一个Selector进行轮训
 * JDK 的 NIO 底层由 epoll 实现，该实现饱受诟病的空轮询 bug 会导致 cpu 飙升 100%
 */
fun main() {
  // 用于轮询是否有新的连接，当产生新的连接时，将新连接绑定在client selector上进行数据轮训
  val serverSelector = Selector.open()
  // 轮训连接是否有数据可读
  val clientSelector = Selector.open()

  // 处理连接
  Thread {
    val serverChannel = ServerSocketChannel.open()
    serverChannel.socket().bind(InetSocketAddress(9090))
    serverChannel.configureBlocking(false)
    serverChannel.register(serverSelector, SelectionKey.OP_ACCEPT)

    while (true) {
      if (serverSelector.select(1) > 0) {
        val it = serverSelector.selectedKeys().iterator()
        while (it.hasNext()) {
          val key = it.next()
          if (key.isAcceptable) {
            val clientChannel = (key.channel() as ServerSocketChannel).accept()
            clientChannel.configureBlocking(false)
            clientChannel.register(clientSelector, SelectionKey.OP_READ)
          }
          it.remove()
        }
      }
    }
  }.start()

  // 处理数据
  Thread {
    while (true) {
      if (clientSelector.select(1) > 0) {
        val it = clientSelector.selectedKeys().iterator()
        while (it.hasNext()) {
          val key = it.next()
          if (key.isReadable) {
            val clientChannel = key.channel() as SocketChannel
            val byteBuffer = ByteBuffer.allocate(1024)
            clientChannel.read(byteBuffer)
            byteBuffer.flip()
            clientChannel.write(byteBuffer)
            byteBuffer.clear()
            clientChannel.close()
          }
          it.remove()
        }
      }
    }
  }.start()

}
