package com.firebase.ui.firestore.paging;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class FirestorePagingSource extends RxPagingSource<PageKey, DocumentSnapshot> {

    private final Query mQuery;
    private final Source mSource;

    public FirestorePagingSource(@NonNull Query query, @NonNull Source source) {
        mQuery = query;
        mSource = source;
    }

    @NonNull
    @Override
    public Single<LoadResult<PageKey, DocumentSnapshot>> loadSingle(@NonNull LoadParams<PageKey> params) {
        final Task<QuerySnapshot> task;
        if (params.getKey() == null) {
            task = mQuery.limit(params.getLoadSize()).get(mSource);
        } else {
            task = params.getKey().getPageQuery(mQuery, params.getLoadSize()).get(mSource);
        }

        return Single.fromCallable(() -> {
            try {
                Tasks.await(task);
                QuerySnapshot snapshot = task.getResult();
                PageKey nextPage = getNextPageKey(snapshot);
                if (snapshot.getDocuments().isEmpty()) {
                    return toLoadResult(snapshot.getDocuments(), null);
                }
                return toLoadResult(snapshot.getDocuments(), nextPage);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception) {
                    // throw the original Exception
                    throw (Exception) e.getCause();
                }
                // Only throw a new Exception when the original
                // Throwable cannot be cast to Exception
                throw new Exception(e);
            } catch (InterruptedException e) {
                // Handle interruption gracefully, e.g.:
                return LoadResult.Error(e);
            }
        }).subscribeOn(Schedulers.io()).onErrorReturn(LoadResult.Error::new);
    }

    private LoadResult<PageKey, DocumentSnapshot> toLoadResult(
            @NonNull List<DocumentSnapshot> snapshots,
            @Nullable PageKey nextPage
    ) {
        return new LoadResult.Page<>(
                snapshots,
                null, // Only paging forward.
                nextPage,
                LoadResult.Page.COUNT_UNDEFINED,
                LoadResult.Page.COUNT_UNDEFINED);
    }

    @Nullable
    @Override
    public PageKey getRefreshKey(@NonNull PagingState<PageKey, DocumentSnapshot> state) {
        return null;
    }

    @NonNull
    private PageKey getNextPageKey(@NonNull QuerySnapshot snapshot) {
        List<DocumentSnapshot> data = snapshot.getDocuments();
        DocumentSnapshot last = getLast(data);
        return new PageKey(last, null);
    }

    @Nullable
    private DocumentSnapshot getLast(@NonNull List<DocumentSnapshot> data) {
        if (data.isEmpty()) {
            return null;
        } else {
            return data.get(data.size() - 1);
        }
    }
}
