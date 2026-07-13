# Kazi Sasa release ProGuard/R8 rules.
#
# Empty for now - isMinifyEnabled is false in the release build type (see
# app/build.gradle.kts), so this file isn't actually consulted yet. It exists
# purely because proguardFiles(...) references it by name; a missing file
# there fails the release build configuration outright.
#
# When you do turn minification on before a real release, start by adding
# keep rules for anything reflection-touches:
#   - kotlinx.serialization @Serializable classes (Room-adjacent DTOs/snapshots)
#   - Room entities/DAOs (the KSP-generated implementations reference these by name)
#   - Retrofit service interfaces
# The official docs for each library (Room, Retrofit, kotlinx.serialization)
# publish consumer ProGuard rules that cover most of this automatically via
# their AAR metadata - verify what R8 actually strips before hand-writing more
# than the gaps.
