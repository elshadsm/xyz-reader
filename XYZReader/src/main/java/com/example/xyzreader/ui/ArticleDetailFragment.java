package com.example.xyzreader.ui;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";
    private static final float PARALLAX_FACTOR = 1.25f;
    private static final String SCROLL_POSITION_KEY = "scroll_position_key";
    private static final String SEE_MORE_VISIBLE_KEY = "see_more_visible_key";

    @BindView(R.id.scrollview)
    ObservableScrollView scrollView;
    @BindView(R.id.draw_insets_frame_layout)
    DrawInsetsFrameLayout drawInsetsFrameLayout;
    @BindView(R.id.photo_container)
    View photoContainerView;
    @BindView(R.id.photo)
    ImageView photoView;
    @BindView(R.id.article_title)
    TextView titleView;
    @BindView(R.id.article_byline)
    TextView bylineView;
    @BindView(R.id.article_body)
    TextView bodyView;
    @BindView(R.id.see_more)
    TextView seeMore;
    @BindView(R.id.meta_bar)
    LinearLayout metaBar;

    private Bundle savedInstanceState;
    private ColorDrawable statusBarColorDrawable;
    private int statusBarFullOpacityBottom;
    private int mutedColor = 0xFF333333;
    private boolean isCard = false;
    private View rootView;
    private Cursor cursor;
    private int topInset;
    private long itemId;
    private int scrollY;

    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2, 1, 1);

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putIntArray(SCROLL_POSITION_KEY, new int[]{scrollView.getScrollX(), scrollView.getScrollY()});
        outState.putBoolean(SEE_MORE_VISIBLE_KEY, seeMore.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.savedInstanceState = savedInstanceState;
        if (getArguments() != null && getArguments().containsKey(ARG_ITEM_ID)) {
            itemId = getArguments().getLong(ARG_ITEM_ID);
        }
        isCard = getResources().getBoolean(R.bool.detail_is_card);
        statusBarFullOpacityBottom = getResources()
                .getDimensionPixelSize(R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        ButterKnife.bind(this, rootView);
        statusBarColorDrawable = new ColorDrawable(0);
        registerEventHandlers();
        bindViews();
        updateStatusBar();
        restoreScrollViewState();
        return rootView;
    }

    private void registerEventHandlers() {
        drawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                topInset = insets.top;
            }
        });
        scrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                scrollY = scrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(itemId, ArticleDetailFragment.this);
                photoContainerView.setTranslationY((int) (scrollY - scrollY / PARALLAX_FACTOR));
                updateStatusBar();
            }
        });
    }

    private void updateStatusBar() {
        int color = 0;
        if (photoView != null && topInset != 0 && scrollY > 0) {
            float f = progress(scrollY,
                    statusBarFullOpacityBottom - topInset * 3,
                    statusBarFullOpacityBottom - topInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mutedColor) * 0.9),
                    (int) (Color.green(mutedColor) * 0.9),
                    (int) (Color.blue(mutedColor) * 0.9));
        }
        statusBarColorDrawable.setColor(color);
        drawInsetsFrameLayout.setInsetBackground(statusBarColorDrawable);
    }

    public void restoreScrollViewState() {
        if (savedInstanceState == null) {
            return;
        }
        final int[] position = savedInstanceState.getIntArray(SCROLL_POSITION_KEY);
        if (position == null) {
            return;
        }
        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(position[0], position[1]);
            }
        });
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min));
    }

    static float constrain(float val) {
        if (val < (float) 0) {
            return (float) 0;
        } else if (val > (float) 1) {
            return (float) 1;
        } else {
            return val;
        }
    }

    private Date parsePublishedDate() {
        try {
            String date = cursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            Log.i(TAG, "passing today's date");
            return new Date();
        }
    }

    private void bindViews() {
        if (rootView == null) {
            return;
        }
        bylineView.setMovementMethod(new LinkMovementMethod());
        bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));
        if (cursor != null) {
            rootView.setAlpha(0);
            rootView.setVisibility(View.VISIBLE);
            rootView.animate().alpha(1);
            titleView.setText(cursor.getString(ArticleLoader.Query.TITLE));
            applyBylineConfiguration();
            applyLoadMoreConfiguration();
            applyMetaBarConfiguration();
        } else {
            rootView.setVisibility(View.GONE);
            titleView.setText("N/A");
            bylineView.setText("N/A");
            bodyView.setText("N/A");
        }
    }

    private void applyBylineConfiguration() {
        Date publishedDate = parsePublishedDate();
        if (!publishedDate.before(START_OF_EPOCH.getTime())) {
            bylineView.setText(Html.fromHtml(
                    DateUtils.getRelativeTimeSpanString(
                            publishedDate.getTime(),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by <font color='#ffffff'>"
                            + cursor.getString(ArticleLoader.Query.AUTHOR)
                            + "</font>"));
        } else {
            // If date is before 1902, just show the string
            bylineView.setText(Html.fromHtml(
                    outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                            + cursor.getString(ArticleLoader.Query.AUTHOR)
                            + "</font>"));
        }
    }

    private void applyLoadMoreConfiguration() {
        final String details = cursor.getString(ArticleLoader.Query.BODY);
        boolean seeMoreVisible = true;
        if (savedInstanceState != null) {
            seeMoreVisible = savedInstanceState.getBoolean(SEE_MORE_VISIBLE_KEY, true);
        }
        if (seeMoreVisible && details.length() > 200) {
            Spanned fromHtml = Html.fromHtml(details.substring(0, 200)
                    .replaceAll("(\r\n|\n)", "<br />"));
            bodyView.setText(String.format("%s...", fromHtml.toString()));
            seeMore.setVisibility(View.VISIBLE);
            seeMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    bodyView.setText(Html.fromHtml(details.replaceAll("(\r\n|\n)", "<br />")));
                    view.setVisibility(View.GONE);
                }
            });
        } else {
            bodyView.setText(Html.fromHtml(details.replaceAll("(\r\n|\n)", "<br />")));
        }
    }

    private void applyMetaBarConfiguration() {
        ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                .get(cursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                    @Override
                    public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                        Bitmap bitmap = imageContainer.getBitmap();
                        if (bitmap != null) {
                            Palette p = Palette.from(bitmap).generate();
                            mutedColor = p.getDarkMutedColor(0xFF333333);
                            photoView.setImageBitmap(imageContainer.getBitmap());
                            metaBar.setBackgroundColor(mutedColor);
                            updateStatusBar();
                        }
                    }

                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                    }
                });
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), itemId);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        this.cursor = cursor;
        if (this.cursor != null && !this.cursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            this.cursor.close();
            this.cursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> cursorLoader) {
        this.cursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (photoContainerView == null || photoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return isCard
                ? (int) photoContainerView.getTranslationY() + photoView.getHeight() - scrollY
                : photoView.getHeight() - scrollY;
    }
}
