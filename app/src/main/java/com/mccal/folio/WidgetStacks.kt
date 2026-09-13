package com.mccal.folio

/**
 * iOS-style Smart Stacks. A widget placement shows its own widget first; [extras] are further widgets
 * stacked behind it in the same spot (swipe vertically between them). Pure logic, unit-tested.
 *
 * Safety: the widget host deletes any bound widget id that Home doesn't retain, so every stacked id
 * must stay in [retained] for as long as its placement (or the undoable previous layout) exists.
 */
internal object WidgetStacks {
    /** All cards of a stack in display order. */
    fun cards(primary: Int, extras: List<Int>?): List<Int> = (listOf(primary) + extras.orEmpty()).distinct()

    /** Bound widget ids held by stacks whose placement still exists in one of [slots]. */
    fun retained(stacks: Map<Int, List<Int>>, slots: Set<Int>): Set<Int> =
        stacks.filterKeys { it in slots }.values.flatten().filter { it >= 0 }.toSet()

    /** Drops stacks for placements that no longer exist and empty stacks. */
    fun prune(stacks: Map<Int, List<Int>>, slots: Set<Int>): Map<Int, List<Int>> =
        stacks.filter { (slot, extras) -> slot in slots && extras.isNotEmpty() }

    fun add(extras: List<Int>?, primary: Int, id: Int): List<Int> =
        if (id == primary || extras?.contains(id) == true) extras.orEmpty() else extras.orEmpty() + id

    /**
     * Removes [id] from a stack. Returns the new primary id and extras, or null when [id] isn't in the
     * stack or is its only card (remove the whole widget instead).
     */
    fun remove(primary: Int, extras: List<Int>, id: Int): Pair<Int, List<Int>>? = when {
        id != primary && id in extras -> primary to (extras - id)
        id == primary && extras.isNotEmpty() -> extras.first() to extras.drop(1)
        else -> null
    }

    /** Makes [id] the card shown first, keeping the others in their order after it. */
    fun showFirst(primary: Int, extras: List<Int>, id: Int): Pair<Int, List<Int>>? {
        val all = cards(primary, extras)
        if (id !in all || id == primary) return null
        val rest = all - id
        return id to rest
    }
}

/** Stacks and Smart Rotate for Home's widget drawing (provided by LauncherScreen). */
internal val LocalWidgetStacks = androidx.compose.runtime.compositionLocalOf { emptyMap<Int, List<Int>>() }
internal val LocalStackRotate = androidx.compose.runtime.compositionLocalOf { true }
