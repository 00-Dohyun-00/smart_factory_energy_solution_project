package com.smartfactory.energy.global.failover;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

// 쿼리 하나하나마다 SupabaseHealthChecker가 들고 있는 현재 대상(primary/backup)으로 보냅니다.
public class FailoverRoutingDataSource extends AbstractRoutingDataSource {

    private final SupabaseHealthChecker checker;

    public FailoverRoutingDataSource(SupabaseHealthChecker checker) {
        this.checker = checker;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return checker.currentTarget();
    }
}
