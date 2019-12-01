package org.hotswap.agent.plugin.deltaspike.testRepositories;

import javax.enterprise.context.ApplicationScoped;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import org.hotswap.agent.plugin.deltaspike.testEntities.TestEntity;

@ApplicationScoped
@Repository
public interface TestRepository extends EntityRepository<TestEntity, Long> {

}
