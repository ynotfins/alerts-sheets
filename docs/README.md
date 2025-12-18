# Documentation Organization

This directory contains all project documentation, organized by purpose and audience.

## Directory Structure

### `/docs/tasks/` - **ACTIVE TASKS** (AG should read these)
**Purpose:** Current work items, bug fixes, immediate implementation tasks  
**Audience:** AI agents (AG, Claude, etc.) working on active features  
**Lifespan:** Temporary (archive after completion)

**Current Files:**
- `AG_PARSING_FIX_PROMPT.md` - ⏸️ Paused: Original comprehensive parsing fix
- `AG_FINAL_PARSING_FIXES.md` - ✅ **ACTIVE:** Phase 1 parsing fixes (3 changes)
- `AG_QUICK_FIX_SUMMARY.md` - ✅ **ACTIVE:** TL;DR for AG (quick reference)

---

### `/docs/architecture/` - **SYSTEM DESIGN** (Reference for understanding)
**Purpose:** How the system works, data flows, integration specs  
**Audience:** All developers and AI agents (read-only reference)  
**Lifespan:** Permanent (update as system evolves)

**Files:**
- `HANDOFF.md` - System architecture, build instructions, troubleshooting
- `DIAGNOSTICS.md` - Debugging procedures, log analysis
- `parsing.md` - BNN parsing specification (authoritative)
- `PERMISSIONS_GUIDE.md` - Maximum permissions setup (Android 15+)
- `SHEET_UPDATE_LOGIC.md` - **NEW:** Google Sheet column behavior (static vs dynamic)
- `VISUAL_STANDARD.md` - **NEW:** Underlined headers = static fields (visual convention)
- `ENRICHMENT_PIPELINE.md` - Phase 2 backend architecture (geocoding, AI, property data)
- `STRATEGIC_DECISION.md` - Why frontend + backend parsing (architecture rationale)
- `CREDENTIALS_AUDIT.md` - **NEW:** What credentials we have vs need (Phase 2)

---

### `/docs/refactor/` - **FUTURE PLANNING** (AG should IGNORE these)
**Purpose:** Long-term improvements, architectural refactoring plans  
**Audience:** Human developers, project managers  
**Lifespan:** Permanent (planning documents)

**Files:**
- `MASTER_PLAN.md` - Complete refactor blueprint (23-day plan)
- `EXECUTIVE_SUMMARY.md` - Business case, ROI analysis
- `DAY_1_CHECKLIST.md` - Step-by-step implementation guide

**⚠️ IMPORTANT:** These are planning documents for AFTER current bugs are fixed.  
Do NOT implement these changes until explicitly instructed.

---

## Guidelines for AI Agents

### When AG is working on a specific task:
✅ **READ:**
- `/docs/tasks/{task-name}.md` (your current task)
- `/docs/architecture/` (understanding system context)
- Root-level files: `parsing.md`, `prompt.md` (active specs)

❌ **IGNORE:**
- `/docs/refactor/` (future work, will confuse context)

### When starting a refactor (future):
✅ **READ:**
- `/docs/refactor/` (now it's your task!)
- `/docs/architecture/` (current state to refactor from)

---

## Quick Reference

| Need | Location |
|------|----------|
| Current bug to fix | `/docs/tasks/` |
| How system works | `/docs/architecture/` |
| Parsing rules | `/docs/architecture/parsing.md` |
| Future improvements | `/docs/refactor/` |
| Build commands | `/docs/architecture/HANDOFF.md` |

