# Gson reads these unannotated persistence fields through reflection. Retain
# their names and values while optimizing the player and Compose UI normally.
-keepclassmembers class yos.music.player.data.libraries.** {
    <fields>;
}
