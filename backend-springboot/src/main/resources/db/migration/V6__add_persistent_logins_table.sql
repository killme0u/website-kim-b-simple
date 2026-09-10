-- V6__add_persistent_logins_table.sql
-- Spring Security JdbcTokenRepositoryImpl 표준 스키마.
-- 컬럼명·길이는 Spring Security 내장 SQL이 고정하므로 임의로 바꾸지 않는다.
-- (참고: https://docs.spring.io/spring-security/reference/7.0/servlet/appendix/database-schema.html)

CREATE TABLE persistent_logins (
    username  VARCHAR(64) NOT NULL,
    series    VARCHAR(64) PRIMARY KEY,
    token     VARCHAR(64) NOT NULL,
    last_used TIMESTAMPTZ NOT NULL
);
