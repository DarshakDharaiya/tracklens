package io.github.tracklens

/**
 * Marks declarations that are `public` only so TrackLens modules can share them.
 *
 * They are not part of the public API: they are excluded from the ABI dump, may change or disappear
 * in any release, and using them outside TrackLens is a compile error.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Internal TrackLens API. Do not use it outside TrackLens modules.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalTrackLensApi
