package dev.ashraf.buds4readonly.core;

/** Pure presentation policy shared by the activity and widget. */
public final class UiStateResolver {
    public enum VisualState { OFFLINE, NORMAL, LOCKED, WORKING, RECOVERY, ERROR }
    public enum Action { NONE, BLOCK, RESTORE }
    public record Presentation(VisualState visualState, boolean toggleEnabled, Action action) {}

    private UiStateResolver() {}

    public static Presentation resolve(SleepLockStatus lockStatus,
                                       ConnectionStatus connectionStatus) {
        if (connectionStatus != ConnectionStatus.CONNECTED) {
            Action retryAction = switch (lockStatus) {
                case LOCKED, RECOVERY -> Action.RESTORE;
                case NORMAL, ERROR -> Action.BLOCK;
                case WORKING -> Action.NONE;
            };
            return new Presentation(VisualState.OFFLINE,
                    lockStatus != SleepLockStatus.WORKING, retryAction);
        }
        return switch (lockStatus) {
            case NORMAL -> new Presentation(VisualState.NORMAL, true, Action.BLOCK);
            case LOCKED -> new Presentation(VisualState.LOCKED, true, Action.RESTORE);
            case RECOVERY -> new Presentation(VisualState.RECOVERY, true, Action.RESTORE);
            case ERROR -> new Presentation(VisualState.ERROR, true, Action.BLOCK);
            case WORKING -> new Presentation(VisualState.WORKING, false, Action.NONE);
        };
    }
}
