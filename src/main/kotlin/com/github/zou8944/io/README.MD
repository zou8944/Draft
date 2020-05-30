# Asynchronous (异步)
本文介绍Java中的IO模型。

Java中的BIO、NIO、AIO等各种API，可以理解为是对操作系统的各种IO模型的封装，使得程序员在使用时能够不必关心操作系统层面的知识。明白这一点很重要，千万不要以为是Java自己实现了这些模型。

Java中提供的IO有关的API，在文件处理的时候，其实依赖操作系统层面的IO操作实现的。比如在Linux 2.6以后，Java中NIO和AIO都是通过epoll来实现的，而在Windows上，AIO是通过IOCP来实现的。


## 《Linux网络编程》第六章读书笔记
### 背景知识介绍

Linux中网络程序设计完全靠套接字接受和发送消息，Socket是一个接口，它在系统中的位置如下。

![image-20200412174832136](./README/image-20200412174832136.png)

每一个socket都有一个本地的socket号，由操作系统分配。Socket是面向客户端-服务端模式设计的，且针对服务端和客户端提供了不同的socket调用。两端差异如下:

![image-20200412175404551](./README/image-20200412175404551.png)

socket有TCP和UDP两种类型，我们这里只关心TCP

### 套接字地址

在Linux中，docket到底是什么呢 —— 它是通过标准的Unix文件描述符和其它的程序通讯的一个方法

在Unix中，任何东西都是一个文件，在Unix中，任何针对I/O的操作，都是通过读或写一个文件描述符来实现的。

套接字使用方式：调用系统函数socket()，他返回一个Socket描述符，然后针对次描述符执行系统函数进行数据的收发。与套接字相关的调用如下

- socket()
- bind()
- connect()
- listen()
- accept()
- send() - 针对TCP的发送
- recv() - 针对TCP的接收
- sendto() - 针对UDP的发送
- recvfrom() - 针对UDP的接收
- close()
- ......

### 端口介绍

系统有1024个保留端口，他们是留给系统使用的，只有root用户才能使用1024以内的端口。可以在/etc/services文件中查看系统默认的端口设置情况。

检测一个端口是否被占用的绝佳方式——telnet，如果telnet是拒绝连接，则说明该端口没有被占用。同样，在调试客户端-服务端程序时候telnet也非常有用。可以用它来测试服务端有没有启动。

```bash
> telnet 127.0.0.1 8080
```

### 五种IO模式

Linux/UNIX系统中，有如下五种IO模式

- 阻塞I/O
- 非阻塞I/O
- I/O多路复用
- 信号驱动I/O（SIGIO）
- 异步I/O

对于一个套接字的输入操作，一般分为两步。这是大前提，五种IO模式都是在这上面做文章的。

- 等待数据从网络到达本地，当数据到达后，系统将数据从网络层拷贝到内核的缓存
- 将数据从内核的缓存拷贝到应用程序的数据区中

#### 阻塞I/O

缺省模式，一个socket建立后自动处于阻塞I/O模式。如下图，阻塞I/O大致流程为

- 调用recvfrom发起数据接收
- 内核尚未收到数据，于是阻塞等待
- 内核收到数据（数据到了内核缓存），将数据从内核缓存拷贝到应用程序数据区
- 拷贝完成，recvfrom返回，应用程序处理数据

![image-20200412183039820](./README/image-20200412183039820.png)

#### 非阻塞I/O

设置为此模式后，相当于告诉内核：当我请求的IO不能马上返回时，不要让我的进程进入休眠，而是返回一个错误给我。而为了能够及时收到数据，应用程序需要循环调用recevfrom来测试一个文件描述符（创建socket时生成）是否有数据可读。这称作polling。应用程序不停滴polling是一个浪费CPU的操作，因此这种模式不是很普遍。

- 调用recvfrom发起数据接收
- 内核尚未收到数据，响应应用程序EWOULDBLOCK错误，而内核自己则继续等待数据
- 多次调用recvfrom询问内核数据是否准备好。
- 当数据终于准备好时，内核将数据拷贝到应用程序数据区，返回recvfrom
- 应用程序处理数据

![image-20200412184015703](./README/image-20200412184015703.png)

#### I/O多路复用

此模式下，在开始接收数据之前，我们不是调用recvfrom函数，而是调用select函数或poll函数，当他们有响应时，再调用recvfrom接收数据，调用select函数时也会阻塞，但它的优点在于，能够同时等待多个文件描述符，只要有一个准备好了，select就会返回。I/O多路复用经常被使用。

- 调用select，阻塞等待直到有文件描述符的数据就绪
- 有就绪的文件描述符，select返回，应用程序调用recvfrom接收数据
- 内核将数据拷贝到应用程序数据区，返回recvfrom
- 应用程序处理数据

![image-20200412185247113](./README/image-20200412185247113.png)

##### 关于select

select函数可以同时监视多个套接字，它能够告诉你哪一个套接字已经可以读取数据、哪一个套接字已经可以写入数据、哪一个套接字出现了错误等。

##### epoll

select和poll的使用有较大的局限性，无法满足并发量特别高的情况，epoll是对他们的增强。增强的原理这里不深究。

#### 信号驱动I/O

该模式将内核等待数据这段时间变主动为被动，在数据就绪时使用SIGIO（或SIGPOLL）信号通知我们。

使用方法上，让套接字工作在信号驱动I/O工作模式中，并安装一个SIGIO处理函数。这样在内核等待数据期间我们就是完全异步的情况了。只需要在SIGIO处理函数中接收数据处理即可。

- 创建套接字，允许工作在信号驱动模式，并注册SIGIO信号处理函数
- 内核数据就绪后，响应SIGIO信号
- 事先注册的SIGIO处理函数中调用recvfrom函数
- 内核将数据拷贝到应用程序数据区，返回recvfrom
- 应用程序处理数据

信号驱动I/O的编程有一个最大的难点是除了数据就绪外，还有很多触发SIGIO信号的场景，区分这些场景是难点。

![image-20200412190038978](./README/image-20200412190038978.png)

#### 异步I/O

异步I/O模式下，我们只需要告诉内核我们要进行I/O操作，然后内核马上返回，具体的I/O操作和数据拷贝全部由内核完成，完成后再通知我们的应用程序。与信号驱动I/O所不同的是，这次不仅在等待数据阶段是异步的，连内核数据拷贝都是异步的。

- 创建套接字，工作在异步I/O模式，指定套接字文件描述符、数据需要拷贝到的缓冲区、回调函数等，不需要等待，马上返回
- 内核负责等待数据病将数据从内核缓冲区拷贝到应用程序数据区
- 内核拷贝结束后，回调第一步注册的函数，完成应用程序的数据处理

![image-20200412191006888](./README/image-20200412191006888.png)

#### 五种模式总结

前四种模式都有阻塞的地方——将数据从内核拷贝到应用程序数据区，只有第五种是完全异步的。

![image-20200412191435056](./README/image-20200412191435056.png)



## 《Netty权威指南》基础篇及线程模型学习笔记

### I/O多路复用

上面介绍了五种Linux的IO模式，但由于Java的NIO的核心类库多路复用器Selector就是基于epoll的多路复用技术实现，因此这里重点关注它。

I/O多路复用把多个IO的阻塞服用到同一个select的阻塞上，使得单线程情况下可以同时处理多个客户端的请求。

支持I/O多路服用的系统调用有select\pselect\poll\epoll等，Linux很长一段时间使用select做轮询，但其固有的缺陷导致其有很大限制，最终Linux使用epoll替代了select，在性能上有了很大的提升。

### Java IO发展历史

- JDK 1.0 - JDK1.3 Java都只有BIO，很多Unix的概念或接口在其库中都没有体现。
- 2002年发布JDK 1.4，增加java.nio包，主要引入了支持异步操作的各种核心类库：管道、缓冲区、Channel、Selector等。
- 2011年发布JDK 1.7，对原来的NIO包进行了升级，称为NIO2.0，主要提供了AIO功能。

### 使用Netty的理由

尽管Java已经提供了NIO类库，但实际的网络编程环境非常复杂，要开发出一个稳定可用的网络程序使用NIO的周期将会非常长，而Netty封装了各种IO实现，提供简单的API，工作稳定，适用于各种场景。

### Netty的线程模型

通过不同的配置，Netty能够支持Reactor单线程模式、Reactor多线程模式、主从Reactor多线程模式

这里暂时没有必要去纠结单线程还是多线程，只需要关心Reactor模式。在Netty中，它正是基于NIO的多路复用实现的。

Netty的核心NioEventLoop就是基于NIO的多路复用实现的，除了NIO外，它还兼顾处理两个任务

- 用户通过NioEventLoop.execute方法注册的事件放在消息队列中，由I/O线程执行
- 用户通过NioEventLoop.schedule方法注册的定时任务

他们的实现原理就是在一个死循环内反复轮询Selector、消息队列、定时任务

### JDK的epoll bug

原本select()方法应该是阻塞的，但JDK的select()方法在一些情况下会在没有事件时返回。造成在死循环中空转，使得CPU达到100%的情况。该bug到2013年才修复

Netty中的解决方式是在短时间内检测到超过一定数量的select()调用，就判定为空转。通过创建新的Selector并将原Selector中的Channel注册到新的Selector达到消除这个问题的目的。

### 总结

Netty基于NIO构建了自己的EventLoop。

## Java的IO

Java的IO有三种

- BIO

  即传统的Socket API，阻塞等待

- NIO

  引入了多路选择器Selector、通道Channel、缓存区ByteBuffer的概念。通过轮询选择器的方式获取准备好的Channel，数据读取均采用ByteBuffer。

  注意，select()方法是阻塞的，因此使用while(true)时候不会有CPU空转的风险。

  **实现上，Linux 2.6之前是select、poll，2.6之后是epoll，Windows是IOCP**

- NIO2 (AIO)

  异步IO，使用时只需要注册一个处理函数即可。在数据接收完成后系统会调用回调函数进行处理。

  相比NIO的优势在于可

## Java NIO

### 基于NIO构建事件驱动模型

NIO的Selector能够响应多个不同的事件

- ACCEPT
- CONNCET
- READ
- WRITE

针对感兴趣的事件注册事件处理器，单线程轮训Selector，事件到达时执行事件处理器——这就是最简单的Reactor模式

这种最简单的Reactor模式已经能够带来性能上的巨大提升。但考虑到现在的CPU都是多核，因此加入多线程非常有必要，需要的线程主要包括以下几种：

- 事件分发器，单线程选择就绪的事件。

- I/O处理器，包括connect、read、write等，这种纯CPU操作，一般开启CPU核心个线程就可以。

- 业务线程，在处理完I/O后，业务一般还会有自己的业务逻辑，有的还会有其他的阻塞I/O，如DB操作，RPC等。只要有阻塞，就需要单独的线程。

从而有如下示意图的模型。

![image-20200412204806375](./README/image-20200412204806375.png)

### 问题

NIO != 高性能，当连接数小于1000、并发程度不高时，NIO并没有显著的性能优势。

NIO并没有完全屏蔽平台差异，它仍然是基于各个操作系统的I/O系统实现的，差异仍然存在。使用NIO做网络编程构建事件驱动模型并不容易，陷阱重重。

推荐大家使用成熟的NIO框架，如Netty，MINA等。解决了很多NIO的陷阱，并屏蔽了操作系统的差异，有较好的性能和编程模型。

## EventLoop

Event Loop是一个程序结构，用于等待和发送消息和事件。

其实，简单地理解EventLoop，就是一个反复定时轮询检查事件队列，并在事件发生时将事件分发到对应的handler中的一个工具。也算是一种编程模型。

维基百科对EventLoop的讲解可以说是非常清楚的：

事件循环是一种等待和分发程序中事件或消息的编程结构或设计模式。实际工作上，它通过向事件提供程序（如消息队列、NIO的Selector）发出请求以获取事件，然后调用对应的事件处理程序进行工作。因此它有时也被称为消息分发程序。

事件循环可以和Reactor模式相结合，这就是我们常用的NIO编程。

从原理上说，我们可以自己提供事件消息队列，但一般来说这个消息队列是由对应的运行环境(操作系统)提供的，比如Java的NIO，这样可以将I/O放到系统中进行，避免阻塞工作线程。

事件循环是基于消息传输的系统的典型技术特征。

## Reactor Proactor Actor

### Reactor

根据维基百科描述，Reactor是一种设计模式，是一种事件处理模式。

它要求存在一个处理结构，接收并发的多个输入请求，并将这些请求同步分发到关联的请求处理handler的情况。

一个反应器的基本结构如下

- 资源

  可以是向系统发送消息的请求、也可以是从系统获取消息的请求

- 同步的事件复用器。

  用一个EventLoop阻塞地等待所有资源（即请求），比如I/O多路复用模型中的epoll，当资源准备好时EventLoop将资源发送给调度器

- Dispatcher分发器

  用于管理请求处理器的注册和注销。同时将从复用器中得到的资源分发给对应的请求处理器

- 请求处理器

  定义了资源的处理逻辑

所有的Reacto系统在定义上将都是单线程的，但改良后的可以是多线程的，比如Netty中就对Reactor进行了多线程改进，使得能够发挥最大性能。

实际实现中的Reactor模型常常被用来解决IO并发问题，最常见的就是I/O多路复用。死循环阻塞等待select()就是EventLoop，有事件时调用对应于分发器。

在I/O上，这样能够提高I/O高并发的效率；在编程模型上，它将事件的处理逻辑完全分开。

当然，Reactor并不是只能用于I/O，就像Netty在EventLoop加入用户自定义的task和定时task、Vertx基于EventLoop构建自己的神经系统一样，它也可以用来处理事件请求和处理分离的模式，在运行效率和编程效率上都有所提升。

### Proactor

Proactor也是用于事件处理的设计模式，它可以被看做是同步的Reactor模式的变体。它将需要长时间运行的操作异步执行，在执行完后调用对应的处理器进行结果处理即可。

模型上的结构如下和工作流程如下

![Proactor.VSD_SequenceDiagram.png (577×325)](https://upload.wikimedia.org/wikipedia/commons/6/61/Proactor.VSD_SequenceDiagram.png)

在实际应用中，模型中的Asynchronous Operation Processor、Asynchronous Operation、Completion Dispatcher一般依赖于操作系统完成，我们负责发起异步调用和注册完成处理函数。比如LInux的异步IO模型。我们能做的只是发起I/O请求、指明数据要存放的位置、注册处理函数，待系统异步处理完I/O请求后，调用注册的处理函数。

### Actor



## VertxEventLoop和EventBus



# 需要搞清楚的问题

1. Java中各种IO的概念：BIO、NIO、AIO （完成）

2. Linux中的五种IO模型 （完成）

3. Java的IO模型和Linux、Windows中的IO模型是如何对应的 （完成）

4. EventLoop、Actor、Reactor基本概念；还有其它类似的概念吗？

5. EventLoop、Actor、Reactor和Java的IO模型有什么关系

6. Netty中如何对应了Java的NIO模型

   当然是直接使用Java的NIO呀

7. Vertx的异步模型如何对应Java的NIO模型

8. Java的IO演进之路和Linux的各种IO发布时间对比

# 参考资料
1. 《Linux程序设计》
2. 《Linux五种IO模型》(https://mp.weixin.qq.com/s?__biz=Mzg3MjA4MTExMw==&mid=2247484746&idx=1&sn=c0a7f9129d780786cabfcac0a8aa6bb7&source=41#wechat_redirect)
3. 《Java NIO BIO AIO简单总结》(https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/BIO-NIO-AIO.md#1-bio-blocking-io)
4. 《Java并发编程实战》
5. 《Java网络编程》- 第六章
6. 《Netty权威指南》- 基础篇(1、2、18章)
6. https://zhuanlan.zhihu.com/p/23488863
8. 《什么是EventLoop - 阮一峰》(https://www.ruanyifeng.com/blog/2013/10/event_loop.html)