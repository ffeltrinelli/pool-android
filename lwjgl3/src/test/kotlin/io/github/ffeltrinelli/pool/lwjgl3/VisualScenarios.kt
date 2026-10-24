package io.github.ffeltrinelli.pool.lwjgl3

import io.github.ffeltrinelli.pool.opponent.OpponentDifficulty

internal enum class ScenarioGroup { TABLE, VIEW, POCKET, AID, COMPUTER, DIFFICULTY, CUE_VISIBILITY, RULES }
internal data class VisualScenario(val name: String, val group: ScenarioGroup)

/** Named groups make fixture selection explicit while retaining stable report names. */
internal object VisualScenarios {
    private val scenarios = listOf("initial-rack", "cue-and-aim", "cue-stroke-initial", "cue-stroke-withdrawn", "cue-stroke-return") + (1..6).map { "pocket-$it" } + listOf("scratch-foul", "cleared-result") + listOf(0, 12, 18, 24, 48).map { "rolling-markings-$it" }
    private val viewScenarios = listOf("settings-top", "player-initial", "player-right", "player-left", "player-reverse",
            "player-withdrawn", "player-return", "settings-player", "player-shot-pullback", "player-shot-overview", "player-return-to-aim", "player-foul", "player-cleared") +
            (1..6).map { "player-rail-$it" }
    private val pocketScenarios = listOf("top", "player").flatMap { mode ->
            listOf("corner", "side").flatMap { type -> listOf(0, 12, 24, 36, 60).map { "$mode-drop-$type-$it" } +
                listOf("$mode-shelf-$type", "$mode-jaw-$type") }
        }
    private val aidScenarios = listOf("top", "player").flatMap { mode ->
            listOf("enabled", "disabled", "glancing", "rail", "pocket", "blocked", "reset", "settings-off").map { "$mode-aid-$it" }
        }
    private val computerScenarios = listOf("top", "player").flatMap { mode ->
            listOf("human", "thinking", "cue-aim", "cue-withdrawn", "cue-contact", "foul-human", "foul-computer", "human-win", "computer-win", "settings").map { "$mode-computer-$it" }
        }
    private val difficultyScenarios = listOf("top", "player").flatMap { view ->
            OpponentDifficulty.entries.map { "$view-difficulty-${it.name.lowercase()}" }
        }
    private val cueVisibilityScenarios = listOf("top", "player").flatMap { view ->
            listOf("initial", "withdrawn", "return", "ball-overlap", "computer", "settings").map { "$view-cue-visibility-$it" }
        }
    private val rulesScenarios = listOf("top", "player").flatMap { view ->
        listOf("placement-break", "placement-anywhere", "placement-invalid", "open", "assigned", "on-eight", "wrong-first", "mixed").map { "$view-rules-$it" }
    }
    val all = scenarios.map { VisualScenario(it, ScenarioGroup.TABLE) } +
        viewScenarios.map { VisualScenario(it, ScenarioGroup.VIEW) } +
        pocketScenarios.map { VisualScenario(it, ScenarioGroup.POCKET) } +
        aidScenarios.map { VisualScenario(it, ScenarioGroup.AID) } +
        computerScenarios.map { VisualScenario(it, ScenarioGroup.COMPUTER) } +
        difficultyScenarios.map { VisualScenario(it, ScenarioGroup.DIFFICULTY) } +
        cueVisibilityScenarios.map { VisualScenario(it, ScenarioGroup.CUE_VISIBILITY) } +
        rulesScenarios.map { VisualScenario(it, ScenarioGroup.RULES) }
    private val byName = all.associateBy { it.name }
    fun named(name: String) = byName.getValue(name)
}
