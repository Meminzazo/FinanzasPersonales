package com.finanzaspersonales.domain.repository

import kotlinx.coroutines.flow.Flow

/** File locations are opaque to the domain; the data layer resolves provider URIs. */
interface BackupRepository {
 val lastBackup: Flow<String>
 suspend fun export(destination: String, password: CharArray)
 suspend fun restore(source: String, password: CharArray)
 suspend fun exportSafety(destination: String)
}
