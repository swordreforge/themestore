# Screen-Off Rotation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the "Wait for Screen Off" feature by conditionally deferring rotation when the screen is on, and executing immediately when the screen turns off or the user switches back to immediate mode.

**Architecture:** Modify `ThemeRotationReceiver` to check screen state and set pending status. Update `SettingsPage` to trigger pending rotations immediately when the mode changes. Update `RotationProgressCard` to display the pending status.

**Tech Stack:** Kotlin, Android BroadcastReceiver, Jetpack Compose

---

### Task 1: Logic Layer - Implement Deferral in ThemeRotationReceiver

**Files:**
- Modify: `app/src/main/java/com/merak/service/ThemeRotationReceiver.kt`

- [ ] **Step 1: Import necessary classes**

Ensure `PowerManager` is imported (it already is).

- [ ] **Step 2: Refactor onReceive logic**

Replace the logic inside `CoroutineScope(Dispatchers.IO).launch { ... }` block (currently lines 49-60) with the following logic:

```kotlin
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 检查是否需要等待息屏
                val withoutScreenOff = ThemeRotationManager.isWithoutScreenOff()
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isScreenOn = powerManager.isInteractive

                if (withoutScreenOff && isScreenOn) {
                    // 分支 A: 需要等待息屏，且屏幕亮着 -> 挂起任务
                    Log.d(TAG, "Screen is ON and withoutScreenOff is true -> deferring rotation")
                    ThemeRotationManager.setPending(true)
                    
                    // 通知辅助服务更新内存状态
                    Intent(ThemeInstallAccessibilityService.ACTION_ROTATION_PENDING_SET).apply {
                        putExtra("pending", true)
                        setPackage(context.packageName)
                        context.sendBroadcast(this)
                    }
                    
                    // 取消进度通知，避免一直显示
                    ThemeRotationManager.cancelProgressNotification(context)
                } else {
                    // 分支 B: 立即执行 (不需要等待 OR 屏幕已灭)
                    Log.d(TAG, "Executing rotation immediately")
                    
                    // 获取 WakeLock
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK
                                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                                or PowerManager.ON_AFTER_RELEASE,
                        "ThemeStore:RotationWakeLock"
                    )
                    wakeLock.acquire(15 * 1000)

                    try {
                        ThemeRotationManager.performRotation(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Rotation failed", e)
                    } finally {
                        ThemeRotationManager.cancelProgressNotification(context)
                        ThemeRotationManager.scheduleNextRotation(context)
                        if (wakeLock.isHeld) wakeLock.release()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in rotation receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
```

*Note: You should remove the old WakeLock acquisition code outside the coroutine (lines 40-46) as it is now handled inside the coroutine in Branch B.*

- [ ] **Step 3: Verify the build compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/merak/service/ThemeRotationReceiver.kt
git commit -m "feat: implement screen-off deferral logic in rotation receiver"
```

---

### Task 2: Settings Layer - Immediate Execution on Switch Change

**Files:**
- Modify: `app/src/main/java/com/merak/ui/page/SettingsPage.kt`

- [ ] **Step 1: Modify the switch handler**

Locate the `SuperSwitch` for `rotationWithoutScreenOff` (around line 420).
Update the `onCheckedChange` block to check for pending tasks when switching back to immediate mode.

Replace the existing `onCheckedChange` block (lines 424-427) with:

```kotlin
                                onCheckedChange = { checked ->
                                    val wasPending = ThemeRotationManager.isPending()
                                    rotationWithoutScreenOff = checked
                                    ThemeRotationManager.setWithoutScreenOff(checked)

                                    // 如果用户切回"立即轮换"且有挂起任务，立即执行
                                    if (!checked && wasPending) {
                                        ThemeRotationManager.checkAndPerformPendingRotation(appContext)
                                        Toast.makeText(
                                            context,
                                            "已切换为立即轮换，当前待执行任务将立即运行",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
```

- [ ] **Step 2: Verify the build compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/merak/ui/page/SettingsPage.kt
git commit -m "feat: execute pending rotation immediately when switching back to instant mode"
```

---

### Task 3: UI Layer - Pending Status Display

**Files:**
- Modify: `app/src/main/java/com/merak/ui/page/RotationProgressCard.kt`

- [ ] **Step 1: Update LaunchedEffect logic**

Inside the `LaunchedEffect(refreshTick)` loop (currently lines 55-92), modify the text assignment logic to reflect the pending state.

Find the block where `remainingText` and `statusText` are assigned (around lines 73-78).
Update it to check `ThemeRotationManager.isPending()`.

Replace the logic inside the `if (nextTriggerTime > 0)` block:

```kotlin
            if (nextTriggerTime > 0) {
                val remainingMs = (nextTriggerTime - now).coerceAtLeast(0)
                val progressValue = if (intervalMs > 0) {
                    ((intervalMs - remainingMs).toFloat() / intervalMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
                progress = progressValue
                
                // 检查是否处于挂起等待息屏状态
                val isPending = ThemeRotationManager.isPending()
                
                if (isPending && remainingMs == 0L) {
                    // 挂起且时间已到 -> 显示等待息屏
                    elapsedText = formatTime((intervalMs / 60_000).toInt())
                    remainingText = "0m"
                    statusText = "等待息屏中..."
                } else {
                    // 正常倒计时
                    val remainingMin = (remainingMs / 60_000).toInt()
                    val elapsedMin = ((intervalMs - remainingMs) / 60_000).toInt().coerceAtLeast(0)
                    elapsedText = formatTime(elapsedMin)
                    remainingText = if (remainingMs == 0L) {
                        "即将触发..."
                    } else {
                        formatTime(remainingMin)
                    }
                    statusText = ""
                }
            }
```

- [ ] **Step 2: Verify the build compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL with no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/merak/ui/page/RotationProgressCard.kt
git commit -m "ui: show 'waiting for screen off' status when rotation is pending"
```

---

## Self-Review

### Spec Coverage
- [x] Receiver checks `isWithoutScreenOff` and `isInteractive` - Task 1, Step 2
- [x] Receiver sets pending and broadcasts if deferring - Task 1, Step 2
- [x] Receiver does NOT schedule next rotation if deferring - Task 1, Step 2
- [x] Receiver executes immediately if not deferring - Task 1, Step 2
- [x] SettingsPage checks pending on switch change - Task 2, Step 1
- [x] SettingsPage executes pending and shows toast - Task 2, Step 1
- [x] UI shows "等待息屏中..." when pending - Task 3, Step 1

### Placeholder Scan
- No "TBD", "TODO", or "fill in later" found.
- All code blocks contain complete, copy-pasteable implementations.

### Type Consistency
- `isWithoutScreenOff()` returns Boolean.
- `isInteractive` returns Boolean.
- `setPending(boolean)` takes boolean.
- `checkAndPerformPendingRotation(context)` takes Context.
- `isPending()` returns Boolean.
- All types match existing API.

### No Placeholders Found
- All steps contain exact code and commands.
- No references to undefined types or methods.
