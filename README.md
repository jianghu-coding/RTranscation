### 引言
在写之前先来了解一下，分布式事务的一些解决方案。  

现目前处理分布式事务的方案有很多，比如
- 基于 XA 协议的方案
- 基于业务逻辑的 TCC 协议方案
- 基于 SAGA 协议的方案 

而实现了对应的协议的有  

- 在 Java 中基于 XA 协议实现的框架有 Atomikos，JOTM 等框架
- 实现了 TCC 协议的框架有 Sharding、EasyTransaction、tcc-transaction 等框架  

对于 XA 协议来说，它使用的是 2PC 协议的方式，是阻塞式的，并且它还依赖于数据库自身提供的 XA 接口的可靠性，对于大部分商业数据库来说做的都还蛮不错，在 Mysql 中只有 InnoDB 引擎支持 XA 分布式事务
### XA 协议
XA 协议由 Tuxedo 首先提出的，并交给X/Open组织，这个组织随即定义了一套分布式事务的标准即
X/Open DTP(X/Open Distributed Transaction Processing Reference Model) 即一些列的接口各个厂商需要遵循这个标准来实现

在 DTP 模型中定义了五个组成元素
- AP（Application Program）：也就是应用程序，可以理解为使用 DTP 的程序
- TM（Transaction Manager）：事务管理器，负责协调和管理事务，提供给 AP 编程接口以及管理资源管理器
- RM（Resource Manager）：资源管理器，可以理解为一个 DBMS 系统，或者消息服务器管理系统，应用程序通过资源管理器对资源进行控制，资源必须实现 XA 定义的接口（比如 Mysql 就实现了对 XA 协议的支持）
- 通信资源管理器（Communication Resource Manager）：它规定了，对于需要跨应用的分布式事务，事务管理器彼此之间需要进行通信，就需要通过这个组件来完成
- 通信协议（Communication Protocol）
  + XA 协议：应用或应用服务器与事务管理之间通信的协议
  + TX 协议：全局事务管理器与资源管理器之间通信的接口
![](https://user-gold-cdn.xitu.io/2019/10/13/16dc451f8b46ad75?w=760&h=788&f=png&s=278515)

其中在 DTP 中又定义了以下几个概念
- 全局事务：资源管理器会操作多个分支事务，根据分支事务的准备情况决定是提交还是回滚
- 分支事务：一个全局事务中会有多个分支事务，统一由资源管理器来管理，比如购买商品这个方法，可能涉及到扣款事务方法，生成订单事务方法，增加财务流水事务方法调用等
- 控制线程：需要表示全局事务以及分支事务的关系

DTP 模型主要是通过 2PC 来控制事务管理器和资源管理之间的交互的
- 第一阶段：准备阶段，事务管理器询问资源管理器分支事务是否正常执行完毕，资源管理返回是或者否
- 第二阶段：提交阶段，事务管理器根据上一阶段所有资源管理器的反馈结果，如果都是那么提交事务，如果否一个失败，那么回滚事务

#### XA 优缺点
优点：可以做到对业务无侵入，但是对事务管理器和资源管理器要求比较高  

缺点：
- 同步阻塞：在二阶段提交的过程中直到 commit 结束为止，所有节点都需要等到其它节点完成后才能够释放事务资源，这种同步阻塞极大的限制了分布式系统的性能
  + 比如原本在一个购买商品的场景中，一个扣款服务可以直接完成提交事务释放锁资源，现在需要等到订单生成，账户流水记录，积分增加扣减等操作完成后才能释放
  + 并且如果说在这个操作中，资源管理器因为网络等原因没有办法收到事务管理器的指令，那么它会一直处于阻塞状态状态而不释放锁资源
- 单点问题：协调者（事务管理器）如果一旦出现故障那么整个服务就会不可用，所以需要实现对应的协调者（资源管理器）选举操作
- 数据不一致：如果说所有资源管理器都反馈的准备好了，这时候进入 commit 阶段，但是由于网络问题或者说某个资源管理器挂了，那么就会存在一部分机器执行了事务，另外一部分没有执行导致数据不一致
  + 当然这种情况如果说存在协调者选举的话可以一定程度的避免，比如说协调者同时向 A、B、C 三个服务器发出 commit，这时 A 和 B commit 成功，但是 C 在收到命令前就挂了，那么这个时候 C 如果不需要恢复了就不需要管它了（以后启用的时候可以通过比如 mysql binlog 等同步数据）
  + 如果说 C 过一会就恢复了的话，协调者可以保存之前提交的状态，C 去询问协调者该 commit 还是 rollback，协调者就去检查自己的记录去看一下 A 和 B 上次是 commit 成功了还是失败了，然后给 C 发送指令去执行完成这个事务，最终让数据保持一致
  + 但是说，如果说协调者再发送指令给 A、B、C 后它自己立马就挂了，这个时候正好 C 正好执行完了操作后（可能是 commit 可能是 rollback）后也挂了，碰巧的是，协调者马上选举完成了然后 A 和 B 返回成功了，这个时候如果 A 和 B 都 commit 了，而 C rollback 了那么就会造成数据不一致了（因为 C 已经执行完了这个事务，不像上一个场景没有执行事务或者事务执行失败，它可以再执行或者回滚了）
- 容错性不好：如果说在提交询问阶段，参与者挂了那么这个时候协调者就只能依靠超时机制来处理是否需要中断事务

可以看出 2PC 协议存在阻塞低效和数据不一致的问题，所以在大型应用需要较高的吞吐量的应用是很少使用这种方案的

### JTA 事务规范
JTA（Java Transaction API）即 Java 事务 API 和 JTS（Java Transaction Service）即 Java 事务服务，他们为 JAVA 平台提供了分布式事务服务，可以把 JTA 理解为是 XA 协议规范的 Java 版本。它也采用的是 DTP 模型  

我们知道 JTA 它只是一个规范，定义了如何去于实现了 XA 协议的资源管理器交互的接口，但是并没有对应的实现，官方推荐 Atomikos 来实现 XA 分布式事务，感谢的可以直接取研究 Atomikos 源码。  
### 基于 XA 协议实现一个分布式事务处理框架
可以通过对 XA 协议的实际应用来加深我们的理解，代码如下
```java
    public static void test() {
        try {
            // 开启全局事务
            transactionManager.begin();
            // 向服务器 A 数据库写入数据
            saveDB1();
            // 向服务器 B 数据库写入数据
            saveDB2();
            // 询问 RM 分支事务是否准备就绪
            boolean prepareSuccess = transactionManager.prepare();
            // 目前没有涉及到远程事务的支持，在本地都是同步的方式调用所以此处没有做做阻塞等待而是返回立刻知道是否成功
            // 如果涉及到远程事务的支持，那么此处应该就有一个阻塞唤醒机制
            if (prepareSuccess) {
                // 开始提交分支事务
                transactionManager.commit();
            } else {
                // 回滚
                transactionManager.rollback();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 如果出错了就进行回滚各分支事务
            try {
                transactionManager.rollback();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        } finally {
            // 资源回收
            GlobalInfo.remove();
        }
    }
    
    private static void saveDB1() throws XAException, SQLException {
        // 因为存在多个数据源，所以需要指定是使用哪一个数据源
        XAResourceManager xaResourceManager = RMUtil.getResourceManager(dbPool1);
        // 分支事务开启
        xaResourceManager.begin();
        xaResourceManager.execute("insert into test1(name, age)  values('pt', 21)");
        // 事务执行完毕处于准备阶段等待 TM 下达 commit 指令
        xaResourceManager.prepare();
    }

    private static void saveDB2() throws XAException, SQLException {
        XAResourceManager xaResourceManager = RMUtil.getResourceManager(dbPool2);
        // 分支事务开启
        xaResourceManager.begin();
        xaResourceManager.execute("insert into test2(name, age) values('tom', 22)");
        // 事务执行完毕处于准备阶段等待 TM 下达 commit 指令
        xaResourceManager.prepare();
        // 测试回滚
        // throw new RuntimeException("xx");
    }
```
#### 包结构
![](https://user-gold-cdn.xitu.io/2019/10/13/16dc48473aeccdf5?w=538&h=606&f=png&s=57765)
- db：中存放了数据库连接池的实现，可以实现多数据源的切换
- rm：中存放了资源管理器的实现，主要是针对分支事务的处理与隔离
- tm：中存放了事务管理器的实现，它主要是去操作 rm 包来完成全局事务的提交或者回滚

代码放在了 github 上面感兴趣的可以了解一下

[github 代码链接](https://github.com/jianghu-coding/RTranscation)

参考：
- [JTA规范事务模型](http://www.tianshouzhi.com/api/tutorials/distributed_transaction/386)
- [深入理解分布式系统的2PC和3PC](https://www.hollischuang.com/archives/1580)
- [分布式事务原理及解决方案](https://juejin.im/post/5bf379b4e51d457e052fe5e0#heading-4)
TODO
- 远程事务的支持 
- 容错机制