package org.hotswap.agent.plugin.deltaspike_jakarta.testRepositories;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import org.hotswap.agent.plugin.deltaspike_jakarta.testEntities.TestEntity;

@ApplicationScoped
@Repository
public interface TestRepository extends EntityRepository<TestEntity, Long> {

}
