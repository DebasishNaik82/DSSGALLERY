# Preserve Kotlin generic signatures and runtime annotations required by Room and Lifecycle
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Room Database, Entities, DAOs, and KSP-generated *_Impl classes
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class com.example.data.local.*_Impl { *; }
-keepclassmembers class com.example.data.local.*Entity { *; }
-keep enum com.example.data.local.MediaType { *; }

# Coil Image, SVG, GIF, and Video Frame Decoders
-keep class * implements coil.util.DecoderServiceLoaderTarget {
    public <init>();
}
-keep class * implements coil.util.FetcherServiceLoaderTarget {
    public <init>();
}
-keep class coil.decode.SvgDecoder** { *; }
-keep class coil.decode.VideoFrameDecoder** { *; }
-keep class coil.decode.GifDecoder** { *; }
-keep class coil.decode.ImageDecoderDecoder** { *; }

# AndroidSVG vector parser & renderer
-keep class com.caverock.androidsvg.SVG { *; }
-keep class com.caverock.androidsvg.SVG$** { *; }
-keep class com.caverock.androidsvg.RenderOptions { *; }
-keep class com.caverock.androidsvg.PreserveAspectRatio { *; }
