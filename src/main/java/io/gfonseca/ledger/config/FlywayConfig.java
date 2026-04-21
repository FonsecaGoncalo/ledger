package io.gfonseca.ledger.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .loggers("slf4j")
                .load();
    }

    @Bean
    @DependsOn("flyway")
    public DSLContext dslContext(DataSource dataSource) {
        return DSL.using(new TransactionAwareDataSourceProxy(dataSource), org.jooq.SQLDialect.POSTGRES);
    }
}
