package io.github.itech_framework.jpa.config;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;

import io.github.itech_framework.core.exceptions.FrameworkException;
import io.github.itech_framework.core.utils.PropertiesLoader;
import jakarta.persistence.Entity;

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

            hibernateProperties.put(AvailableSettings.JPA_QUERY_COMPLIANCE, "false");
            hibernateProperties.put(AvailableSettings.JPA_TRANSACTION_COMPLIANCE, "true");
            hibernateProperties.put(AvailableSettings.JPA_CLOSED_COMPLIANCE, "true");
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(hibernateProperties)
                    .build();

            MetadataSources metadataSources = new MetadataSources(registry);

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

    private void configureProperties() {
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_URL, "flexi.jpa.connection.url");
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_USER, "flexi.jpa.connection.username");
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, "flexi.jpa.connection.password");
        setRequiredProperty(AvailableSettings.JAKARTA_JDBC_DRIVER, "flexi.jpa.connection.driver_class");
        setRequiredProperty(AvailableSettings.DIALECT, "flexi.jpa.dialect");

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
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);

            Set<Class<?>> entities = new HashSet<>();

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();

                if (resource.getProtocol().equals("file")) {
                    File directory = new File(resource.toURI());
                    if (directory.exists() && directory.isDirectory()) {
                        scanFilesystemEntities(directory, packageName, entities);
                    }
                } else if (resource.getProtocol().equals("jar")) {
                    scanJarEntities(resource, packageName, path, entities);
                }
            }

            return entities;

        } catch (Exception e) {
            throw new FrameworkException("Failed to scan entity package: " + packageName, e);
        }
    }

    private void scanFilesystemEntities(File directory, String packageName, Set<Class<?>> entities) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName + "." + file.getName();
                scanFilesystemEntities(file, subPackage, entities);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(Entity.class)) {
                        entities.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not load class: {}", className, e);
                }
            }
        }
    }

    private void scanJarEntities(URL resource, String packageName, String path, Set<Class<?>> entities) {
        try {
            JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
            JarFile jar = jarConnection.getJarFile();

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(path) && entryName.endsWith(".class") && !entryName.contains("$")) {
                    String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(Entity.class)) {
                            entities.add(clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        logger.warn("Could not load class: {}", className, e);
                    }
                }
            }
        } catch (IOException e) {
            throw new FrameworkException("Failed to scan JAR for entities", e);
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public Properties getHibernateProperties() {
        return hibernateProperties;
    }
}