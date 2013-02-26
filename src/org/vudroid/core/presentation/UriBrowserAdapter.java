package org.vudroid.core.presentation;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import cn.com.cisco.pdf.R;

import java.util.Collections;
import java.util.List;

public class UriBrowserAdapter extends BaseAdapter
{
    private List<Uri> uris = Collections.emptyList();

    public int getCount()
    {
        return uris.size();
    }

    public Uri getItem(int i)
    {
        return uris.get(i);
    }

    public long getItemId(int i)
    {
        return i; 
    }

    public View getView(int i, View view, ViewGroup viewGroup)
    {
        final View browserItem = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.browseritem, viewGroup, false);
        final ImageView imageView = (ImageView) browserItem.findViewById(R.id.browserItemIcon);
        final Uri uri = uris.get(i);
        final TextView textView = (TextView) browserItem.findViewById(R.id.browserItemText);
        textView.setText(uri.getLastPathSegment());
        imageView.setImageResource(R.drawable.book);
        return browserItem;
    }

    public void setUris(List<Uri> uris)
    {
        this.uris = uris;
        notifyDataSetInvalidated();
    }
}
