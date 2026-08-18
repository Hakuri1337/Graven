package tech.hakuri.graven.managers.impl.music;

public enum MusicPlaybackState {
    IDLE,
    FETCHING_INFO,
    FETCHING_URL,
    DOWNLOADING,
    READY,
    PLAYING,
    PAUSED,
    SEEKING,
    ERROR
}
