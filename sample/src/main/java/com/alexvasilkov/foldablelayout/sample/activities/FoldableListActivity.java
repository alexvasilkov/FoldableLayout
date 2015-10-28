package com.alexvasilkov.foldablelayout.sample.activities;

import android.os.Bundle;

import com.alexvasilkov.android.commons.utils.Views;
import com.alexvasilkov.foldablelayout.FoldableListLayout;
import com.alexvasilkov.foldablelayout.sample.R;
import com.alexvasilkov.foldablelayout.sample.items.PaintingsAdapter;

public class FoldableListActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foldable_list);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FoldableListLayout foldableListLayout = Views.find(this, R.id.foldable_list);
        foldableListLayout.setAdapter(new PaintingsAdapter(this));
    }

}
