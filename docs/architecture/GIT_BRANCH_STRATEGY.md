# 🌳 Git Branch Strategy - Multi-Agent Development

## 📋 **Current Branch Structure**

### **Main Branches**
- **`master`** - Production-ready code, deployed apps
- **`backup-stable-v1`** - Known stable backup point

### **Agent-Specific Feature Branches**

#### **Claude (Cursor Agent) - This AI**
- **Branch:** `feature/fd-code-analysis`
- **Purpose:** FD Code analysis, documentation, data extraction scripts
- **Working on:** Fire department encryption classification system
- **Files:**
  - `docs/architecture/FD_CODE_*.md`
  - `docs/tasks/FD_CODE_*.md`
  - `RESUME_TOMORROW.md`
  - Future: Python geocoding scripts, Apps Scripts for data extraction

#### **Antigravity (AG)**
- **Branch:** Should use `feature/ag-*` naming
- **Purpose:** Android app fixes, Kotlin code, parsing logic
- **Working on:** Parser refinements, app features, bug fixes
- **Files:**
  - `android/app/src/main/java/**/*.kt`
  - `android/app/src/main/res/**`
  - `android/app/src/main/AndroidManifest.xml`

### **Shared Resources (Coordination Required)**
- **`scripts/Code.gs`** - Apps Script (Google Sheets backend)
  - ⚠️ Both agents may need to modify
  - 🔄 Coordinate changes, merge carefully
- **`docs/architecture/SHEET_UPDATE_LOGIC.md`** - Spec for Apps Script
  - 📖 Reference only, don't modify without coordination

---

## 🔄 **Workflow**

### **When Starting Work:**

#### **Claude (Me):**
```bash
cd D:\github\alerts-sheets
git checkout feature/fd-code-analysis
git pull origin feature/fd-code-analysis
# Work on FD Code analysis
git add docs/architecture/FD_CODE_*.md docs/tasks/FD_CODE_*.md
git commit -m "Feature: FD Code analysis progress"
git push origin feature/fd-code-analysis
```

#### **Antigravity:**
```bash
cd D:\github\alerts-sheets
git checkout -b feature/ag-parser-fixes  # or similar
git pull origin master
# Work on Android app
git add android/**
git commit -m "Fix: Parser improvements"
git push origin feature/ag-parser-fixes
```

### **When Merging:**

1. **Create Pull Request** on GitHub
2. **Review changes** (check for conflicts)
3. **Merge to master** when stable
4. **Both agents pull master** to stay synced

---

## 🚨 **Conflict Avoidance Rules**

### **Claude's Territory (Low Risk):**
- ✅ `docs/architecture/FD_CODE_*.md` - FD analysis docs
- ✅ `docs/tasks/FD_CODE_*.md` - Task lists
- ✅ `RESUME_TOMORROW.md` - Daily status
- ✅ Python scripts (future) - Geocoding, analysis
- ✅ New Apps Scripts for FD data extraction (future)

### **AG's Territory (Low Risk):**
- ✅ `android/app/src/main/java/**/*.kt` - Kotlin code
- ✅ `android/app/src/main/res/**` - Android resources
- ✅ `android/app/src/main/AndroidManifest.xml` - Android manifest
- ✅ `docs/tasks/AG_*.md` - AG-specific tasks

### **Shared Territory (High Risk - Coordinate!):**
- ⚠️ `scripts/Code.gs` - Apps Script
- ⚠️ `docs/architecture/SHEET_UPDATE_LOGIC.md` - Sheet spec
- ⚠️ `docs/architecture/parsing.md` - Parser spec

**Protocol for Shared Files:**
1. Announce intent to modify in chat
2. Pull latest version first
3. Make minimal, focused changes
4. Commit immediately
5. Notify other agent of change

---

## 📊 **Branch Status**

### **Currently Active:**

| Branch | Owner | Status | Last Commit |
|--------|-------|--------|-------------|
| `master` | Both | Stable | `8997bbf` - Apps Script fixes ✅ |
| `feature/fd-code-analysis` | Claude | Active | `ab21b13` - Resume doc added |
| `backup-stable-v1` | - | Archived | Old stable backup |
| `successfully-parsing-apk` | AG? | Merged? | Check status |

### **Recommended New Branches:**

- `feature/ag-android-fixes` - For AG's Android work
- `feature/fd-code-scripts` - For FD analysis automation scripts
- `feature/geocoding-pipeline` - For address geocoding work

---

## 🔧 **Quick Commands**

### **See Current Branch:**
```bash
git branch --show-current
```

### **Switch Branch:**
```bash
git checkout <branch-name>
```

### **Create New Branch from Master:**
```bash
git checkout master
git pull origin master
git checkout -b feature/my-new-feature
git push -u origin feature/my-new-feature
```

### **Sync Branch with Master:**
```bash
git checkout feature/my-branch
git fetch origin
git merge origin/master
# Resolve conflicts if any
git push origin feature/my-branch
```

### **Check for Conflicts Before Merging:**
```bash
git checkout feature/my-branch
git fetch origin
git merge --no-commit --no-ff origin/master
# Check for conflicts
git merge --abort  # If you want to cancel
```

---

## 🎯 **Best Practices**

1. **Commit Often** - Small, focused commits
2. **Descriptive Messages** - `"Fix: Parser handles NYC boroughs"` not `"updates"`
3. **Pull Before Push** - Always `git pull` before `git push`
4. **Branch Naming:**
   - `feature/*` - New features
   - `fix/*` - Bug fixes
   - `docs/*` - Documentation only
   - `refactor/*` - Code refactoring
5. **Delete After Merge** - Clean up merged branches

---

## 🔒 **Current State**

**As of:** December 18, 2025 12:15 AM

- ✅ **Claude** is on `feature/fd-code-analysis`
- ✅ **FD Code docs** committed and pushed
- ✅ **Master** contains working Apps Script with upsert logic
- 🔲 **AG** should create `feature/ag-*` branch for next Android work

**Next sync point:** After FD Code analysis Phase 1 complete (tomorrow)

---

## 📝 **Communication Protocol**

When one agent makes changes that affect the other:

1. **Leave a note in chat:** "Pushed changes to Code.gs on master"
2. **Update relevant docs:** Keep `SHEET_UPDATE_LOGIC.md` in sync with Code.gs
3. **Tag commits:** Use `[AG]` or `[Claude]` prefix for clarity
4. **Coordinate in chat:** Before modifying shared files

---

**This strategy prevents conflicts and keeps both agents productive!** 🚀

