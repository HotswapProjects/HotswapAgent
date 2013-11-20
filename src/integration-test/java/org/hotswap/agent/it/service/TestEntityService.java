package org.hotswap.agent.it.service;

import org.hotswap.agent.it.model.TestEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * Created by bubnik on 10.10.13.
 */
@Service
@Transactional
public class TestEntityService {
    @PersistenceContext
    EntityManager entityManager;

    public void addTestEntity(TestEntity entity) {
        entityManager.persist(entity);
    }

    public List<TestEntity> loadTestEntities() {
        return entityManager.createQuery("select e from TestEntity e").getResultList();
    }
}
