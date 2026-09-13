package com.mccal.folio

/** JSON keys in the saved launcher state that the everywhere overlay (another component) also reads. */
internal object SettingKeys {
    const val PREFS = "launcher"
    const val STATE = "state"
    const val DOCK = "dock"
    const val LEFT_HANDED = "leftHanded"
    const val DOCK_EVERYWHERE = "dockEverywhere"
    const val ISLAND_EVERYWHERE = "islandEverywhere"
    const val ISLAND_EVENTS_OFF = "islandEventsOff"
    /** Island message cards skip notifications Android will already show as a pop-up. */
    const val MESSAGES_AVOID_DOUBLE = "messagesAvoidDouble"
}
