package com.mccal.folio

/** Jake's reference measures 76px dock artwork against 106px home artwork. */
fun dockIconSize(homeIconSize: Float) = homeIconSize * (76f / 106f)

data class LayoutPreset(
    val iconSize: Float = 66f,
    val rowGap: Float = 8f,
    val dockWidth: Float = 68f,
    val dockPosition: Float = 0.56f,
    val dockAlignToGrid: Boolean = true,
) {
    fun sanitized() = copy(
        iconSize = iconSize.coerceIn(40f, 68f),
        rowGap = rowGap.coerceIn(0f, 28f),
        dockWidth = dockWidth.coerceIn(56f, 84f),
        dockPosition = dockPosition.coerceIn(0.25f, 0.75f),
    )
}

data class HomeGeometry(
    val expanded: Boolean,
    val homeWidth: Float,
    val gridWidth: Float,
    val iconSize: Float,
    val rowHeight: Float,
    val widgetHeight: Float,
    val contentTop: Float,
    val dockTop: Float,
    val dockHeight: Float,
    val dockRowHeight: Float,
    /** Short, wide windows: the page's rows are laid out as two 4-column halves side by side. */
    val splitColumns: Boolean = false,
    val cellWidth: Float = gridWidth / 4f,
    val zoneGap: Float = 0f,
)

/**
 * Where Home's 4×6 cells draw on a page. Stacked: one 4-column grid whose first two rows are the
 * half-height widget rows. Two columns (short, wide windows): rows before [splitRow] on the left and the
 * rest on the right, like a stacked iOS layout rearranging into two columns when there's width for it.
 */
data class HomeCellLayout(val cellWidth: Float, val topPitch: Float, val rowHeight: Float, val splitRow: Int?, val zoneGap: Float) {
    /** Rows 0–1 are widget-height halves, except on a two-column page with no widgets up there. */
    fun pitch(row: Int) = if (row < 2 && splitRow != 3) topPitch else rowHeight
    private fun onRight(row: Int) = splitRow != null && row >= splitRow
    fun x(column: Int, row: Int) = (if (onRight(row)) 4f * cellWidth + zoneGap else 0f) + column * cellWidth
    fun y(row: Int) = ((if (onRight(row)) splitRow!! else 0) until row).fold(0f) { sum, r -> sum + pitch(r) }
    fun spanHeight(row: Int, rows: Int) = (row until row + rows).fold(0f) { sum, r -> sum + pitch(r) }
    fun height(rows: Int) = if (splitRow == null) spanHeight(0, rows) else maxOf(spanHeight(0, splitRow), spanHeight(splitRow, rows - splitRow))

    companion object {
        /** [widgets] are (row, spanY) of the page's widgets; a widget is never cut across the two halves. */
        fun forPage(geometry: HomeGeometry, widgets: List<Pair<Int, Int>>): HomeCellLayout {
            val topPitch = (geometry.widgetHeight + 18f) / 2f
            val split = if (!geometry.splitColumns) null else
                // Like iPhone Duo's Home: widgets top-left with app rows under them, the rest of the apps on the right.
                (if (widgets.any { it.first < 2 }) listOf(4, 2, 3) else listOf(3, 4, 2))
                    .firstOrNull { s -> widgets.none { (row, span) -> row < s && row + span > s } }
            return HomeCellLayout(geometry.cellWidth, topPitch, geometry.rowHeight, split, geometry.zoneGap)
        }
    }
}

/** Advance old defaults without changing individually tuned values. */
fun upgradePreset(preset: LayoutPreset, schema: Int, expanded: Boolean): LayoutPreset = when {
    schema < 2 -> preset.copy(
        iconSize = if (preset.iconSize == if (expanded) 58f else 54f) 66f else preset.iconSize,
        rowGap = if (preset.rowGap == 12f) 8f else preset.rowGap,
        dockWidth = if (preset.dockWidth == 64f) 68f else preset.dockWidth,
    )
    schema == 2 && preset.iconSize == 60f -> preset.copy(iconSize = 66f)
    else -> preset
}

/** Shortest height that still counts as regular size (the unfolded screen in either rotation; the cover's landscape is ~475dp). */
const val REGULAR_MIN_HEIGHT_DP = 560f

/** Regular size class in both dimensions, like iOS size classes: never a device, display or orientation check. */
fun isRegularSize(widthDp: Float, heightDp: Float) = widthDp >= 600f && heightDp >= REGULAR_MIN_HEIGHT_DP

fun homeGeometry(width: Float, height: Float, preset: LayoutPreset, labels: Boolean, statusHeight: Float = 0f, labelHeight: Float = 20f, inLibrary: Boolean = false, homeBottomSpace: Float = 44f,
    /** Whether round controls (search, back) sit at the bottom of the rail on Home; without them the dock may run lower. */
    railControls: Boolean = true): HomeGeometry {
    val p = preset.sanitized()
    // Unfolded Duo layout only with regular size both ways; the cover in landscape is still compact.
    val expanded = width >= 650f && height >= REGULAR_MIN_HEIGHT_DP
    val homeWidth = if (expanded) minOf(460f, width * 0.56f) else width
    var gridWidth = (homeWidth - p.dockWidth - 44f).coerceAtLeast(192f)
    // Keep the same icon rhythm when labels are hidden; allow larger system text to fit.
    val labelSpace = if (labels) maxOf(20f, labelHeight) else 20f
    fun rowFor(iconSize: Float, gap: Float) = maxOf(48f, iconSize + labelSpace) + gap
    var icon = minOf(p.iconSize, (gridWidth / 4f - 10f).coerceAtLeast(32f))
    var gap = p.rowGap
    var widget = minOf(176f, gridWidth / 2f - 5f).coerceAtLeast(88f)
    val fitHeight = height - 16f - homeBottomSpace
    // Short, wide windows (the cover in landscape): two 4-column halves side by side instead of one tall,
    // squashed grid, so icons stay full size (iPad likewise keeps widgets in a column beside its apps).
    val zoneGap = 28f
    val splitCell = (gridWidth - zoneGap) / 8f
    val splitColumns = !expanded && width > height * 1.15f && splitCell >= 64f
    if (splitColumns) {
        icon = minOf(p.iconSize, splitCell - 16f)
        if (4f * rowFor(icon, gap) > fitHeight) gap = 0f
        if (4f * rowFor(icon, gap) > fitHeight) icon = minOf(icon, (fitHeight / 4f - labelSpace).coerceAtLeast(40f))
        widget = minOf(176f, 2f * splitCell - 10f)
        gridWidth = 8f * splitCell + zoneGap
    } else {
        // Other short windows: tighten row spacing, then icons, then the widget row, so a page fits the
        // height instead of running under the controls. Rows stay at least 48dp tall.
        fun needed() = widget + 18f + 4f * rowFor(icon, gap)
        if (needed() > fitHeight) gap = 0f
        if (needed() > fitHeight) icon = minOf(icon, ((fitHeight - widget - 18f) / 4f - labelSpace).coerceAtLeast(40f))
        if (needed() > fitHeight) widget = minOf(widget, (fitHeight - 18f - 4f * rowFor(icon, gap)).coerceAtLeast(88f))
    }
    val row = rowFor(icon, gap)
    val contentTop = ((height - (if (splitColumns) 4f * row else widget + 18f + 4f * row) - homeBottomSpace) / 2f).coerceIn(16f, 72f)
    // Search reclaims the redundant bottom controls' space for all four dock apps.
    // Extremely short windows still scroll rather than reduce touch targets below 48dp.
    // The status rail sits at the content top; in two columns the dock shares its edge with it, so it starts below.
    val homeReserve = if (railControls) 124f else 28f
    val bottomReserve = if (inLibrary) 12f else homeReserve
    val belowStatus = contentTop + statusHeight
    val topLimit = if (splitColumns && statusHeight > 0f && height - belowStatus - bottomReserve >= 4f * 48f + 16f) belowStatus
        else maxOf(8f, statusHeight)
    // Outer dock edges span the first through third icon images, excluding the last label.
    // Never shorter than four 48dp dock targets, even when a short window has shrunk the rows.
    val desiredHeight = maxOf(4f * 48f + 16f, if (p.dockAlignToGrid) 2f * row + icon else 256f)
    val dockHeight = minOf(desiredHeight, (height - topLimit - bottomReserve).coerceAtLeast(76f))
    val dockRowHeight = ((dockHeight - 16f) / 4f).coerceAtLeast(48f)
    // Use the home position as the anchor, so removing library buttons does not
    // move a low-positioned dock on ordinary page swipes. Move up only to fit.
    val homeDockHeight = minOf(desiredHeight, (height - topLimit - homeReserve).coerceAtLeast(76f))
    // Aligned with the app rows: below the widget row when stacked; in two columns the app rows start at the
    // top, so the dock starts below the status rail instead of running into it.
    // Two columns (iPhone Duo): status pinned to the top of the rail, the dock to the bottom, open space between.
    val homeDockTop = (if (splitColumns) height - homeReserve - homeDockHeight
        else if (p.dockAlignToGrid) contentTop + widget + 18f else height * p.dockPosition - homeDockHeight / 2f)
        .coerceIn(topLimit, maxOf(topLimit, height - homeDockHeight - homeReserve))
    val dockTop = homeDockTop.coerceIn(topLimit, maxOf(topLimit, height - dockHeight - bottomReserve))
    return HomeGeometry(expanded, homeWidth, gridWidth, icon, row, widget, contentTop, dockTop, dockHeight, dockRowHeight,
        splitColumns = splitColumns, cellWidth = if (splitColumns) splitCell else gridWidth / 4f, zoneGap = if (splitColumns) zoneGap else 0f)
}

/** Keep stored order stable across installs, removals and configuration changes. */
fun reconcileOrder(saved: List<String>, installed: List<String>): List<String> {
    val present = installed.toSet()
    return (saved.filter { it in present } + installed).distinct()
}

/** Installing an app must never create a home-screen pin. */
fun reconcilePins(saved: List<String>, installed: List<String>): List<String> {
    val available = installed.toSet()
    return saved.filter { it in available }.distinct()
}

fun migrateHomePins(legacy: List<String>, installed: List<String>, suggested: List<String>): List<String> {
    val surviving = reconcilePins(legacy, installed)
    val oldSet = surviving.toSet()
    val wasReordered = surviving.isNotEmpty() && surviving != installed.filter { it in oldSet }
    return if (wasReordered) surviving.take(16) else reconcilePins(suggested, installed).take(16)
}

fun homePageCount(cellCount: Int) = maxOf(1, (cellCount + HOME_CELLS - 1) / HOME_CELLS)

fun moveApp(order: List<String>, id: String, offset: Int): List<String> {
    val from = order.indexOf(id)
    if (from < 0) return order
    val to = (from + offset).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
