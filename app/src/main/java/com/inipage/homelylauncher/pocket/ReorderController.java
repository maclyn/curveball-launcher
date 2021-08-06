package com.inipage.homelylauncher.pocket;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.grid.GridItemHandleView;

import java.util.ArrayList;
import java.util.List;

public class ReorderController<T> {

    private final List<T> mReorderedList;
    private final LinearLayout mListContainer;
    private final ItemBinder<T> mItemBinder;

    public ReorderController(
        List<T> originalList,
        LinearLayout listContainer,
        ItemBinder<T> binder) {
        mReorderedList = new ArrayList<>(originalList);
        mListContainer = listContainer;
        mItemBinder = binder;
        for (int idx = 0; idx < mReorderedList.size(); idx++) {
            final ReorderItemViewHolder viewHolder = new ReorderItemViewHolder(listContainer);
            listContainer.addView(
                viewHolder.root,
                new LinearLayout.LayoutParams(
                    (int) listContainer.getResources()
                        .getDimension(R.dimen.reorder_item_view_width),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        rebind();
    }

    public List<T> getReorderedList() {
        return mReorderedList;
    }

    private void rebind() {
        final int size = mListContainer.getChildCount();
        final boolean singleItemList = size == 1;
        for (int idx = 0; idx < size; idx++) {
            final ReorderItemViewHolder holder =
                (ReorderItemViewHolder) mListContainer.getChildAt(idx).getTag();
            final int index = idx;
            final T item = mReorderedList.get(idx);
            mItemBinder.bind(item, holder.icon, holder.label);
            holder.indexLabel.setText(String.valueOf(index + 1));
            holder.handle.setArrowsEnabled(idx != 0, idx != size - 1);
            holder.removeButton.setVisibility(singleItemList ? View.INVISIBLE : View.VISIBLE);
            holder.removeButton.setOnClickListener(__ -> {
                mReorderedList.remove(index);
                mListContainer.removeViewAt(index);
                rebind();
            });
            holder.handle.setListener(direction -> {
                final int insertIdx = direction == GridItemHandleView.Direction.LEFT_UP ?
                                      index - 1 :
                                      index + 1;
                mReorderedList.remove(index);
                mReorderedList.add(insertIdx, item);
                rebind();
            });
        }
    }

    public interface ItemBinder<T> {
        void bind(T item, ImageView icon, TextView label);
    }

    private static class ReorderItemViewHolder {

        public final View root;
        public final ImageView icon;
        public final TextView label;
        public final TextView indexLabel;
        public final GridItemHandleView handle;
        public final View removeButton;

        ReorderItemViewHolder(ViewGroup container) {
            final LayoutInflater inflater = LayoutInflater.from(container.getContext());
            root = inflater.inflate(R.layout.reorder_item_view, container, false);
            root.setTag(this);
            icon = root.findViewById(R.id.reorder_item_icon);
            label = root.findViewById(R.id.reorder_item_label);
            indexLabel = root.findViewById(R.id.reorder_item_index);
            handle = root.findViewById(R.id.reorder_item_handle);
            removeButton = root.findViewById(R.id.reorder_item_remove);
        }
    }
}
