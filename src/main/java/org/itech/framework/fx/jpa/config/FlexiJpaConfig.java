package org.itech.framework.fx.jpa.config;

import jakarta.persistence.Entity;
import lombok.Getter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.itech.framework.fx.core.utils.PropertiesLoader;
import org.itech.framework.fx.exceptions.FrameworkException;

import java.io.File;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class FlexiJpaConfig {
    private static final Logger logger = LogManager.getLogger(FlexiJpaConfig.class);

    private SessionFactory sessionFactory;
    private boolean initialized;
    private final Properties hibernateProperties = new Properties();

    public FlexiJpaConfig() {
        initialize();
    }

    public synchronized void initialize() {
        if (initialized) return;

        try {
            configureProperties();
            validateConfiguration();

            // Apply JPA compliance settings
            hibernateProperties.put(AvailableSettings.JPA_QUERY_COMPLIANCE, "false");
            hibernateProperties.put(AvailableSettings.JPA_TRANSACTION_COMPLIANCE, "true");
            hibernateProperties.put(AvailableSettings.JPA_CLOSED_COMPLIANCE, "true");
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(hibernateProperties)
                    .build();

            MetadataSources metadataSources = new MetadataSources(registry);

            // Scan and add entity classes
            String entityPackage = getEntityPackage();
            Set<Class<?>> entityClasses = scanEntities(entityPackage);
            for (Class<?> entityClass : entityClasses) {
                metadataSources.addAnnotatedClass(entityClass);
            }

            Metadata metadata = metadataSources.buildMetadata();
            this.sessionFactory = metadata.getSessionFactoryBuilder().build();

            this.initialized = true;
            logger.info("Hibernate initialized successfully with {} entities", entityClasses.size());

        } catch (Exception e) {
            throw new FrameworkException("JPA Initialization Failed: " + e.getMessage(), e);
        }
    }

    /*private ValidatorFactory buildValidatorFactory() {
        return Validation.byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .externalClassLoader(Thread.currentThread().getContextClassLoader())
                .buildValidatorFactory();
    }*/

    private void configureProperties() {
        // Required properties
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_URL, "flexi.jpa.connection.url");
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_USER, "flexi.jpa.connection.username");
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, "flexi.jpa.connection.password");
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_DRIVER, "flexi.jpa.connection.driver_class");
        setRequiredProperty(AvailableSettings.DIALECT, "flexi.jpa.dialect");

        // Optional properties
        hibernateProperties.put(AvailableSettings.HBM2DDL_AUTO,
                PropertiesLoader.getProperty("flexi.jpa.hbm2ddl.auto", "validate"));
        hibernateProperties.put(AvailableSettings.SHOW_SQL,
                PropertiesLoader.getProperty("flexi.jpa.show_sql", "false"));
    }

    private void setRequiredProperty(String hibernateKey, String configKey) {
        String value = PropertiesLoader.getProperty(configKey, "");
        hibernateProperties.put(hibernateKey, value);
    }

    private void validateConfiguration() {
        String dialect = hibernateProperties.getProperty(AvailableSettings.DIALECT);
        try {
            Class.forName(dialect);
        } catch (ClassNotFoundException e) {
            throw new FrameworkException("Invalid Hibernate dialect: " + dialect, e);
        }
    }

    private String getEntityPackage() {
        return PropertiesLoader.getProperty("flexi.jpa.entity-package", "");
    }

    public Session openSession() {
        if (!initialized) {
            throw new IllegalStateException("Hibernate not initialized");
        }
        return sessionFactory.openSession();
    }

    public void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
            logger.info("Hibernate shutdown completed");
        }
    }

    private Set<Class<?>> scanEntities(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            throw new FrameworkException("Entity package is not defined in properties");
        }

        try {
            String path = packageName.replace('.', '/');
            URL resource = Thread.currentThread().getContextClassLoader().getResource(path);
            if (resource == null) {
                throw new FrameworkException("Entity package path not found: " + packageName);
            }

            File directory = new File(resource.toURI());
            if (!directory.exists() || !directory.isDirectory()) {
                throw new FrameworkException("Entity package directory does not exist: " + packageName);
            }

            return Stream.of(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(".class"))))
                    .map(file -> {
                        String className = packageName + "." + file.getName().replace(".class", "");
                        try {
                            return Class.forName(className);
                        } catch (ClassNotFoundException e) {
                            logger.warn("Could not load class: {}", className, e);
                            return null;
                        }
                    })
                    .filter(clazz -> clazz != null && clazz.isAnnotationPresent(Entity.class))
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            throw new FrameworkException("Failed to scan entity package: " + packageName, e);
        }
    }
}
