# tracklens-storage is found at runtime with ServiceLoader (META-INF/services/io.github.tracklens.internal.spi.TrackLensModule).
# Keep the implementation and its no-argument constructor in hosts that minify debug builds.
-keep class io.github.tracklens.internal.storage.StorageModule { public <init>(); }
