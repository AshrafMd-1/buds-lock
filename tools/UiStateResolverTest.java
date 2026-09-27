import dev.ashraf.buds4readonly.core.ConnectionStatus;
import dev.ashraf.buds4readonly.core.SleepLockStatus;
import dev.ashraf.buds4readonly.core.UiStateResolver;

public final class UiStateResolverTest {
    public static void main(String[] args) {
        require(UiStateResolver.resolve(SleepLockStatus.LOCKED, ConnectionStatus.CONNECTED)
                        .equals(new UiStateResolver.Presentation(
                                UiStateResolver.VisualState.LOCKED, true,
                                UiStateResolver.Action.RESTORE)),
                "connected locked state stays actionable");

        require(UiStateResolver.resolve(SleepLockStatus.NORMAL, ConnectionStatus.CONNECTED)
                        .equals(new UiStateResolver.Presentation(
                                UiStateResolver.VisualState.NORMAL, true,
                                UiStateResolver.Action.BLOCK)),
                "connected normal state can block");

        require(UiStateResolver.resolve(SleepLockStatus.RECOVERY, ConnectionStatus.CONNECTED)
                        .equals(new UiStateResolver.Presentation(
                                UiStateResolver.VisualState.RECOVERY, true,
                                UiStateResolver.Action.RESTORE)),
                "connected recovery state restores");

        require(UiStateResolver.resolve(SleepLockStatus.WORKING, ConnectionStatus.CONNECTED)
                        .equals(new UiStateResolver.Presentation(
                                UiStateResolver.VisualState.WORKING, false,
                                UiStateResolver.Action.NONE)),
                "working state rejects duplicate toggles");

        UiStateResolver.Presentation disconnectedLocked = UiStateResolver.resolve(
                SleepLockStatus.LOCKED, ConnectionStatus.DISCONNECTED);
        require(disconnectedLocked.visualState() == UiStateResolver.VisualState.OFFLINE,
                "disconnected overrides locked visual state");
        require(disconnectedLocked.toggleEnabled(),
                "disconnected locked state permits a transport-level retry");
        require(disconnectedLocked.action() == UiStateResolver.Action.RESTORE,
                "disconnected locked state retains restore intent");

        UiStateResolver.Presentation unknownRecovery = UiStateResolver.resolve(
                SleepLockStatus.RECOVERY, ConnectionStatus.UNKNOWN);
        require(unknownRecovery.visualState() == UiStateResolver.VisualState.OFFLINE,
                "unknown connection is fail-closed");
        require(unknownRecovery.toggleEnabled(),
                "unknown recovery state permits a safe restore retry");
        require(unknownRecovery.action() == UiStateResolver.Action.RESTORE,
                "unknown recovery state retains restore intent");

        System.out.println("UI state regression tests passed");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
