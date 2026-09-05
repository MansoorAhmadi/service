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

- **`spring-boot-starter-actuator`** — ``exposes production-readiness endpoints (health, info, metrics, etc.) under `/actuator`, useful for checking the app is up and inspecting its internal state without writing custom code.``

- **`spring-boot-devtools`** *(dev tool, runtime/optional)* — enables automatic restarts and live reload during development, so code changes are picked up without manually stopping/starting the app.

- **`spring-boot-docker-compose`** *(dev tool, runtime/optional)* — would normally auto-detect `compose.yaml` and start/stop the Postgres container for you when the app runs. It is **commented out** in this project because the container's port is mapped manually (`5432:5432` in `compose.yaml`) and started explicitly with `docker compose up -d`, so Spring Boot's own compose lifecycle management is not needed.

- **`org.graalvm.buildtools:native-maven-plugin`** *(build plugin, GraalVM Native Support)* — allows compiling the application into a native executable via GraalVM instead of running on a standard JVM, for faster startup and lower memory footprint. Used together with the GraalVM JDK (Java 25 build) rather than a plain OpenJDK.

- **`io.spring.javaformat:spring-javaformat-maven-plugin`** *(build plugin)* — enforces Spring's own code style automatically at build time, failing `mvn package` on formatting violations.

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

## Code formatting (Spring Java Format)

`spring-javaformat-maven-plugin` is bound to the `validate` phase, so `mvn package` fails the build if any source file violates Spring's code style — before compilation even starts.

Failing example — formatting violations found:

```
$ mvn package

[INFO] --- spring-javaformat:0.0.48:validate (default) @ service ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal io.spring.javaformat:spring-javaformat-maven-plugin:0.0.48:validate (default) on project service: Formatting violations found in the following files:
[ERROR]  * /Users/user/java_course/service/src/main/java/com/example/repository/CustomerRepository.java
[ERROR]  * /Users/user/java_course/service/src/main/java/com/example/ServiceApplication.java
[ERROR]  * /Users/user/java_course/service/src/main/java/com/example/model/Customer.java
[ERROR] 
[ERROR] Run `spring-javaformat:apply` to fix.
```

Fix it with `spring-javaformat:apply`, which rewrites the offending files in place:

```
$ mvn spring-javaformat:apply

[INFO] --- spring-javaformat:0.0.48:apply (default-cli) @ service ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Passing example — `mvn package` after `apply`:

```
$ mvn package

[INFO] --- spring-javaformat:0.0.48:validate (default) @ service ---
[INFO] 
[INFO] --- resources:3.5.0:resources (default-resources) @ service ---
...
[INFO] --- jar:3.5.1:jar (default-jar) @ service ---
[INFO] --- spring-boot:4.1.1:repackage (repackage) @ service ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

## Java version management (SDKMAN)

This project no longer relies on Homebrew-installed JDKs or manual `JAVA_HOME` shell functions to switch Java versions. **SDKMAN is the only tool used to install and switch Java versions on this machine** — Homebrew-installed JDKs (`openjdk@17`, the standalone `graalvm-25.jdk`) were removed beforehand so there is no ambiguity about which `java` is picked up.

SDKMAN is a command-line tool that installs, manages, and switches between multiple versions of SDKs (Java, Maven, Gradle, etc.) — see the [official usage docs](https://sdkman.io/usage/) for the full reference.

### Necessary commands

| Command | Description |
|---|---|
| `sdk version` | Show the installed SDKMAN script/native version. |
| `sdk list` | List every SDK (Java, Maven, Gradle, …) that SDKMAN can manage. |
| `sdk list java` | List installable Java vendors/versions for this platform, flagging which are installed (`*`) or in use (`>`). |
| `sdk install java <identifier>` | Download and install a specific Java build (e.g. `17.0.12-graal`). |
| `sdk uninstall java <identifier>` | Remove an installed Java version. |
| `sdk use java <identifier>` | Switch Java version for the **current shell only** (temporary). |
| `sdk default java <identifier>` | Set a Java version as the **global default** for every new shell. |
| `sdk current java` | Show which Java version is currently active as the default. |
| `sdk env init` | Create a `.sdkmanrc` file in the current directory, pinning the Java version this project should use. |
| `sdk env` | Apply the versions listed in the current directory's `.sdkmanrc` to the shell. |
| `sdk env clear` | Reset the shell back to the versions used before `.sdkmanrc` was applied. |
| `sdk env install` | Install every SDK version listed in `.sdkmanrc` that isn't already installed. |
| `sdk config` | Open SDKMAN's config file, e.g. to toggle `sdkman_auto_env` (see below). |

### Enable automatic switching first (`sdkman_auto_env`)

Set `sdkman_auto_env=true` via `sdk config` **before** pinning any project directory below. This is what makes `cd`-ing into a directory with a `.sdkmanrc` automatically activate the Java version listed in it, instead of requiring a manual `sdk env` on every visit:

```
$ sdk config
...
4 sdkman_auto_env=true
...
```

> Strictly speaking, `sdk use java <identifier>` and `sdk env init` themselves work regardless of this setting — `sdkman_auto_env` only controls whether `cd`-ing into a directory *automatically* re-runs `sdk env` for you. But since the whole point of pinning each project below is switching Java by directory alone (no manual step per `cd`), enable it first.

### Installing and setting a Java version

```
$ sdk install java 17.0.12-graal

Downloading: java 17.0.12-graal

In progress...
################################################################################################################################ 100.0%

Repackaging Java 17.0.12-graal...

Done repackaging...
Cleaning up residual files...

Installing: java 17.0.12-graal
Done installing!

Do you want java 17.0.12-graal to be set as default? (Y/n): y

Setting java 17.0.12-graal as default.
```

```
$ java -version
java version "17.0.12" 2024-07-16 LTS
Java(TM) SE Runtime Environment Oracle GraalVM 17.0.12+8.1 (build 17.0.12+8-LTS-jvmci-23.0-b41)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 17.0.12+8.1 (build 17.0.12+8-LTS-jvmci-23.0-b41, mixed mode, sharing)
```

### Per-project Java version with `.sdkmanrc`

This project (`~/java_course/service`) targets **Java 25**, so a GraalVM 25 build is installed and pinned locally instead of set as the global default:

```
$ cd ~/java_course/service
$ sdk use java 25.3.4+1.r25-graal
Using java version 25.3.4+1.r25-graal in this shell.

$ sdk env init
.sdkmanrc created.

$ cat .sdkmanrc
# Enable auto-env through the sdkman_auto_env config
# Add key=value pairs of SDKs to use below
java=25.3.4+1.r25-graal
```

```
$ sdk current java
Current default java version 25.3.4+1.r25-graal
```

### Other projects pinned to Java 17

Two other local projects are pinned to a different Java version (`17.0.12-graal`) the same way — `sdk use` then `sdk env init` in each project's own directory:

```
$ cd ~/delta/delta-gss/delta-gss-distribution
$ sdk use java 17.0.12-graal
Using java version 17.0.12-graal in this shell.

$ sdk env init
.sdkmanrc created.

$ cat .sdkmanrc
# Enable auto-env through the sdkman_auto_env config
# Add key=value pairs of SDKs to use below
java=17.0.12-graal
```

```
$ cd ~/delta/SesamEO/sesame-distribution
$ sdk use java 17.0.12-graal
Using java version 17.0.12-graal in this shell.

$ sdk env init
.sdkmanrc created.

$ cat .sdkmanrc
# Enable auto-env through the sdkman_auto_env config
# Add key=value pairs of SDKs to use below
java=17.0.12-graal
```

> `.sdkmanrc` only records the version — it doesn't switch anything by itself. With `sdkman_auto_env` enabled (above), it's picked up automatically on `cd`; otherwise it needs a manual `sdk env`. To stop pinning a project, delete the file: `rm .sdkmanrc`.

With all three `.sdkmanrc` files in place and `sdkman_auto_env=true`, `cd`-ing between `~/java_course/service` (Java 25), `~/delta/delta-gss/delta-gss-distribution` (Java 17), and `~/delta/SesamEO/sesame-distribution` (Java 17) switches `java`/`JAVA_HOME` automatically, with no manual step per directory.

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
