package com.alexvasilkov.foldablelayout.sample.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.alexvasilkov.android.commons.adapters.ItemsAdapter;
import com.alexvasilkov.android.commons.ui.Views;
import com.alexvasilkov.foldablelayout.sample.R;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements ListView.OnItemClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ListView listView = Views.find(this, R.id.main_list);
        listView.setAdapter(getSampleAdapter());
        listView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ActivityInfo info = (ActivityInfo) parent.getItemAtPosition(position);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, info.name));
        startActivity(intent);
    }

    private BaseAdapter getSampleAdapter() {
        List<ActivityInfo> items = new ArrayList<>();

        try {
            ActivityInfo[] activitiesInfo = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES).activities;

            for (ActivityInfo info : activitiesInfo) {
                if (!getClass().getName().equals(info.name)) {
                    items.add(info);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return new SampleAdapter(items);
    }

    private static class SampleAdapter extends ItemsAdapter<ActivityInfo, ItemViewHolder> {

        SampleAdapter(List<ActivityInfo> list) {
            setItemsList(list);
        }

        @Override
        protected ItemViewHolder onCreateHolder(ViewGroup parent, int viewType) {
            return new ItemViewHolder(parent);
        }

        @Override
        protected void onBindHolder(ItemViewHolder holder, int position) {
            final ActivityInfo info = getItem(position);

            if (TextUtils.isEmpty(info.nonLocalizedLabel)) {
                holder.text.setText(info.labelRes);
            } else {
                holder.text.setText(info.nonLocalizedLabel);
            }
        }

    }

    private static class ItemViewHolder extends ItemsAdapter.ViewHolder {
        final TextView text;

        ItemViewHolder(ViewGroup parent) {
            super(Views.inflate(parent, android.R.layout.simple_list_item_1));
            text = (TextView) itemView;
        }
    }

}
