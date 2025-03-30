package org.itech.framework.fx.jpa.repository.simple;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.itech.framework.fx.core.store.ComponentStore;
import org.itech.framework.fx.exceptions.FrameworkException;
import org.itech.framework.fx.jpa.config.FlexiJpaConfig;
import org.itech.framework.fx.jpa.repository.FlexiJpaRepository;
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
    public T save(T entity) {
        return executeReturningTransaction(session -> {
            session.persist(entity);
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

    // Enhanced query methods
    public <R> R executeQuery(Function<Session, R> query) {
        return executeReturningTransaction(query);
    }

    public List<T> findBy(String queryString, QueryParameters parameters) {
        return executeReturningTransaction(session -> {
            TypedQuery<T> query = session.createQuery(queryString, entityClass);
            parameters.applyTo(query);
            return query.getResultList();
        });
    }

    public Optional<T> findOneBy(String queryString, QueryParameters parameters) {
        return Optional.ofNullable(
                executeReturningTransaction(session -> {
                    TypedQuery<T> query = session.createQuery(queryString, entityClass);
                    parameters.applyTo(query);
                    return query.setMaxResults(1).getSingleResult();
                })
        );
    }

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

    // Helper classes
    public static class QueryParameters {
        private final Map<String, Object> parameters = new HashMap<>();

        public QueryParameters add(String name, Object value) {
            parameters.put(name, value);
            return this;
        }

        void applyTo(TypedQuery<?> query) {
            parameters.forEach(query::setParameter);
        }
    }

    public static class Pageable {
        private final int page;
        private final int size;

        public Pageable(int page, int size) {
            this.page = page;
            this.size = size;
        }

        public int getOffset() {
            return page * size;
        }

        public int getPageSize() {
            return size;
        }
    }

    public record Page<T>(List<T> content, long totalElements, Pageable pageable) {
        public int getTotalPages() {
            return (int) Math.ceil((double) totalElements / pageable.getPageSize());
        }
    }
}