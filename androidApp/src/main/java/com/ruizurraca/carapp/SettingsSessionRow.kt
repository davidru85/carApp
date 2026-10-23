package com.ruizurraca.carapp

/**
 * The session rows of the Settings account section (D-215). A permanent owner can sign out or delete
 * the account; a local or anonymous owner has one "delete local data" row, because its identity
 * exists only on this device (`D-156`, `docs/CONTRACTS.md §11.5`).
 */
internal enum class SettingsSessionRow { SIGN_OUT, DELETE_ACCOUNT, DELETE_LOCAL_DATA }
