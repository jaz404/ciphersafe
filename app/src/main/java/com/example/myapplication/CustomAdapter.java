package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
public class CustomAdapter extends BaseAdapter {

    private Context context;
    private List<String[]> dataList;

    public CustomAdapter(Context context, List<String[]> dataList) {
        this.context = context;
        this.dataList = dataList;
    }

    @Override
    public int getCount() {
        return dataList.size();
    }

    @Override
    public Object getItem(int position) {
        return dataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.list_item_password, parent, false);
        }

        TextView accountNameTextView = convertView.findViewById(R.id.account_name);
        TextView usernameTextView = convertView.findViewById(R.id.username);

        String[] data = dataList.get(position);
        accountNameTextView.setText(data[0]);
        usernameTextView.setText(data[1]);

        return convertView;
    }

    public void updateData(List<String[]> newDataList) {
        dataList = newDataList;
        notifyDataSetChanged();
    }
}
