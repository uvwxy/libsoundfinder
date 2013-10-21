libsoundfinder
==============

Libsoundfinder provides an Android Activity to guide you to a given destination using sound.
The closer you move towards the target, the faster a repeated beep is heard. Also, the pitch is increased when moving towards the target.
To use the library import the SoundFinder class. Then use the helper function to launch the Activity via Intent.

*Use Headphones!*

Usage:
------
```java
    public static void findNode(
      Activity act, // required for getContext()
      double lat, // latitude of the destination
      double lon, // longitude of the destination
      double alt, // altitude of the destination (not used yet!)
      double precision, // maximum location error, if this is exceeded a warning sound is heard
      double zoneRadius, // radius around destination, on entering destination, target sound is played
      double maxDistance) // the maximum distance to use to generate sounds.
                          // use the initial distance to the destination
```
#### Exampe:
```java
    Location l = getLocation(...)
    SoundFinder.findNode(getActivity(),l.getLatitude(),l.getLongitude(), l.getAltitude(), 25, 15, dist);
```

### Sound files:
  - sound_beep.wav ("right" direction)
  - sound_destination.wav (within zoneRadius)
  - sound_error.wav (if location error is > precision)
  - sound_noise_static.wav ("wrong" direction)
