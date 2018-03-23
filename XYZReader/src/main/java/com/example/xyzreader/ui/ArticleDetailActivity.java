package com.example.xyzreader.ui;

import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private Cursor cursor;
    private long startId;

    private long selectedItemId;
    private int selectedItemUpButtonFloor = Integer.MAX_VALUE;
    private int topInset;

    private MyPagerAdapter pagerAdapter;
    @BindView(R.id.pager)
    ViewPager pager;
    @BindView(R.id.up_container)
    View upButtonContainer;
    @BindView(R.id.action_up)
    View upButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
        setupWindowAnimations();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.activity_article_detail);
        ButterKnife.bind(this);
        getSupportLoaderManager().initLoader(0, null, this);
        pagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(pagerAdapter);
        pager.setPageMargin((int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
        pager.setPageMarginDrawable(new ColorDrawable(0x22000000));
        registerEventHandlers();
        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getData() != null) {
                startId = ItemsContract.Items.getItemId(getIntent().getData());
                selectedItemId = startId;
            }
        }
    }

    private void registerEventHandlers() {
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                upButton.animate()
                        .alpha((state == ViewPager.SCROLL_STATE_IDLE) ? 1f : 0f)
                        .setDuration(300);
            }

            @Override
            public void onPageSelected(int position) {
                if (cursor != null) {
                    cursor.moveToPosition(position);
                }
                assert cursor != null;
                selectedItemId = cursor.getLong(ArticleLoader.Query._ID);
                updateUpButtonPosition();
            }
        });
        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onSupportNavigateUp();
            }
        });
        upButtonContainer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                view.onApplyWindowInsets(windowInsets);
                topInset = windowInsets.getSystemWindowInsetTop();
                upButtonContainer.setTranslationY(topInset);
                updateUpButtonPosition();
                return windowInsets;
            }
        });
    }

    private void setupWindowAnimations() {
        getWindow().setEnterTransition(new Fade());
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> cursorLoader, Cursor cursor) {
        this.cursor = cursor;
        pagerAdapter.notifyDataSetChanged();

        // Select the start ID
        if (startId > 0) {
            cursor.moveToFirst();
            // TODO: optimize
            while (!cursor.isAfterLast()) {
                if (cursor.getLong(ArticleLoader.Query._ID) == startId) {
                    final int position = cursor.getPosition();
                    pager.setCurrentItem(position, false);
                    break;
                }
                cursor.moveToNext();
            }
            startId = 0;
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> cursorLoader) {
        cursor = null;
        pagerAdapter.notifyDataSetChanged();
    }

    public void onUpButtonFloorChanged(long itemId, ArticleDetailFragment fragment) {
        if (itemId == selectedItemId) {
            selectedItemUpButtonFloor = fragment.getUpButtonFloor();
            updateUpButtonPosition();
        }
    }

    private void updateUpButtonPosition() {
        int upButtonNormalBottom = topInset + upButton.getHeight();
        upButton.setTranslationY(Math.min(selectedItemUpButtonFloor - upButtonNormalBottom, 0));
    }

    private class MyPagerAdapter extends FragmentStatePagerAdapter {
        MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            ArticleDetailFragment fragment = (ArticleDetailFragment) object;
            if (fragment != null) {
                selectedItemUpButtonFloor = fragment.getUpButtonFloor();
                updateUpButtonPosition();
            }
        }

        @Override
        public Fragment getItem(int position) {
            cursor.moveToPosition(position);
            return ArticleDetailFragment.newInstance(cursor.getLong(ArticleLoader.Query._ID));
        }

        @Override
        public int getCount() {
            return (cursor != null) ? cursor.getCount() : 0;
        }
    }
}
