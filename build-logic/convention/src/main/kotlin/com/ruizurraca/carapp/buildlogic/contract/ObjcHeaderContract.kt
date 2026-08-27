package com.ruizurraca.carapp.buildlogic.contract

internal val REQUIRED_SWIFT_HEADER_SYMBOLS =
    setOf(
        "createSwiftAppGraph",
        "SwiftAppGraph",
        "VehicleListStateHolder",
        "VehicleFormStateHolder",
        "FuelEntryListStateHolder",
        "FuelEntryFormStateHolder",
        "SessionStateHolder",
        "SyncStateHolder",
        "VehicleListUiState",
        "VehicleListItemUi",
        "VehicleFormUiState",
        "FuelEntryListUiState",
        "FuelEntryListItemUi",
        "FuelEntryFormUiState",
        "SessionUiState",
        "SyncUiState",
        "UiMessage",
        "UiMessageKind",
        "SyncStatus",
        "FuelType",
        "AuthProvider",
        "Confirmation",
        "ConsumptionInvalidReason",
        "MoneyInputMode",
        "SessionPhase",
        "SyncTrigger",
    )

internal data class ObjcHeaderValidation(
    val missing: Set<String>,
    val forbidden: Set<String>,
    val swiftFactoryDeclarationCount: Int,
) {
    val isValid: Boolean
        get() = missing.isEmpty() && forbidden.isEmpty() && swiftFactoryDeclarationCount == 1
}

internal fun validateObjcHeader(header: String): ObjcHeaderValidation {
    val declarations =
        header
            .replace(Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("//.*"), " ")
    val missing = REQUIRED_SWIFT_HEADER_SYMBOLS.filterNotTo(sortedSetOf()) { declarations.contains(it) }
    val forbidden =
        FORBIDDEN_SWIFT_HEADER_SYMBOLS
            .filterValues { pattern -> pattern.containsMatchIn(declarations) }
            .keys
            .toSortedSet()
    val factoryCount = Regex("\\bcreateSwiftAppGraph\\b").findAll(declarations).count()

    return ObjcHeaderValidation(
        missing = missing,
        forbidden = forbidden,
        swiftFactoryDeclarationCount = factoryCount,
    )
}

private val FORBIDDEN_SWIFT_HEADER_SYMBOLS =
    mapOf(
        "AppProviders" to Regex("\\b(?:Shared)?AppProviders\\b"),
        "AppGraphDependencies" to Regex("\\b(?:Shared)?AppGraphDependencies\\b"),
        "buildAppGraph" to Regex("\\bbuildAppGraph\\b"),
        "AppGraph" to Regex("\\b(?:Shared)?AppGraph\\b"),
        "SyncController" to Regex("\\b(?:Shared)?SyncController\\b"),
        "CoroutineScope" to Regex("\\bCoroutineScope\\b"),
        "Outcome" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?Outcome\\b"),
        "AppError" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?AppError\\b"),
        "EntityId" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?EntityId\\b"),
        "OwnerId" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?OwnerId\\b"),
        "CurrencyCode" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?CurrencyCode\\b"),
        "VehicleRepository" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?VehicleRepository\\b"),
        "FuelEntryRepository" to Regex("\\b(?:Shared[A-Za-z0-9_]*)?FuelEntryRepository\\b"),
        "Greeting" to Regex("\\b(?:Shared)?Greeting\\b"),
    )
