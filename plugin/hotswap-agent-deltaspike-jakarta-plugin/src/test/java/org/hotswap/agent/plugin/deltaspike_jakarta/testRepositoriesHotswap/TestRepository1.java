package org.hotswap.agent.plugin.deltaspike_jakarta.testRepositoriesHotswap;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.Repository;
import org.hotswap.agent.plugin.deltaspike_jakarta.testEntities.TestEntity;

@ApplicationScoped
@Repository
public interface TestRepository1 extends EntityRepository<TestEntity, Long> {
    public Optional<TestEntity> findOptionalByName(String name);
    @Query("SELECT t FROM TestEntity WHERE name=?1")
    public TestEntity findByName1(String name);
}
