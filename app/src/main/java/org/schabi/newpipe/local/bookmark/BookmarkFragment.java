package org.schabi.newpipe.local.bookmark;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.database.playlist.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import icepick.State;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public final class BookmarkFragment
        extends BaseLocalListFragment<List<PlaylistLocalItem>, Void> {

    @State
    protected Parcelable itemsListState;

    public List<StreamEntity> recommendations;
    public String RECOMMENDATIONS_PLAYLIST_THUMBNAIL = "https://cdn.pixabay.com/photo/2015/02/24/15/41/dog-647528__340.jpg";

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;

    private void createOrUpdateRecommendationsPlaylist(List<PlaylistMetadataEntry> playlists) {
        // TODO: I think this method might be the problem
        boolean foundExisting = false;
        long existingUid = 0;
        for (PlaylistMetadataEntry pme : playlists) {
            if (pme.thumbnailUrl.equals(RECOMMENDATIONS_PLAYLIST_THUMBNAIL)) {
                foundExisting = true;
                existingUid = pme.uid;
                Log.e("found_existing", "Found existing: " + pme.name);
            }
        }
        Log.e("pme_uid", "" + existingUid);
        if (foundExisting) {
            localPlaylistManager.modifyPlaylistStreams(existingUid, recommendations)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(newStreams -> Toast.makeText(getContext(), "Modified existing", Toast.LENGTH_SHORT).show(), err -> Log.e("error", "Error modifying recommended playlist", err));
        } else {
            Log.e("making_playlist", "Creating playlist");
            localPlaylistManager.createPlaylist("Recommended for you", recommendations, RECOMMENDATIONS_PLAYLIST_THUMBNAIL)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(longs -> Toast.makeText(getContext(), "Recommended Playlist Created", Toast.LENGTH_LONG), err -> Log.e("error", err.toString()));
        }
    }

    private boolean hasChanged(List<PlaylistLocalItem> before, List<PlaylistLocalItem> after) {
        if (before.size() != after.size()) return true;
        int size = before.size();
        for (int i = 0; i < size; i++) {
            PlaylistLocalItem bef = before.get(i);
            PlaylistLocalItem aft = after.get(i);
            boolean befIsMeta = bef instanceof PlaylistMetadataEntry;
            boolean aftIsMeta = aft instanceof PlaylistMetadataEntry;
            if (befIsMeta && aftIsMeta) {
                PlaylistMetadataEntry pmeBef = (PlaylistMetadataEntry) bef;
                PlaylistMetadataEntry pmeAft = (PlaylistMetadataEntry) aft;
                Flowable<List<PlaylistStreamEntry>> befEntries = localPlaylistManager.getPlaylistStreams(pmeBef.uid);
                Flowable<List<PlaylistStreamEntry>> aftEntries = localPlaylistManager.getPlaylistStreams(pmeAft.uid);

            }
        }
        return false; // PLACEHOLDER
    }

    public Single<List<StreamEntity>> getRecommendations(List<PlaylistStreamEntry> liked) {
        HashMap<String, Integer> artistFrequencies = new HashMap<>();
        ArrayList<StreamEntity> pureLiked = new ArrayList<>();
        Log.e("num_liked", "" + liked.size());
        /*StreamingService service = null;
        try {
            service = NewPipe.getService(0); // 0 should correspond to YouTube
        } catch (Throwable err) {
            Log.e("error_getting_service", "Failed to get service for service ID 0: " + err);
            return result;
        }*/
        List<Single<SearchInfo>> observables = new ArrayList<>();
        for (PlaylistStreamEntry entry : liked) {
            String artist = entry.uploader;
            Log.e("artist", artist);
            StreamEntity likeAsEntity = new StreamEntity(entry.serviceId, entry.title, entry.url, entry.streamType, entry.thumbnailUrl, entry.uploader, entry.duration);
            pureLiked.add(likeAsEntity);
            if (artistFrequencies.containsKey(artist)) {
                artistFrequencies.put(artist, artistFrequencies.get(artist) + 1);
            } else {
                artistFrequencies.put(artist, 1);
            }
            Single<SearchInfo> ssi = ExtractorHelper.searchFor(0, artist + " music", new ArrayList<>(), "");
            Log.e("searching", "Searching:" + artist + " music");
            observables.add(ssi);
        }
        if (pureLiked.isEmpty()) {
            return Single.just(new ArrayList<>());
        } else {
            return Single.zip(observables, list -> {
                List<StreamEntity> streamEntities = new ArrayList<>();
                for (int i = 0; i < list.length; i++) {
                    Object si = list[i];
                    if (si instanceof SearchInfo) {
                        SearchInfo s = (SearchInfo) si;
                        List<InfoItem> items = s.getRelatedItems();
                        streamEntities.add(pureLiked.get(i));
                        for (int j = 0; j < Math.min(10, items.size()); j++) {
                            InfoItem item = items.get(j);
                            if (item.getInfoType() == InfoItem.InfoType.STREAM && !item.getUrl().equals(pureLiked.get(i).getUrl())) {
                                streamEntities.add(new StreamEntity(0, item.getName(), item.getUrl(), StreamType.VIDEO_STREAM, item.getThumbnailUrl(), "Unknown", 100));
                            }
                        }
                    } else {
                        throw new Error("Object was not a search info");
                    }
                }
                Log.e("seay", streamEntities.size() + "");
                return streamEntities;
            });
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (activity == null) return;
        final AppDatabase database = NewPipeDatabase.getInstance(activity);
        localPlaylistManager = new LocalPlaylistManager(database);
        remotePlaylistManager = new RemotePlaylistManager(database);
        disposables = new CompositeDisposable();
        //Log.e("num_playlists_l", "Num playlists: " + localPlaylistManager.getPlaylists().count());
        //Log.e("test", "This is a test");
        //localPlaylistManager.createPlaylist("Auto Playlist", new ArrayList<>());
        localPlaylistManager.clearPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             Bundle savedInstanceState) {

        if(!useAsFrontPage) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }


    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (activity != null && isVisibleToUser) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        itemListAdapter.setSelectedListener(new OnClickGesture<LocalItem>() {
            @Override
            public void selected(LocalItem selectedItem) {
                final FragmentManager fragmentManager = getFM();

                if (selectedItem instanceof PlaylistMetadataEntry) {
                    final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                    NavigationHelper.openLocalPlaylistFragment(fragmentManager, entry.uid,
                            entry.name);

                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            entry.getServiceId(),
                            entry.getUrl(),
                            entry.getName());
                }
            }

            @Override
            public void held(LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistMetadataEntry) {
                    showLocalDialog((PlaylistMetadataEntry) selectedItem);
                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    showRemoteDeleteDialog((PlaylistRemoteEntity) selectedItem);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);

        Flowable.combineLatest(
                localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(),
                BookmarkFragment::merge
        ).onBackpressureLatest()
         .observeOn(AndroidSchedulers.mainThread())
         .subscribe(getPlaylistsSubscriber());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (disposables != null) disposables.clear();
        if (databaseSubscription != null) databaseSubscription.cancel();

        databaseSubscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.dispose();

        disposables = null;
        localPlaylistManager = null;
        remotePlaylistManager = null;
        itemsListState = null;
    }

    private boolean areEquivalent(List<PlaylistStreamEntry> a, List<PlaylistStreamEntry> b) {
        if (a.size() != b.size()) return false;
        int size = a.size();
        for (int i = 0; i < size; i++) {
            PlaylistStreamEntry aElem = a.get(i);
            PlaylistStreamEntry bElem = b.get(i);
            // TODO: if necessary
        }
        return false;
    }

    List<PlaylistStreamEntry> lastFavoriteStreams = null;
    long lastPid = 0;

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistLocalItem>> getPlaylistsSubscriber() {
        return new Subscriber<List<PlaylistLocalItem>>() {
            @Override
            public void onSubscribe(Subscription s) {
                showLoading();
                if (databaseSubscription != null) databaseSubscription.cancel();
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(List<PlaylistLocalItem> subscriptions) {
                handleResult(subscriptions);
                // if it's the same as last time, we don't need to run
                /*if (subscriptions.size() == lastPlaylistLocalItem.size() && subscriptions.size() > 0) {
                    boolean same = true;
                    for (int i = 0; i < subscriptions.size(); i++) {
                        PlaylistLocalItem s1 = subscriptions.get(i);
                        PlaylistLocalItem s2 = lastPlaylistLocalItem.get(i);
                        if (!s1.getOrderingName().equals(s2.getOrderingName())) {
                            same = false;
                            break;
                        }
                        if (s1 instanceof PlaylistLocalItem && s2 instanceof PlaylistLocalItem) {

                        }
                    }
                    if (same) return;
                }
                lastPlaylistLocalItem = new ArrayList<>(subscriptions);*/
                if (databaseSubscription != null) databaseSubscription.request(1);
                /*boolean hasFavoritesPlaylist = false;
                long pid = 0;
                Flowable<List<PlaylistStreamEntry>> favoriteStreams = null;
                List<PlaylistMetadataEntry> metaList = new ArrayList<>();

                for (PlaylistLocalItem pli : subscriptions) {
                    if (pli instanceof PlaylistMetadataEntry) {
                        PlaylistMetadataEntry pme = (PlaylistMetadataEntry) pli;
                        metaList.add(pme);
                        if (pme.thumbnailUrl.equals(LocalPlaylistManager.AUTO_PLAYLIST_THUMBNAIL)) {
                            hasFavoritesPlaylist = true;
                            pid = pme.uid;
                            favoriteStreams = localPlaylistManager.getPlaylistStreams(pid);
                        }
                    }
                }

                if (!hasFavoritesPlaylist) {
                    localPlaylistManager.createPlaylist("Favorites", new ArrayList<>())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe();
                }

                //if (pid == lastPid) return;
                lastPid = pid;

                Log.e("favorites_playlist", "Favorites playlist ID changed");

                // check if last and current favorite streams are equivalent
                if (favoriteStreams != null) {
                    favoriteStreams.subscribe(new Subscriber<List<PlaylistStreamEntry>>() {

                        @Override
                        public void onSubscribe(Subscription s) {
                            s.request(1);
                        }

                        @Override
                        public void onNext(List<PlaylistStreamEntry> playlistStreamEntries) {
                            if (lastFavoriteStreams != null && lastFavoriteStreams.equals(playlistStreamEntries)) return;
                            Log.e("update_favorites", "Favorites have updated");
                            lastFavoriteStreams = playlistStreamEntries;
                            if (playlistStreamEntries.isEmpty()) {
                                recommendations = new ArrayList<>();
                                createOrUpdateRecommendationsPlaylist(metaList);
                            } else {
                                Single<List<StreamEntity>> recommendationsSingle = getRecommendations(playlistStreamEntries);
                                recommendationsSingle.subscribe(recs -> {
                                    recommendations = recs;
                                    Log.e("recommendations_num", "# recommendations: " + recommendations.size());
                                    createOrUpdateRecommendationsPlaylist(metaList);
                                }, err -> Log.e("err_recommendations", "Error retrieving recommendations", err));
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            Log.e("err_fetching_favorites", "Error fetching favorites from playlist", t);
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
                }*/
                Log.e("num_playlists", "# Playlists: " + subscriptions.size());
                boolean hasAutoPlaylist = false;
                List<PlaylistMetadataEntry> metaList = new ArrayList<>();
                for (PlaylistLocalItem s : subscriptions) {
                    if (s instanceof PlaylistMetadataEntry) {
                        metaList.add((PlaylistMetadataEntry)s);
                        if (((PlaylistMetadataEntry)s).thumbnailUrl.equals(LocalPlaylistManager.AUTO_PLAYLIST_THUMBNAIL)) {
                            hasAutoPlaylist = true;
                        }
                    }
                }
                if (!hasAutoPlaylist) {
                    localPlaylistManager.createPlaylist("Test Playlist", new ArrayList<>())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> Toast.makeText(getContext(), "Created auto-playlist successfully", Toast.LENGTH_LONG).show());
                }
                Flowable<List<PlaylistStreamEntry>> favoritesStreams = null;
                if (subscriptions.size() > 0) {
                    for (PlaylistLocalItem pli : subscriptions) {
                        if (pli instanceof PlaylistMetadataEntry) {
                            PlaylistMetadataEntry pme = (PlaylistMetadataEntry) pli;
                            if (pme.thumbnailUrl.equals(LocalPlaylistManager.AUTO_PLAYLIST_THUMBNAIL)) {
                                long pid = pme.uid;
                                favoritesStreams = localPlaylistManager.getPlaylistStreams(pid);
                            }
                        }
                    }
                }
                if (favoritesStreams != null) {
                    favoritesStreams.subscribe(new Subscriber<List<PlaylistStreamEntry>>() {
                        @Override
                        public void onSubscribe(Subscription s) {

                            Log.e("subbingeyey", "Subscribing");
                            s.request(1);
                        }

                        @Override
                        public void onNext(List<PlaylistStreamEntry> playlistStreamEntries) {
                            Log.e("changed_favorites", "Favorites have been changed");
                            Log.e("entries_for_first", playlistStreamEntries.size() + "");
                            if (playlistStreamEntries.isEmpty()) return;
                            Single<List<StreamEntity>> r = getRecommendations(playlistStreamEntries);
                            if (r != null) {
                                    r.subscribe(rec -> {
                                        Log.e("r", rec.size() + "");
                                        recommendations = rec;
                                        createOrUpdateRecommendationsPlaylist(metaList);
                                    }, err -> {
                                        Log.e("err_recomm", "Error with recommendations: " + err, err);
                                    });
                            }

                        }

                        @Override
                        public void onError(Throwable t) {
                            Log.e("err", "Error" + t);
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
                }

            }

            @Override
            public void onError(Throwable exception) {
                BookmarkFragment.this.onError(exception);
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull List<PlaylistLocalItem> result) {
        super.handleResult(result);

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(result);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        hideLoading();
    }
    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Bookmark", R.string.general_error);
        return true;
    }

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (disposables != null) disposables.clear();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private void showRemoteDeleteDialog(final PlaylistRemoteEntity item) {
        showDeleteDialog(item.getName(), remotePlaylistManager.deletePlaylist(item.getUid()));
    }

    private void showLocalDialog(PlaylistMetadataEntry selectedItem) {
        View dialogView = View.inflate(getContext(), R.layout.dialog_bookmark, null);
        EditText editText = dialogView.findViewById(R.id.playlist_name_edit_text);
        editText.setText(selectedItem.name);

        Builder builder = new AlertDialog.Builder(activity);
        builder.setView(dialogView)
            .setPositiveButton(R.string.rename_playlist, (dialog, which) -> {
                changeLocalPlaylistName(selectedItem.uid, editText.getText().toString());
            })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.delete, (dialog, which) -> {
                showDeleteDialog(selectedItem.name,
                    localPlaylistManager.deletePlaylist(selectedItem.uid));
                dialog.dismiss();
            })
            .create()
            .show();
    }

    private void showDeleteDialog(final String name, final Single<Integer> deleteReactor) {
        if (activity == null || disposables == null) return;

        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) ->
                        disposables.add(deleteReactor
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(ignored -> {/*Do nothing on success*/}, this::onError))
                )
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void changeLocalPlaylistName(long id, String name) {
        if (localPlaylistManager == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + id +
                "] with new name=[" + name + "] items");
        }

        localPlaylistManager.renamePlaylist(id, name);
        final Disposable disposable = localPlaylistManager.renamePlaylist(id, name)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(longs -> {/*Do nothing on success*/}, this::onError);
        disposables.add(disposable);
    }

    private static List<PlaylistLocalItem> merge(final List<PlaylistMetadataEntry> localPlaylists,
                                                 final List<PlaylistRemoteEntity> remotePlaylists) {
        List<PlaylistLocalItem> items = new ArrayList<>(
                localPlaylists.size() + remotePlaylists.size());
        items.addAll(localPlaylists);
        items.addAll(remotePlaylists);

        Collections.sort(items, (left, right) ->
                left.getOrderingName().compareToIgnoreCase(right.getOrderingName()));

        return items;
    }
}

