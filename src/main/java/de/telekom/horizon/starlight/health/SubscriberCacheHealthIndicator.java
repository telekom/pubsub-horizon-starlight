package de.telekom.horizon.starlight.health;

import de.telekom.eni.pandora.horizon.kubernetes.InformerStoreInitHandler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "kubernetes.enabled", havingValue = "true")
public class SubscriberCacheHealthIndicator implements HealthIndicator {

    private final InformerStoreInitHandler informerStoreInitHandler;

    public SubscriberCacheHealthIndicator(InformerStoreInitHandler informerStoreInitHandler) {
        this.informerStoreInitHandler = informerStoreInitHandler;
    }

    @Override
    public Health health() {
        Health.Builder status = Health.up();

        if (!informerStoreInitHandler.isFullySynced()) {
            status = Health.down();
        }

        return status.withDetails(informerStoreInitHandler.getInitalSyncedStats()).build();
    }
}