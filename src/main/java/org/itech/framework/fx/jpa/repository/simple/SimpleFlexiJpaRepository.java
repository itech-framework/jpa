package org.itech.framework.fx.jpa.repository.simple;


import org.itech.framework.fx.core.store.ComponentStore;
import org.itech.framework.fx.exceptions.FrameworkException;
import org.itech.framework.fx.jpa.config.FlexiJpaConfig;
import org.itech.framework.fx.jpa.repository.FlexiJpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class SimpleFlexiJpaRepository<T, ID> implements FlexiJpaRepository<T, ID> {

    private final Class<T> entityClass;
    protected final FlexiJpaConfig jpaConfig;

    @SuppressWarnings("unchecked")
    public SimpleFlexiJpaRepository() {
        this.entityClass = (Class<T>) ((java.lang.reflect.ParameterizedType)
                getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.jpaConfig = ComponentStore.getComponent(FlexiJpaConfig.class)
                .orElseThrow(() -> new FrameworkException("JPA configuration not found"));
    }

    protected Object performInTransaction(TransactionOperation operation) {
        Object session = null;
        Object transaction = null;
        try {
            session = jpaConfig.getSession();
            Method beginTransaction = session.getClass().getMethod("beginTransaction");
            transaction = beginTransaction.invoke(session);

            Object result = operation.execute(session);

            Method commit = transaction.getClass().getMethod("commit");
            commit.invoke(transaction);
            return result;
        } catch (Exception e) {
            if (transaction != null) {
                try {
                    Method rollback = transaction.getClass().getMethod("rollback");
                    rollback.invoke(transaction);
                } catch (Exception ex) {
                    throw new FrameworkException("Transaction rollback failed", ex);
                }
            }
            throw new FrameworkException("Database operation failed", e);
        } finally {
            if (session != null) {
                try {
                    Method close = session.getClass().getMethod("close");
                    close.invoke(session);
                } catch (Exception e) {
                    throw new FrameworkException("Session closure failed", e);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T save(T entity) {
        return (T) performInTransaction(session -> {
            Method save = session.getClass().getMethod("save", Object.class);
            return save.invoke(session, entity);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<T> findById(ID id) {
        return Optional.ofNullable((T) performInTransaction(session -> {
            Method get = session.getClass().getMethod("get", Class.class, Object.class);
            return get.invoke(session, entityClass, id);
        }));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> findAll() {
        return (List<T>) performInTransaction(session -> {
            Method createQuery = session.getClass().getMethod("createQuery", String.class);
            Object query = createQuery.invoke(session, "FROM " + entityClass.getSimpleName());

            Method getResultList = query.getClass().getMethod("getResultList");
            return getResultList.invoke(query);
        });
    }

    @Override
    public void deleteById(ID id) {
        performInTransaction(session -> {
            Object entity = session.getClass()
                    .getMethod("get", Class.class, Object.class)
                    .invoke(session, entityClass, id);

            if (entity != null) {
                Method delete = session.getClass().getMethod("delete", Object.class);
                delete.invoke(session, entity);
            }
            return null;
        });
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        return (List<S>) performInTransaction(session -> {
            Method save = session.getClass().getMethod("save", Object.class);
            List<S> result = new java.util.ArrayList<>();

            for (S entity : entities) {
                result.add((S) save.invoke(session, entity));
            }
            return result;
        });
    }

    @FunctionalInterface
    protected interface TransactionOperation {
        Object execute(Object session) throws Exception;
    }

    // Helper method for custom queries
    protected Object executeQuery(String query, Object... params) {
        return performInTransaction(session -> {
            Method createQuery = session.getClass().getMethod("createQuery", String.class);
            Object queryObject = createQuery.invoke(session, query);

            Method setParameter = queryObject.getClass().getMethod("setParameter", int.class, Object.class);
            for (int i = 0; i < params.length; i++) {
                setParameter.invoke(queryObject, i, params[i]);
            }

            return queryObject.getClass().getMethod("getResultList").invoke(queryObject);
        });
    }
}
