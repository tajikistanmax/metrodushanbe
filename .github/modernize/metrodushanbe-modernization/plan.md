# Metro Dushanbe Backend - 16-Week Modernization Plan
**Production-Ready Azure Deployment**

**Project:** Metro Dushanbe Backend (Spring Boot 3.4.5, Java 25, PostgreSQL + PostGIS)  
**Assessment Score:** 8.5/10 | **Cloud Readiness:** READY_WITH_IMPROVEMENTS  
**Timeline:** 16 weeks | **Target Deployment:** Azure App Service + Azure Database for PostgreSQL  
**Created:** 2026-07-05

---

## Executive Summary

The Metro Dushanbe backend has excellent architecture (modular monolith, clean layered design) but requires critical security hardening, Java LTS migration, and cloud DevOps setup before production deployment. This plan addresses all pre-production blockers and provides a structured 16-week roadmap with 4 interdependent phases.

**Critical Path:**
1. **Phase 1 (Weeks 1-4): Security Hardening** - Blocks deployment
2. **Phase 2 (Weeks 5-8): Modernization** - Enables production-grade testing
3. **Phase 3 (Weeks 9-12): Cloud DevOps** - Enables monitoring and scaling
4. **Phase 4 (Weeks 13-16): Optimization** - Production performance tuning

---

## Phase 1: Security Hardening (Weeks 1-4)
**Objective:** Remove all pre-production blockers and implement enterprise security.  
**Effort:** 4 weeks | **Team:** 2 engineers  
**Dependency:** None (parallel start)

### 1.1 OAuth2/Keycloak Implementation (3 weeks)
**Current State:** Dev-mode X-Admin-Key header (shared secret, no RBAC/MFA)  
**Target State:** OAuth2/JWT with Keycloak, RBAC, MFA support

#### Tasks:
- **1.1.1** Provision Keycloak instance (Azure Container Instances or managed service)
- **1.1.2** Configure Spring Security OAuth2 Resource Server dependency
- **1.1.3** Implement JWT validation and extraction from Bearer token
- **1.1.4** Create admin authorization filter with role-based access control (admin, auditor, viewer)
- **1.1.5** Migrate X-Admin-Actor header to extract from JWT `sub` claim
- **1.1.6** Implement role-based endpoint protection (@RolesAllowed annotations)
- **1.1.7** Create test fixtures with Keycloak TestContainers
- **1.1.8** Update OpenAPI/Swagger security scheme to OAuth2
- **1.1.9** Deprecate X-Admin-Key (log warnings, schedule removal in Phase 2)

**Success Criteria:**
- ✅ All admin endpoints require valid JWT with admin role
- ✅ Unauthenticated requests return 401 Unauthorized
- ✅ Role-based access enforced (test with auditor/viewer roles)
- ✅ Integration tests pass with Keycloak TestContainers
- ✅ Swagger UI shows OAuth2 scheme

**Estimated Effort:** 18 person-days

---

### 1.2 Azure Key Vault Integration (1.5 weeks)
**Current State:** Secrets in application.yml (database password, admin key)  
**Target State:** All secrets in Azure Key Vault, zero secrets in repo

#### Tasks:
- **1.2.1** Create Azure Key Vault resource and access policies
- **1.2.2** Migrate all secrets (DB credentials, Keycloak client secret, JWT signing key)
- **1.2.3** Add Spring Cloud Azure Key Vault starter dependency
- **1.2.4** Configure PropertySourceLocator for Key Vault integration
- **1.2.5** Update CI/CD pipeline to inject AZURE_KEYVAULT_ENDPOINT env var
- **1.2.6** Remove secrets from application.yml, add placeholders with defaults
- **1.2.7** Audit git history, remove leaked credentials (git-filter-repo)
- **1.2.8** Test local dev with env vars (no vault access needed)

**Success Criteria:**
- ✅ Zero secrets in git (scan with git-secrets or TruffleHog)
- ✅ App starts with env vars and Key Vault endpoint
- ✅ Integration tests pass with mocked secrets
- ✅ Local dev uses sensible defaults without vault access

**Estimated Effort:** 10 person-days

---

### 1.3 TLS/HTTPS Configuration (0.5 weeks)
**Current State:** No explicit HTTPS configuration  
**Target State:** Enforced HTTPS, certificate management

#### Tasks:
- **1.3.1** Configure server.ssl.* properties in production profile (Azure Key Vault-backed certificate)
- **1.3.2** Enable HTTP to HTTPS redirect (server.http.enabled=false, redirect middleware)
- **1.3.3** Add Security headers (HSTS, X-Content-Type-Options, X-Frame-Options)
- **1.3.4** Configure CORS to use https:// origins in production
- **1.3.5** Test SSL/TLS with curl and browser tools
- **1.3.6** Document certificate renewal process

**Success Criteria:**
- ✅ HTTPS enforced on all production endpoints
- ✅ Mixed content warnings eliminated
- ✅ SSL/TLS report score A+ (SSL Labs or similar)
- ✅ HSTS header present (max-age=31536000)

**Estimated Effort:** 3 person-days

---

### Phase 1 Deliverables:
- ✅ OAuth2 resource server with JWT validation
- ✅ Keycloak integration (local and Azure)
- ✅ Role-based access control (admin, auditor, viewer)
- ✅ Azure Key Vault integration (Spring Cloud Azure)
- ✅ HTTPS/TLS enabled with security headers
- ✅ Updated application-prod.yml with env var placeholders
- ✅ Integration test suite with Keycloak TestContainers
- ✅ Security audit report (pen test ready)

---

## Phase 2: Java Modernization & Testing (Weeks 5-8)
**Objective:** Achieve production-grade code quality and LTS compliance.  
**Effort:** 4 weeks | **Team:** 2 engineers  
**Dependency:** Phase 1 complete (security tests must pass)

### 2.1 Java 21 LTS Migration (1.5 weeks)
**Current State:** Java 25 (non-LTS, support ends Sept 2026)  
**Target State:** Java 21 LTS (long-term support, stable)

#### Tasks:
- **2.1.1** Update Maven toolchain plugin to Java 21
- **2.1.2** Update Maven compiler plugin (release=21, enable preview features if needed)
- **2.1.3** Update base Docker image to eclipse-temurin:21-jdk (build) and :21-jre (runtime)
- **2.1.4** Validate ByteBuddy compatibility (remove -XX:+UnlockExperimentalVMOptions if Java 21 doesn't need it)
- **2.1.5** Test all module builds (mvn clean package)
- **2.1.6** Run full test suite
- **2.1.7** Verify Docker builds and multi-stage compilation
- **2.1.8** Update CI/CD pipeline to use Java 21

**Success Criteria:**
- ✅ Maven build succeeds with Java 21 compiler
- ✅ All tests pass (unit, integration)
- ✅ Docker image builds and runs successfully
- ✅ No ByteBuddy or runtime compatibility errors
- ✅ Dockerfile uses openjdk:21-jre or eclipse-temurin:21-jre

**Estimated Effort:** 10 person-days

---

### 2.2 Static Code Analysis & Quality Gates (1.5 weeks)
**Current State:** No static analysis tools (no Checkstyle, SpotBugs, SonarQube)  
**Target State:** Automated code quality checks, SQ gate enforced

#### Tasks:
- **2.2.1** Add Checkstyle Maven plugin (Google style rules)
- **2.2.2** Add SpotBugs Maven plugin (bug detection)
- **2.2.3** Configure maven-pmd-plugin (code smell detection)
- **2.2.4** Add failOnViolation=true to enforce in build
- **2.2.5** Fix all Checkstyle violations (formatting, naming, etc.)
- **2.2.6** Fix all SpotBugs issues (potential bugs)
- **2.2.7** Fix all PMD violations (design issues)
- **2.2.8** Integrate SonarQube (optional cloud instance for CI/CD)
- **2.2.9** Update CI/CD to run static analysis pre-build

**Success Criteria:**
- ✅ Maven build includes static analysis (mvn clean verify)
- ✅ Zero critical/blocker issues
- ✅ Build fails if new violations introduced
- ✅ SonarQube dashboard shows clean code

**Estimated Effort:** 12 person-days

---

### 2.3 Test Coverage Expansion (1.5 weeks)
**Current State:** 41% test coverage (2.4K test LOC / 5.7K main LOC)  
**Target State:** 70% coverage with high-value integration and E2E tests

#### Tasks:
- **2.3.1** Audit existing test suite, identify coverage gaps
- **2.3.2** Write unit tests for business logic (service layer):
  - AlertService, RoutingService, ScheduleService
  - Error cases, edge cases, validation
- **2.3.3** Write integration tests with TestContainers (PostgreSQL + PostGIS):
  - Network repository CRUD, GeoJSON queries
  - Alert lifecycle (create, update, soft delete)
  - Service alert targets (N+1 optimization testing)
- **2.3.4** Write API integration tests (test full request/response flow):
  - GET /api/v1/lines, /stations, /network/geojson
  - POST /admin/alerts, /admin/lines (with JWT auth)
  - Error responses (400, 401, 404, 500)
- **2.3.5** Add JaCoCo Maven plugin for coverage reporting
- **2.3.6** Configure CI/CD to fail build if coverage < 70%
- **2.3.7** Document test strategy and module-by-module coverage targets

**Success Criteria:**
- ✅ Overall code coverage ≥ 70% (JaCoCo report)
- ✅ All service classes covered
- ✅ All integration points tested with TestContainers
- ✅ All API endpoints tested with JWT auth
- ✅ Build fails if coverage drops below 70%

**Estimated Effort:** 14 person-days

---

### 2.4 Dependency Audit & Upgrades (0.5 weeks)
**Current State:** Spring Boot 3.4.5 BOM (up-to-date, 0 high-severity CVEs)  
**Target State:** All dependencies pinned, audit automated, upgrade policy documented

#### Tasks:
- **2.4.1** Run mvn dependency:check and resolve any CVEs
- **2.4.2** Review and update direct dependencies (9 managed by BOM)
- **2.4.3** Pin TestContainers 1.21.4+ (Docker 29 API compatibility)
- **2.4.4** Add maven-dependency-check-plugin (OWASP CVE scanning)
- **2.4.5** Document dependency upgrade policy (quarterly reviews)
- **2.4.6** Add CI/CD step to scan dependencies on every build

**Success Criteria:**
- ✅ Zero high-severity CVEs detected by OWASP
- ✅ All CVE scan results logged and tracked
- ✅ Dependency policy documented

**Estimated Effort:** 3 person-days

---

### Phase 2 Deliverables:
- ✅ Java 21 LTS build (Dockerfile and Maven)
- ✅ Static analysis pipeline (Checkstyle, SpotBugs, PMD)
- ✅ 70% code coverage (JaCoCo)
- ✅ 30+ integration tests with TestContainers
- ✅ All CVEs resolved
- ✅ Updated CI/CD workflow with quality gates

---

## Phase 3: Cloud DevOps & Observability (Weeks 9-12)
**Objective:** Enable production monitoring, scalability, and CI/CD automation.  
**Effort:** 4 weeks | **Team:** 2 engineers (1 cloud, 1 DevOps)  
**Dependency:** Phase 2 complete (clean build required)

### 3.1 CI/CD Pipeline Setup (2 weeks)
**Current State:** No CI/CD pipeline (GitHub Actions not implemented)  
**Target State:** Automated build, test, scan, Docker push, deploy-to-staging

#### Tasks:
- **3.1.1** Create .github/workflows/build-test-publish.yml:
  - Trigger: push to main, PR
  - Build: mvn clean package
  - Test: mvn test (with coverage report)
  - Static analysis: Checkstyle, SpotBugs, SonarQube scan
  - Docker build: Build and tag image
  - Push to Azure Container Registry (ACR)
  - Deploy to staging (Azure App Service slot or ACI)
- **3.1.2** Set up GitHub repository secrets (AZURE_CREDENTIALS, ACR_LOGIN_SERVER)
- **3.1.3** Create Azure Container Registry (ACR) for image storage
- **3.1.4** Configure GitHub OIDC federated identity for Azure authentication (no secrets in CI/CD)
- **3.1.5** Set up Docker BuildKit for faster builds
- **3.1.6** Create test reports upload (JaCoCo, SonarQube)
- **3.1.7** Add PR checks: build must succeed, coverage must be ≥70%, no critical SQ issues
- **3.1.8** Test full pipeline end-to-end (dummy PR)

**Success Criteria:**
- ✅ CI/CD runs on every push and PR
- ✅ All checks pass (build, test, coverage, analysis)
- ✅ Docker image built and pushed to ACR
- ✅ Staging environment updates automatically
- ✅ PR blocked if any check fails

**Estimated Effort:** 12 person-days

---

### 3.2 Application Insights Integration (1.5 weeks)
**Current State:** Console logging only, no structured observability  
**Target State:** Application Insights (Azure Monitor), distributed tracing, custom metrics

#### Tasks:
- **3.2.1** Create Azure Application Insights resource
- **3.2.2** Add Spring Cloud Azure Application Insights starter (spring-cloud-azure-starter-monitor-spring-cloud-sleuth)
- **3.2.3** Configure Application Insights connection string in Key Vault
- **3.2.4** Enable distributed tracing (Spring Cloud Sleuth) with X-Request-Id propagation
- **3.2.5** Configure custom metrics:
  - API request latency (by endpoint)
  - Alert lifecycle metrics (created, updated, resolved)
  - Data import duration (by source)
  - Database query count and latency
- **3.2.6** Set up log aggregation (filter by request ID for debugging)
- **3.2.7** Create Application Map (understand service dependencies)
- **3.2.8** Set up live metrics dashboard (monitor in real-time)
- **3.2.9** Configure alerts (response time > 5s, error rate > 1%, availability < 99%)
- **3.2.10** Create Azure Monitor workbooks for operational dashboards

**Success Criteria:**
- ✅ Application Insights receives telemetry from all requests
- ✅ Distributed traces visible with request flow
- ✅ Custom metrics dashboard shows API latency, alert counts, DB queries
- ✅ Alerts fire correctly for error/latency spikes
- ✅ Logs searchable by request ID

**Estimated Effort:** 10 person-days

---

### 3.3 Azure Database & Backup Strategy (1 week)
**Current State:** Local PostgreSQL only, no managed backup  
**Target State:** Azure Database for PostgreSQL (Flexible Server), automated backups, DR plan

#### Tasks:
- **3.3.1** Create Azure Database for PostgreSQL - Flexible Server
  - Region: Same as App Service (low latency)
  - Compute: 2 vCores, 8 GB RAM (start, scale as needed)
  - Storage: 64 GB (auto-scale enabled)
  - PostgreSQL 15 with PostGIS extension pre-installed
  - High availability enabled (standby replica)
- **3.3.2** Configure firewall rules (allow App Service subnet)
- **3.3.3** Enable SSL enforcement (REQUIRE in connection strings)
- **3.3.4** Migrate schema and data:
  - Export local DB: pg_dump
  - Apply Flyway migrations on Azure (first time setup)
  - Validate data integrity
- **3.3.5** Test connection pooling (HikariCP 20 connections for prod)
- **3.3.6** Configure automated backups (7-day retention + long-term monthly backups)
- **3.3.7** Test restore procedure (restore to point-in-time 24h ago)
- **3.3.8** Document disaster recovery plan:
  - RTO: 1 hour (restore from most recent backup)
  - RPO: 15 min (backup frequency)

**Success Criteria:**
- ✅ Azure DB created with HA enabled
- ✅ Schema and data migrated successfully
- ✅ App Service connects with SSL
- ✅ Connection pooling working (monitored in Azure Portal)
- ✅ Backup and restore tested
- ✅ DR plan documented

**Estimated Effort:** 8 person-days

---

### Phase 3 Deliverables:
- ✅ GitHub Actions CI/CD pipeline (build → test → push → deploy)
- ✅ Azure Container Registry with images
- ✅ Application Insights with dashboards and alerts
- ✅ Azure Database for PostgreSQL with HA
- ✅ Automated backup and restore verified
- ✅ CI/CD security: GitHub OIDC federated identity, no secrets
- ✅ Production deployment checklist

---

## Phase 4: Performance Optimization (Weeks 13-16)
**Objective:** Achieve production SLAs (response time <500ms, 99.9% availability, cost optimized).  
**Effort:** 4 weeks | **Team:** 1 performance engineer, 1 DBA  
**Dependency:** Phase 3 complete (staging environment with real monitoring)

### 4.1 Redis Caching Implementation (1.5 weeks)
**Current State:** No caching, all requests hit database  
**Target State:** Distributed cache (Redis) with invalidation strategy

#### Tasks:
- **4.1.1** Create Azure Cache for Redis (Premium tier, 1 GB, 6 replicas for HA)
- **4.1.2** Add Spring Data Redis starter dependency
- **4.1.3** Implement cache configuration (connection pooling, serialization)
- **4.1.4** Add caching to high-traffic endpoints:
  - GET /api/v1/lines (TTL: 1 hour) - rarely changes
  - GET /api/v1/stations (TTL: 1 hour)
  - GET /api/v1/network/geojson (TTL: 6 hours) - large payload
  - GET /api/v1/alerts (TTL: 10 minutes) - frequently updated
- **4.1.5** Implement cache invalidation:
  - @CacheEvict on POST/PUT/DELETE endpoints
  - Scheduled cache refresh (Quartz for scheduled imports)
  - Manual cache flush endpoint (admin-only)
- **4.1.6** Add cache statistics endpoint (Redis monitoring)
- **4.1.7** Load test cache effectiveness (simulate 1000 req/s)
- **4.1.8** Document cache strategy and TTL rationale

**Success Criteria:**
- ✅ Cache hit ratio > 80% for popular endpoints
- ✅ API response time: 200ms → 50ms for cached requests
- ✅ Cache invalidation working correctly (no stale data)
- ✅ Load test passes (1000 req/s with 50ms latency)
- ✅ Redis memory usage < 500 MB (within budget)

**Estimated Effort:** 10 person-days

---

### 4.2 Database Query Optimization (1.5 weeks)
**Current State:** No query profiling, potential N+1 issues, suboptimal indexes  
**Target State:** Profiled queries, optimized joins, composite indexes

#### Tasks:
- **4.2.1** Enable query logging (spring.jpa.show-sql=true + Hibernate SQLStatementLogger)
- **4.2.2** Profile all API endpoints with slow-query threshold (>100ms)
- **4.2.3** Fix N+1 problems:
  - service_alert_target @ElementCollection → use @Fetch(FetchMode.JOIN)
  - Line.stations relationship → use @EntityGraph in repository
  - Alert.targets → lazy-load with specific query for performance
- **4.2.4** Add @Query with explicit fetch joins (avoid default LazyInitializationException)
- **4.2.5** Create composite indexes:
  - (line_id, status) for alert queries
  - (station_id, created_at DESC) for history queries
  - (geometry) GiST index for GeoJSON queries (already exists)
- **4.2.6** Batch operations:
  - Use JdbcTemplate for bulk inserts (data imports)
  - Implement batching in @Transactional methods
- **4.2.7** Load test with realistic data volume (100K stations, 1M alerts)
- **4.2.8** Document query optimization guidelines for team

**Success Criteria:**
- ✅ Average query time < 50ms (99th percentile < 200ms)
- ✅ No N+1 queries detected (verify with query count assertions)
- ✅ Indexes optimized (query plans using indexes)
- ✅ Bulk imports 10K records in < 5 seconds
- ✅ Load test: 500 req/s with p99 latency < 500ms

**Estimated Effort:** 10 person-days

---

### 4.3 Azure Deployment & Scaling (0.75 weeks)
**Current State:** Staging environment only (manual deployment)  
**Target State:** Production environment with auto-scaling, CDN, load balancer

#### Tasks:
- **4.3.1** Create production App Service plan (Premium P1V2 or higher)
- **4.3.2** Deploy application container to App Service
- **4.3.3** Configure auto-scaling rules:
  - Scale out if CPU > 70% or memory > 80%
  - Scale in if CPU < 30% (cooldown 5 min)
  - Min instances: 2 (high availability)
  - Max instances: 10 (cost cap)
- **4.3.4** Set up Azure CDN (front GeoJSON and static assets)
  - Cache control: 1 hour for GeoJSON, 24h for static
  - GZip compression enabled
- **4.3.5** Configure Application Gateway (load balancer, SSL termination)
  - Health probe: /api/v1/info (must respond 200)
  - Session affinity: Cookie-based
- **4.3.6** Enable Azure WAF (DDoS protection, SQL injection detection)
- **4.3.7** Validate production health checks
- **4.3.8** Set up drain time for graceful shutdown (Kubernetes readiness probe behavior)

**Success Criteria:**
- ✅ App Service scales up/down based on metrics
- ✅ CDN cache hit ratio > 90% for GeoJSON
- ✅ Application Gateway health probes pass
- ✅ WAF rules logged and monitored
- ✅ Graceful shutdown (in-flight requests complete)

**Estimated Effort:** 6 person-days

---

### 4.4 Performance Testing & Validation (0.75 weeks)
**Current State:** No load or stress tests  
**Target State:** Validated for 5000 concurrent users, <500ms latency (p99)

#### Tasks:
- **4.4.1** Set up JMeter or Gatling load testing framework
- **4.4.2** Create test scenarios:
  - Baseline: 100 concurrent users, read-only traffic
  - Ramp-up: 100 → 1000 → 5000 users (10 min ramp)
  - Mixed: 80% reads (GET), 20% writes (POST/PUT)
  - Spike: 5000 → 10000 users (sudden spike)
- **4.4.3** Run load tests against staging environment
- **4.4.4** Collect metrics:
  - Response time (avg, p50, p95, p99)
  - Error rate (acceptable: <0.1%)
  - Throughput (req/s)
  - Database connection pool utilization
  - Redis cache hit ratio
- **4.4.5** Identify bottlenecks and resolve (may trigger Phase 4.2 re-optimization)
- **4.4.6** Document performance baselines and SLAs
- **4.4.7** Create on-call playbook for performance incidents

**Success Criteria:**
- ✅ Load test: 5000 concurrent users, <500ms p99 latency
- ✅ Error rate < 0.1% (only timeout or connection errors under stress)
- ✅ Throughput > 2000 req/s
- ✅ Database connection pool not exhausted (< 80% utilization)
- ✅ Redis memory stable (no memory leaks)

**Estimated Effort:** 5 person-days

---

### Phase 4 Deliverables:
- ✅ Redis caching with invalidation strategy
- ✅ Optimized database queries (no N+1, composite indexes)
- ✅ Production App Service with auto-scaling
- ✅ Azure CDN for static/GeoJSON content
- ✅ Load test results: 5000 users, <500ms latency
- ✅ On-call runbooks and performance dashboards
- ✅ Cost optimization report (estimated $1K-2K/month for prod)

---

## Cross-Phase Concerns

### Security & Compliance
- ✅ OAuth2/JWT for all APIs
- ✅ Azure Key Vault for secrets
- ✅ HTTPS/TLS with HSTS
- ✅ Azure WAF for DDoS/SQL injection
- ✅ Audit logging (X-Request-Id, admin actor)
- ✅ Data residency (keep DB in same region as app)
- ✅ Backup encryption (Azure managed keys)

### Observability & Debugging
- ✅ Application Insights (distributed tracing, custom metrics)
- ✅ Structured logging (JSON with request ID)
- ✅ Performance dashboards (response time, error rate, cache hit ratio)
- ✅ Alerting (anomaly detection, threshold-based)
- ✅ On-call dashboards and runbooks

### Testing Strategy
- **Unit Tests:** Service layer (41% baseline → 70% coverage)
- **Integration Tests:** Repository layer with TestContainers (PostgreSQL + PostGIS)
- **API Tests:** Full HTTP request/response with JWT auth
- **Load Tests:** 5000 concurrent users, mixed read/write
- **Security Tests:** OWASP Top 10, JWT validation, CORS

### Rollback & Recovery
- **Database:** Point-in-time restore (15-min RPO, 1-hour RTO)
- **Application:** Blue-green deployment (App Service slots)
- **Secrets:** Azure Key Vault versioning (rotate without downtime)
- **Configuration:** Environment variable rollback (CI/CD revision history)

### Cost Estimation
| Component | Tier | Monthly Cost |
|-----------|------|--------------|
| App Service | Premium P1V2 | $200 |
| Azure Database for PostgreSQL | 2 vCores | $300 |
| Azure Cache for Redis | Premium 1GB | $200 |
| Application Insights | Pay-as-you-go (100GB ingestion) | $150 |
| Azure Container Registry | Premium | $60 |
| Azure CDN | Standard | $100 |
| **Total** | | **~$1,010/month** |

*Note: Costs scale with data volume and traffic. Implement auto-scaling and CDN to optimize.*

---

## Success Criteria & Go-Live Checklist

### Security Sign-Off
- [ ] OAuth2/Keycloak configured with RBAC
- [ ] All secrets in Azure Key Vault (zero in git)
- [ ] HTTPS/TLS enforced with A+ SSL score
- [ ] Security audit passed (pen test if required)
- [ ] Audit logging enabled and monitored

### Performance Sign-Off
- [ ] Load test: 5000 concurrent users, <500ms p99
- [ ] Database query optimization complete (no N+1)
- [ ] Redis cache effective (>80% hit ratio)
- [ ] Error rate < 0.1%
- [ ] Disk/memory/CPU headroom for 2x traffic spike

### Operations Sign-Off
- [ ] CI/CD pipeline automated (0 manual deployments)
- [ ] Application Insights dashboard set up with alerts
- [ ] Auto-scaling configured and tested
- [ ] Backup and restore tested (DR plan verified)
- [ ] On-call runbooks documented
- [ ] Team trained on production operations

### Compliance Sign-Off
- [ ] Data residency in same Azure region
- [ ] Backup encryption enabled
- [ ] Audit trails accessible and compliant
- [ ] API rate limiting (if required)
- [ ] GDPR/data retention policies documented

---

## Timeline & Gantt View

```
Phase 1 (Sec):  [████████████] Weeks 1-4  (OAuth2, Key Vault, TLS)
Phase 2 (Java): [████████████] Weeks 5-8  (Java 21, Tests, SCA)
Phase 3 (DevOps): [████████████] Weeks 9-12 (CI/CD, App Insights, DB)
Phase 4 (Perf): [████████████] Weeks 13-16 (Cache, Queries, Load test)
```

### Critical Path Summary
- **Week 4:** OAuth2/Secrets/TLS complete, Phase 2 can start
- **Week 8:** Java 21/Tests/SCA complete, Phase 3 can start
- **Week 12:** CI/CD/Observability complete, Phase 4 can start
- **Week 16:** Production ready for deployment

---

## Team & Resource Allocation

| Phase | Team | Allocation | Notes |
|-------|------|-----------|-------|
| Phase 1 | 2 backend engineers | 4 weeks | OAuth2, security expertise |
| Phase 2 | 2 backend engineers | 4 weeks | Java, testing, code quality |
| Phase 3 | 1 cloud engineer + 1 DevOps | 4 weeks | Azure, CI/CD, monitoring |
| Phase 4 | 1 performance engineer + 1 DBA | 4 weeks | Load testing, query optimization |

**Total Effort:** ~16 person-weeks  
**Calendar Timeline:** 16 weeks (1 team, sequential phases)  
**Optimized Timeline:** 10-12 weeks (2-3 teams, parallel phases where possible)

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| Java 21 incompatibility | Low | High | Test early with ByteBuddy, Docker builds |
| OAuth2 complexity | Medium | Medium | Use Keycloak managed service, TestContainers |
| Performance degradation | Medium | High | Load test in Phase 4, profile early, cache strategy |
| Database migration issues | Low | High | Full backup, test restore, use Flyway migrations |
| Cost overruns | Medium | Medium | Monitor Azure usage, implement auto-scaling limits |
| Security vulnerability in transit | Low | Critical | Penetration test Phase 1, CVE scanning in Phase 2 |

---

## Metrics & KPIs

**Before Modernization:**
- Test coverage: 41%
- Security score: 6/10
- Performance: Baseline (no load test)
- Deployment: Manual (weeks)

**After Modernization (Target):**
- Test coverage: ≥70%
- Security score: 9.5/10
- Performance: p99 latency <500ms @ 5000 concurrent users
- Deployment: Automated (minutes)

---

## Next Steps

1. **Week 1:** Kick-off meeting, assign team leads, provision Keycloak
2. **Week 2:** Begin OAuth2 implementation, start Azure setup
3. **Weekly:** Sync on progress, resolve blockers, update burn-down chart
4. **Week 4 Review:** Phase 1 acceptance test, move to Phase 2
5. **Week 8 Review:** Phase 2 acceptance test, move to Phase 3
6. **Week 12 Review:** Phase 3 acceptance test, move to Phase 4
7. **Week 16:** Production deployment, go-live celebration 🚀

---

## References

- Spring Boot 3.4.5 Security: https://spring.io/projects/spring-security
- Azure Key Vault Integration: https://learn.microsoft.com/en-us/java/azure/spring-framework/
- Application Insights: https://learn.microsoft.com/en-us/azure/azure-monitor/app/app-insights-overview
- Keycloak: https://www.keycloak.org/
- JUnit 5 & TestContainers: https://www.testcontainers.org/
- Azure Database for PostgreSQL: https://learn.microsoft.com/en-us/azure/postgresql/
- GitHub Actions: https://docs.github.com/en/actions
