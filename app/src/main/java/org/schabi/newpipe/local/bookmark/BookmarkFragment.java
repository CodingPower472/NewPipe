package org.schabi.newpipe.local.bookmark;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.StrictMode;
import android.util.Log;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;

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
import io.reactivex.internal.schedulers.NewThreadScheduler;
import io.reactivex.schedulers.Schedulers;

public final class BookmarkFragment
        extends BaseLocalListFragment<List<PlaylistLocalItem>, Void> {

    @State
    protected Parcelable itemsListState;

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;
    private List<String> favoritesURLs;

    private void setRecommendations(List<StreamEntity> recommendations) {
        if (recommendations.isEmpty()) return;
        localPlaylistManager.getPlaylists()
                .firstElement()
                .subscribe(playlists -> {
                    for (PlaylistLocalItem pli : playlists) {
                        if (pli instanceof PlaylistMetadataEntry) {
                            PlaylistMetadataEntry pme = (PlaylistMetadataEntry) pli;
                            if (pme.getOrderingName().equalsIgnoreCase("Recommended for you")) {
                                Log.i("found_existing", "Found existing Recommended for you");
                                localPlaylistManager.deletePlaylist(pme.uid)
                                        .subscribe();
                            }
                        }
                    }
                    localPlaylistManager.createPlaylist("Recommended for you", recommendations)
                            .subscribe();
                }, err -> {
                    Log.e("err_set_recs", "Error setting recommendations", err);
                });
        /*localPlaylistManager.createPlaylist("Recommended for you", recommendations)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();*/
    }

    public Single<List<StreamEntity>> getRecommendations(List<PlaylistStreamEntry> liked) {
        HashMap<String, Integer> artistFrequencies = new HashMap<>();
        ArrayList<StreamEntity> pureLiked = new ArrayList<>();
        Log.i("num_liked", "" + liked.size());
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
            Log.i("artist", artist);
            StreamEntity likeAsEntity = new StreamEntity(entry.serviceId, entry.title, entry.url, entry.streamType, entry.thumbnailUrl, entry.uploader, entry.duration);
            pureLiked.add(likeAsEntity);
            if (artistFrequencies.containsKey(artist)) {
                artistFrequencies.put(artist, artistFrequencies.get(artist) + 1);
            } else {
                artistFrequencies.put(artist, 1);
            }
            Single<SearchInfo> ssi = ExtractorHelper.searchFor(0, artist + " music", new ArrayList<>(), "");
            Log.i("searching", "Searching:" + artist + " music");
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

        // TEST
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // END TEST

        // NOT USED, USELESS CODE:
        /*Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                remotePlaylistManager.getPlaylists()
                        .first(new ArrayList<>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(playlists -> {
                            Log.i("playlists", "Got playlists, num: " + playlists.size());
                            PlaylistMetadataEntry favoritesPME = null;
                             for (PlaylistLocalItem pli : playlists) {
                                 if (pli instanceof PlaylistMetadataEntry && pli.getOrderingName().toLowerCase().equals("favorites")) {
                                     favoritesPME = (PlaylistMetadataEntry) pli;
                                 }
                             }
                             if (favoritesPME != null) {
                                 Log.i("got_favorites", "Retrieved playlists");
                                 localPlaylistManager.getPlaylistStreams(favoritesPME.uid)
                                         .first(new ArrayList<>())
                                         .observeOn(AndroidSchedulers.mainThread())
                                         .subscribe(streams -> {
                                             List<String> urls = new ArrayList<>();
                                             for (PlaylistStreamEntry pse : streams) {
                                                 urls.add(pse.url);
                                             }
                                             Log.i("new_urls", "New favorite streams URLs: " + Arrays.toString(urls.toArray()));
                                             if (urls.equals(favoritesURLs)) {
                                                 Log.i("same", "Same as previous");
                                                 return;
                                             }
                                             favoritesURLs = urls;

                                         }, err -> {
                                             Log.e("err_streams", "Error obtaining favorite streams", err);
                                         });
                             }
                        }, err -> {
                            Log.e("err_playlists", "Error fetching playlists", err);
                        });
                handler.postDelayed(this, 10000);
            }
        }, 5000);*/
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

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    List<PlaylistLocalItem> lastPlaylists;

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
                // STOCK
                handleResult(subscriptions);
                if (databaseSubscription != null) databaseSubscription.request(1);
                // END STOCK

                // we can trust .equals because they were overridden for both PlaylistMetadataEntry and PlaylistRemoteEntry
                /*if (subscriptions.equals(lastPlaylists)) {
                    Log.i("playlists_no_change", "No change to playlists");
                    return;
                }
                if (lastPlaylists != null && !lastPlaylists.isEmpty() && !subscriptions.isEmpty()) {
                    Log.i("are_equal", "" + subscriptions.get(0).equals(lastPlaylists.get(0)));
                }
                lastPlaylists = subscriptions;
                for (PlaylistLocalItem pli : subscriptions) {
                    boolean isMeta = pli instanceof PlaylistMetadataEntry;
                    Log.i("found_pli", "Has playlist with name " + pli.getOrderingName() + " and is" + (isMeta ? "" : " not") + " meta");
                }
                if (subscriptions.size() > 0) {
                    PlaylistLocalItem firstPli = subscriptions.get(0);
                    if (firstPli instanceof PlaylistMetadataEntry) {
                        PlaylistMetadataEntry pme = (PlaylistMetadataEntry) firstPli;
                        Log.i("has_meta", "Has a local playlist with name " + pme.getOrderingName() + " and uid " + pme.uid);

                        Flowable<List<PlaylistStreamEntry>> streamsFlowable = localPlaylistManager.getPlaylistStreams(pme.uid);
                        streamsFlowable.first(new ArrayList<>())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(streams -> {
                                    Log.i("update_streams", "Update streams");
                                });
                        streamsFlowable.subscribe(streams -> {
                            Log.i("update_streams", "Update streams for playlist with uid " + pme.uid);

                        }, err -> {
                            Log.e("err_retrv_streams", "Error retrieving streams", err);
                        });
                    }
                }
                Toast.makeText(getContext(), "Playlists changed", Toast.LENGTH_SHORT).show();*/
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

    List<String> lastFavURLs = null;

    // TODO: not a todo this is just the actual right method
    private void playlistsUpdate(@NonNull List<PlaylistLocalItem> result) {
        /*if (result.equals(lastPlaylists)) {
            Log.i("playlists_no_change", "No change to playlists");
            return;
        }
        if (lastPlaylists != null && !lastPlaylists.isEmpty() && !result.isEmpty()) {
            Log.i("are_equal", "" + result.get(0).equals(lastPlaylists.get(0)));
        }
        lastPlaylists = result;*/

        boolean hasFavoritesPlaylist = false;

        for (PlaylistLocalItem pli : result) {
            boolean isMeta = pli instanceof PlaylistMetadataEntry;
            Log.i("found_pli", "Has playlist with name " + pli.getOrderingName() + " and is" + (isMeta ? "" : " not") + " meta");
            if (!isMeta) continue;
            PlaylistMetadataEntry pme = (PlaylistMetadataEntry) pli;
            if (pme.getOrderingName().equalsIgnoreCase("Favorites")) {
                Log.i("found_favorites", "Found favorites (uid " + pme.uid + ")");
                Flowable<List<PlaylistStreamEntry>> streamsFlowable = localPlaylistManager.getPlaylistStreams(pme.uid);
                hasFavoritesPlaylist = true;
                streamsFlowable.firstElement()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(streams -> {
                            List<String> urls = new ArrayList<>(streams.size());
                            for (PlaylistStreamEntry pse : streams) {
                                urls.add(pse.url);
                            }
                            if (!urls.equals(lastFavURLs)) {
                                Log.i("update_streams", "Update streams");
                                lastFavURLs = urls;
                                getRecommendations(streams)
                                        .subscribeOn(Schedulers.newThread())
                                        .subscribe(recs -> {
                                            Log.i("recom_done", "Retrieved recommendations");
                                            setRecommendations(recs);
                                        }, err -> {
                                            Log.e("fail_recs", "Failed to retrieve recommendations", err);
                                        });
                            }
                        });
            }
        }
        if (!hasFavoritesPlaylist) {
            lastFavURLs = null;
        }
        /*if (result.size() > 0) {
            PlaylistLocalItem firstPli = result.get(0);
            if (firstPli instanceof PlaylistMetadataEntry) {
                PlaylistMetadataEntry pme = (PlaylistMetadataEntry) firstPli;
                Log.i("has_meta", "Has a local playlist with name " + pme.getOrderingName() + " and uid " + pme.uid);

                Flowable<List<PlaylistStreamEntry>> streamsFlowable = localPlaylistManager.getPlaylistStreams(pme.uid);
                streamsFlowable.first(new ArrayList<>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(streams -> {
                            Log.i("update_streams", "Update streams");
                        });
                streamsFlowable.subscribe(streams -> {
                    Log.i("update_streams", "Update streams for playlist with uid " + pme.uid);

                }, err -> {
                    Log.e("err_retrv_streams", "Error retrieving streams", err);
                });
            }
        }*/
        Toast.makeText(getContext(), "Playlists changed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void handleResult(@NonNull List<PlaylistLocalItem> result) {
        super.handleResult(result);

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        // OURS
        Log.i("handle_result", "Handle result called");
        playlistsUpdate(result);
        // END OURS

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

