# Asynchronous (异步)
本文介绍Java中的IO模型。

Java中的BIO、NIO、AIO等各种API，可以理解为是对操作系统的各种IO模型的封装，使得程序员在使用时能够不必关心操作系统层面的知识。明白这一点很重要，千万不要以为是Java自己实现了这些模型。

Java中提供的IO有关的API，在文件处理的时候，其实依赖操作系统层面的IO操作实现的。比如在Linux 2.6以后，Java中NIO和AIO都是通过epoll来实现的，而在Windows上，AIO是通过IOCP来实现的。

# 需要搞清楚的问题
1. Java中各种IO的概念：BIO、NIO、AIO
2. Linux中的五种IO模型
3. Java的IO模型和Linux、Windows中的IO模型是如何对应的
4. EventLoop、Actor、Reactor基本概念；还有其它类似的概念吗？
5. EventLoop、Actor、Reactor和Java的IO模型有什么关系

# 参考资料
1. 《Linux程序设计》
2. 《Linux五种IO模型》(https://mp.weixin.qq.com/s?__biz=Mzg3MjA4MTExMw==&mid=2247484746&idx=1&sn=c0a7f9129d780786cabfcac0a8aa6bb7&source=41#wechat_redirect)
3. 《Java NIO BIO AIO简单总结》(https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/BIO-NIO-AIO.md#1-bio-blocking-io)
4. 《Java并发编程实战》
5. 《Java网络编程》