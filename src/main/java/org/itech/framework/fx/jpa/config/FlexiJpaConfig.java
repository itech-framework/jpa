package org.itech.framework.fx.jpa.config;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.itech.framework.fx.core.annotations.methods.InitMethod;
import org.itech.framework.fx.core.store.ComponentStore;
import org.itech.framework.fx.core.utils.PropertiesLoader;
import org.itech.framework.fx.exceptions.FrameworkException;

import java.lang.reflect.Method;

@Getter
public class FlexiJpaConfig {
    private volatile Object sessionFactory;
    private boolean jpaEnabled;
    private String jpaVariant;
    private String dialect;
    private final Logger logger = LogManager.getLogger(getClass());

    public FlexiJpaConfig() {
        super();

        this.initialize();
    }

    public boolean isInitialized() {
        return getSessionFactory() != null;
    }


    private boolean checkJpaEnabled() {
        try {
            return classExists("org.hibernate.SessionFactory") &&
                    (classExists("javax.persistence.Entity") ||
                            classExists("jakarta.persistence.Entity"));
        } catch (Exception e) {
            return false;
        }
    }

    private String detectJpaVariant() {
        if (classExists("jakarta.persistence.Entity")) return "jakarta";
        if (classExists("javax.persistence.Entity")) return "javax";
        return "none";
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void initialize() {
        this.jpaEnabled = checkJpaEnabled();
        this.jpaVariant = detectJpaVariant();
        this.dialect = PropertiesLoader.getProperty("flexi.jpa.dialect", "");


        if(jpaEnabled) {
            try {



                validateDialect();

                Class<?> registryBuilderClass = Class.forName("org.hibernate.boot.registry.StandardServiceRegistryBuilder");
                Object registryBuilder = registryBuilderClass.getConstructor().newInstance();

                Method applySetting = registryBuilderClass.getMethod("applySetting", String.class, Object.class);

                // Set dialect from configuration
                applySetting.invoke(registryBuilder, "hibernate.dialect", dialect);

                // Set common connection properties
                applyCommonSettings(registryBuilder, applySetting);

                Method configureMethod = registryBuilderClass.getMethod("configure");
                Object registry = configureMethod.invoke(registryBuilder);

                Class<?> metadataSourcesClass = Class.forName("org.hibernate.boot.MetadataSources");
                Object metadataSources = metadataSourcesClass.getConstructor(registry.getClass()).newInstance(registry);

                configureJpaCompliance(metadataSourcesClass, metadataSources);

                Class<?> metadataClass = Class.forName("org.hibernate.boot.Metadata");
                Object metadata = metadataSourcesClass.getMethod("buildMetadata").invoke(metadataSources);

                this.sessionFactory = metadataClass.getMethod("buildSessionFactory").invoke(metadata);

            } catch (Exception e) {
                throw new FrameworkException(
                        """
                                Failed to initialize flexi.jpa. Check:
                                1. Database driver dependency exists
                                2. flexi.jpa.dialect is set correctly
                                3. Database connection properties are configured"""
                );
            }
        }
    }

    private void validateDialect() {
        if(dialect == null || dialect.isEmpty()) {
            throw new FrameworkException(
                    """
                            JPA dialect not configured. Add to properties:
                            For MySQL: flexi.jpa.dialect=org.hibernate.dialect.MySQL8Dialect
                            For PostgreSQL: flexi.jpa.dialect=org.hibernate.dialect.PostgreSQLDialect"""
            );
        }
        try {
            Class.forName(dialect);
        } catch (ClassNotFoundException e) {
            throw new FrameworkException("Dialect class not found: " + dialect +
                    "\nCheck your Hibernate version and database dependencies");
        }
    }

    private void applyCommonSettings(Object registryBuilder, Method applySetting) throws Exception {
        // Get connection properties from configuration
        String url = PropertiesLoader.getProperty("flexi.jpa.connection.url", "");
        String user = PropertiesLoader.getProperty("flexi.jpa.connection.username", "");
        String pass = PropertiesLoader.getProperty("flexi.jpa.connection.password", "");
        String driver = PropertiesLoader.getProperty("flexi.jpa.connection.driver_class", "");
        logger.debug("JPA Config: \nurl: {},user: {}, driver: {}",url,user,driver);
        applySetting.invoke(registryBuilder, "hibernate.connection.url", url);
        applySetting.invoke(registryBuilder, "hibernate.connection.username", user);
        applySetting.invoke(registryBuilder, "hibernate.connection.password", pass);
        applySetting.invoke(registryBuilder, "hibernate.connection.driver_class", driver);
        applySetting.invoke(registryBuilder, "hibernate.hbm2ddl.auto",
                PropertiesLoader.getProperty("flexi.jpa.hbm2ddl.auto", "validate"));
    }

    private void configureJpaCompliance(Class<?> metadataSourcesClass, Object metadataSources) throws Exception {
        try {
            Method setJpaCompliance = metadataSourcesClass.getMethod(
                    "setJpaCompliance",
                    Class.forName("org.hibernate.jpa.JpaCompliance")

            );
            Object jpaCompliance = Class.forName("org.hibernate.flexi.jpa.JpaCompliance")
                    .getConstructor(boolean.class, boolean.class, boolean.class, boolean.class)
                    .newInstance(true, true, true, true);
            setJpaCompliance.invoke(metadataSources, jpaCompliance);
        } catch (NoSuchMethodException e) {
            logger.debug("JpaCompliance configuration not available in this Hibernate version");
        }
    }

    public Object getSession() {
        try {
            return sessionFactory.getClass()
                    .getMethod("openSession")
                    .invoke(sessionFactory);
        } catch (Exception e) {
            throw new RuntimeException("""
                    Failed to open session. Check:
                    1. Database server is running
                    2. Connection properties are correct
                    3. Network connectivity""",
                    e
            );
        }
    }
    public Object getSessionFactory() {
        if(sessionFactory == null) {
            synchronized(this) {
                if(sessionFactory == null) {
                    initialize();
                }
            }
        }
        return sessionFactory;
    }
}
