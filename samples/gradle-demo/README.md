# Gradle 本地引用 Starter

先把组件安装到本机 Maven 仓库，Gradle 通过 `mavenLocal()` 解析 SNAPSHOT。

在仓库根目录：

```bash
mvn -DskipTests install
```

再进入本目录：

```bash
./gradlew test --refresh-dependencies
```

`bootRun` 需要本机 Redis（`127.0.0.1:6379`）：

```bash
./gradlew bootRun
curl -X POST 'http://127.0.0.1:18080/orders/pay?orderId=1001'
```

业务工程里最少要有：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.github.coder-jeffery:distributed-lock-spring-boot-starter:0.1.0-SNAPSHOT'
}
```
