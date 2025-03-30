package org.itech.framework.fx.jpa.repository;

import java.util.List;
import java.util.Optional;

public interface FlexiJpaRepository<T, ID> {T save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    void deleteById(ID id);

    default <S extends T> List<S> saveAll(Iterable<S> entities) {
        throw new UnsupportedOperationException("Bulk save not implemented");
    }
}
