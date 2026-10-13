# Distributed Lock Starter

Spring Boot 3 / Java 17 分布式锁组件：Redis Lua 互斥、可重入、owner token 防误删、看门狗续期、`@DistributedLocked` 注解。

Maven 坐标（发布后）：

```xml
<dependency>
    <groupId>io.github.coder-jeffery</groupId>
    <artifactId>distributed-lock-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

本地尚未发布时，先 `mvn -DskipTests install`，版本用 `0.1.0-SNAPSHOT`。

Gradle 工程必须把 `mavenLocal()` 放在仓库列表最前，才能解析这个 SNAPSHOT：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.github.coder-jeffery:distributed-lock-spring-boot-starter:0.1.0-SNAPSHOT'
}
```

完整示例见 `samples/gradle-demo`，在仓库根目录 `mvn -DskipTests install` 后执行 `cd samples/gradle-demo && ./gradlew test`。

## 使用

需要可用的 Redis（沿用 `spring.data.redis.*`）。

编程式：

```java
@Service
public class OrderService {

    private final DistributedLockClient lockClient;

    public OrderService(DistributedLockClient lockClient) {
        this.lockClient = lockClient;
    }

    public void pay(String orderId) {
        DistributedLock lock = lockClient.getLock("order:" + orderId);
        if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("busy");
        }
        try {
            // critical section
        } finally {
            lock.unlock();
        }
    }
}
```

注解式（`key` 为 SpEL，字符串字面量要加引号）：

```java
@DistributedLocked(key = "'order:' + #orderId", waitTime = 3, leaseTime = 30, timeUnit = TimeUnit.SECONDS)
public void pay(String orderId) {
    // critical section
}
```

## 配置

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

distributed-lock:
  enabled: true
  prefix: dlock
  default-wait-time: 3s
  default-lease-time: 30s
  watchdog-enabled: true
  aop-order: 0
```

`aop-order` 默认 `0`，会包在 Spring `@Transactional` 外面：先拿锁再开事务。

## 模块

| 模块 | 作用 |
|------|------|
| `distributed-lock-core` | 锁 API + Redis Lua 实现 |
| `distributed-lock-spring-boot-autoconfigure` | 自动配置、注解、AOP |
| `distributed-lock-spring-boot-starter` | 给业务应用引入 |

锁实现要点：

- Redis Hash：`{ clientId:threadId -> holdCount }` + `PEXPIRE`
- 解锁 / 续期用 Lua，且校验 owner，避免删掉别人的锁
- 看门狗按 `lease/3` 续期；进程崩溃后靠 TTL 释放
- Redis 主从异步复制场景下，failover 仍可能丢锁；强一致请用 Redlock/fencing 或 Redis Cluster 共识方案

## 构建

```bash
mvn -B verify
```

需要 JDK 17+（本仓库用 `--release 17` 编译）。

## 发布到 Maven Central

1. 用 GitHub 登录 [Central Portal](https://central.sonatype.com)，确认 namespace `io.github.coder-jeffery` 已验证。
2. 生成 User Token，写入 `~/.m2/settings.xml`：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

3. 生成 GPG 主密钥并上传到 `keyserver.ubuntu.com` / `keys.openpgp.org`。
4. 把本仓库推到 `https://github.com/coder-jeffery/distributed-lock-starter`。
5. 改掉 SNAPSHOT 版本后执行：

```bash
mvn -B -P release -DskipTests deploy
```

默认不会自动 Publish；到 [Deployments](https://central.sonatype.com/publishing/deployments) 校验通过后再点发布。CI 可把插件的 `autoPublish` 设为 `true`。

版本一旦进入 Central 不能覆盖或删除。
