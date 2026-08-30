# service

A minimal Spring Boot REST service backed by PostgreSQL, using Spring Data JDBC and functional routing. Built as a learning project for Java 25 / Spring Boot 4.

## Tech stack

- **Java 25**
- **Spring Boot 4.1.1**
- **Maven** (with the `mvnw` wrapper)
- **PostgreSQL** (run via Docker Compose)

## Dependencies

From `pom.xml`, and why each one is included:

- **`spring-boot-starter-webmvc`** — gives the application the need to receive HTTP requests and send HTTP responses. It's a Function-based instead of Annotation-based → @RestController.
- **`spring-boot-starter-data-jdbc`** — gives access to Spring Data JDBC (`ListCrudRepository`), a lightweight ORM alternative to JPA/Hibernate. It maps the `Customer` model directly to SQL rows.

- **`org.postgresql:postgresql`** — the JDBC driver that lets the application talk to the PostgreSQL database at runtime. Required because Spring Data JDBC needs a concrete driver to open connections to `jdbc:postgresql://localhost/database`.

- **`spring-boot-starter-actuator`** — exposes production-readiness endpoints (health, info, metrics, etc.) under `/actuator`, useful for checking the app is up and inspecting its internal state without writing custom code.

- **`spring-boot-devtools`** *(dev tool, runtime/optional)* — enables automatic restarts and live reload during development, so code changes are picked up without manually stopping/starting the app.

- **`spring-boot-docker-compose`** *(dev tool, runtime/optional)* — would normally auto-detect `compose.yaml` and start/stop the Postgres container for you when the app runs. It is **commented out** in this project because the container's port is mapped manually (`5432:5432` in `compose.yaml`) and started explicitly with `docker compose up -d`, so Spring Boot's own compose lifecycle management is not needed.

- **`org.graalvm.buildtools:native-maven-plugin`** *(build plugin, GraalVM Native Support)* — allows compiling the application into a native executable via GraalVM instead of running on a standard JVM, for faster startup and lower memory footprint. Used together with the GraalVM JDK (Java 25 build) rather than a plain OpenJDK.

Test-scoped dependencies (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jdbc-test`, `spring-boot-starter-actuator-test`) mirror the runtime starters above and provide the corresponding testing utilities (e.g. `MockMvc`, embedded/test database support).

## Project structure

```
src/main/java/com/example/
├── ServiceApplication.java             # main class + functional routes (/hello, /customers)
├── model/Customer.java                 # @Table("customer") entity mapped by Spring Data JDBC
└── repository/CustomerRepository.java  # ListCrudRepository<Customer, Integer>

src/main/resources/
├── application.properties              # datasource config + schema init
├── schema.sql                          # creates the `customer` table
└── data.sql                            # seeds 5 sample customers

compose.yaml                            # PostgreSQL container definition
```

## Database

`compose.yaml` defines a single PostgreSQL container:

```yaml
services:
  postgres:
    image: 'postgres:latest'
    environment:
      - 'POSTGRES_DB=database'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=user'
    ports:
      - '5432:5432'
```

`application.properties` points the app at that same database:

```properties
spring.sql.init.mode=always
spring.datasource.url=jdbc:postgresql://localhost/database
spring.datasource.username=user
spring.datasource.password=secret

spring.aot.repositories.enabled=false
```

`spring.sql.init.mode=always` makes Spring Boot run `schema.sql` (drop/create the `customer` table) and `data.sql` (insert 5 rows) on every startup.

`spring.aot.repositories.enabled=false` disables Spring Data JDBC's ahead-of-time repository generation — see [GraalVM native image](#graalvm-native-image) below for why that's needed here.

## Running

1. Start the database:
   ```bash
   docker compose up -d
   ```
2. Compile the application
   ```bash
   mvn clean package -DskipTests
    ```
   (build only, skipping tests: `mvn clean package -DskipTests`)

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

The service app starts on `http://localhost:8080`.

Ensure Java 25 is provided by Homebrew (personally, I prefer homebrew)
```
    brew search graalvm
```

And
```
brew search --cask graalvm
```

INSTALL graalvm-jdk@25
```
brew install --cask graalvm-jdk@25
```

ENSURE Java versions
```
/usr/libexec/java_home -V
    Matching Java Virtual Machines (2):
    25.0.4 (arm64) "Oracle Corporation" - "Oracle GraalVM 25.0.4+7.1" /Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home
    17.0.18 (arm64) "Homebrew" - "OpenJDK 17.0.18" /opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home

```
And
```
brew list --cask | grep graal
```

> **Note:** the project targets Java 25. If multiple JDKs are installed locally, switch to a Java 25/GraalVM build before running Maven — e.g. via a shell function that sets `JAVA_HOME` (see `~/.zshrc`):
 ```
 nano ~/.zshrc
```

````
 java17() {
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    export PATH="$JAVA_HOME/bin:$BASE_PATH"
    echo "Java 17 activated"
    java --version
 }
 
 java25() {
   export JAVA_HOME=$(/usr/libexec/java_home -v 25)
   export PATH="$JAVA_HOME/bin:$BASE_PATH"
   echo "Java 25 / GraalVM activated"
   java --version
 }
 ````
 
```
# SAVE THE MODIFICATION
 1. Ctrl + O
 2. Press Enter
 3. Ctrl + X
```

```
# SOURCE THE MODIFICATION
 source ~/.zshrc
```

```
# CHOOSE DIFFERENT JAVA VERSIONS BASED ON THE CORRESPONDING PROJECT
 export PATH="$JAVA_HOME/bin:$PATH"
 source ~/.zshrc
 java25
```

Show GraalVM Native Image version
```
native-image --version
```

Existing setup
```
Homebrew
│
├── OpenJDK 17
│
└── GraalVM JDK 25
```

> `java25` here is a custom shell function (defined in `~/.zshrc`) that points `JAVA_HOME`/`PATH` at the installed GraalVM JDK 25 and prints its version — it must be active before running the `native:compile` build below, since only a GraalVM JDK ships the `native-image` tool.

## GraalVM native image

[GraalVM](https://www.graalvm.org/) is a JDK distribution that, in addition to running Java normally on a JVM, ships a `native-image` tool capable of **ahead-of-time (AOT) compiling** a Java application into a standalone native executable — no JVM required at runtime. The trade-off: startup time and memory footprint drop dramatically (useful for CLIs, serverless, or containers), at the cost of a much longer build step and stricter constraints around reflection/dynamic class loading, which is why Spring Boot needs its own AOT processing pass to generate the metadata `native-image` relies on.

Build the application as a native executable with GraalVM

```bash
# 1 - build the application as a native executable with GraalVM
./mvnw -DskipTests -Pnative native:compile
```

>- **`-Pnative`** activates the `native` Maven profile (contributed by `spring-boot-starter-parent`), which wires the `org.graalvm.buildtools:native-maven-plugin` (declared under `<build><plugins>` in `pom.xml`) and Spring Boot's `process-aot` goal into the build.

>- **`process-aot`** runs first: it actually starts the Spring application context to record, ahead of time, which beans, reflection targets, and proxies the app needs — work that would otherwise happen dynamically at runtime (something `native-image` can't do on its own).

>- **`native:compile`** is the goal that then invokes GraalVM's `native-image` tool, compiling the compiled classes plus the AOT-recorded metadata into a single native binary under `target/` (e.g. `target/service`), runnable directly with `./target/service` — no `java` command needed.

> - **`-DskipTests`** skips the test phase, since native compilation is already a slow step on its own.

> **Note:** because `process-aot` actually starts the Spring context, it will try to resolve real beans — including ones backed by the database — at build time, not just at runtime. This project disables Spring Data JDBC's AOT repository generation (`spring.aot.repositories.enabled=false` in `application.properties`) specifically to avoid needing a live Postgres connection during this build step.


```bash
# 2 - Enter the target directory
cd target
```

```bash
# 3 - Check the disk usage in human-readable
du -hs .
```

```bash
# 4 - RUN THE APPLICATION ( as native image & JVM on local machine )
./service
```

SUMMARY
```
// RUN THE APPLICATION either via :
1. ./service                → Native Image
2. java -jar service.jar    → JVM
```

## Endpoints

### `GET /hello`

```json
{
  "message": "Hello World"
}
```

### `GET /customers`

```json
[
  { "id": 1, "name": "Josh" },
  { "id": 2, "name": "Tom" },
  { "id": 3, "name": "Ben" },
  { "id": 4, "name": "Luke" },
  { "id": 5, "name": "Sam" }
]
```

### `GET /actuator`

```json
{
  "_links": {
    "self": {
      "href": "http://localhost:8080/actuator",
      "templated": false
    },
    "health": {
      "href": "http://localhost:8080/actuator/health",
      "templated": false
    },
    "health-path": {
      "href": "http://localhost:8080/actuator/health/{*path}",
      "templated": true
    }
  }
}
```

### `GET /actuator/health`

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```
