package xyz.mashtoolz.wtz.features.mount.bank;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.relay.RelayManager;
import xyz.mashtoolz.wtz.util.ChatHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MountBankIndexer {
    private static final boolean ENABLED = false;
    private static final int PAGE_CLICK_DELAY_TICKS = 4;
    private static final int PAGE_SETTLE_TICKS = 2;
    private static final int PAGE_WAIT_TIMEOUT_TICKS = 40;
    private static final int MAX_PAGE_CLICK_ATTEMPTS = 2;

    private static State state = State.IDLE;
    private static HandledScreen<?> activeScreen;
    private static final List<MountBankIndexEntry> entries = new ArrayList<>();
    private static final Set<Integer> scannedPages = new HashSet<>();
    private static int clickDelayTicks = -1;
    private static int waitTicks = 0;
    private static int quietTicks = 0;
    private static int updateRevision = 0;
    private static int waitStartRevision = 0;
    private static int pageClickAttempts = 0;
    private static PageIdentity waitingFrom = PageIdentity.UNKNOWN;
    private static boolean registered = false;

    private MountBankIndexer() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        if (!ENABLED) return;
        entries.clear();
        entries.addAll(MountBankIndexStore.load());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static boolean isDisabled() {
        return !ENABLED;
    }

    public static void start() {
        if (!ENABLED) {
            ChatHelper.sendWarning("Mount Bank Indexing is temporarily disabled.");
            return;
        }
        MinecraftClient client = WTZClient.client();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            ChatHelper.sendError("Open your bank before starting mount bank indexing.");
            return;
        }
        if (!MountBankScanner.isBankScreen(screen)) {
            ChatHelper.sendError("Open a Wynncraft bank page before starting mount bank indexing.");
            return;
        }

        if (state != State.IDLE) {
            ChatHelper.sendWarning("Mount bank indexing is already running.");
            return;
        }

        activeScreen = screen;
        entries.clear();
        scannedPages.clear();
        pageClickAttempts = 0;
        quietTicks = 0;
        updateRevision++;
        ChatHelper.sendInfo("Started mount bank indexing.");
        state = State.SETTLING_INITIAL_PAGE;
    }

    public static void cancel() {
        if (!ENABLED) {
            ChatHelper.sendInfo("Mount Bank Indexing is temporarily disabled.");
            return;
        }
        if (state == State.IDLE) {
            ChatHelper.sendInfo("Mount bank indexing is not running.");
            return;
        }
        int count = entries.size();
        reset();
        ChatHelper.sendWarning("Cancelled mount bank indexing after " + count + " mounts.");
    }

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static int lastEntryCount() {
        return entries.size();
    }

    public static void onSlotsUpdated(HandledScreen<?> screen) {
        if (!ENABLED) return;
        markPageUpdate(screen);
    }

    public static void onSlotChanged(HandledScreen<?> screen) {
        if (!ENABLED) return;
        markPageUpdate(screen);
    }

    private static void markPageUpdate(HandledScreen<?> screen) {
        if (state == State.IDLE) return;
        if (screen != activeScreen) return;
        updateRevision++;
        quietTicks = 0;
    }

    private static void tick() {
        if (state == State.IDLE) return;
        quietTicks++;

        MinecraftClient client = WTZClient.client();
        if (client.currentScreen != activeScreen) {
            int count = entries.size();
            reset();
            ChatHelper.sendWarning("Mount bank indexing stopped because the bank screen closed. Indexed " + count + " mounts.");
            return;
        }

        if (state == State.SETTLING_INITIAL_PAGE) {
            if (!isPageSettled()) return;
            if (MountBankScanner.hasPreviousPage(activeScreen.getScreenHandler())) {
                schedulePreviousClick();
            } else {
                scanCurrentPage();
            }
            return;
        }

        if (state == State.READY_TO_CLICK_PREVIOUS) {
            if (--clickDelayTicks > 0) return;
            clickPreviousPage();
            return;
        }

        if (state == State.READY_TO_CLICK) {
            if (--clickDelayTicks > 0) return;
            clickNextPage();
            return;
        }

        if (state == State.WAITING_PREVIOUS_PAGE) {
            waitForPreviousPage();
            return;
        }

        if (state == State.WAITING_PAGE) {
            waitForNextPage();
        }
    }

    private static void scanCurrentPage() {
        if (activeScreen == null) {
            reset();
            return;
        }

        int page = MountBankScanner.readCurrentPage(activeScreen).orElse(scannedPages.size() + 1);
        if (scannedPages.add(page)) {
            entries.addAll(MountBankScanner.scanPage(activeScreen, page));
        }

        if (MountBankScanner.hasNextPage(activeScreen.getScreenHandler())) {
            scheduleNextClick();
        } else {
            finish();
        }
    }

    private static void schedulePreviousClick() {
        clickDelayTicks = PAGE_CLICK_DELAY_TICKS;
        state = State.READY_TO_CLICK_PREVIOUS;
    }

    private static void scheduleNextClick() {
        clickDelayTicks = PAGE_CLICK_DELAY_TICKS;
        state = State.READY_TO_CLICK;
    }

    private static void clickNextPage() {
        if (activeScreen == null || !MountBankScanner.hasNextPage(activeScreen.getScreenHandler())) {
            finish();
            return;
        }

        pageClickAttempts = 0;
        attemptNextPageClick();
    }

    private static void attemptNextPageClick() {
        clickDelayTicks = -1;
        beginPageWait();
        pageClickAttempts++;
        state = State.WAITING_PAGE;
        if (!MountBankScanner.clickNextPage(activeScreen)) {
            cancelAfterFailedPageClick();
        }
    }

    private static void clickPreviousPage() {
        if (activeScreen == null || !MountBankScanner.hasPreviousPage(activeScreen.getScreenHandler())) {
            scanCurrentPage();
            return;
        }

        pageClickAttempts = 0;
        attemptPreviousPageClick();
    }

    private static void attemptPreviousPageClick() {
        clickDelayTicks = -1;
        beginPageWait();
        pageClickAttempts++;
        state = State.WAITING_PREVIOUS_PAGE;
        boolean clicked = pageClickAttempts == 1 && MountBankScanner.clickQuickJumpPage(activeScreen, 1);
        if (!clicked) {
            clicked = MountBankScanner.clickPreviousPage(activeScreen);
        }

        if (!clicked) {
            cancelAfterFailedPageClick();
        }
    }

    private static void beginPageWait() {
        waitTicks = 0;
        quietTicks = 0;
        waitStartRevision = updateRevision;
        waitingFrom = readPageIdentity();
    }

    private static void waitForPreviousPage() {
        WaitResult result = pageWaitResult();
        if (result == WaitResult.WAITING) return;

        if (result == WaitResult.TIMED_OUT) {
            retryOrCancelPageClick();
            return;
        }

        pageClickAttempts = 0;
        if (MountBankScanner.hasPreviousPage(activeScreen.getScreenHandler())) {
            schedulePreviousClick();
        } else {
            scanCurrentPage();
        }
    }

    private static void waitForNextPage() {
        WaitResult result = pageWaitResult();
        if (result == WaitResult.WAITING) return;

        if (result == WaitResult.TIMED_OUT) {
            retryOrCancelPageClick();
            return;
        }

        pageClickAttempts = 0;
        scanCurrentPage();
    }

    private static WaitResult pageWaitResult() {
        waitTicks++;
        if (hasPageChanged() && isPageSettled()) {
            return WaitResult.READY;
        }

        if (waitTicks >= PAGE_WAIT_TIMEOUT_TICKS) {
            if (hasPageChanged()) {
                return WaitResult.READY;
            }
            return WaitResult.TIMED_OUT;
        }

        return WaitResult.WAITING;
    }

    private static void retryOrCancelPageClick() {
        if (pageClickAttempts >= MAX_PAGE_CLICK_ATTEMPTS) {
            cancelAfterFailedPageClick();
            return;
        }

        if (state == State.WAITING_PREVIOUS_PAGE) {
            attemptPreviousPageClick();
        } else if (state == State.WAITING_PAGE) {
            attemptNextPageClick();
        }
    }

    private static void cancelAfterFailedPageClick() {
        int count = entries.size();
        String page = MountBankScanner.readCurrentPage(activeScreen).map(String::valueOf).orElse("unknown");
        reset();
        ChatHelper.sendWarning("Mount bank indexing stopped because the bank page did not change after "
                + MAX_PAGE_CLICK_ATTEMPTS + " clicks. Current page: " + page + ". Indexed " + count + " mounts.");
    }

    private static boolean hasPageChanged() {
        PageIdentity current = readPageIdentity();
        if (waitingFrom.page().isPresent() && current.page().isPresent()
                && !waitingFrom.page().get().equals(current.page().get())) {
            return true;
        }
        return updateRevision > waitStartRevision && !waitingFrom.signature().equals(current.signature());
    }

    private static boolean isPageSettled() {
        return quietTicks >= PAGE_SETTLE_TICKS;
    }

    private static PageIdentity readPageIdentity() {
        if (activeScreen == null) return PageIdentity.UNKNOWN;
        return new PageIdentity(
                MountBankScanner.readCurrentPage(activeScreen),
                MountBankScanner.pageSignature(activeScreen.getScreenHandler())
        );
    }

    private static void finish() {
        int count = entries.size();
        int pages = scannedPages.size();
        MountBankIndexStore.save(entries);
        RelayManager.getInstance().sendAppMessage("wtz.mount_bank_index", MountBankIndexStore.toJson(entries));
        state = State.IDLE;
        activeScreen = null;
        scannedPages.clear();
        clickDelayTicks = -1;
        waitTicks = 0;
        quietTicks = 0;
        pageClickAttempts = 0;
        waitingFrom = PageIdentity.UNKNOWN;
        ChatHelper.sendSuccess("Finished mount bank indexing: " + count + " mounts across " + pages + " pages.");
    }

    private static void reset() {
        state = State.IDLE;
        activeScreen = null;
        scannedPages.clear();
        clickDelayTicks = -1;
        waitTicks = 0;
        quietTicks = 0;
        pageClickAttempts = 0;
        waitingFrom = PageIdentity.UNKNOWN;
    }

    private enum State {
        IDLE,
        SETTLING_INITIAL_PAGE,
        READY_TO_CLICK_PREVIOUS,
        WAITING_PREVIOUS_PAGE,
        READY_TO_CLICK,
        WAITING_PAGE
    }

    private enum WaitResult {
        WAITING,
        READY,
        TIMED_OUT
    }

    private record PageIdentity(Optional<Integer> page, String signature) {
        private static final PageIdentity UNKNOWN = new PageIdentity(Optional.empty(), "");
    }
}
