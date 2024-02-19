package de.telekom.horizon.starlight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.cache.CacheMetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {CacheMetricsAutoConfiguration.class, MongoAutoConfiguration.class, RedisAutoConfiguration.class})
//@EnableConfigurationProperties({RedisReportConfig.class})
@ConfigurationPropertiesScan("de.telekom.horizon.starlight.config")
@EnableScheduling
public class StarlightApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarlightApplication.class, args);
    }
}
