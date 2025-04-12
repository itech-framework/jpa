package org.itech.framework.fx.jpa.repository;

import jakarta.persistence.TypedQuery;
import org.hibernate.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public interface FlexiJpaRepository<T, ID> {
    T save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    void deleteById(ID id);

    List<T> findBy(String queryString, QueryParameters parameters);
    Optional<T> findOneBy(String queryString, QueryParameters parameters);
    Page<T> findAll(Pageable pageable);
    <R> R executeQuery(Function<Session, R> query);

    default <S extends T> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("Bulk save not implemented");
    }

    class QueryParameters {
        private final Map<String, Object> parameters = new HashMap<>();

        public QueryParameters add(String name, Object value) {
            parameters.put(name, value);
            return this;
        }

        public void applyTo(TypedQuery<?> query) {
            parameters.forEach(query::setParameter);
        }
    }

    class Pageable {
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

    record Page<T>(List<T> content, long totalElements, Pageable pageable) {
        public int getTotalPages() {
            return (int) Math.ceil((double) totalElements / pageable.getPageSize());
        }
    }

}
