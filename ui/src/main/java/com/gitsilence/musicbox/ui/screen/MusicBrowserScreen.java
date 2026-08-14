package com.gitsilence.musicbox.ui.screen;

import com.gitsilence.musicbox.ui.catalog.CatalogPlaylist;
import com.gitsilence.musicbox.ui.catalog.CatalogTrack;
import com.gitsilence.musicbox.ui.catalog.MusicCatalog;
import com.gitsilence.musicbox.ui.catalog.MusicCatalogProvider;
import com.gitsilence.musicbox.ui.catalog.MusicPlatform;
import com.gitsilence.musicbox.ui.catalog.PageResult;
import com.gitsilence.musicbox.ui.catalog.PlaylistDetail;
import com.gitsilence.musicbox.ui.catalog.ResolverSourceInfo;
import com.gitsilence.musicbox.ui.state.BrowserSessionState;
import com.gitsilence.musicbox.ui.cover.CoverTextureCache;
import com.gitsilence.musicbox.server.playlist.SharedPlaylist;
import java.util.List;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MusicBrowserScreen extends Screen {
    private static final int PAGE_SIZE = 20;
    private static final int ROW_HEIGHT = 30;
    private static final int COVER_SIZE = 24;
    private static final int PANEL_TOP = 10;
    private static final int HEADER_HEIGHT = 25;
    private static final int LIST_TOP = 96;
    private static final int FOOTER_HEIGHT = 49;
    private static final int GROUND = 0xFF1E1F26;
    private static final int SURFACE = 0xFF252730;
    private static final int FIELD = 0xFF2A2C34;
    private static final int BAND = 0xFF15161B;
    private static final int BORDER = 0xFF0C0C10;
    private static final int TEXT = 0xFFF0F1F4;
    private static final int TEXT_DIM = 0xFFA9ADBB;
    private static final int TEXT_FAINT = 0xFF6F7482;
    private static final int ACCENT = 0xFF5B9CFF;
    private static final int CTA = 0xFFFFAA00;
    private static final int ERROR = 0xFFE5534B;
    private static final List<String> QUALITIES = List.of("128k", "320k", "flac", "flac24bit");
    private static final BrowserSessionState SESSION = new BrowserSessionState();

    private final TrackPlayHandler playHandler;
    private final TrackPlayHandler enqueueHandler;
    private final QueueControlHandler queueControlHandler;
    private final Runnable stopHandler;
    private final PlaylistImportHandler importHandler;
    private final java.util.function.Consumer<String> deleteHandler;
    private final Runnable sharedRefreshHandler;
    private final String initialQuality;
    private final List<ResolverSourceInfo> resolverSources;
    private final CoverTextureCache covers = new CoverTextureCache();
    private List<String> queue = List.of();
    private boolean queueView;
    private List<SharedPlaylist> sharedPlaylists;
    private SharedPlaylist selectedSharedPlaylist;

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
    private ResolverSourceInfo resolverSource;
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
    private StyledButton kugouButton;
    private StyledButton neteaseButton;
    private StyledButton qqButton;
    private StyledButton kuwoButton;
    private StyledButton miguButton;
    private StyledButton tracksButton;
    private StyledButton playlistsButton;
    private StyledButton sharedButton;
    private StyledButton importButton;
    private StyledButton deleteButton;
    private StyledButton previousButton;
    private StyledButton nextButton;
    private StyledButton backButton;
    private StyledButton refreshButton;
    private StyledButton playButton;
    private StyledButton qualityButton;
    private StyledButton queueButton;
    private StyledButton addButton;
    private StyledButton skipButton;
    private StyledButton sourceButton;
    private StyledButton searchButton;

    public MusicBrowserScreen(String initialQuality, List<ResolverSourceInfo> resolverSources,
                              TrackPlayHandler playHandler, TrackPlayHandler enqueueHandler,
                              QueueControlHandler queueControlHandler, Runnable stopHandler,
                              PlaylistImportHandler importHandler, java.util.function.Consumer<String> deleteHandler,
                              Runnable sharedRefreshHandler, List<SharedPlaylist> initialSharedPlaylists) {
        super(Component.translatable("screen.musicbox.title"));
        this.initialQuality = QUALITIES.contains(initialQuality) ? initialQuality : "320k";
        this.platform = SESSION.platform();
        this.mode = SESSION.playlistMode() ? ViewMode.PLAYLISTS : ViewMode.TRACKS;
        this.quality = SESSION.qualityOr(this.initialQuality);
        this.resolverSources = List.copyOf(resolverSources);
        this.resolverSource = this.resolverSources.stream()
                .filter(source -> source.id().equals(SESSION.resolverSourceId())).findFirst()
                .orElse(this.resolverSources.isEmpty() ? null : this.resolverSources.getFirst());
        if (resolverSource != null) SESSION.resolverSourceId(resolverSource.id());
        this.playHandler = playHandler;
        this.enqueueHandler = enqueueHandler;
        this.queueControlHandler = queueControlHandler;
        this.stopHandler = stopHandler;
        this.importHandler = importHandler;
        this.deleteHandler = deleteHandler;
        this.sharedRefreshHandler = sharedRefreshHandler;
        this.sharedPlaylists = List.copyOf(initialSharedPlaylists);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int right = panelRight();
        int navY = PANEL_TOP + HEADER_HEIGHT + 3;

        kugouButton = addStyled(left + 8, navY, 42, 18,
                Component.translatable(MusicPlatform.KUGOU.translationKey()), ButtonStyle.TAB,
                () -> switchPlatform(MusicPlatform.KUGOU));
        neteaseButton = addStyled(left + 52, navY, 50, 18,
                Component.translatable(MusicPlatform.NETEASE.translationKey()), ButtonStyle.TAB,
                () -> switchPlatform(MusicPlatform.NETEASE));
        qqButton = addStyled(left + 104, navY, 50, 18,
                Component.translatable(MusicPlatform.QQ.translationKey()), ButtonStyle.TAB,
                () -> switchPlatform(MusicPlatform.QQ));
        kuwoButton = addStyled(left + 156, navY, 42, 18,
                Component.translatable(MusicPlatform.KUWO.translationKey()), ButtonStyle.TAB,
                () -> switchPlatform(MusicPlatform.KUWO));
        miguButton = addStyled(left + 200, navY, 42, 18,
                Component.translatable(MusicPlatform.MIGU.translationKey()), ButtonStyle.TAB,
                () -> switchPlatform(MusicPlatform.MIGU));
        sourceButton = addStyled(left + 244, navY, 48, 18, sourceLabel(), ButtonStyle.NORMAL,
                this::cycleResolverSource);
        tracksButton = addStyled(right - 164, navY, 48, 18,
                Component.translatable("screen.musicbox.tab.tracks"), ButtonStyle.TAB,
                () -> switchMode(ViewMode.TRACKS));
        playlistsButton = addStyled(right - 114, navY, 54, 18,
                Component.translatable("screen.musicbox.tab.playlists"), ButtonStyle.TAB,
                () -> switchMode(ViewMode.PLAYLISTS));
        sharedButton = addStyled(right - 58, navY, 50, 18,
                Component.translatable("screen.musicbox.tab.shared"), ButtonStyle.TAB,
                () -> switchMode(ViewMode.SHARED));

        int searchY = navY + 31;
        int searchButtonWidth = 48;
        int historyButtonWidth = 44;
        searchField = addRenderableWidget(new EditBox(font, left + 13, searchY + 5,
                right - left - searchButtonWidth - historyButtonWidth - 31, 12,
                Component.translatable("screen.musicbox.search.narration")));
        searchField.setBordered(false);
        searchField.setTextColor(TEXT);
        searchField.setTextColorUneditable(TEXT_DIM);
        searchField.setHint(Component.translatable(mode == ViewMode.TRACKS
                ? "screen.musicbox.search.hint.tracks" : "screen.musicbox.search.hint.playlists"));
        searchField.setMaxLength(512);
        searchField.setValue(SESSION.query(platform, mode == ViewMode.PLAYLISTS));
        searchField.setResponder(value -> {
            SESSION.query(platform, mode == ViewMode.PLAYLISTS, value);
            historyIndex = -1;
            updateButtons();
        });
        searchButton = addStyled(right - searchButtonWidth - historyButtonWidth - 6, searchY, searchButtonWidth, 20,
                Component.translatable("screen.musicbox.search"), ButtonStyle.ACCENT, () -> search(1));
        addStyled(right - historyButtonWidth, searchY, historyButtonWidth, 20,
                Component.translatable("screen.musicbox.history"), ButtonStyle.NORMAL, this::cycleHistory);
        importButton = addStyled(right - searchButtonWidth - historyButtonWidth - 6, searchY, searchButtonWidth, 20,
                Component.translatable("screen.musicbox.import"), ButtonStyle.ACCENT, this::importSharedPlaylist);

        int bottom = height - 29;
        previousButton = addStyled(left + 8, bottom, 22, 20,
                Component.translatable("screen.musicbox.previous"), ButtonStyle.GHOST,
                () -> search(page - 1));
        nextButton = addStyled(left + 32, bottom, 22, 20,
                Component.translatable("screen.musicbox.next"), ButtonStyle.GHOST,
                () -> search(page + 1));
        backButton = addStyled(left + 8, bottom, 52, 20, Component.translatable("screen.musicbox.back"),
                ButtonStyle.NORMAL, this::closePlaylist);
        refreshButton = addStyled(left + 56, bottom, 22, 20,
                Component.translatable("screen.musicbox.refresh"), ButtonStyle.GHOST,
                this::refresh);
        qualityButton = addStyled(left + 80, bottom, 64, 20, qualityLabel(), ButtonStyle.NORMAL,
                this::cycleQuality);
        queueButton = addStyled(left + 146, bottom, 46, 20, Component.translatable("screen.musicbox.queue"),
                ButtonStyle.NORMAL, this::toggleQueue);
        skipButton = addStyled(left + 194, bottom, 40, 20, Component.translatable("screen.musicbox.skip"),
                ButtonStyle.GHOST, () -> queueControlHandler.apply(QueueOperation.SKIP, 0));
        addButton = addStyled(right - 146, bottom, 40, 20, Component.translatable("screen.musicbox.enqueue"),
                ButtonStyle.NORMAL, this::enqueueSelected);
        deleteButton = addStyled(right - 146, bottom, 40, 20, Component.translatable("screen.musicbox.delete"),
                ButtonStyle.DANGER, this::deleteSelectedShared);
        playButton = addStyled(right - 104, bottom, 48, 20, Component.translatable("screen.musicbox.play"),
                ButtonStyle.ACCENT, this::primaryAction);
        addStyled(right - 54, bottom, 46, 20, Component.translatable("screen.musicbox.stop"), ButtonStyle.DANGER,
                stopHandler);

        updateButtons();
        if (mode == ViewMode.SHARED) applySharedFilter();
        else if (mode == ViewMode.PLAYLISTS || !searchField.getValue().isBlank()) search(1);
    }

    public void updateQueue(List<String> tracks) {
        queue = List.copyOf(tracks);
        if (queueView) {
            scrollOffset = Math.min(scrollOffset, Math.max(0, queue.size() - 1));
            selectedVisibleIndex = -1;
            updateButtons();
        }
    }

    public void updateSharedPlaylists(List<SharedPlaylist> values, String message) {
        sharedPlaylists = List.copyOf(values);
        if (mode == ViewMode.SHARED && !playlistDetail) applySharedFilter();
        if (message != null && !message.isBlank()) {
            status = Component.translatable("screen.musicbox.shared." + message);
            errorStatus = message.endsWith("failed") || message.equals("permission_denied");
        }
        updateButtons();
    }

    private void importSharedPlaylist() {
        String input = searchField.getValue().strip();
        if (input.isEmpty()) { status = Component.translatable("screen.musicbox.status.enter_playlist_url"); return; }
        importHandler.importPlaylist(platform, input, "");
        status = Component.translatable("screen.musicbox.status.importing");
        errorStatus = false;
    }

    private void deleteSelectedShared() {
        if (selectedSharedPlaylist == null) return;
        deleteHandler.accept(selectedSharedPlaylist.key());
        status = Component.translatable("screen.musicbox.status.deleting");
    }

    private void toggleQueue() {
        queueView = !queueView;
        scrollOffset = 0;
        selectedVisibleIndex = -1;
        queueButton.selected = queueView;
        status = Component.translatable("screen.musicbox.status.queue", queue.size());
        updateButtons();
    }

    private void enqueueSelected() {
        if (queueView) {
            queueControlHandler.apply(QueueOperation.CLEAR, 0);
            return;
        }
        if (selectedTrack == null || !selectedTrack.hasQuality(quality) || !sourceSupportsSelection()) return;
        enqueueHandler.play(selectedTrack, quality, resolverSource.id());
        status = Component.translatable("screen.musicbox.status.enqueued", selectedTrack.title());
    }

    private StyledButton addStyled(int x, int y, int width, int height, Component label, ButtonStyle style,
                                   Runnable action) {
        return addRenderableWidget(new StyledButton(x, y, width, height, label, style, action));
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
        SESSION.playlistMode(next != ViewMode.TRACKS);
        playlistDetail = false;
        clearResults();
        restoreQuery();
        searchField.setHint(Component.translatable(next == ViewMode.TRACKS ? "screen.musicbox.search.hint.tracks"
                : next == ViewMode.PLAYLISTS ? "screen.musicbox.search.hint.playlists" : "screen.musicbox.search.hint.shared"));
        if (next == ViewMode.SHARED) { sharedRefreshHandler.run(); applySharedFilter(); }
        else if (next == ViewMode.PLAYLISTS || !searchField.getValue().isBlank()) search(1);
        updateButtons();
    }

    private void search(int requestedPage) {
        if (loading || requestedPage < 1) return;
        if (mode == ViewMode.SHARED) { applySharedFilter(); return; }
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

    private void applySharedFilter() {
        String query = searchField == null ? "" : searchField.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        List<SharedPlaylist> filtered = sharedPlaylists.stream().filter(value -> query.isEmpty()
                        || value.name().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || value.creator().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || value.playlistId().contains(query))
                .filter(value -> value.musicPlatform() == platform).toList();
        playlists = filtered.stream().map(SharedPlaylist::toCatalogPlaylist).toList();
        tracks = List.of(); currentPage = null; page = 1; resetSelection();
        status = Component.translatable("screen.musicbox.status.shared_results", playlists.size());
        errorStatus = false; updateButtons();
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
        if (mode == ViewMode.SHARED) {
            selectedSharedPlaylist = sharedPlaylists.stream().filter(value -> value.musicPlatform() == playlist.platform()
                    && value.playlistId().equals(playlist.id())).findFirst().orElse(null);
            if (selectedSharedPlaylist == null) return;
            tracks = selectedSharedPlaylist.tracks().stream().map(track -> track.toCatalogTrack(playlist.platform())).toList();
            playlists = List.of(); playlistDetail = true; resetSelection(); selectedPlaylist = playlist;
            status = Component.translatable("screen.musicbox.status.playlist_tracks", tracks.size()); updateButtons();
            return;
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
        selectedSharedPlaylist = null;
        selectedPlaylist = parentSelectedIndex >= 0 && parentSelectedIndex < playlists.size()
                ? playlists.get(parentSelectedIndex) : null;
        status = Component.translatable("screen.musicbox.status.results",
                currentPage == null ? playlists.size() : currentPage.total());
        errorStatus = false;
        updateButtons();
    }

    private void refresh() {
        if (loading) return;
        if (mode == ViewMode.SHARED) { sharedRefreshHandler.run(); applySharedFilter(); return; }
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
        updateSearchHint();
        historyIndex = -1;
    }

    private void updateSearchHint() {
        searchField.setHint(Component.translatable(mode == ViewMode.TRACKS ? "screen.musicbox.search.hint.tracks"
                : mode == ViewMode.PLAYLISTS ? "screen.musicbox.search.hint.playlists" : "screen.musicbox.search.hint.shared"));
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
        selectedSharedPlaylist = null;
        lastClickIndex = -1;
    }

    private void cycleQuality() {
        int index = (QUALITIES.indexOf(quality) + 1) % QUALITIES.size();
        quality = QUALITIES.get(index);
        SESSION.setQuality(quality);
        qualityButton.setMessage(qualityLabel());
        updateButtons();
    }

    private void cycleResolverSource() {
        if (resolverSources.size() < 2 || resolverSource == null) return;
        int index = (resolverSources.indexOf(resolverSource) + 1) % resolverSources.size();
        resolverSource = resolverSources.get(index);
        SESSION.resolverSourceId(resolverSource.id());
        sourceButton.setMessage(sourceLabel());
        status = Component.translatable("screen.musicbox.status.source_selected", resolverSource.displayName());
        updateButtons();
    }

    private Component sourceLabel() {
        return resolverSource == null
                ? Component.translatable("screen.musicbox.source.none")
                : Component.translatable("screen.musicbox.source", resolverSource.displayName());
    }

    private Component qualityLabel() {
        return Component.translatable("screen.musicbox.quality_short", quality);
    }

    private void playSelected() {
        if (selectedTrack == null || loading || !selectedTrack.hasQuality(quality) || !sourceSupportsSelection()) return;
        playHandler.play(selectedTrack, quality, resolverSource.id());
        status = Component.translatable("screen.musicbox.status.requested", selectedTrack.title());
        errorStatus = false;
    }

    private void primaryAction() {
        if (queueView) {
            if (selectedVisibleIndex >= 0) queueControlHandler.apply(QueueOperation.REMOVE,
                    scrollOffset + selectedVisibleIndex);
            return;
        }
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
        qqButton.active = !loading && platform != MusicPlatform.QQ;
        kuwoButton.active = !loading && platform != MusicPlatform.KUWO;
        miguButton.active = !loading && platform != MusicPlatform.MIGU;
        tracksButton.active = !loading && (mode != ViewMode.TRACKS || playlistDetail);
        playlistsButton.active = !loading && (mode != ViewMode.PLAYLISTS || playlistDetail);
        sharedButton.active = !loading && (mode != ViewMode.SHARED || playlistDetail);
        kugouButton.selected = platform == MusicPlatform.KUGOU;
        neteaseButton.selected = platform == MusicPlatform.NETEASE;
        qqButton.selected = platform == MusicPlatform.QQ;
        kuwoButton.selected = platform == MusicPlatform.KUWO;
        miguButton.selected = platform == MusicPlatform.MIGU;
        tracksButton.selected = mode == ViewMode.TRACKS && !playlistDetail;
        playlistsButton.selected = mode == ViewMode.PLAYLISTS || playlistDetail;
        sharedButton.selected = mode == ViewMode.SHARED;
        searchButton.visible = mode != ViewMode.SHARED;
        importButton.visible = mode == ViewMode.SHARED && !playlistDetail;
        importButton.active = !loading && !searchField.getValue().isBlank();
        previousButton.active = !loading && !playlistDetail && currentPage != null && currentPage.hasPrevious();
        nextButton.active = !loading && !playlistDetail && currentPage != null && currentPage.hasNext();
        previousButton.visible = !playlistDetail;
        nextButton.visible = !playlistDetail;
        backButton.visible = playlistDetail;
        backButton.active = !loading;
        refreshButton.active = !loading && (playlistDetail || currentPage != null || mode == ViewMode.PLAYLISTS
                || !searchField.getValue().isBlank());
        qualityButton.active = !loading;
        sourceButton.active = !loading && resolverSources.size() > 1;
        queueButton.active = !loading;
        addButton.active = !loading && (queueView ? !queue.isEmpty()
                : selectedTrack != null && selectedTrack.hasQuality(quality) && sourceSupportsSelection());
        addButton.visible = mode != ViewMode.SHARED || playlistDetail || queueView;
        deleteButton.visible = mode == ViewMode.SHARED && !playlistDetail && !queueView;
        deleteButton.active = !loading && selectedSharedPlaylist != null;
        addButton.setMessage(Component.translatable(queueView ? "screen.musicbox.clear" : "screen.musicbox.enqueue"));
        skipButton.active = !loading;
        boolean canPlayTrack = selectedTrack != null && selectedTrack.hasQuality(quality) && sourceSupportsSelection();
        boolean canOpenPlaylist = selectedPlaylist != null && !playlistDetail && !playlists.isEmpty();
        playButton.active = !loading && (queueView ? selectedVisibleIndex >= 0 : canPlayTrack || canOpenPlaylist);
        playButton.setMessage(Component.translatable(queueView ? "screen.musicbox.remove"
                : canOpenPlaylist ? "screen.musicbox.open" : "screen.musicbox.play"));
    }

    private boolean sourceSupportsSelection() {
        return resolverSource != null && resolverSource.supports(platform.source(), quality);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // Background blur is deferred; flush it before drawing this screen or it will blur rows and covers too.
        graphics.flush();
        int left = panelLeft();
        int right = panelRight();
        int panelBottom = height - 7;
        drawWorkspace(graphics, left, right, panelBottom);
        drawText(graphics, title, left + 10, PANEL_TOP + 8, TEXT);
        String context = Component.translatable(platform.translationKey()).getString() + "  ·  "
                + Component.translatable(mode == ViewMode.TRACKS ? "screen.musicbox.tab.tracks"
                        : mode == ViewMode.PLAYLISTS ? "screen.musicbox.tab.playlists" : "screen.musicbox.tab.shared").getString();
        context = font.plainSubstrByWidth(context, Math.max(40, (right - left) / 2 - 18));
        drawText(graphics, Component.literal(context), right - 10 - font.width(context), PANEL_TOP + 8, TEXT_DIM);

        String searchDescription = Component.translatable(mode == ViewMode.TRACKS
                ? "screen.musicbox.search.description.tracks" : mode == ViewMode.PLAYLISTS
                ? "screen.musicbox.search.description.playlists" : "screen.musicbox.search.description.shared").getString();
        drawText(graphics, Component.literal(font.plainSubstrByWidth(searchDescription, right - left - 20)),
                left + 10, PANEL_TOP + 53, TEXT_DIM);

        int listTop = LIST_TOP;
        int listBottom = height - FOOTER_HEIGHT;
        graphics.fill(left + 7, listTop - 3, right - 7, listBottom + 2, SURFACE);
        renderRows(graphics, left, right, listTop, listBottom, mouseX, mouseY);

        int statusX = left + 10;
        int statusColor = errorStatus ? ERROR : loading ? CTA : TEXT_DIM;
        graphics.fill(statusX, height - 42, statusX + 3, height - 39, statusColor);
        String statusText = playlistDetail && selectedPlaylist != null
                ? selectedPlaylist.name() + " · " + status.getString() : status.getString();
        drawText(graphics, Component.literal(font.plainSubstrByWidth(statusText, right - statusX - 12)),
                statusX + 7, height - 45, statusColor);

        // Screen#render applies its background pass here; render widgets directly so custom rows stay sharp.
        for (GuiEventListener child : children()) {
            if (child instanceof Renderable renderable) renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawWorkspace(GuiGraphics graphics, int left, int right, int bottom) {
        graphics.fill(left + 4, PANEL_TOP + 4, right + 4, bottom + 4, 0x80000000);
        graphics.fill(left, PANEL_TOP, right, bottom, BORDER);
        graphics.fill(left + 2, PANEL_TOP + 2, right - 2, PANEL_TOP + HEADER_HEIGHT, BAND);
        graphics.fill(left + 2, PANEL_TOP + HEADER_HEIGHT + 2, right - 2, bottom - 2, GROUND);
        for (int y = PANEL_TOP + HEADER_HEIGHT + 8; y < bottom - 4; y += 16) {
            for (int x = left + 8; x < right - 4; x += 16) graphics.fill(x, y, x + 1, y + 1, 0x283F424D);
        }
        graphics.fill(left + 8, PANEL_TOP + 60, right - 102, PANEL_TOP + 80, FIELD);
        border(graphics, left + 8, PANEL_TOP + 60, right - left - 110, 20, 1, 0xFF3B3E49);
    }

    private void drawText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y, x + thickness, y + height, color);
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }

    private void renderRows(GuiGraphics graphics, int left, int right, int top, int bottom, int mouseX, int mouseY) {
        int visibleRows = visibleRows(top, bottom);
        int total = itemCount();
        int end = Math.min(total, scrollOffset + visibleRows);
        for (int index = scrollOffset; index < end; index++) {
            int visibleIndex = index - scrollOffset;
            int y = top + visibleIndex * ROW_HEIGHT;
            boolean selected = visibleIndex == selectedVisibleIndex;
            boolean hovered = mouseX >= left + 8 && mouseX < right - 8 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (selected || hovered) {
                graphics.fill(left + 9, y + 1, right - 10, y + ROW_HEIGHT - 1,
                        selected ? 0xFF344963 : 0x503F4552);
            }
            if (selected) graphics.fill(left + 9, y + 4, left + 12, y + ROW_HEIGHT - 4, ACCENT);
            if (queueView) renderQueueRow(graphics, queue.get(index), left, right, y, index + 1);
            else if (!tracks.isEmpty()) renderTrackRow(graphics, tracks.get(index), left, right, y, displayNumber(index));
            else if (!playlists.isEmpty()) renderPlaylistRow(graphics, playlists.get(index), left, right, y, displayNumber(index));
        }
        if (total == 0 && !loading) {
            String emptyHint = font.plainSubstrByWidth(
                    Component.translatable("screen.musicbox.empty_hint").getString(), right - left - 32);
            graphics.drawCenteredString(font, Component.literal(emptyHint), (left + right) / 2,
                    top + Math.max(8, (bottom - top) / 2 - 4), TEXT_FAINT);
        }
        if (total > visibleRows) {
            int trackHeight = Math.max(10, (bottom - top) * visibleRows / total);
            int maxOffset = total - visibleRows;
            int thumbY = top + (bottom - top - trackHeight) * scrollOffset / Math.max(1, maxOffset);
            graphics.fill(right - 11, thumbY, right - 8, thumbY + trackHeight, 0xFF5A5F6D);
        }
    }

    private void renderQueueRow(GuiGraphics graphics, String track, int left, int right, int y, int number) {
        graphics.drawString(font, String.valueOf(number), left + 17, y + 11, TEXT_FAINT, false);
        graphics.drawString(font, font.plainSubstrByWidth(track, right - left - 70), left + 43, y + 11, TEXT, false);
    }

    private void renderTrackRow(GuiGraphics graphics, CatalogTrack track, int left, int right, int y, int number) {
        graphics.drawString(font, String.valueOf(number), left + 17, y + 11, TEXT_FAINT, false);
        renderCover(graphics, track.coverUrl(), left + 38, y + 3);
        int titleX = left + 68;
        String duration = formatDuration(track.durationSeconds());
        int textRight = right - 14 - font.width(duration);
        String title = font.plainSubstrByWidth(track.title(), Math.max(30, textRight - titleX - 8));
        String detail = track.artist();
        if (!track.album().isBlank()) detail += " · " + track.album();
        detail = font.plainSubstrByWidth(detail, Math.max(30, right - titleX - 18));
        graphics.drawString(font, title, titleX, y + 4, TEXT, false);
        graphics.drawString(font, detail, titleX, y + 17, TEXT_DIM, false);
        graphics.drawString(font, duration, textRight, y + 4, TEXT_FAINT, false);
    }

    private void renderPlaylistRow(GuiGraphics graphics, CatalogPlaylist playlist, int left, int right, int y, int number) {
        graphics.drawString(font, String.valueOf(number), left + 17, y + 11, TEXT_FAINT, false);
        renderCover(graphics, playlist.coverUrl(), left + 38, y + 3);
        int titleX = left + 68;
        Component count = Component.translatable("screen.musicbox.track_count", playlist.trackCount());
        int countX = right - 14 - font.width(count);
        String name = font.plainSubstrByWidth(playlist.name(), Math.max(40, countX - titleX - 8));
        String detail = playlist.creator();
        if (!playlist.description().isBlank()) detail += " · " + playlist.description();
        detail = font.plainSubstrByWidth(detail, Math.max(40, right - titleX - 18));
        graphics.drawString(font, name, titleX, y + 4, TEXT, false);
        graphics.drawString(font, detail, titleX, y + 17, TEXT_DIM, false);
        graphics.drawString(font, count, countX, y + 4, 0xFF79C5D2, false);
    }

    private void renderCover(GuiGraphics graphics, String url, int x, int y) {
        ResourceLocation texture = covers.get(url);
        if (texture == null) {
            graphics.fill(x, y, x + COVER_SIZE, y + COVER_SIZE, 0xFF343741);
            graphics.fill(x + 7, y + 7, x + 17, y + 17, 0xFF5A5F6D);
        } else {
            graphics.blit(texture, x, y, 0, 0, COVER_SIZE, COVER_SIZE, COVER_SIZE, COVER_SIZE);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int top = LIST_TOP;
        int bottom = height - FOOTER_HEIGHT;
        if (button != 0 || mouseX < panelLeft() || mouseX >= panelRight() || mouseY < top || mouseY >= bottom) return false;
        int visibleIndex = (int) ((mouseY - top) / ROW_HEIGHT);
        int index = scrollOffset + visibleIndex;
        if (index < 0 || index >= itemCount()) return false;
        selectedVisibleIndex = visibleIndex;
        long now = Util.getMillis();
        boolean doubleClick = lastClickIndex == index && now - lastClickTime < 350;
        lastClickIndex = index;
        lastClickTime = now;
        if (queueView) {
            // The selected queue index is handled by the Remove action.
        } else if (!tracks.isEmpty()) {
            selectedTrack = tracks.get(index);
            if (doubleClick) playSelected();
        } else if (!playlists.isEmpty()) {
            selectedPlaylist = playlists.get(index);
            if (mode == ViewMode.SHARED) selectedSharedPlaylist = sharedPlaylists.stream()
                    .filter(value -> value.musicPlatform() == selectedPlaylist.platform()
                            && value.playlistId().equals(selectedPlaylist.id())).findFirst().orElse(null);
            if (doubleClick) openPlaylist(selectedPlaylist);
        }
        updateButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxOffset = Math.max(0, itemCount() - visibleRows(LIST_TOP, height - FOOTER_HEIGHT));
        if (mouseX >= panelLeft() && mouseX < panelRight() && mouseY >= LIST_TOP
                && mouseY < height - FOOTER_HEIGHT) {
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
        int rows = visibleRows(LIST_TOP, height - FOOTER_HEIGHT);
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
        return queueView ? queue.size() : !tracks.isEmpty() ? tracks.size() : playlists.size();
    }

    private int visibleRows(int top, int bottom) {
        return Math.max(1, (bottom - top) / ROW_HEIGHT);
    }

    private int displayNumber(int index) {
        return playlistDetail ? index + 1 : (page - 1) * PAGE_SIZE + index + 1;
    }

    private int panelLeft() {
        return Math.max(10, (width - Math.min(460, width - 20)) / 2);
    }

    private int panelRight() {
        return Math.min(width - 10, panelLeft() + 460);
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
        covers.close();
        super.removed();
    }

    @FunctionalInterface
    public interface TrackPlayHandler {
        void play(CatalogTrack track, String quality, String sourceId);
    }

    @FunctionalInterface
    public interface QueueControlHandler {
        void apply(QueueOperation operation, int index);
    }

    @FunctionalInterface
    public interface PlaylistImportHandler { void importPlaylist(MusicPlatform platform, String input, String displayName); }

    public enum QueueOperation { REMOVE, SKIP, CLEAR }

    private final class StyledButton extends AbstractButton {
        private final ButtonStyle style;
        private final Runnable action;
        private boolean selected;

        private StyledButton(int x, int y, int width, int height, Component label, ButtonStyle style,
                             Runnable action) {
            super(x, y, width, height, label);
            this.style = style;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = active && isHoveredOrFocused();
            int background = switch (style) {
                case TAB, GHOST -> hover ? 0x503F4552 : 0x00000000;
                case NORMAL -> hover ? 0xFF3B3E49 : FIELD;
                case ACCENT -> active ? (hover ? 0xFF73AAFA : ACCENT) : FIELD;
                case DANGER -> active ? (hover ? 0xFFEF6B64 : 0xFFB84540) : FIELD;
            };
            if ((background >>> 24) != 0) graphics.fill(getX(), getY(), getRight(), getBottom(), background);
            if (style == ButtonStyle.NORMAL) border(graphics, getX(), getY(), getWidth(), getHeight(), 1, 0xFF3B3E49);
            if (selected) graphics.fill(getX() + 4, getBottom() - 3, getRight() - 4, getBottom() - 1, CTA);
            int color = !active && !selected ? TEXT_FAINT
                    : style == ButtonStyle.ACCENT || style == ButtonStyle.DANGER ? 0xFFFFFFFF : TEXT;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 8));
            int textX = getX() + (getWidth() - font.width(label)) / 2;
            int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
            drawText(graphics, Component.literal(label), textX, textY, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private enum ButtonStyle {
        TAB,
        NORMAL,
        ACCENT,
        DANGER,
        GHOST
    }

    private enum ViewMode {
        TRACKS,
        PLAYLISTS,
        SHARED
    }
}
