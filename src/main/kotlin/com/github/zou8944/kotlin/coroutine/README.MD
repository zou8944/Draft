## 协程

Kotlin的标准库仅提供最底层的API，协程并不在Kotlin标准库中提供。而是以一个扩展库的形式提供：kotlinx.coroutines

### 启动协程

#### 启动主协程

协程必须在某些CoroutineScope上下文中与launch协程构建器一起启动

- GlobalScope.launch{}

  在全局范围内启动一个新协程，该协程的生命周期和应用程序的生命周期一致

  GlobalScope启动的协程并不能使线程保活，从这个意义上说，它像守护线程

- runBlocking{}

  启动一个新的协程，并阻塞当前线程，直到其内部的协程全部执行完成

  注意，它会等待其内部的逻辑及所有子协程的逻辑全部执行完才算完成

#### 启动子协程

- launch{}

  启动新协程，继承当前作用域

  launch返回一个Job对象，调用job.join()可以等待该协程完成再往下执行

- coroutineScope{}

  **不启动新协程，而是创建一个新的作用域**，它会等待内部所有逻辑及所有子协程结束后才结束，它只是一个挂起函数。

- launch对比coroutineScope

  

- runBlocking对比coroutineScope

  runBlocking阻塞线程，而coroutineScope不阻塞线程

  runBlocking是常规函数，可以在任意地方调用；coroutineScope是挂起函数，只能在协程内部调用。



### 取消协程

job.cancel或deffer.cancel方法可以取消协程

对于协程标准库中的挂起方法，在协程被取消后，他们会抛出CancellationException异常

#### 取消是协作的

协程的取消是协作的，即调用job.cancel仅仅是通知协程它被取消了；但是否真的要取消协程是协程内部的逻辑说了算：如果协程中的代码不检测取消标记，那协程本身并不会被取消。

```kotlin
val job = launch(Dispatchers.Default) {
    // 如果这里不检测isActive标记，协程就不会被正常cancel，而是执行直到正常结束
    while (isActive) { 
        ......
    }
}
job.cancelAndJoin() // 取消该作业并等待它结束
```

这一点与线程的interrupt类似

#### 超时取消

通过withTimeout设置超时，如果超时，withTimeout会跑出异常TimeoutCancellationException，如果不想抛出异常，可以使用withTimeoutOrNull

```kotlin
withTimeout(1300L) {
    repeat(1000) { i ->
        println("I'm sleeping $i ...")
        delay(500L)
    }
}
```

### 协程上下文与调度器

CoroutineContext，是在Kotlin标准库中定义的。其实，所有协程定义都在Kotlin标准库中，协程的实现则是放在协程扩展库kotlinx.coroutine中的

协程上下文包含很多内容，其中最重要的是协程调度器CoroutineDispatcher，它控制了协程以何种策略分配到哪些线程上运行。

所有的协程构造器都接收一个可选的CoroutineContext参数，用于显式地为新的协程指定一个新调度器。

常用的几个调度器

- Dispatchers.Default

  所有标准协程构建器的默认调度器。它是由JVM上的一个共享线程池支撑，该调度器默认的最大并发度是CPU的核数，最低为2。

- Dispatchers.Unconfined

  非受限调度器，该调度器不会将操作限制在任何特定的线程上执行。它在发起协程的线程上执行第一个挂起点的操作；在挂起点恢复之后由对应的挂起函数决定接下来在哪个线程上执行。

  在该调度器上启动的子协程会组成一个EventLoop以避免栈溢出

- Dispatchers.IO

  IO调度器，该调度器将阻塞的IO任务分流到一个共享的线程池中，使得不阻塞当前线程。

  该线程池大小为环境变量kotlinx.coroutines.io.parallelism的值，默认是64或核心数的较大者。

  该调度器和Dispatchers.Default共享线程，因此使用withContext(Dispatchers.IO)创建新的协程不一定会导致线程的切换。

- Dispatchers.Main

  该调度器限制所有执行都在UI主线程，它是专门用于UI的，并且会随着平台的不同而不同

  - 对于JS或Native，其效果等同于Dispatchers.Default
  - 对于JVM，它是Android的主线程、JavaFx或者Swing EDT的dispatcher之一。

  并且为了使用该调度器，还必须增加对应的组件

  - kotlinx-coroutines-android
  - kotlinx-coroutines-javafx
  - kotlinx-coroutines-swing

如果创建协程时不指定调度器，则默认使用父协程的调度器。

#### 父子协程

父协程中启动的新协程为该协程的子协程（GlobalScope中的除外）。

父协程会自动等待所有子协程执行结束，不必显式调用join

#### 命名协程

协程可以命名

```kotlin
val v2 = async(CoroutineName("v2coroutine")) {
    delay(1000)
    log("Computing v2")
    6
}
```

#### 组合上下文元素

翻看CoroutineContext的定义，可以发现它是一个Element元素的集合，而CoroutineName、Dispatcher、Job都是Element的子类型，在启动协程时指定的其实是Element，创建方法将他们包装成CoroutineContext对象

正因为此，可以组合多个上下文元素

```kotlin
// 分别指定上下文的调度器和新的协程名
launch(Dispatchers.Default + CoroutineName("test")) {
    println("I'm working in thread ${Thread.currentThread().name}")
}
```

### 协程作用域 CoroutineScope

CoroutineScope抽象了协程的作用域，所有的协程构建函数都是构建在CoroutineScope的扩展，不信你去看它们的定义

一般来说，使用withContext、GlobalScope.launch等创建新协程会自动创建一个CoroutinScope。

如果想创建一个独立的CoroutineScope的对象，可以调用CoroutineScope()和MainScope()工厂方法。

如果要将自己的类与CoroutineScope无缝衔接，可以继承它，常规的方式是使用委托，而不是直接实现

```kotlin
class Activity : CoroutineScope by CoroutineScope(Dispatchers.Default) {......
```

#### 看源码

CoroutineScope接口中仅包含了一个CoroutineContext，它会在CoroutineScope扩展的构建方法中被使用。我们只能只能操作上下文中的Job

- GlobalScope

  GlobalScope仅是继承自CoroutineScope的单例对象，它维护着一个EmptyCoroutineContext(里面啥都没有)

  GlobalScope.launch的launch就是CoroutineScope的launch，只不过launch函数会在GlobalScope没有调度器时安装一个Dispatchers.Default调度器，所以它能正常启动。

- coroutineScope{}

  该挂起函数创建一个新的作用域。该作用域内，除Job外都继承自父作用域。

### 协程日志

常规日志仅会打印当前线程，当使用协程时，仅打印线程是没用的，最好将协程也打出来，该问题可以用JVM参数解决

```bash
-Dkotlinx.coroutines.debug
```



### 异步流

首先说，Kotlin中的异步流和RxJava中的流在概念上非常类似，可以被归为响应式流。并且Kotlin也提供响应的库将它转换为其它响应式流

- kotlinx-coroutines-reactive 用于Reactive Streams
- kotlinx-coroutines-reactor  用于Project Reactor
- kotlinx-coroutines-rx2      用于RxJava2

#### 为什么需要异步流

一个挂起函数能够异步地返回单一的值，如果我们要异步返回多个值并针对每个都做处理呢？这也是一般响应式流遇到的场景，在Kotlin中，异步流用来解决它。

**已有现成的响应式流，为啥还有异步流？**

从功能上说，现有的响应式流库能够解决问题，但在Kotlin中不够优雅。Flow的设计目标是拥有尽可能精简的设计，能够完美融合到Kotlin的协程API中。

#### 示例

```kotlin
  val flow = flow {
    // 耗时操作1
    delay(1000)
    emit(12)
    // 耗时操作2
    delay(1000)
    emit(13)
  }

  runBlocking {
    flow.collect { println(it) }
  }
```

- flow接收的lambda表达式是一个协程环境，里面的操作实在一个协程中执行
- collect是收集操作，**只有收集时才会真的去执行流中定义的逻辑**

- flow也可以被取消

#### 构建流

- flow {}
- (1..3).asFlow()

#### 操作符

**转换操作符**

- map
- filter
- transform
- take

**末端操作符**

末端操作符会触发流逻辑的执行

- toList
- toSet
- first/single
- reduce/fold

#### 流的上下文保存

流的收集总是在调用它的协程的上下文中运行，无论流的实现细节如何，该属性称为上下文保存

对应地，flow{...}构建器中的代码运行在相应流的收集器提供的上下文中

如果要想修改构建器能够工作在其它上下文中，只能使用flowOn，而不能强行使用withContext

```kotlin
val flow = flow {
    ......
}.flowOn(Dispatchers.Default)

runBlocking {
    flow.collect { println(it) }
}
```

#### 其它

其它功能这里不一一列举

- 缓冲
- 异常捕获等



### 通道

通道用于在多个协程之间传输数据。

Channel是和BlockingQueue非常相似的概念。不同的是写入和读取数据用的是异步的send和recieve

#### 构建通道

- 直接通过Channel构造函数

  ```kotlin
  val channel = Channel<Int>()
  launch {
      for (x in 1..5) channel.send(x * x)
      channel.close()
  }
  for (y in channel) println(y)
  println("Done!")
  ```

- 使用produce构建器

  ```kotlin
  val channel = produce {
      send(12)
      send(13)
  }
  
  for (value in channel) {
      println(value)
  }
  ```

在通道正常工作的情况下，在通道没有数据时，调用recieve会导致协程挂起；在通道缓冲满时，调用send会导致协程挂起。

#### 通道是公平的

发送和接收操作遵循FIFO原则，先调用的协程会先发送/接收数据



### 异常处理

当协程被取消时，会在挂起点抛出CancellationException，

#### 协程异常与父协程

如果协程遇到异常，则它的父协程会被cancel，它的姊妹协程也都会被cancel。

#### CoroutineExceptionHandler

这是一个上下文元素，可以在协程启动时注册，该协程内异常将由它处理

```kotlin
val handler = CoroutineExceptionHandler { _, exception -> 
    println("Caught $exception") 
}
val job = GlobalScope.launch(handler) {
    throw AssertionError()
}
```

#### 异常的传播特性

Kotlin协程异常处理有两种风格

- 自动传播异常 - launch构建器
- 将异常交由用户处理 - async构建器

### 监督

取消是双向机制，但有时候想要单向取消一个协程，即强制取消，就需要用到监督。

SupervisorJob，与Job不同，SupervisorJob的取消只会向下传播

使用方法是创建一个SuervisorJob然后当做上下文元素传入

#### 监督作用域

supervisorScope{}可以用来替代coroutineScope{}，它只会单向传播，并且当作业自身执行失败时将所有子作业取消。

### 共享的可变状态与并发

Kotlin的协程从本质上来说就是更好用的线程的封装，因此还是有可能会面临多线程并发问题。最主要的问题是访问共享的可变状态。

总的说来，解决并发访问的方法有如下几种

- 使用并发数据结构，如Atomic数据类
- 限制访问数据的协程在指定的单线程上运行——newSingleTreadContext()
- 使用互斥量，即对访问数据的代码块加锁——Mutex类
- 使用actor块

#### actor

actor是由一个协程、其封装的状态、一个它与其它协程通信的通道组成。我们在创建actor后，通过其返回的消息邮箱的引用向actor发送消息以操作该actor执行某些动作或回复它的状态值。

和scala一样，正确使用actor的方式是先创建消息类，然后定义actor，然后发送消息

```kotlin
// 计数器 Actor 的各种类型
sealed class CounterMsg
object IncCounter : CounterMsg() // 递增计数器的单向消息
class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg() // 携带回复的请求

fun main() = runBlocking<Unit> {
  val counter = actor<CounterMsg> {
    var counter = 0 // actor 状态
    for (msg in channel) { // 即将到来消息的迭代器
      when (msg) {
        is IncCounter -> counter++
        is GetCounter -> msg.response.complete(counter)
      }
    }
  }
  withContext(Dispatchers.Default) {
    counter.send(IncCounter)
  }
  // 发送一条消息以用来从一个 actor 中获取计数值
  val response = CompletableDeferred<Int>()
  counter.send(GetCounter(response))
  println("Counter = ${response.await()}")
  counter.close() // 关闭该actor
}
```

actor能够并发安全的原因是它将并发过来的请求存储在通道中，再一个一个地处理。达到了不需要加锁的串行调用。比直接并发安全，比加锁高效。



## 协程源码解读

### CoroutineScope

为新的协程定义了一个scope，每一个协程构建器都是CoroutineScope的扩展，并且生成的协程会继承该CoroutineScope的coroutineContext（通过调用CoroutineScope.coroutineContext），以自动传播上下文和取消。

最好的得到一个单独的scope的方法是两个工厂方法：CoroutineScope()和MainScope()。

在自己的类实现CoroutineScope最好的方式是通过委托的方式继承CoroutineScope，而不是直接实现它。自己的类实现CoroutineScope后应该有一个良好的生命周期管理，一个好的示例：

```kotlin
class MyActivity: AppCompactActivity(), CoroutineScope by MainScope(){
    override fun onDestroy(){
        cancel()
    }
    
    fun showSomeData() = launch {
        draw(data)
    }
}
```

CoroutineScope的接口定义如下

```kotlin
public interface CoroutineScope {
    
    // 这里只有一个上下文属性，它用在协程构建器上
    // 一般是不能手动操作该对象的，只能操作其中的Job
    public val coroutineContext: CoroutineContext
}
```

#### MainScope()

```kotlin
public fun MainScope(): CoroutineScope = ContextScope(SupervisorJob() + Dispatchers.Main)
```

创建了一个带有SupervisorJob和Main分发器的Scope，用于UI绘制

#### GlobalScope

```kotlin
public object GlobalScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}
```

可以看到GlobalScope是一个单例对象，它的上下文是一个空的上下文。

一个单例对象，就意味着该scope是从JVM启动时就加载了该类；在JVM关闭时才会销毁，意味着该scope的生命周期和整个应用程序一致。同时意味着如果协程的逻辑有问题导致协程不停止，则该协程永远不会停止；如果这类协程过多，则会造成内存泄露；而又由于它拥有的是一个空的上下文，因此每次调用launch启动的协程之间都是独立的，相互之间没有任何影响。

#### coroutineScope{}

一个挂起方法，意味着它会阻塞协程，会等待传入的挂起块和子协程执行完成后再返回。

创建一个新的CoroutineScope并用该新创建的scope调用传入的挂起块。

新创建的scope会继承外部scope的coroutineContext，只是覆盖了Job，**这是关键，意味着在该scope内的协程和外部scope脱钩了，如果内部协程挂了，也不会导致外部协程退出，只会导致该scope挂掉。**

它的存在是为了将一个长耗时任务拆分成多个并行的短耗时任务，根据上面的分析，它完全集成了外部scope，唯一的区别是覆盖了job，让内部协程与外部脱钩，使得即使该长耗时任务失败了也没关系，捕获异常后不会影响外部协程。

它是一个挂起方法，如果发生了异常，可以直接捕获而不影响外部协程和scope的运行。

##### 什么时候使用？

看起来是在suspend方法中启动协程专用。官方说它用于并行解析（实在看不懂）



### Job

从概念上讲，一个job是一个带有生命周期的的可取消的东西，当job完成时，它的声明就走到终点。

Job能够被组织成父子层次结构

- 一旦父job退出，所有子job会马上退出。
- 子job抛出CancellationException以外的异常会导致父job马上退出

最基本的创建Job实例的方式是通过launch启动协程后得到的Job。默认情况下任何job的子job的失败将导致其父job的失败，进而取消剩余父job的所有其它子job。该行为在SupervisorJob有所不同

一个job的所有状态如下

| **State**                        | [isActive] | [isCompleted] | [isCancelled] |
| -------------------------------- | ---------- | ------------- | ------------- |
| _New_ (optional initial state)   | `false`    | `false`       | `false`       |
| _Active_ (default initial state) | `true`     | `false`       | `false`       |
| _Completing_ (transient state)   | `true`     | `false`       | `false`       |
| _Cancelling_ (transient state)   | `false`    | `false`       | `true`        |
| _Cancelled_ (final state)        | `false`    | `true`        | `true`        |
| _Completed_ (final state)        | `false`    | `true`        | `false`       |

一般情况下，使用launch得到的job已经是Active状态的了，但如果使用LAZY的启动选项，将会得到一个New状态的Job，此时调用start才能启动。

各种状态转换关系

![image-20200506235514350](./Kotlin复习笔记/image-20200506235514350.png)

父协程等待所有子协程完成或取消时的状态是Completing或Cancelling，注意，Completing只是一个内部状态，从外部观察的话，得到的还是一个Active状态。

区分是取消还是异常导致的cancel，可以查看cancellation exception cause （getCancellationException()）

在coroutineContext中的Job实例，代表了协程本身。

### Scope、Job、Context的关系

Scope封装了Context，而在协程层次关系中，Job代表了该协程，放在了Context中。

CoroutineScope创建新协程时，会将新创建的协程的Job添加到原协程Job的child中，形成父子关系。

CoroutineScope执行取消操作时，会调用当前scope内封装的context中对应的job的cancel方法，从而cancel掉整个Job层次结构（因为Job代表了协程，所以也就cancel了整个协程）

### GlobalScope的launch和cancel会发生什么

#### GlobalScope.launch

从GlobalScope的定义可以看出来，它封装了一个空的协程上下文，即啥都没有，自然也没有Job。使用launch创建协程时，launch方法会为它分配一个默认的调度器Dispatchers.Default，新协程的Job在向父Job中添加自己时会因为父Job不存在而放弃。

这样新创建出来的协程，我们就只能通过该协程本身的Job去操作它和它的子协程的生命周期了。

#### GlobalScope.cancel

cancel操作会寻找当前scope封装的context对应的job，调用job.cancel，由于GlobalScope没有job，因此会报无法cancel的异常。

### 协程启动器对比

#### launch

CoroutineScope的扩展方法

启动一个新协程，不阻塞当前线程，并返回新协程的Job引用，当调用该Job的cancel时协程会被取消。

新协程的上下文从CoroutineScope中继承而来，也可以额外增加context元素.如果上下文中没有任何调度器或ContinuationInterceptor,则Dispatchers.Default将会被使用.

协程的父job继承子CoroutineScope,不过也可以通过适当的context元素进行覆盖

默认情况下,新协程会被马上调度执行,但也可以通过start参数设置为懒启动,只有调用job.start或job.join时才会实际执行.

新协程的未处理的异常将会导致父协程取消.

#### async

CoroutineScope的扩展方法

启动一个新协程,返回一个Deffered,包装了未来的结果.当调用Deffered.cancel时协程会被取消

在上下文继承、默认调度器、父job的选择等和launch一致

默认情况下，新协程马上执行，但也可以通过start参数设置为懒启动，只有调用job.start、job.join、deffered.await或awaitAll时才会启动

#### withContext

一个挂起方法

在给定的上下文中执行给定的挂起块，并返回结果。它并不启动协程。

用它执行的挂起块中的上下文是当前协程的上下文和新指定上下文的merge结果。

该方法使用新指定的上下文中的调度器，将挂起块的执行转移到其它线程上执行，执行完成后再切换回来。

注意withContext方法的调用是以一种可取消的方式分配给原上下文的，这意味着如果原上下文被cancel，该方法的结果将被忽略，并抛出一个CancellationException

#### runBlocking

一个裸方法，创建一个协程，并阻塞当前线程，直到协程执行完毕

该方法设计目的是在阻塞代码和以suspend风格编写的库之间架起桥梁，常用在main方法或测试中。

该构建器默认的调度器是一个EventLoop的内部实现，以在当前阻塞的线程上执行续体（Continuation）

如果调度器有显式指定，则新协程跑在新的调度器上，当前线程直接阻塞。如果指定的调度器是另一个runBlocking的EventLoop实现，则直接使用该EventLoop

如果当前阻塞的线程被中断（interrupt），则协程也会被取消，该runBlocking方法报InterruptedException

#### coroutineScope vs withContext

coroutineScope是withContext的特殊情况，前者比后者差的是一个切换上下文的功能。

coroutineScope目的在于将几个子协程的生命周期进行绑定，用于将一个任务解构成几个并发的子任务，任何一个子任务失败都会导致其它子任务的失败。我们无法改变子任务的上下文。

withContext目的不在于启动子协程，尽管它也能这样做。它主要用于暂时为当前协程切换上下文（主要是切线程）。它最初用于将长耗时操作从UI线程切换走，完成后再切回来。

#### coroutineScope和几个async嵌套有什么区别？

使用coroutineScope写法的代码

```kotlin

```

使用几个async嵌套写法的代码

```kotlin

```

## 协程系列文章研究

### [Coroutine Context and Scope](https://medium.com/@elizarov/coroutine-context-and-scope-c8b255d59055)

实际上非常相似东西，在不同方面的用处通常会给一个不同的名字，以强调该方面的目的。就像绳索一样，根据用途，海员对于绳索可能会有十来个单词，尽管他们本质上是一回事。

Kotlin中每个协程都有一个上下文——CoroutineContext，一个协程由Job代表，它负责协程的生命周期和父子关系。

Kotlin中也有一个CoroutineScope，它仅由一个CoroutineScope属性构成。

CoroutineContext和CoroutineScope看起来作用一致，他们的不同之处在于使用目的。

用于启动协程的launch被定义为CoroutineScope的扩展方法，启动时可传入新的上下文，启动的新协程将原scope的上下文和传入上下文结合（新传入上下文会覆盖原上下文），加上新协程的Job，构成新协程的上下文。

它们的关系如图

![image-20200509001457226](./Kotlin复习笔记/image-20200509001457226.png)

launch(和其它携程启动器)的CoroutineScope接收者的目的是当新协程启动时同时传入一个scope。一般情况下，CoroutineScope中的上下文都包含一个Job，它是所有新创建协程的父Job（GlobalScope除外）。

launch启动器中的CoroutineContext参数用于覆盖从父scope中继承的上下文。

一般情况下，我们不向launch中传入Job，以免打破协程的父子关系。除非想要显式地用NonCancellable这个job。

在一个scope中，scope的上下文属性和协程的上下文是一个对象。可以想象，scope包含context，context包含job，job代表协程，那scope包含的context和包含job的context本来就是一个。

```kotlin
fun main() {
  suspend fun test(): CoroutineContext {
      // 返回当前协程的上下文
    return coroutineContext
  }

  runBlocking {
    // true
    val outer = this.coroutineContext
    println(outer === test())
    launch {
      val inner = this.coroutineContext
      // true
      println(inner === test())
      // false
      println(outer === inner)
    }
  }
}
```

##### 禁止做

从编译通过上，如下代码是可以正常运行的。它用当前上下文构建一个新的Scope对象，并在该scope对象基础上启动一个新协程。

```kotlin
suspend fun doNotDoThis() {
    CoroutineScope(coroutineContext).launch {
        println("I'm confused")
    }
}
```

这样做的问题？这里对于方法调用者来说，并不会知道该方法有启动一个协程，虽然结果正确，但协程的启动应该遵从设计，从外部scope启动它，而不是自己无缘无故自己创建一个scope。协程是与其它代码并发的工作，它的启动必须明确。

上面的做法可能想要的效果是在方法中启动一个协程，方法异步返回。正确的做法是定义CoroutineScope的或者方法或将CoroutineScope传入方法

```kotlin
fun CoroutineScope.doThis() {
    launch { println("I'm fine") }
}

fun doThatIn(scope: CoroutineScope) {
    scope.launch { println("I'm fine, too") }
}
```

挂起方法的设计是非阻塞的，在它中不应有启动任何并发任务的副作用（比如启动协程）。挂起方法返回时，它之中启动的任务应该都已经完成。

综合stackoverflow上的二者对比，我认为比较规范的说法：

- CoroutineScope规范了CoroutineContext的继承和管理方式。

### [The reason to avoid GlobalScope](https://medium.com/@elizarov/the-reason-to-avoid-globalscope-835337445abc)



### [Structed concurrency](https://medium.com/@elizarov/structured-concurrency-722d765aa952)

结构化并发，不仅仅是一个功能，更是一个观念意识的巨大转变。

#### 直接launch和GlobalScope.launch

在一个scope中直接launch，scope负责了协程的生命周期；而GlobalScope.launch显式指明了它的全局性质，表明了它是全局的。并不是GlobalScope不能使用，而是只有在少数情况下你的协程需要和应用的生命周期一致时才能使用，而我们应该尽量将生命周期缩小。

#### 并行分解

借用文章中的代码，一般想要并行启动两个子协程并等待结果，我们可能想到这样做

```kotlin
suspend fun loadAndCombine(name1: String, name2: String): Image { 
    val deferred1 = async { loadImage(name1) }
    val deferred2 = async { loadImage(name2) }
    return combineImages(deferred1.await(), deferred2.await())
}
```

上面的代码只能作为作用域类的成员方法，其它情况会报错，这里只讨论编译正确的情况。假设下面这样使用。

```kotlin
class Example: CoroutineScope {
    ......
    fun test(){
        val job = launch {
            // do some job 1
            loadAndCombine("name1", "name2")
            // do some job 2
        }
        if (someCondition){
             job.cancel()
        }
    }
    suspend fun loadAndCombine(name1: String, name2: String): Image { 
        val deferred1 = async { loadImage(name1) }
        val deferred2 = async { loadImage(name2) }
        return combineImages(deferred1.await(), deferred2.await())
    }
    ......
}
```

分析，test()方法中启动了CoroutineScope作用域下第一级子协程；loadAndCombine同样启动了两个第一级子协程。问题在于，当someCondition为true时，job被cancel，按照业务逻辑，loadAndCombine下启动的两个协程也应该被cancel，但在现在这种情况下，这两个协程和test中启动的协程属于同级，所以不会受到job.cancel影响，如果大量这样的情况出现，将使得性能白白消耗。

为了使得job退出时另两个协程也退出，可以这样改，在async启动时加上(coroutineContext)参数，前面已经证明过，在协程中获取coroutineContext，得到的是协程的上下文对象，而不是作用域的。这样就使得协程2\3称为了协程1的子协程，当协程1取消时，协程2和协程3就都能够取消了。

```kotlin
class Example: CoroutineScope {
    ......
    fun test(){
        // 协程1
        val job = launch {
            // do some job 1
            loadAndCombine("name1", "name2")
            // do some job 2
        }
        if (someCondition){
             job.cancel()
        }
    }
    suspend fun loadAndCombine(name1: String, name2: String): Image { 
        // 协程2\3
        val deferred1 = async(coroutineContext) { loadImage(name1) }
        val deferred2 = async(coroutineContext) { loadImage(name2) }
        return combineImages(deferred1.await(), deferred2.await())
    }
    ......
}
```

不过这样还是会有一个问题。当协程2发生错误抛出异常时，协程3并不会收到影响，还会继续运行。如果想要这两个协程绑定，需要使用coroutineScope{}

```kotlin
class Example: CoroutineScope {
    ......
    fun test(){
        val job = launch {
            // do some job 1
            loadAndCombine("name1", "name2")
            // do some job 2
        }
        if (someCondition){
             job.cancel()
        }
    }
    suspend fun loadAndCombine(name1: String, name2: String): Image =
    coroutineScope { 
        val deferred1 = async { loadImage(name1) }
        val deferred2 = async { loadImage(name2) }
        combineImages(deferred1.await(), deferred2.await())
    }
    ......
}
```

使用coroutineScope包裹的块，当scope发生异常，或scope被取消时，其内部所有协程也都会被取消。

#### 结构化并发

##### 非结构化并发

https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/

goto语句的坏处：破坏代码的抽象性。让控制流从一个功能主题完全跳到另一个功能主体。使得在抽象时无法预测代码走向。

go语句带来的坏处：破坏代码的抽象性。无论何时调用一个函数，它都可能产生一些后台任务，函数貌似返回了，但它仍然在后台运行。如果不了解其中的逻辑，就不能知道它什么时候结束。**这违反了因果关系**。

go语句带来的坏处，在所有异步编程的代码中都会出现，你不知道你所调用的函数是否有新启动一个协程、或者新注册一个回调函数，这是这个问题，导致了背压、正确关闭等问题。

举个例子，在一个函数中打开文件，然后启动一个线程读取该文件，为了文件能够被正常读取和关闭，我们需要以某种方式跟踪该线程，但是一般我们只能手动处理这种问题，语言帮不了我们，这就是前面说的正确关闭问题。

尽管目前所有的异步框架和库都能够提供能够正确工作的代码，但都会通过比较麻烦，违反编程直觉的的方式，让你感觉自己并不是在编程，而是在使用某种工具。而且，异步还会丢失代码回溯的能力。

总之，**Go 语句的基本问题在于，当你调用一个函数时，你不知道它是否会产生一些后台任务，而这些后台任务在完成之后还会继续运行。** 

##### 结构化并发

结构化并发，就是将分裂的多个并发路径在最终再次连接起来。如下左图是非结构化，分为两个并发流，且其中一个结束，另一个不清楚；右图分为三个并发流，但最终再次合并为一个。

![image-20200517221308834](./images/image-20200517221308834.png)![image-20200517221326701](./images/image-20200517221326701.png)

一般结构化并发会需要一个运行环境，用于包装真正的异步操作，比如Kotlin的CoroutineScope。还会有取消机制、异常传播机制等。

##### Kotlin结构化并发

Kotlin的结构化并发关键在于CoroutineScope，好处是

- 负责其内协程的的生命周期
- 可以取消子协程
- 等待所有子协程完成

### [Explicit Concurrency](https://medium.com/@elizarov/explicit-concurrency-67a8e8fd9b25)

据说这篇文章可以解释为什么要用coroutineScope的问题。

有两个约定俗称的东西

1. 每一个声明为CoroutineScope的扩展方法的方法，都会马上返回，但是会并发地执行扩展方法指定的内容

   这也是runBlocking不是CoroutineScope的扩展方法的原因之一

2. 每一个仅声明为suspend的方法，会等待其内部逻辑完成后再返回给调用者

但是如下的方法签名结合了上述两个约定，代表它既会挂起调用者、也可能启动一个并发执行的逻辑。具体情况，还要看实现，但这种方式并不建议使用，因为调用者并不知道在该方法内是否启动新的协程，这是不好的，suspend方法就应该在所有任务都完成后再返回。如果在suspend方法内部有需要并发执行的内容，那就应该等待他们都完成后再返回，此时可以使用coroutineScope{}，而不是在方法签名上加上CoroutineScope扩展。

```kotlin
suspend fun CoroutineScope.obfuscate(data: Data)
```

根据经验，我们应该更倾向于使用挂起函数，因为并发并不是绝对安全的东西。

一定要抵制住直接使用GlobalScope启动协程的诱惑，因为这很容易让我们丢失对并发操作的跟踪，从而产生资源泄露等问题，

### [Blocking threads, suspending coroutines](https://medium.com/@elizarov/blocking-threads-suspending-coroutines-d33e11bf4761)

阻塞线程的场景

- 运行CPU密集型代码
- 使用阻塞IO

使用时要注意不要阻塞线程（使用NIO、不阻塞主要线程等方法）

通常所说的线程阻塞并不一定是Thread的Blocking状态。



协程的挂起，通过suspend关键字，不会阻塞线程。但suspend关键字并不能神奇地让你变得挂起。而是需要在协程作用域中才行。

当然，更加确切地说：挂起方法是不阻塞调用者的线程，该方法总要在某个线程上去运行。

### [Kotlin Coroutines pattern & anti-pattern](https://proandroiddev.com/kotlin-coroutines-patterns-anti-patterns-f9d12984c68e)

文章内容没啥，主要列举了两个需要遵循和不要踩的坑。

## 问题

### Kotlin支持多继承吗？



### actual、expect关键字

actual关键字在lazy方法中可以看到



### 协程的作用域是啥意思，和启动新的协程的区别？用法看起来一致



### 从名字subroutine、coroutine理解协程在计算机科学中的级别



### 父子协程是通过什么维护父子关系的？



### 各种常用的启动协程或作用域的方法的作用？



如果要深入，可以查看一些协程定义：CoroutineScope、CoroutineContext、Job

已经完成的协程需要关闭吗？协程的退出如何相互影响？



### 为什么suspend的CoroutineScope的扩展方法会报错呢？

suspend的原则：不要在suspend中启动协程，如果要启动，则要等待协程完全执行完成后再返回该挂起方法，这叫做结构化并发。

解答：suspend方法本身是运行在协程中，隐式地包含了一个coroutineContext对象；CoroutineScope本身也包含了coroutineScope对象。因此只要用到了coroutineContext的地方，就会产生歧义，编译器也不知道用哪个coroutineContext，从而报黄。

# Kotlin协程提案学习笔记

## 设计目标

- 不依赖于复杂的库提供的特定实现，如Future
- 同时涵盖 async/await 用例及 生成器代码块
- 使用Kotlin协程能够包装各种现有的异步API，如Java NIO、各类Future的实现等



协程，可被看做 可挂起的计算 的实例。即可以在某些点挂起，稍后在另一个线程上恢复执行。协程相互调用(相互传递数据)，即可形成协作式多任务处理机制。

