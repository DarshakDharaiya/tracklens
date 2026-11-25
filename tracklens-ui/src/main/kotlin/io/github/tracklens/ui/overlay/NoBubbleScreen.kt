package io.github.tracklens.ui.overlay

/**
 * Marks an activity that never shows the bubble: TrackLens' own screens (Task 19 onwards), where a bubble that opens
 * TrackLens would only get in the way. Host apps use `BubbleConfig.excludeActivities` instead.
 */
internal interface NoBubbleScreen
