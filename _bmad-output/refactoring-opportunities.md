# Code Refactoring Opportunities for Vi6Clean

Based on analysis of the codebase, here are the areas that can be refactored:

## 1. Large Classes (>300 lines) - Single Responsibility Violation

### fr.nekotine.vi6clean.game.Vi6Game (385 lines)
**Issues:**
- Combines game state management, scoreboard handling, phase machine control, dialog handling, and event processing
- 29 public methods covering vastly different concerns
- `close()` method has multiple unrelated cleanup operations in try-catch blocks

**Refactoring opportunities:**
1. Extract `IGameEventListener` interface with:
   - `PlayerJoined`, `PlayerLeft`, `PlayerTeamChange` handlers
2. Separate scoreboard management into dedicated class
3. Extract dialog creation to factory or builder pattern
4. Move all `setupGuard/Thief` logic to separate team handler classes

---

## 2. Configuration Manager Anti-Patterns

### All Configuration Classes (ConfigManager + ~15 sub-configs)
**Issues:**
- Static registry pattern in ConfigManager with hardcoded map lookups
- Every mechanic config mirrors same structure with minor variations
- No shared interfaces for common config operations

**Refactoring opportunities:**
1. Create `IConfig` interface with standard lifecycle methods
2. Implement abstract base class `AbstractMechanicConfig<T>`
3. Extract common validation logic to utility class
4. Consolidate repeated initialization patterns

---

## 3. Phase Classes - Template Method Violation

### fr.nekotine.vi6clean.game.phase.* (Vi6PhaseGlobal, Vi6PhaseLobby, etc.)
**Issues:**
- Each phase extends different base or stands alone
- No shared contract for phase behavior
- Similar initialization/cleanup patterns per phase

**Refactoring opportunities:**
1. Create `IPhase` interface with lifecycle methods:
   - `onStart()`, `onEnd()`, `onPlayerJoinLeave()`
2. Extract common phase setup to base implementation class
3. Move phase-specific logic to strategy pattern implementations

---

## 4. Status Effects - Template Method Problem

### fr.nekotine.vi6clean.status.effect.* (~15 files)
**Issues:**
- Each status effect is a single ~20 line class with minimal behavior
- Nearly identical structure across: Asthma, Emp, Murmur, Omnicapture, Suffocation, Tazed
- All extend from different base classes (AbstractInvisibilityStatus, standalone enums)

**Refactoring opportunities:**
1. Create `AbstractStatusEffect` base class
2. Extract common logic to abstract methods:
   - `onTick(Entity)`
   - `canApplyTo(Entity)`
   - `getDescription()`
3. Use composite pattern for multi-effect status (e.g., Omnicapture combines invis+emp)

---

## 5. Tool Handlers - Factory/G_builder Pattern

### fr.nekotine.vi6clean.tool.personal.* (~50 handlers)
**Issues:**
- Every tool has: `<Tool>.java` + `<Tool>Handler.java` pair
- Handler classes nearly identical except for tool-specific logic
- No abstraction between handler and implementation

**Refactoring opportunities:**
1. Create `AbstractToolHandler` with common lifecycle
2. Extract effect application to separate strategy pattern
3. Consolidate shared effects (glow, sound) in base class
4. Group related tools:
   - Visibility: Invisibility, Sneak, Shadow
   - Detection: Miner, Scanner, Radar, Sixth Sense
   - Movement: Speed, Double Jump, Hog Rider

---

## 6. Wrapper Classes - Composition Over Inheritance

### fr.nekotine.vi6clean.wrapper.* (5 wrappers)
**Issues:**
- Extension-per-phase creates tight coupling to game loop
- Each wrapper duplicates Player API calls

**Refactoring opportunities:**
1. Single `PlayerWrapper` implementation per context type
2. Use context object injection instead of inheritance
3. Separate persistent properties from phase-specific behavior

---

## 7. Constants - Missing Centralization

### All *.java files
**Issues:**
- Constants scattered everywhere (team names, dialog keys, etc.)
- No central constants module

**Refactoring opportunities:**
1. Create `package-info.java` with constant imports
2. Extract shared constants to `fr.nekotine.vi6clean.constant.*` (already exists)
3. Add missing categories:
   - Phase constants
   - Event type constants
   - Permission nodes

---

## 8. Dialog Handling - Builder Smell

### Vi6Game#displayGameSettingsDialog
**Issues:**
- 40+ line method building dialog
- Switch statement with one case + catch-all default

**Refactoring opportunities:**
1. Extract to `fr.nekotine.vi6clean.dialog.*` package
2. Create `IGameDialog` builder
3. Separate validation from display logic

---

## 9. Sound Repeated Constant Pattern

### Multiple files use identical sound patterns
**Issues:**
- `Sound.sound(Key.key("block.note_block.bell"), Sound.Source.VOICE, 1.0f, Xf)` appears often

**Refactoring opportunities:**
1. Create `SoundUtils` singleton
2. Extract to constants or method:
   ```java
   public static Sound createBellSound(float multiplier) {
       return Sound.sound(KEY_BELL, SOURCE_VOICE, 1.0f, multiplier);
   }
   ```

---

## 10. Collections - Copy-on-Write Anti-Pattern

### ObservableCollection usage in Vi6Game
**Issues:**
- Exposes ObservableCollection directly in `getPlayerList()`
- Clients receive shared mutable collections

**Refactoring opportunities:**
1. Return immutable snapshots via copies
2. Or add proper read-write contract documentation
3. Extract collection management to dedicated class

---

## 11. Team Management - State Machine

### guards.addAdditionCallback / remove callbacks
**Issues:**
- Complex state transitions in scoreboard team registration
- Callback chains obscure actual team state

**Refactoring opportunities:**
1. Create `TeamState` enum: LOBBY, PREPARE_INTEGRATE, INTEGRATED, DISINTEGRATING
2. Extract to state machine class
3. Add logging for team membership changes

---

## 12. Exception Message French/English Mix
**Issues:**
- Error messages in English (57 lines), but comments/log messages in French throughout
- Inconsistency: "Un erreur est survenur" (French) vs stack traces in English

**Refactoring opportunities:**
1. Decide on French only for user-facing strings
2. Create resource bundle for messages
3. Extract logging to message service

---

## Priority Recommendations

### HIGH PRIORITY (Core architecture):
1. **Vi6Game** - split into GameEngine + event handlers
2. **AbstractMechanicConfig** - reduce duplication
3. IP phase abstraction

### MEDIUM PRIORITY (Maintainability):
4. Status effect factory pattern
5. Tool handler base classes
6. Dialog builder extraction

### LOW PRIORITY (Nitpicks):
7. Constants centralization
8. Sound utility methods
9. Collection immutability contract

---

## Implementation Approach

1. Create interfaces for extracted concepts first
2. Implement backward compatible adapters  
3. Add unit tests for abstractions
4. Apply spotlessApply before committing refactors
5. Gradually migrate existing code to use new abstractions
