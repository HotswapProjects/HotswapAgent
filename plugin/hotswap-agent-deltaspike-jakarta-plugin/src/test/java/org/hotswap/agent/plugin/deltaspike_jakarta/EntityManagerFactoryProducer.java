package org.hotswap.agent.plugin.deltaspike_jakarta;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.Version;

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