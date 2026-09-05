package com.github.ftpmirror

import kotlin.math.abs

enum class FileDecision {
    SKIP,
    UPLOAD,
    DOWNLOAD,
    CONFLICT
}

object SyncConflictResolver {
    const val CLOCK_SKEW_TOLERANCE_MS = 2_000L

    fun decide(
        localSize: Long,
        localModifiedMillis: Long?,
        remoteSize: Long,
        remoteModifiedMillis: Long?,
        policy: ConflictPolicy
    ): FileDecision {
        val localTime = localModifiedMillis?.takeIf { it > 0L }
        val remoteTime = remoteModifiedMillis?.takeIf { it > 0L }

        if (localTime != null && remoteTime != null) {
            val delta = localTime - remoteTime
            if (abs(delta) <= CLOCK_SKEW_TOLERANCE_MS && localSize == remoteSize) {
                return FileDecision.SKIP
            }

            return when (policy) {
                ConflictPolicy.LOCAL_WINS -> FileDecision.UPLOAD
                ConflictPolicy.REMOTE_WINS -> FileDecision.DOWNLOAD
                ConflictPolicy.NEWEST_WINS -> when {
                    delta > CLOCK_SKEW_TOLERANCE_MS -> FileDecision.UPLOAD
                    delta < -CLOCK_SKEW_TOLERANCE_MS -> FileDecision.DOWNLOAD
                    else -> FileDecision.CONFLICT
                }
            }
        }

        if (localSize == remoteSize) {
            // Without trustworthy timestamps, equal size is not enough to justify a destructive action.
            return FileDecision.SKIP
        }

        return when (policy) {
            ConflictPolicy.LOCAL_WINS -> FileDecision.UPLOAD
            ConflictPolicy.REMOTE_WINS -> FileDecision.DOWNLOAD
            ConflictPolicy.NEWEST_WINS -> FileDecision.CONFLICT
        }
    }
}
