package com.alexvasilkov.foldablelayout.sample.activities;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.View;
import com.alexvasilkov.foldablelayout.sample.R;
import com.alexvasilkov.foldablelayout.sample.fragments.DetailsFragment;
import com.alexvasilkov.foldablelayout.sample.fragments.ListFragment;
import com.alexvasilkov.foldablelayout.sample.items.Painting;

public class UnfoldableDetailsActivity extends BaseActivity {

    private DetailsFragment mDetailsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldable_details);

        String listFragmentTag = ListFragment.class.getSimpleName();
        String detailsFragmentTag = DetailsFragment.class.getSimpleName();

        ListFragment listFragment = (ListFragment) getFragmentManager().findFragmentByTag(listFragmentTag);
        DetailsFragment detailsFragment = (DetailsFragment) getFragmentManager().findFragmentByTag(detailsFragmentTag);

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (listFragment == null) {
            listFragment = new ListFragment();
            transaction.add(R.id.fragment_container, listFragment, listFragmentTag);
        }
        if (detailsFragment == null) {
            detailsFragment = new DetailsFragment();
            transaction.add(R.id.fragment_container, detailsFragment, detailsFragmentTag);
        }
        transaction.commit();

        mDetailsFragment = detailsFragment;
    }

    @Override
    public void onBackPressed() {
        if (mDetailsFragment == null || !mDetailsFragment.onActivityBackPressed()) {
            super.onBackPressed();
        }
    }

    public void openDetails(View coverView, Painting painting) {
        mDetailsFragment.showDetails(coverView, painting);
    }

}
