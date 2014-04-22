package com.alexvasilkov.foldablelayout.sample.fragments;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.alexvasilkov.foldablelayout.UnfoldableView;
import com.alexvasilkov.foldablelayout.sample.items.Painting;
import com.alexvasilkov.foldablelayout.sample.R;
import com.alexvasilkov.foldablelayout.shadow.GlanceFoldShadow;
import com.azcltd.fluffycommons.texts.SpannableBuilder;
import com.squareup.picasso.Picasso;

public class DetailsFragment extends Fragment {

    private UnfoldableView mUnfoldableView;
    private View mTouchInterceptorView;

    private View mDetailsLayout;
    private ImageView mImageView;
    private TextView mTitleView;
    private TextView mDescriptionView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mUnfoldableView = (UnfoldableView) view.findViewById(R.id.unfoldable_view);
        mTouchInterceptorView = view.findViewById(R.id.touch_interceptor_view);

        mDetailsLayout = view.findViewById(R.id.details_layout);
        mDetailsLayout.setVisibility(View.INVISIBLE);

        mUnfoldableView.setOnFoldingListener(new UnfoldableView.OnFoldingListener() {
            @Override
            public void onFoldProgress(UnfoldableView unfoldableView, float progress) {
                // NO-OP
            }

            @Override
            public void onUnfolding(UnfoldableView unfoldableView) {
                mTouchInterceptorView.setClickable(true);
            }

            @Override
            public void onUnfolded(UnfoldableView unfoldableView) {
                mTouchInterceptorView.setClickable(false);
            }

            @Override
            public void onFoldingBack(UnfoldableView unfoldableView) {
                mTouchInterceptorView.setClickable(true);
            }

            @Override
            public void onFoldedBack(UnfoldableView unfoldableView) {
                mTouchInterceptorView.setClickable(false);
                mDetailsLayout.setVisibility(View.INVISIBLE);
            }
        });

        Bitmap glance = ((BitmapDrawable) getResources().getDrawable(R.drawable.unfold_glance)).getBitmap();
        mUnfoldableView.setFoldShader(new GlanceFoldShadow(getActivity(), glance));

        mImageView = (ImageView) view.findViewById(R.id.details_image);
        mTitleView = (TextView) view.findViewById(R.id.details_title);
        mDescriptionView = (TextView) view.findViewById(R.id.details_text);
    }

    public void showDetails(View coverView, Painting painting) {
        Picasso.with(getActivity()).load(painting.getImageId()).into(mImageView);
        mTitleView.setText(painting.getTitle());

        SpannableBuilder builder = new SpannableBuilder(getActivity());
        builder
                .createStyle().setFont(Typeface.DEFAULT_BOLD).apply()
                .append(R.string.year).append(": ")
                .clearStyle()
                .append(painting.getYear())
                .createStyle().setFont(Typeface.DEFAULT_BOLD).apply()
                .append("\n").append(R.string.location).append(": ")
                .clearStyle()
                .append(painting.getLocation());
        mDescriptionView.setText(builder.build());

        mDetailsLayout.setVisibility(View.VISIBLE);
        mUnfoldableView.unfold(coverView, mDetailsLayout);
    }

    public boolean onActivityBackPressed() {
        if (mUnfoldableView.isUnfolded() || mUnfoldableView.isUnfolding()) {
            mUnfoldableView.foldBack();
            return true;
        } else {
            return false;
        }
    }

}
