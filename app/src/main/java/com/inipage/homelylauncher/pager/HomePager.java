package com.inipage.homelylauncher.pager;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.drawer.AppDrawerController;
import com.inipage.homelylauncher.grid.GridPageController;
import com.inipage.homelylauncher.model.GridPage;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.state.EditingEvent;
import com.inipage.homelylauncher.state.PagesChangedEvent;
import com.inipage.homelylauncher.utils.ViewUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomePager extends RecyclerView.Adapter<HomePager.PagerHolder> {

    private static final int VIEW_TYPE_APP_DRAWER = 0;
    private static final int VIEW_TYPE_GRID_PAGE = 1;
    private final Host mHost;
    private final List<GridPage> mGridPages;
    private final AppDrawerController mAppDrawerController;
    private final List<GridPageController> mGridPageControllers;
    private final Map<String, GridPageController> mGridPageIdToController;

    public HomePager(final Host host, final ViewGroup rootView) {
        mHost = host;
        mAppDrawerController = new AppDrawerController(host, rootView);
        mGridPages = DatabaseEditor.get().getGridPages();
        if (mGridPages.isEmpty()) {
            mGridPages.add(GridPage.getInitialPage());
            DatabaseEditor.get().saveGridPages(mGridPages);
        }
        mGridPageControllers = new ArrayList<>();
        mGridPageIdToController = new HashMap<>();
        for (GridPage page : mGridPages) {
            final GridPageController gridPageController = new GridPageController(host, page, false);
            mGridPageIdToController.put(page.getID(), gridPageController);
            mGridPageControllers.add(gridPageController);
        }
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    public void spawnNewPage() {
        final GridPage newPage =
            GridPage.spawnNewPage(mGridPages.get(mGridPages.size() - 1));
        mGridPages.add(newPage);
        final GridPageController newController = new GridPageController(mHost, newPage, true);
        mGridPageIdToController.put(newPage.getID(), newController);
        mGridPageControllers.add(newController);
        DatabaseEditor.get().saveGridPages(mGridPages);
        notifyItemInserted(mGridPages.size());
        EventBus.getDefault().post(new PagesChangedEvent(mGridPages.size()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEditingEvent(EditingEvent event) {
        if (event.isEditing()) {
            return;
        }

        // Drop empty pages
        Set<GridPage> pagesToDrop = new HashSet<>();
        Set<GridPageController> controllersToDrop = new HashSet<>();
        for (int i = 0; i < mGridPages.size(); i++) {
            GridPage page = mGridPages.get(i);
            if (page.getItems().isEmpty() && i != 0) {
                pagesToDrop.add(page);
                controllersToDrop.add(mGridPageControllers.get(i));
                DatabaseEditor.get().dropPage(page.getID());
                mGridPageIdToController.remove(page.getID());
            }
        }
        if (controllersToDrop.isEmpty()) {
            return;
        }
        mGridPages.removeAll(pagesToDrop);
        mGridPageControllers.removeAll(controllersToDrop);
        notifyDataSetChanged();
        EventBus.getDefault().post(new PagesChangedEvent(mGridPages.size()));
    }

    @NonNull
    @Override
    public PagerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_APP_DRAWER:
                view = mAppDrawerController.getView();
                break;
            case VIEW_TYPE_GRID_PAGE:
            default:
                view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.grid_layout_view, parent, false);
                break;
        }
        return new PagerHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PagerHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_APP_DRAWER) {
            holder.attachPageController(getAppDrawerController());
            return;
        }
        final GridPageController relevantController = mGridPageControllers.get(position - 1);
        relevantController.bind(holder.mainView);
        holder.attachPageController(relevantController);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_APP_DRAWER;
        }
        return VIEW_TYPE_GRID_PAGE;
    }

    @Override
    public int getItemCount() {
        return mGridPageControllers.size() + 1;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull PagerHolder holder) {
        if (holder.pageController != null) {
            holder.pageController.attach(getActivity());
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull PagerHolder holder) {
        if (holder.pageController != null) {
            getAppDrawerController().getView().getContext();
            holder.pageController.detach(getActivity());
        }
    }

    private Activity getActivity() {
        return ViewUtils.activityOf(mAppDrawerController.getView().getContext());
    }

    public AppDrawerController getAppDrawerController() {
        return mAppDrawerController;
    }

    public GridPageController getGridController(String id) {
        return mGridPageIdToController.get(id);
    }

    public BasePageController getPageController(int index) {
        if (index == 0) {
            return mAppDrawerController;
        }
        return mGridPageControllers.get(index - 1);
    }

    public float getWallpaperOffsetSteps() {
        return getItemCount() == 0 ? 1 : 1F / getItemCount();
    }

    public float getWallpaperOffset(int selectedItem, float offset) {
        final float delta = getItemCount() / 1F;
        return (selectedItem * delta) + (offset * delta);
    }

    public interface Host extends AppDrawerController.Host, GridPageController.Host {
    }

    public static class PagerHolder extends RecyclerView.ViewHolder {

        public View mainView;
        public BasePageController pageController;

        public PagerHolder(View view) {
            super(view);
            mainView = view;
        }

        public void attachPageController(BasePageController pageController) {
            this.pageController = pageController;
        }
    }
}
