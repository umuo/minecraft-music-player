package com.gitsilence.musicbox.ui.screen;

import com.gitsilence.musicbox.ui.catalog.CatalogPlaylist;
import com.gitsilence.musicbox.ui.catalog.CatalogTrack;
import com.gitsilence.musicbox.ui.catalog.MusicCatalog;
import com.gitsilence.musicbox.ui.catalog.MusicCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.MusicPlatform;
import com.gitsilence.musicbox.ui.catalog.PageResult;
import com.gitsilence.musicbox.ui.catalog.PlaylistDetail;
import com.gitsilence.musicbox.ui.state.BrowserSessionState;
import java.util.List;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MusicBrowserScreen extends Screen {
    private static final int PAGE_SIZE = 20;
    private static final int ROW_HEIGHT = 22;
    private static final List<String> QUALITIES = List.of("128k", "320k", "flac", "flac24bit");
    private static final BrowserSessionState SESSION = new BrowserSessionState();

    private final TrackPlayHandler playHandler;
    private final Runnable stopHandler;
    private final String initialQuality;

    private MusicPlatform platform;
    private ViewMode mode;
    private List<CatalogTrack> tracks = List.of();
    private List<CatalogPlaylist> playlists = List.of();
    private PageResult<?> currentPage;
    private CatalogTrack selectedTrack;
    private CatalogPlaylist selectedPlaylist;
    private boolean playlistDetail;
    private boolean loading;
    private boolean errorStatus;
    private int requestGeneration;
    private int scrollOffset;
    private int selectedVisibleIndex = -1;
    private int page = 1;
    private String quality;
    private Component status = Component.translatable("screen.musicbox.status.ready");
    private long lastClickTime;
    private int lastClickIndex = -1;
    private int historyIndex = -1;
    private List<CatalogPlaylist> parentPlaylists = List.of();
    private PageResult<?> parentPage;
    private int parentPageNumber = 1;
    private int parentScrollOffset;
    private int parentSelectedIndex = -1;

    private EditBox searchField;
    private Button kugouButton;
    private Button neteaseButton;
    private Button tracksButton;
    private Button playlistsButton;
    private Button previousButton;
    private Button nextButton;
    private Button backButton;
    private Button refreshButton;
    private Button playButton;
    private Button qualityButton;

    public MusicBrowserScreen(String initialQuality, TrackPlayHandler playHandler, Runnable stopHandler) {
        super(Component.translatable("screen.musicbox.title"));
        this.initialQuality = QUALITIES.contains(initialQuality) ? initialQuality : "320k";
        this.platform = SESSION.platform();
        this.mode = SESSION.playlistMode() ? ViewMode.PLAYLISTS : ViewMode.TRACKS;
        this.quality = SESSION.qualityOr(this.initialQuality);
        this.playHandler = playHandler;
        this.stopHandler = stopHandler;
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int right = panelRight();
        int top = 25;

        kugouButton = addRenderableWidget(Button.builder(Component.translatable(MusicPlatform.KUGOU.translationKey()),
                        button -> switchPlatform(MusicPlatform.KUGOU))
                .bounds(left, top, 62, 20).build());
        neteaseButton = addRenderableWidget(Button.builder(Component.translatable(MusicPlatform.NETEASE.translationKey()),
                        button -> switchPlatform(MusicPlatform.NETEASE))
                .bounds(left + 66, top, 72, 20).build());
        tracksButton = addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.tab.tracks"),
                        button -> switchMode(ViewMode.TRACKS))
                .bounds(left + 146, top, 54, 20).build());
        playlistsButton = addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.tab.playlists"),
                        button -> switchMode(ViewMode.PLAYLISTS))
                .bounds(left + 204, top, 54, 20).build());

        int searchY = top + 25;
        int searchButtonWidth = 48;
        int historyButtonWidth = 44;
        searchField = addRenderableWidget(new EditBox(font, left, searchY,
                right - left - searchButtonWidth - historyButtonWidth - 8, 20,
                Component.translatable("screen.musicbox.search.hint")));
        searchField.setHint(Component.translatable("screen.musicbox.search.hint"));
        searchField.setMaxLength(80);
        searchField.setValue(SESSION.query(platform, mode == ViewMode.PLAYLISTS));
        searchField.setResponder(value -> {
            SESSION.query(platform, mode == ViewMode.PLAYLISTS, value);
            historyIndex = -1;
        });
        addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.search"), button -> search(1))
                .bounds(right - searchButtonWidth - historyButtonWidth - 4, searchY, searchButtonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.history"), button -> cycleHistory())
                .bounds(right - historyButtonWidth, searchY, historyButtonWidth, 20).build());

        int bottom = height - 29;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> search(page - 1))
                .bounds(left, bottom, 24, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> search(page + 1))
                .bounds(left + 28, bottom, 24, 20).build());
        backButton = addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.back"), button -> closePlaylist())
                .bounds(left, bottom, 52, 20).build());
        refreshButton = addRenderableWidget(Button.builder(Component.literal("↻"), button -> refresh())
                .bounds(left + 56, bottom, 24, 20).build());
        qualityButton = addRenderableWidget(Button.builder(qualityLabel(), button -> cycleQuality())
                .bounds(left + 84, bottom, 78, 20).build());
        playButton = addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.play"), button -> primaryAction())
                .bounds(right - 126, bottom, 58, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.musicbox.stop"), button -> stopHandler.run())
                .bounds(right - 64, bottom, 64, 20).build());

        updateButtons();
        if (mode == ViewMode.PLAYLISTS || !searchField.getValue().isBlank()) search(1);
    }

    private void switchPlatform(MusicPlatform next) {
        if (platform == next) return;
        rememberCurrentQuery();
        platform = next;
        SESSION.platform(next);
        playlistDetail = false;
        clearResults();
        restoreQuery();
        if (mode == ViewMode.PLAYLISTS || !searchField.getValue().isBlank()) search(1);
        updateButtons();
    }

    private void switchMode(ViewMode next) {
        if (mode == next && !playlistDetail) return;
        rememberCurrentQuery();
        mode = next;
        SESSION.playlistMode(next == ViewMode.PLAYLISTS);
        playlistDetail = false;
        clearResults();
        restoreQuery();
        if (next == ViewMode.PLAYLISTS || !searchField.getValue().isBlank()) search(1);
        updateButtons();
    }

    private void search(int requestedPage) {
        if (loading || requestedPage < 1) return;
        String query = searchField.getValue().strip();
        if (mode == ViewMode.TRACKS && query.isEmpty()) {
            status = Component.translatable("screen.musicbox.status.enter_query");
            return;
        }
        SESSION.remember(platform, mode == ViewMode.PLAYLISTS, query);
        historyIndex = -1;
        playlistDetail = false;
        MusicCatalogProvider provider = MusicCatalog.provider(platform);
        int generation = ++requestGeneration;
        setLoading(true);
        status = Component.translatable("screen.musicbox.status.loading");
        if (mode == ViewMode.TRACKS) {
            provider.searchTracks(query, requestedPage, PAGE_SIZE)
                    .whenComplete((result, error) -> onMainThread(generation, () -> applyTracks(result, error)));
        } else {
            provider.playlists(query, requestedPage, PAGE_SIZE)
                    .whenComplete((result, error) -> onMainThread(generation, () -> applyPlaylists(result, error)));
        }
    }

    private void openPlaylist(CatalogPlaylist playlist) {
        if (loading) return;
        if (!playlistDetail) {
            parentPlaylists = playlists;
            parentPage = currentPage;
            parentPageNumber = page;
            parentScrollOffset = scrollOffset;
            parentSelectedIndex = selectedVisibleIndex < 0 ? -1 : scrollOffset + selectedVisibleIndex;
        }
        int generation = ++requestGeneration;
        selectedPlaylist = playlist;
        setLoading(true);
        status = Component.translatable("screen.musicbox.status.loading_playlist", playlist.name());
        MusicCatalog.provider(platform).playlistDetail(playlist)
                .whenComplete((result, error) -> onMainThread(generation, () -> applyPlaylistDetail(result, error)));
    }

    private void applyTracks(PageResult<CatalogTrack> result, Throwable error) {
        setLoading(false);
        if (error != null) {
            showError(error);
            return;
        }
        tracks = result.items();
        playlists = List.of();
        currentPage = result;
        page = result.page();
        resetSelection();
        status = tracks.isEmpty()
                ? Component.translatable("screen.musicbox.status.empty")
                : Component.translatable("screen.musicbox.status.results", result.total());
        errorStatus = false;
        updateButtons();
    }

    private void applyPlaylists(PageResult<CatalogPlaylist> result, Throwable error) {
        setLoading(false);
        if (error != null) {
            showError(error);
            return;
        }
        playlists = result.items();
        tracks = List.of();
        currentPage = result;
        page = result.page();
        resetSelection();
        status = playlists.isEmpty()
                ? Component.translatable("screen.musicbox.status.empty")
                : Component.translatable("screen.musicbox.status.results", result.total());
        errorStatus = false;
        updateButtons();
    }

    private void applyPlaylistDetail(PlaylistDetail result, Throwable error) {
        setLoading(false);
        if (error != null) {
            showError(error);
            return;
        }
        tracks = result.tracks();
        playlists = List.of();
        playlistDetail = true;
        currentPage = null;
        resetSelection();
        selectedPlaylist = result.playlist();
        status = Component.translatable("screen.musicbox.status.playlist_tracks", tracks.size());
        errorStatus = false;
        updateButtons();
    }

    private void closePlaylist() {
        if (!playlistDetail) return;
        playlistDetail = false;
        tracks = List.of();
        playlists = parentPlaylists;
        currentPage = parentPage;
        page = parentPageNumber;
        scrollOffset = parentScrollOffset;
        selectedVisibleIndex = parentSelectedIndex < 0 ? -1 : parentSelectedIndex - scrollOffset;
        selectedTrack = null;
        selectedPlaylist = parentSelectedIndex >= 0 && parentSelectedIndex < playlists.size()
                ? playlists.get(parentSelectedIndex) : null;
        status = Component.translatable("screen.musicbox.status.results",
                currentPage == null ? playlists.size() : currentPage.total());
        errorStatus = false;
        updateButtons();
    }

    private void refresh() {
        if (loading) return;
        if (playlistDetail && selectedPlaylist != null) openPlaylist(selectedPlaylist);
        else search(page);
    }

    private void cycleHistory() {
        List<String> history = SESSION.history(platform, mode == ViewMode.PLAYLISTS);
        if (history.isEmpty()) {
            status = Component.translatable("screen.musicbox.status.no_history");
            return;
        }
        int nextIndex = (historyIndex + 1) % history.size();
        searchField.setValue(history.get(nextIndex));
        historyIndex = nextIndex;
        searchField.moveCursorToEnd(false);
        status = Component.translatable("screen.musicbox.status.history", historyIndex + 1, history.size());
    }

    private void rememberCurrentQuery() {
        if (searchField != null) SESSION.query(platform, mode == ViewMode.PLAYLISTS, searchField.getValue());
    }

    private void restoreQuery() {
        searchField.setValue(SESSION.query(platform, mode == ViewMode.PLAYLISTS));
        historyIndex = -1;
    }

    private void onMainThread(int generation, Runnable action) {
        Minecraft.getInstance().execute(() -> {
            if (generation == requestGeneration) action.run();
        });
    }

    private void showError(Throwable error) {
        Throwable root = error;
        while ((root instanceof CompletionException || root.getCause() != null) && root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        status = Component.translatable("screen.musicbox.status.error", message);
        errorStatus = true;
        updateButtons();
    }

    private void setLoading(boolean value) {
        loading = value;
        updateButtons();
    }

    private void clearResults() {
        requestGeneration++;
        tracks = List.of();
        playlists = List.of();
        currentPage = null;
        page = 1;
        resetSelection();
        status = Component.translatable("screen.musicbox.status.ready");
        errorStatus = false;
    }

    private void resetSelection() {
        scrollOffset = 0;
        selectedVisibleIndex = -1;
        selectedTrack = null;
        selectedPlaylist = null;
        lastClickIndex = -1;
    }

    private void cycleQuality() {
        int index = (QUALITIES.indexOf(quality) + 1) % QUALITIES.size();
        quality = QUALITIES.get(index);
        SESSION.setQuality(quality);
        qualityButton.setMessage(qualityLabel());
        updateButtons();
    }

    private Component qualityLabel() {
        return Component.translatable("screen.musicbox.quality_short", quality);
    }

    private void playSelected() {
        if (selectedTrack == null || loading) return;
        playHandler.play(selectedTrack, quality);
        status = Component.translatable("screen.musicbox.status.requested", selectedTrack.title());
        errorStatus = false;
    }

    private void primaryAction() {
        if (selectedTrack != null) {
            playSelected();
        } else if (selectedPlaylist != null && !playlistDetail) {
            openPlaylist(selectedPlaylist);
        }
    }

    private void updateButtons() {
        if (kugouButton == null) return;
        kugouButton.active = !loading && platform != MusicPlatform.KUGOU;
        neteaseButton.active = !loading && platform != MusicPlatform.NETEASE;
        tracksButton.active = !loading && (mode != ViewMode.TRACKS || playlistDetail);
        playlistsButton.active = !loading && (mode != ViewMode.PLAYLISTS || playlistDetail);
        previousButton.active = !loading && !playlistDetail && currentPage != null && currentPage.hasPrevious();
        nextButton.active = !loading && !playlistDetail && currentPage != null && currentPage.hasNext();
        previousButton.visible = !playlistDetail;
        nextButton.visible = !playlistDetail;
        backButton.visible = playlistDetail;
        backButton.active = !loading;
        refreshButton.active = !loading && (playlistDetail || currentPage != null || mode == ViewMode.PLAYLISTS
                || !searchField.getValue().isBlank());
        qualityButton.active = !loading;
        boolean canPlayTrack = selectedTrack != null && selectedTrack.hasQuality(quality);
        boolean canOpenPlaylist = selectedPlaylist != null && !playlistDetail && !playlists.isEmpty();
        playButton.active = !loading && (canPlayTrack || canOpenPlaylist);
        playButton.setMessage(Component.translatable(canOpenPlaylist ? "screen.musicbox.open" : "screen.musicbox.play"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int right = panelRight();
        graphics.fill(left - 6, 12, right + 6, height - 4, 0xD0181818);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);

        int listTop = 75;
        int listBottom = height - 35;
        graphics.fill(left, listTop, right, listBottom, 0x90000000);
        renderRows(graphics, left, right, listTop, listBottom);

        String header = playlistDetail && selectedPlaylist != null
                ? font.plainSubstrByWidth(selectedPlaylist.name(), Math.max(40, right - left - 145))
                : "";
        if (!header.isEmpty()) graphics.drawString(font, header, left + 264, 31, 0xFFD166, false);
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), right - left - 174), left + 168, height - 23,
                errorStatus ? 0xFF6B6B : 0xA8A8A8, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int left, int right, int top, int bottom) {
        int visibleRows = visibleRows(top, bottom);
        int total = itemCount();
        int end = Math.min(total, scrollOffset + visibleRows);
        for (int index = scrollOffset; index < end; index++) {
            int visibleIndex = index - scrollOffset;
            int y = top + visibleIndex * ROW_HEIGHT;
            boolean selected = visibleIndex == selectedVisibleIndex;
            graphics.fill(left + 1, y + 1, right - 1, y + ROW_HEIGHT - 1,
                    selected ? 0xB04B6B88 : (visibleIndex % 2 == 0 ? 0x401F1F1F : 0x20292929));
            if (!tracks.isEmpty()) renderTrackRow(graphics, tracks.get(index), left, right, y, displayNumber(index));
            else if (!playlists.isEmpty()) renderPlaylistRow(graphics, playlists.get(index), left, right, y, displayNumber(index));
        }
        if (total == 0 && !loading) {
            graphics.drawCenteredString(font, Component.translatable("screen.musicbox.empty_hint"), (left + right) / 2,
                    top + Math.max(8, (bottom - top) / 2 - 4), 0x777777);
        }
        if (total > visibleRows) {
            int trackHeight = Math.max(10, (bottom - top) * visibleRows / total);
            int maxOffset = total - visibleRows;
            int thumbY = top + (bottom - top - trackHeight) * scrollOffset / Math.max(1, maxOffset);
            graphics.fill(right - 3, top, right, bottom, 0x60333333);
            graphics.fill(right - 3, thumbY, right, thumbY + trackHeight, 0xFF7A9BB8);
        }
    }

    private void renderTrackRow(GuiGraphics graphics, CatalogTrack track, int left, int right, int y, int number) {
        graphics.drawString(font, String.valueOf(number), left + 6, y + 7, 0x777777, false);
        int titleX = left + 30;
        int durationWidth = 38;
        String title = font.plainSubstrByWidth(track.title(), Math.max(30, (right - left) / 2 - 42));
        String artist = font.plainSubstrByWidth(track.artist(), Math.max(30, (right - left) / 2 - 52));
        graphics.drawString(font, title, titleX, y + 3, 0xFFFFFF, false);
        graphics.drawString(font, artist, titleX, y + 12, 0xA8A8A8, false);
        String album = font.plainSubstrByWidth(track.album(), Math.max(20, right - left - 220));
        graphics.drawString(font, album, left + Math.max(150, (right - left) / 2), y + 7, 0x8DB3D3, false);
        graphics.drawString(font, formatDuration(track.durationSeconds()), right - durationWidth, y + 7, 0x888888, false);
    }

    private void renderPlaylistRow(GuiGraphics graphics, CatalogPlaylist playlist, int left, int right, int y, int number) {
        graphics.drawString(font, String.valueOf(number), left + 6, y + 7, 0x777777, false);
        String name = font.plainSubstrByWidth(playlist.name(), Math.max(40, right - left - 155));
        String creator = font.plainSubstrByWidth(playlist.creator(), Math.max(40, right - left - 190));
        graphics.drawString(font, name, left + 30, y + 3, 0xFFFFFF, false);
        graphics.drawString(font, creator, left + 30, y + 12, 0xA8A8A8, false);
        graphics.drawString(font, Component.translatable("screen.musicbox.track_count", playlist.trackCount()), right - 78, y + 7,
                0x8DB3D3, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int top = 75;
        int bottom = height - 35;
        if (button != 0 || mouseX < panelLeft() || mouseX >= panelRight() || mouseY < top || mouseY >= bottom) return false;
        int visibleIndex = (int) ((mouseY - top) / ROW_HEIGHT);
        int index = scrollOffset + visibleIndex;
        if (index < 0 || index >= itemCount()) return false;
        selectedVisibleIndex = visibleIndex;
        long now = Util.getMillis();
        boolean doubleClick = lastClickIndex == index && now - lastClickTime < 350;
        lastClickIndex = index;
        lastClickTime = now;
        if (!tracks.isEmpty()) {
            selectedTrack = tracks.get(index);
            if (doubleClick) playSelected();
        } else if (!playlists.isEmpty()) {
            selectedPlaylist = playlists.get(index);
            if (doubleClick) openPlaylist(selectedPlaylist);
        }
        updateButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxOffset = Math.max(0, itemCount() - visibleRows(75, height - 35));
        if (mouseX >= panelLeft() && mouseX < panelRight() && mouseY >= 75 && mouseY < height - 35) {
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY)));
            selectedVisibleIndex = -1;
            selectedTrack = null;
            if (!playlistDetail) selectedPlaylist = null;
            updateButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && searchField.isFocused()) {
            search(1);
            return true;
        }
        if (keyCode == 256 && playlistDetail) {
            closePlaylist();
            return true;
        }
        if (keyCode == 70 && hasControlDown()) {
            setFocused(searchField);
            searchField.setFocused(true);
            return true;
        }
        if (!searchField.isFocused() && (keyCode == 264 || keyCode == 265)) {
            moveSelection(keyCode == 264 ? 1 : -1);
            return true;
        }
        if (!searchField.isFocused() && (keyCode == 257 || keyCode == 335)) {
            primaryAction();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int direction) {
        int count = itemCount();
        if (count == 0 || loading) return;
        int current = selectedVisibleIndex < 0 ? (direction > 0 ? -1 : count) : scrollOffset + selectedVisibleIndex;
        int selected = Math.max(0, Math.min(count - 1, current + direction));
        int rows = visibleRows(75, height - 35);
        if (selected < scrollOffset) scrollOffset = selected;
        else if (selected >= scrollOffset + rows) scrollOffset = selected - rows + 1;
        selectedVisibleIndex = selected - scrollOffset;
        if (!tracks.isEmpty()) {
            selectedTrack = tracks.get(selected);
            selectedPlaylist = null;
        } else {
            selectedPlaylist = playlists.get(selected);
            selectedTrack = null;
        }
        updateButtons();
    }

    private int itemCount() {
        return !tracks.isEmpty() ? tracks.size() : playlists.size();
    }

    private int visibleRows(int top, int bottom) {
        return Math.max(1, (bottom - top) / ROW_HEIGHT);
    }

    private int displayNumber(int index) {
        return playlistDetail ? index + 1 : (page - 1) * PAGE_SIZE + index + 1;
    }

    private int panelLeft() {
        return Math.max(14, (width - 390) / 2);
    }

    private int panelRight() {
        return Math.min(width - 14, panelLeft() + 390);
    }

    private static String formatDuration(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        rememberCurrentQuery();
        super.removed();
    }

    @FunctionalInterface
    public interface TrackPlayHandler {
        void play(CatalogTrack track, String quality);
    }

    private enum ViewMode {
        TRACKS,
        PLAYLISTS
    }
}
