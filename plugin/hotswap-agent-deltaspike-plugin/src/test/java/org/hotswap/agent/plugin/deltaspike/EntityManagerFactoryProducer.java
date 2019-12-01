package org.hotswap.agent.plugin.deltaspike;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.hibernate.Version;

@Dependent
public class EntityManagerFactoryProducer
{
    @Produces
    @ApplicationScoped
    public EntityManagerFactory create() {
        String[] version = Version.getVersionString().split("\\.");
        boolean version52OrGreater = Integer.valueOf(version[0]) == 5 && Integer.valueOf(version[1]) >= 2;
        String persistentUnit;
        if (version52OrGreater) {
            persistentUnit = "TestPU52";
        }  else {
            persistentUnit = "TestPU52";
        }

        return Persistence.createEntityManagerFactory(persistentUnit);
    }

    public void destroy(@Disposes EntityManagerFactory factory) {
        factory.close();
    }
}