# FlexiTech JPA ✨

**Seamless JPA Integration for FlexiTech Applications**  
![Version](https://img.shields.io/badge/version-1.0.0-green) ![License](https://img.shields.io/badge/license-MIT-blue)

## 🛠 Installation

### 1. Add Dependency
```xml
<dependency>
    <groupId>org.itech.framework.fx</groupId>
    <artifactId>jpa</artifactId>
    <version>${flexitech.version}</version>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>${mysql.8.version}</version>
</dependency>
```

### 2. Enable JPA
```java
@ComponentScan(basePackage = "your.package.com")
@EnableJPA
public class MyApp {
    public static void main(String[] args) {
        ItechApplication.run(MyApp.class, args);
    }
}
```

> 📌 **Note**: Ensure entities are within `@ComponentScan` base packages

## ⚙ Configuration
```properties
# Database Configuration
flexi.jpa.connection.driver_class={driver_class}
flexi.jpa.connection.url={your_db_url}
flexi.jpa.connection.username={db_username}
flexi.jpa.connection.password={db_password}

# Hibernate Settings
flexi.jpa.dialect={db_dialect}
flexi.jpa.hbm2ddl.auto={ddl_auto}

# Entities packages
flexi.jpa.entity-package=your.entities.class.package
```

## 🧩 Example Entity
```java
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    // Getters/setters
}
```

## 🤝 Support
For assistance:  
📧 Email: `support@flexitech.com`  
📑 [Open an Issue]([https://github.com/your-repo/issues](https://github.com/itech-framework/jpa/issues))

---

*Made with ❤️ by the FlexiTech Team*
