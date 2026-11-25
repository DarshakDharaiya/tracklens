package io.github.tracklens

/** Marks TrackLens configuration builders, so nested `trackLensConfig { }` blocks can't call outer builders by accident. */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class TrackLensDsl
