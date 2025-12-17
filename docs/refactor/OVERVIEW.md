# Refactor Plan - FUTURE WORK (After Current Bugs Fixed)

**Status:** 📋 Planning Phase (DO NOT IMPLEMENT YET)  
**Timeline:** 5-6 weeks (23 working days)  
**Goal:** Transform prototype → production-grade app

---

## ⚠️ IMPORTANT: Read This First

**This directory contains FUTURE PLANNING documents.**

If you're AG or another AI agent working on a specific bug fix:
- ❌ **DO NOT implement these changes now**
- ❌ **DO NOT reference these docs for current work**
- ✅ **Focus on `/docs/tasks/` for active assignments**

**These docs are for AFTER current bugs are resolved.**

---

## Current State Assessment

| Category | Score | Issue |
|----------|-------|-------|
| Architecture | 2/10 | No MVVM, business logic in Activities |
| Testing | 0/10 | Zero unit tests |
| Dependencies | 3/10 | Manual singletons, tight coupling |
| Data | 4/10 | SharedPreferences + raw SQLite |
| Background | 5/10 | Manual queue works but fragile |

**Overall:** 38/90 - Functional prototype, not production-ready

---

## Key Problems to Fix (Later)

### 1. Architecture
**Current:** Activities do everything (UI + logic + data)  
**Target:** Clean Architecture (Data/Domain/Presentation layers)  
**Benefit:** Testable, maintainable, scalable

### 2. Testing
**Current:** 0% coverage, cannot refactor safely  
**Target:** 80%+ coverage (unit + integration tests)  
**Benefit:** Catch bugs before production, refactor with confidence

### 3. Dependency Injection
**Current:** Global singletons (untestable)  
**Target:** Hilt DI (Google-recommended)  
**Benefit:** Loose coupling, mockable for tests

### 4. Data Persistence
**Current:** SharedPreferences (blocking) + raw SQLite  
**Target:** Room (type-safe) + DataStore (async)  
**Benefit:** Compile-time SQL checks, no main thread blocking

### 5. Background Processing
**Current:** Manual coroutine loop with AtomicBoolean  
**Target:** WorkManager (battery-efficient, Doze-aware)  
**Benefit:** Guaranteed execution, automatic retries, respects constraints

---

## 10-Phase Plan

1. **Foundation** (3 days): Hilt DI, build config, Timber logging
2. **Data Layer** (2 days): Room migration, DataStore preferences
3. **Domain Layer** (2 days): Use Cases, parser interfaces
4. **Presentation** (3 days): ViewModels, StateFlow, reactive UI
5. **Background** (2 days): WorkManager replaces manual queue
6. **Error Handling** (2 days): Result sealed class, structured errors
7. **Testing** (3 days): Unit tests, repository tests, VM tests
8. **UI/UX** (2 days): Material 3, empty states, polish
9. **Performance** (2 days): DB indexes, pagination, profiling
10. **Deployment** (2 days): ProGuard, docs, release build

---

## Expected ROI

### Development Velocity
- **Before:** 3-5 days per feature
- **After:** 1-2 days per feature
- **Improvement:** 2-3x faster

### Maintenance Cost
- **Before:** 18 hours/month
- **After:** 5 hours/month
- **Reduction:** 72%

### Bug Rate
- **Before:** High (no tests)
- **After:** Low (80%+ coverage)
- **Reduction:** ~70%

---

## When to Start This Refactor

**Prerequisites (ALL must be met):**
1. ✅ All P0/P1 bugs fixed (e.g., parsing issue resolved)
2. ✅ App stable in production for 2+ weeks
3. ✅ Stakeholder approval obtained
4. ✅ 5-6 week timeline allocated
5. ✅ Dedicated developer assigned

**Go/No-Go Decision Point:** Week 3 checkpoint  
- 20+ tests passing
- Room database operational
- One activity refactored to MVVM
- No regressions

---

## Detailed Planning Docs

See the full documents (if needed) for complete implementation details:
- `MASTER_PLAN.md` - Complete 23-day breakdown (if created)
- `EXECUTIVE_SUMMARY.md` - Business case, ROI analysis (if created)
- `DAY_1_CHECKLIST.md` - Step-by-step foundation setup (if created)

---

## Final Note

**This is GOOD planning, but WRONG timing.**

Current focus must be:
1. Fix BNN parsing (all sheet columns empty)
2. Stabilize runtime behavior
3. Resolve any critical bugs
4. Ensure production reliability

**THEN** we refactor for scalability.

**Don't let perfect be the enemy of good. Ship fixes first, refactor later.** ✅

