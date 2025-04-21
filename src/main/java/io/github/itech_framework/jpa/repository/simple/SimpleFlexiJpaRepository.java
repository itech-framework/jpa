package io.github.itech_framework.jpa.repository.simple;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import io.github.itech_framework.core.store.ComponentStore;
import io.github.itech_framework.core.exceptions.FrameworkException;
import io.github.itech_framework.jpa.config.FlexiJpaConfig;
import io.github.itech_framework.jpa.repository.FlexiJpaRepository;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

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

    // Generic transaction handling methods
    protected <R> R executeReturningTransaction(Function<Session, R> operation) {
        try (Session session = jpaConfig.openSession()) {
            return executeInTransaction(session, operation);
        } catch (Exception e) {
            throw new FrameworkException("Session operation failed", e);
        }
    }

    protected void executeVoidTransaction(Consumer<Session> operation) {
        try (Session session = jpaConfig.openSession()) {
            executeInTransaction(session, wrapConsumer(operation));
        } catch (Exception e) {
            throw new FrameworkException("Session operation failed", e);
        }
    }

    private Function<Session, Void> wrapConsumer(Consumer<Session> consumer) {
        return session -> {
            consumer.accept(session);
            return null;
        };
    }

    private <R> R executeInTransaction(Session session, Function<Session, R> operation) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            R result = operation.apply(session);
            transaction.commit();
            return result;
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            throw new FrameworkException("Transaction failed", e);
        }
    }

    private Function<Session, Void> sessionConsumerWrapper(Consumer<Session> consumer) {
        return session -> {
            consumer.accept(session);
            return null;
        };
    }

    // Enhanced CRUD operations
    @Override
    public T save(final T entity) {
        return executeReturningTransaction(session -> {
            if (isNewEntity(entity)) {
                session.persist(entity);
            } else {
               return session.merge(entity);
            }
            return entity;
        });
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(
                executeReturningTransaction(session -> session.get(entityClass, id))
        );
    }

    @Override
    public List<T> findAll() {
        return executeReturningTransaction(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            cq.select(root);
            return session.createQuery(cq).getResultList();
        });
    }

    @Override
    public void deleteById(ID id) {
        executeVoidTransaction(session -> {
            T entity = session.get(entityClass, id);
            if (entity != null) session.remove(entity);
        });
    }

    @Override
    public <R> R executeQuery(Function<Session, R> query) {
        return executeReturningTransaction(query);
    }

    @Override
    public List<T> findBy(String queryString, QueryParameters parameters) {
        return executeReturningTransaction(session -> {
            TypedQuery<T> query = session.createQuery(queryString, entityClass);
            parameters.applyTo(query);
            return query.getResultList();
        });
    }

    @Override
    public Optional<T> findOneBy(String queryString, QueryParameters parameters) {
        return Optional.ofNullable(
                executeReturningTransaction(session -> {
                    TypedQuery<T> query = session.createQuery(queryString, entityClass);
                    parameters.applyTo(query);
                    return query.setMaxResults(1).getSingleResult();
                })
        );
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return executeReturningTransaction(session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            cq.select(root);

            TypedQuery<T> query = session.createQuery(cq);
            query.setFirstResult(pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());

            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            countQuery.select(cb.count(countQuery.from(entityClass)));
            Long total = session.createQuery(countQuery).getSingleResult();

            return new Page<>(query.getResultList(), total, pageable);
        });
    }


    private boolean isNewEntity(T entity) {
        // Implement your logic to check if entity is new
        // Example for typical ID-based check:
        return (Long) jpaConfig.getSessionFactory().getPersistenceUnitUtil().getIdentifier(entity) == null;
    }
    // Helper classes

}