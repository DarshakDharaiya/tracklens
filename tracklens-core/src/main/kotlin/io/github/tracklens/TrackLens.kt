package io.github.tracklens

/**
 * Entry point of TrackLens.
 *
 * This file exists in both tracklens-core and tracklens-noop with an identical public API;
 * the build fails if the two drift apart. The full API arrives in Task 8 (plan section 2).
 * This copy is the debug artifact (tracklens-core).
 */
public object TrackLens {
    /** `true` in tracklens-noop, `false` in the debug artifact. Lets host code skip debug-only work. */
    @JvmStatic
    public val isNoOp: Boolean = false
}
