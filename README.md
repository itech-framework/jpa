# FlexiTech JPA ✨

**Seamless JPA and Hibernate integration for FlexiTech applications.**  
![Version](https://img.shields.io/badge/version-1.0.11-green) ![License](https://img.shields.io/badge/license-MIT-blue)

## Features
- Hibernate `SessionFactory` created and managed automatically at startup
- Automatic entity scanning from a configured package (directories and JARs)
- Ready-made generic repository with CRUD, HQL queries and pagination
- Every repository operation runs in its own transaction, with automatic rollback on failure
- Direct access to the Hibernate `Session` for custom queries
- Schema generation and validation through `flexi.jpa.hbm2ddl.auto`
- SQL logging through `flexi.jpa.show_sql`
- Java 17+ and Hibernate 6
- Configuration through properties, with profile support from the core framework

## 🛠 Installation

### Requirements
- Java 17+
- FlexiTech Fx core framework

### 1. Add Dependencies
```xml
<dependencies>
    <dependency>
        <groupId>io.github.itech-framework</groupId>
        <artifactId>core</artifactId>
        <version>1.0.12-RC1</version>
    </dependency>

    <dependency>
        <groupId>io.github.itech-framework</groupId>
        <artifactId>jpa</artifactId>
        <version>1.0.11</version>
    </dependency>

    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-core</artifactId>
        <version>6.4.4.Final</version>
    </dependency>

    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.4.0</version>
    </dependency>
</dependencies>
```

Use the JDBC driver for your own database instead of MySQL if needed. The `java-fx` module is only required for JavaFX applications.

### 2. Enable JPA
```java
@ComponentScan(basePackage = "your.package.com")
@EnableJPA
public class MyApp {
    public static void main(String[] args) throws Exception {
        ITechApplication.run(MyApp.class);
    }
}
```

For a JavaFX application, put `@EnableJPA` on your `ITechJavaFxApplication` main class next to `@ComponentScan`.

The module registers itself with the framework at startup, builds the Hibernate `SessionFactory` and connects to the database. If anything is wrong with the configuration or the database is unreachable, startup fails with `JPA Initialization Failed: ...`.

## ⚙ Configuration

Add these keys to `application.properties`. Profile overrides such as `application-dev.properties` work the same as in the core framework.

```properties
flexi.jpa.connection.driver_class=com.mysql.cj.jdbc.Driver
flexi.jpa.connection.url=jdbc:mysql://localhost:3306/mydb
flexi.jpa.connection.username=root
flexi.jpa.connection.password=secret

flexi.jpa.dialect=org.hibernate.dialect.MySQLDialect
flexi.jpa.hbm2ddl.auto=update
flexi.jpa.show_sql=false

flexi.jpa.entity-package=your.package.com.entities
```

| Key | Required | Default | Description |
|-----|----------|---------|-------------|
| `flexi.jpa.connection.driver_class` | Yes | - | JDBC driver class name. |
| `flexi.jpa.connection.url` | Yes | - | JDBC connection URL. |
| `flexi.jpa.connection.username` | Yes | - | Database username. |
| `flexi.jpa.connection.password` | Yes | - | Database password. Keep it out of version control, for example in a profile file such as `application-prod.properties` that is not committed. |
| `flexi.jpa.dialect` | Yes | - | Fully qualified Hibernate dialect class name. It must exist on the classpath, otherwise startup fails with `Invalid Hibernate dialect`. |
| `flexi.jpa.entity-package` | Yes | - | Package that contains your `@Entity` classes. Sub-packages are scanned too, in directories and in JARs. |
| `flexi.jpa.hbm2ddl.auto` | No | `validate` | Schema mode: `none`, `validate`, `update`, `create` or `create-drop`. The default `validate` fails on an empty database, so use `update` or `create` for the first run. Prefer `validate` or `none` in production. |
| `flexi.jpa.show_sql` | No | `false` | Print every SQL statement Hibernate executes. |

> 📌 **Note**: Only classes annotated with `@Entity` are registered. `@MappedSuperclass` and `@Embeddable` classes are not picked up by the scanner.

## 🧩 Example Entity
```java
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
}
```
Add getters and setters (or use Lombok). Use a `Long` wrapper type for the id, see [Repository Notes](#repository-notes).

## 🧱 Repositories

Extend `SimpleFlexiJpaRepository<T, ID>` and register the class as a data access component:

```java
@DataAccess
public class UserRepository extends SimpleFlexiJpaRepository<User, Long> {
}
```

Inject it into a service with `@Use`:

```java
@Service
public class UserServiceImpl implements UserService {
    @Use
    private UserRepository repository;

    public User register(String username) {
        User user = new User();
        user.setUsername(username);
        return repository.save(user);
    }
}
```

The entity type is read from the generic parameter, so the repository must extend `SimpleFlexiJpaRepository` directly with its type arguments, as above.

### Available Operations

| Method | Description |
|--------|-------------|
| `save(entity)` | Inserts the entity if its id is `null`, otherwise merges it. Returns the saved entity. |
| `findById(id)` | Returns an `Optional` with the entity, or empty if there is none. |
| `findAll()` | Returns all entities. |
| `findAll(Pageable)` | Returns a `Page` of entities. |
| `deleteById(id)` | Deletes the entity. Does nothing if it does not exist. |
| `deleteAll()` | Deletes all rows of the entity. |
| `findBy(hql, parameters)` | Runs an HQL query and returns the matching list. |
| `findOneBy(hql, parameters)` | Runs an HQL query and returns the first result. |
| `executeQuery(session -> ...)` | Runs your own code with a Hibernate `Session` inside a transaction. |
| `saveAll(entities)` | Not implemented yet. It throws `UnsupportedOperationException`. |

### Custom Queries
Use named parameters with `QueryParameters`:
```java
@DataAccess
public class UserRepository extends SimpleFlexiJpaRepository<User, Long> {
    public List<User> findByUsername(String username) {
        return findBy("FROM User u WHERE u.username = :username",
                new QueryParameters().add("username", username));
    }
}
```

### Pagination
`Pageable` is zero-based:
```java
Page<User> page = repository.findAll(new Pageable(0, 20));
List<User> users = page.content();
long total = page.totalElements();
int pages = page.getTotalPages();
```
`Pageable` and `Page` are nested types of `FlexiJpaRepository`. Import them from `io.github.itech_framework.jpa.repository.FlexiJpaRepository`, or use them inside a repository class without an import.

### Working with the Session
`executeQuery` and the protected `executeReturningTransaction` and `executeVoidTransaction` methods give you the Hibernate `Session` for anything the built-in methods do not cover:
```java
public List<User> findWithOrders() {
    return executeQuery(session ->
            session.createQuery("SELECT DISTINCT u FROM User u JOIN FETCH u.orders", User.class)
                    .getResultList());
}
```

### Repository Notes
- **One transaction per call.** Each method opens a session, runs in a transaction, commits or rolls back, then closes the session. Several repository calls are therefore not one transaction.
- **Lazy associations.** Because the session closes after each call, accessing a lazy association afterwards throws `LazyInitializationException`. Load what you need with `JOIN FETCH` in the query.
- **Errors.** Every failure is thrown as a `FrameworkException`, with the original exception as its cause.
- **Ids.** `save` checks for a new entity with a `Long` id, so use `Long` for entity ids.

### Using the Session Factory Directly
`FlexiJpaConfig` is a registered component, so you can inject it:
```java
@Use
private FlexiJpaConfig jpaConfig;
```
It provides `openSession()`, `getSessionFactory()` and `shutdown()`.

## 🔧 Troubleshooting

| Message | Cause |
|---------|-------|
| `Entity package is not defined in properties` | `flexi.jpa.entity-package` is missing or empty. |
| `Invalid Hibernate dialect: ...` | `flexi.jpa.dialect` is missing, or is not a dialect class on the classpath. |
| `JPA Initialization Failed: ...` | Wrong connection settings, an unreachable database, a schema that fails `validate`, or an invalid mapping. See the cause. |
| `JPA configuration not found` | A repository was created before JPA was initialized. Check that `@EnableJPA` is present. |
| `Hibernate not initialized` | `openSession()` was called before initialization finished. |

## 🤝 Support
For assistance:  
📧 Email: `itech.saizawmyint@gmail.com`  
📑 [Open an Issue](https://github.com/itech-framework/jpa/issues)

---

*Made with ❤️ by the FlexiTech Team*