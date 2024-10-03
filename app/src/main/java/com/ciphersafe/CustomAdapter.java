package com.ciphersafe;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.ArrayList;

/**
 * CustomAdapter is a custom adapter class used to bind data to a ListView. It displays account names and usernames in a list.
 */
public class CustomAdapter extends BaseAdapter {

    private Context context;
    private List<String[]> dataList;

    /**
     * Constructor for the CustomAdapter.
     *
     * @param context  The context in which the adapter is being used.
     * @param dataList A list of string arrays where each array represents an account name and username.
     */
    public CustomAdapter(Context context, List<String[]> dataList) {
        this.context = context;
        this.dataList = new ArrayList<>(dataList);
    }

    /**
     * Returns the number of items in the data list.
     *
     * @return The size of the data list.
     */
    @Override
    public int getCount() {
        return dataList.size();
    }

    /**
     * Returns the data item at a specific position.
     *
     * @param position The position of the item in the data list.
     * @return The data item (string array) at the specified position.
     */
    @Override
    public Object getItem(int position) {
        return dataList.get(position);
    }

    /**
     * Returns the ID of the item at the specified position.
     * In this case, the position is used as the ID.
     *
     * @param position The position of the item in the data list.
     * @return The position of the item, used as its ID.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Provides a view for an adapter view (ListView) with the data for a specific position.
     * Inflates the layout if necessary and sets the account name and username text views.
     *
     * @param position    The position of the data in the list.
     * @param convertView The recycled view to populate (if available), or null to inflate a new one.
     * @param parent      The parent view to which the view will be attached.
     * @return The View corresponding to the data at the specified position.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.list_item_password, parent, false);
        }

        TextView accountNameTextView = convertView.findViewById(R.id.account_name);
        TextView usernameTextView = convertView.findViewById(R.id.username);

        // Retrieve the account name and username from the data list for the current position
        String[] data = dataList.get(position);
        accountNameTextView.setText(data[0]);
        usernameTextView.setText(data[1]);

        // Set the click listener on the entire item view (convertView)
        convertView.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                // Call the showPasswordDetails method in MainActivity, passing the account name
                ((MainActivity) context).showPasswordDetails(data[0]);
            }
        });

        return convertView;
    }

    /**
     * Updates the data list with new data and refreshes the ListView.
     *
     * @param newDataList The new list of data to be displayed in the ListView.
     */
    public void updateData(List<String[]> newDataList) {
        dataList.clear();
        dataList.addAll(newDataList);
        notifyDataSetChanged();
    }
}
