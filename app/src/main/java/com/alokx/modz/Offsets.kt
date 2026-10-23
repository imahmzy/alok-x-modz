package com.alokx.modz

/**
 * Free Fire 1.132.1 offsets.
 * These are a STARTING SET - FF shifts them on most updates.
 * If ESP is blank or boxes are off, these are the values to correct.
 */
object Offsets {
    const val IL2CPP = "libil2cpp.so"

    // ---- static fields (address = il2cppBase + value) ----
    const val STATIC_GAME_FACADE = 0x2D4A8C0L
    const val STATIC_CAMERA      = 0x2D4B1A8L

    // ---- GameFacade ----
    const val GF_LOCAL_PLAYER = 0x2C0L
    const val GF_PLAYER_LIST  = 0x2D8L

    // ---- List<Player> ----
    const val LIST_ITEMS = 0x10L
    const val LIST_COUNT = 0x18L

    // ---- il2cpp array ----
    const val ARRAY_DATA = 0x20L

    // ---- Player ----
    const val P_TRANSFORM = 0x38L
    const val P_HEALTH    = 0x9CL
    const val P_TEAM      = 0xA0L
    const val P_IS_DEAD   = 0x50L
    const val P_SPREAD    = 0xB4L

    // ---- Transform ----
    const val T_POSITION = 0x90L
    const val T_ROTATION = 0xA0L

    // ---- Camera ----
    const val C_TRANSFORM       = 0x20L
    const val C_WORLD_TO_CAMERA = 0x2E0L
    const val C_PROJECTION      = 0x330L

    const val HEAD_HEIGHT = 1.65f
}
