package com.inipage.homelylauncher.grid;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.utils.DebugLogUtils;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_ICON_CASCADE;


/**
 * Wrapper for finding items on the grid.
 */
public class GridViewHolderMap {

    private final GridMetrics mMetrics;
    // Rows -> Columns -> ViewHolders
    private final Map<Integer, Map<Integer, GridViewHolder>> mGrid;
    private final Set<GridViewHolder> mItems;

    public GridViewHolderMap(GridMetrics metrics) {
        mMetrics = metrics;
        mGrid = new HashMap<>();
        mItems = new HashSet<>();
        seedRows();
    }

    // Setters

    private void seedRows() {
        for (int i = 0; i < mMetrics.getRowCount(); i++) {
            mGrid.put(i, new HashMap<>());
        }
    }

    public void addHolder(GridViewHolder viewHolder) {
        mItems.add(viewHolder);
        addHolderToGrid(viewHolder);
    }

    private void addHolderToGrid(GridViewHolder viewHolder) {
        final GridItem gridItem = viewHolder.getItem();
        final int startX = gridItem.getX();
        final int startY = gridItem.getY();
        final int endX = startX + gridItem.getWidth();
        final int endY = startY + gridItem.getHeight();
        for (int row = startY; row < endY; row++) {
            for (int column = startX; column < endX; column++) {
                mGrid.get(row).put(column, viewHolder);
            }
        }
    }

    public void removeHolder(GridViewHolder gridViewHolder) {
        mItems.remove(gridViewHolder);
        final GridItem gridItem = gridViewHolder.getItem();
        final int startX = gridItem.getX();
        final int startY = gridItem.getY();
        final int endX = startX + gridItem.getWidth();
        final int endY = startY + gridItem.getHeight();
        for (int row = startY; row < endY; row++) {
            for (int column = startX; column < endX; column++) {
                mGrid.get(row).remove(column);
            }
        }
        gridViewHolder.detachHost();
    }

    /**
     * Underlying data in a GridItem has changed. Rebuild.
     */
    public void invalidate() {
        mGrid.clear();
        seedRows();
        for (GridViewHolder item : mItems) {
            addHolderToGrid(item);
        }
    }

    // Getters

    public Set<GridViewHolder> getHolders() {
        return mItems;
    }

    public boolean canItemExpandOutInDirection(
        GridViewHolder viewHolder, GridViewHolder.ResizeDirection direction) {
        GridItem item = viewHolder.getItem();
        final int x = item.getX();
        final int y = item.getY();
        final int width = item.getWidth();
        final int height = item.getHeight();
        switch (direction) {
            case UP:
                if (y == 0) {
                    return false;
                }
                for (int i = x; i < x + width; i++) {
                    if (hasItemAtIdx(y - 1, i)) {
                        return false;
                    }
                }
                return true;
            case DOWN:
                if (y + height >= mMetrics.getRowCount()) {
                    return false;
                }
                for (int i = x; i < x + width; i++) {
                    if (hasItemAtIdx(y + height, i)) {
                        return false;
                    }
                }
                return true;
            case LEFT:
                if (x == 0) {
                    return false;
                }
                for (int i = y; i < y + height; i++) {
                    if (hasItemAtIdx(i, x - 1)) {
                        return false;
                    }
                }
                return true;
            case RIGHT:
                if (x + width >= mMetrics.getColumnCount()) {
                    return false;
                }
                for (int i = y; i < y + height; i++) {
                    if (hasItemAtIdx(i, x + width)) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    public boolean hasItemAtIdx(int row, int column) {
        return getItemAtIndex(row, column) != null;
    }

    @Nullable
    public GridViewHolder getItemAtIndex(int row, int column) {
        @Nullable Map<Integer, GridViewHolder> viewHolder = mGrid.get(row);
        if (viewHolder == null) {
            return null;
        }
        return viewHolder.get(column);
    }

    // TODO: isRegionUsed()

    public Map<Pair<Integer, Integer>, Boolean> getRightAndDownItemFits(int row, int column) {
        Map<Pair<Integer, Integer>, Boolean> sizeMap = new HashMap<>();
        int i = row, j;
        for (; i < mMetrics.getRowCount(); i++) {
            if (hasItemAtIdx(i, column)) {
                break;
            }
            j = column;
            for (; j < mMetrics.getColumnCount(); j++) {
                if (hasItemAtIdx(i, j)) {
                    break;
                }
                sizeMap.put(new Pair<>((j - column) + 1, (i - row) + 1), true);
            }
        }
        return sizeMap;
    }

    @Nullable
    public GridViewHolder getItemAtPositionPx(float x, float y, GridMetrics metrics) {
        final int colIdx = (int) x / metrics.getCellWidthPx();
        final int rowIdx = (int) y / metrics.getCellHeightPx();
        return getItemAtIndex(rowIdx, colIdx);
    }

    public void dumpGridContents_SLOW() {
        DebugLogUtils.needle(
            TAG_ICON_CASCADE,
            "Grid of " + mItems.size() + " items; " + mMetrics.getColumnCount() + "x" +
                mMetrics.getRowCount());
        StringBuilder header = new StringBuilder();
        header.append(" x=");
        for (int i = 0; i < mMetrics.getColumnCount(); i++) {
            header.append("[");
            header.append(i);
            header.append("]");
        }
        DebugLogUtils.needle(TAG_ICON_CASCADE, header.toString());
        for (int i = 0; i < mMetrics.getRowCount(); i++) {
            StringBuilder out = new StringBuilder();
            out.append(String.format("%2d", i));
            out.append("=");
            for (int j = 0; j < mMetrics.getColumnCount(); j++) {
                out
                    .append("[")
                    .append(hasItemAtIdx(i, j)
                            ? String.valueOf(getItemAtIndex(
                        i,
                        j).mItem.getType())
                            : "x")
                    .append("]");
            }
            DebugLogUtils.needle(TAG_ICON_CASCADE, out.toString());
        }
    }

    //region NAIVE displacement solution code
    // This code is responsible for moving stuff out of the way when dragging and dropping content
    // around a grid page. It punts on a lot of questions around whether there's a reasonable
    // soultion by simply saying "no". Hence, naive code.

    public boolean isAreaOccupied(int column, int row, int width, int height) {
        for (int x = column; x < column + width; x++) {
            if (x >= mMetrics.getColumnCount()) {
                return true;
            }
            for (int y = row; y < row + height; y++) {
                if (y >= mMetrics.getRowCount()) {
                    return true;
                }
                if (hasItemAtIdx(y, x)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    public Set<GridViewHolder> solveForTranslationsToFitMovement(
        Point targetCell,
        @Nullable Point lastCommittedCell,
        @Nullable Point lastTargetCell,
        GridItem draggedItem) {
        final int height = draggedItem.getHeight();
        final int width = draggedItem.getWidth();
        DebugLogUtils.needle(
            TAG_ICON_CASCADE,
            "Asked to solve for " + targetCell + " w/ width=" + width + ", height=" + height +
                "; lastCell=" + lastTargetCell);
        if (targetCell.x + width > mMetrics.getColumnCount()) {
            return null;
        }
        if (targetCell.y + height > mMetrics.getRowCount()) {
            return null;
        }
        ChangeDirection preferredDirection = lastTargetCell != null ?
                                             ChangeDirection.fromDelta(
                                                 targetCell.x - lastTargetCell.x,
                                                 targetCell.y - lastTargetCell.y) :
                                             ChangeDirection.RIGHT;
        DebugLogUtils.needle(
            TAG_ICON_CASCADE,
            "Preferred direction = " + preferredDirection.name());
        ChangeDirection secondaryDirection = preferredDirection.opposite();
        ChangeDirection[] directionsToProbe = ChangeDirection.values();
        int preferredDirectionIdx = 0;
        int secondaryDirectionIdx = 1;
        for (int i = 0; i < directionsToProbe.length; i++) {
            if (directionsToProbe[i] == preferredDirection) {
                preferredDirectionIdx = i;
            } else if (directionsToProbe[i] == secondaryDirection) {
                secondaryDirectionIdx = i;
            }
        }
        ChangeDirection swap = directionsToProbe[0];
        ChangeDirection secondarySwap = directionsToProbe[1];
        directionsToProbe[0] = preferredDirection;
        directionsToProbe[preferredDirectionIdx] = swap;
        directionsToProbe[1] = secondaryDirection;
        directionsToProbe[secondaryDirectionIdx] = secondarySwap;
        DebugLogUtils.needle(
            TAG_ICON_CASCADE,
            "Probing translation-based solution starting with directions in array=" +
                Arrays.toString(directionsToProbe));

        // Probe for solution by a translation solution
        for (ChangeDirection direction : directionsToProbe) {
            DirectionCheck:
            {
                DebugLogUtils.needle(TAG_ICON_CASCADE, "Checking " + direction.name());

                // Elements move from holdersToMove -> movedHolders; when holdersToMove is
                // empty (or we expressly continue, because we found something that couldn't be moved)
                // we're done
                final Deque<GridViewHolder> holdersToMove = new ArrayDeque<>();
                final Set<GridViewHolder> movedHolders = new HashSet<>();
                switch (direction) {
                    case UP: {
                        // Build holders to move
                        // The order here matters: we want to move the bottom items first,
                        // then the top items, etc.
                        for (int y = targetCell.y + height - 1; y >= targetCell.y; y--) {
                            for (int x = targetCell.x; x < targetCell.x + width; x++) {
                                @Nullable GridViewHolder holder = getItemAtIndex(y, x);
                                if (holder != null && !holdersToMove.contains(holder)) {
                                    DebugLogUtils.needle(
                                        TAG_ICON_CASCADE,
                                        "Initially queueing " + holder.getItem() +
                                            " for displacement");
                                    holdersToMove.add(holder);
                                }
                            }
                        }
                        for (GridViewHolder holder : holdersToMove) {
                            holder.clearQueuedTranslation();
                        }

                        final int[] colToWorkingRowIndex = new int[mMetrics.getColumnCount()];
                        for (int col = 0; col < mMetrics.getColumnCount(); col++) {
                            colToWorkingRowIndex[col] = targetCell.y - 1;
                        }
                        while (!holdersToMove.isEmpty()) {
                            GridViewHolder holder = holdersToMove.pop();
                            GridItem item = holder.getItem();
                            final int targetX = item.getX();
                            int targetY = Integer.MAX_VALUE;
                            for (
                                int col = item.getX();
                                col < item.getX() + item.getWidth();
                                col++) {
                                targetY = Math.min(
                                    targetY,
                                    colToWorkingRowIndex[col] - (item.getHeight() - 1));
                            }
                            if (targetY < 0) {
                                // Without changing column too, we can't accommodate this movement
                                DebugLogUtils.needle(
                                    TAG_ICON_CASCADE,
                                    "Couldn't find an up solution b/c " + item.toString() +
                                        " was pushed to " + targetX + "x" + targetY);
                                break DirectionCheck;
                            }
                            // Queue movements
                            DebugLogUtils.needle(
                                TAG_ICON_CASCADE,
                                "Queue translation to " + targetX + ", " + targetY + " for " +
                                    holder.getItem());
                            holder.queueTranslation(targetX, targetY);
                            // Mark all the things it's covering up now for later processing
                            for (int x = item.getX(); x < item.getX() + item.getWidth(); x++) {
                                // Write back new working index
                                colToWorkingRowIndex[x] = targetY - 1;
                                for (int y = targetY + item.getHeight() - 1; y >= targetY; y--) {
                                    @Nullable GridViewHolder displacedHolder = getItemAtIndex(y, x);
                                    if (displacedHolder != null &&
                                        !holdersToMove.contains(displacedHolder) &&
                                        displacedHolder != holder) {
                                        DebugLogUtils.needle(
                                            TAG_ICON_CASCADE,
                                            "Secondary[+] order move queued for " +
                                                displacedHolder.getItem());
                                        holdersToMove.add(displacedHolder);
                                    }
                                }
                            }
                            movedHolders.add(holder);
                        }
                        DebugLogUtils.needle(TAG_ICON_CASCADE, "Found up solution");
                        dumpQueuedChanges(movedHolders);
                        return movedHolders;
                    }
                    case DOWN: {
                        // Build holders to move
                        // The order here matters: we want to move the top items first,
                        // then the bottom items, etc.
                        for (int y = targetCell.y; y < targetCell.y + height; y++) {
                            for (int x = targetCell.x; x < targetCell.x + width; x++) {
                                @Nullable GridViewHolder holder = getItemAtIndex(y, x);
                                if (holder != null && !holdersToMove.contains(holder)) {
                                    DebugLogUtils.needle(
                                        TAG_ICON_CASCADE,
                                        "Initially queueing " + holder.getItem() +
                                            " for displacement");
                                    holdersToMove.add(holder);
                                }
                            }
                        }
                        for (GridViewHolder holder : holdersToMove) {
                            holder.clearQueuedTranslation();
                        }

                        final int[] colToWorkingRowIndex = new int[mMetrics.getColumnCount()];
                        for (int col = 0; col < mMetrics.getColumnCount(); col++) {
                            colToWorkingRowIndex[col] = targetCell.y + height;
                        }
                        while (!holdersToMove.isEmpty()) {
                            GridViewHolder holder = holdersToMove.pop();
                            GridItem item = holder.getItem();
                            final int targetX = item.getX();
                            int targetY = Integer.MIN_VALUE;
                            for (
                                int col = item.getX();
                                col < item.getX() + item.getWidth();
                                col++) {
                                targetY = Math.max(
                                    targetY,
                                    colToWorkingRowIndex[col]);
                            }
                            if (targetY + item.getHeight() > mMetrics.getRowCount()) {
                                // Without changing column too, we can't accommodate this movement
                                DebugLogUtils.needle(
                                    TAG_ICON_CASCADE,
                                    "Couldn't find a down solution b/c " + item.toString() +
                                        " was pushed to " + targetX + "x" + targetY);
                                break DirectionCheck;
                            }
                            // Queue movements
                            holder.queueTranslation(targetX, targetY);
                            // Mark all the things it's covering up now for later processing
                            for (int x = targetX; x < targetX + item.getWidth(); x++) {
                                // Write back new working index
                                colToWorkingRowIndex[x] = targetY + item.getHeight();
                                for (int y = targetY; y < targetY + item.getHeight(); y++) {
                                    @Nullable GridViewHolder displacedHolder = getItemAtIndex(y, x);
                                    if (displacedHolder != null &&
                                        !holdersToMove.contains(displacedHolder) &&
                                        displacedHolder != holder) {
                                        holdersToMove.add(displacedHolder);
                                    }
                                }
                            }
                            movedHolders.add(holder);
                        }
                        DebugLogUtils.needle(TAG_ICON_CASCADE, "Found down solution");
                        dumpQueuedChanges(movedHolders);
                        return movedHolders;
                    }
                    case LEFT: {
                        // Build holders to move
                        // The order here matters: we want to move the rightmost items first,
                        // top to bottom
                        for (int y = targetCell.y; y < targetCell.y + height; y++) {
                            for (int x = targetCell.x + width - 1; x >= targetCell.x; x--) {
                                @Nullable GridViewHolder holder = getItemAtIndex(y, x);
                                if (holder != null && !holdersToMove.contains(holder)) {
                                    DebugLogUtils.needle(
                                        TAG_ICON_CASCADE,
                                        "Initially queueing " + holder.getItem() +
                                            " for displacement");
                                    holdersToMove.add(holder);
                                }
                            }
                        }
                        for (GridViewHolder holder : holdersToMove) {
                            holder.clearQueuedTranslation();
                        }

                        final int[] rowToWorkingColIndex = new int[mMetrics.getRowCount()];
                        for (int col = 0; col < mMetrics.getRowCount(); col++) {
                            rowToWorkingColIndex[col] = targetCell.x - 1;
                        }
                        while (!holdersToMove.isEmpty()) {
                            GridViewHolder holder = holdersToMove.pop();
                            GridItem item = holder.getItem();
                            final int targetY = item.getY();
                            int targetX = Integer.MAX_VALUE;
                            for (
                                int row = item.getY();
                                row < item.getY() + item.getHeight();
                                row++) {
                                targetX = Math.min(
                                    targetX,
                                    rowToWorkingColIndex[row] - (item.getWidth() - 1));
                            }
                            if (targetX < 0) {
                                // Without changing row too, we can't accommodate this movement
                                DebugLogUtils.needle(
                                    TAG_ICON_CASCADE,
                                    "Couldn't find an up solution b/c " + item.toString() +
                                        " was pushed to " + targetX + "x" + targetY);
                                break DirectionCheck;
                            }
                            // Queue movements
                            DebugLogUtils.needle(
                                TAG_ICON_CASCADE,
                                "Queue translation to " + targetX + ", " + targetY + " for " +
                                    holder.getItem());
                            holder.queueTranslation(targetX, targetY);
                            // Mark all the things it's covering up now for later processing
                            // This loop has to match the loop at the top of the block
                            for (int y = targetY; y < targetY + item.getHeight(); y++) {
                                // Write back new working index
                                rowToWorkingColIndex[y] = targetX - item.getWidth();
                                for (int x = targetX; x < targetX + item.getWidth(); x++) {
                                    @Nullable GridViewHolder displacedHolder = getItemAtIndex(y, x);
                                    if (displacedHolder != null &&
                                        !holdersToMove.contains(displacedHolder) &&
                                        displacedHolder != holder) {
                                        DebugLogUtils.needle(
                                            TAG_ICON_CASCADE,
                                            "Secondary[+] order move queued for " +
                                                displacedHolder.getItem());
                                        holdersToMove.add(displacedHolder);
                                    }
                                }
                            }
                            movedHolders.add(holder);
                        }
                        DebugLogUtils.needle(TAG_ICON_CASCADE, "Found left solution");
                        dumpQueuedChanges(movedHolders);
                        return movedHolders;
                    }
                    case RIGHT: {
                        // Build holders to move
                        // The order here matters: we want to move the leftmost items first,
                        // top to bottom
                        for (int y = targetCell.y; y < targetCell.y + height; y++) {
                            for (int x = targetCell.x; x < targetCell.x + width; x++) {
                                @Nullable GridViewHolder holder = getItemAtIndex(y, x);
                                if (holder != null && !holdersToMove.contains(holder)) {
                                    DebugLogUtils.needle(
                                        TAG_ICON_CASCADE,
                                        "Initially queueing " + holder.getItem() +
                                            " for displacement");
                                    holdersToMove.add(holder);
                                }
                            }
                        }
                        for (GridViewHolder holder : holdersToMove) {
                            holder.clearQueuedTranslation();
                        }

                        final int[] rowToWorkingColIndex = new int[mMetrics.getRowCount()];
                        for (int col = 0; col < mMetrics.getRowCount(); col++) {
                            rowToWorkingColIndex[col] = targetCell.x + width;
                        }
                        while (!holdersToMove.isEmpty()) {
                            GridViewHolder holder = holdersToMove.pop();
                            GridItem item = holder.getItem();
                            final int targetY = item.getY();
                            int targetX = Integer.MIN_VALUE;
                            for (
                                int row = item.getY();
                                row < item.getY() + item.getHeight();
                                row++) {
                                targetX = Math.max(
                                    targetX,
                                    rowToWorkingColIndex[row]);
                            }
                            if (targetX + item.getWidth() > mMetrics.getColumnCount()) {
                                // Without changing row too, we can't accommodate this movement
                                DebugLogUtils.needle(
                                    TAG_ICON_CASCADE,
                                    "Couldn't find an up solution b/c " + item.toString() +
                                        " was pushed to " + targetX + "x" + targetY);
                                break DirectionCheck;
                            }
                            // Queue movements
                            DebugLogUtils.needle(
                                TAG_ICON_CASCADE,
                                "Queue translation to " + targetX + ", " + targetY + " for " +
                                    holder.getItem());
                            holder.queueTranslation(targetX, targetY);
                            // Mark all the things it's covering up now for later processing
                            // This loop has to match the loop at the top of the block
                            for (int y = targetY; y < targetY + item.getHeight(); y++) {
                                // Write back new working index
                                rowToWorkingColIndex[y] = targetX + item.getWidth();
                                for (int x = targetX; x < targetX + item.getWidth(); x++) {
                                    @Nullable GridViewHolder displacedHolder = getItemAtIndex(y, x);
                                    if (displacedHolder != null &&
                                        !holdersToMove.contains(displacedHolder) &&
                                        displacedHolder != holder) {
                                        DebugLogUtils.needle(
                                            TAG_ICON_CASCADE,
                                            "Secondary[+] order move queued for " +
                                                displacedHolder.getItem());
                                        holdersToMove.add(displacedHolder);
                                    }
                                }
                            }
                            movedHolders.add(holder);
                        }
                        DebugLogUtils.needle(TAG_ICON_CASCADE, "Found right solution");
                        dumpQueuedChanges(movedHolders);
                        return movedHolders;
                    }
                }
            }
        }

        if (lastCommittedCell == null) {
            DebugLogUtils.needle(
                TAG_ICON_CASCADE,
                "Last committed cell is null (freshly dragged app icon); not trying swap");
            return null;
        }
        DebugLogUtils.needle(TAG_ICON_CASCADE, "Attempting swap solution...");
        // Try a naive swap solution, maybe
        // Does what's occupied in the drop region expand outside that region (e.g. widgets)
        int xMinBound = Integer.MAX_VALUE, yMinBound = Integer.MAX_VALUE,
            xMaxBound = Integer.MIN_VALUE, yMaxBound = Integer.MIN_VALUE;
        final Set<GridViewHolder> initialHoldersToMove = new HashSet<>();
        for (int x = targetCell.x; x < targetCell.x + width; x++) {
            for (int y = targetCell.y; y < targetCell.y + height; y++) {
                @Nullable GridViewHolder holder = getItemAtIndex(y, x);
                if (holder == null || initialHoldersToMove.contains(holder)) {
                    continue;
                }
                initialHoldersToMove.add(holder);
                final int itemX = holder.getItem().getX();
                final int itemY = holder.getItem().getY();
                final int itemWidth = holder.getItem().getWidth();
                final int itemHeight = holder.getItem().getHeight();
                if (itemX < xMinBound) {
                    xMinBound = itemX;
                }
                if (itemX + itemWidth > xMaxBound) {
                    xMaxBound = itemX + itemWidth;
                }
                if (itemY < yMinBound) {
                    yMinBound = itemY;
                }
                if (itemY + itemHeight > yMaxBound) {
                    yMaxBound = itemY + itemHeight;
                }
            }
        }
        final boolean displacedItemsStretchBeyondTargetBounds =
            xMinBound < targetCell.x ||
                xMaxBound > targetCell.x + width ||
                yMinBound < targetCell.y ||
                yMaxBound > targetCell.y + height;
        final Rect oldArea = new Rect(
            lastCommittedCell.x,
            lastCommittedCell.y,
            lastCommittedCell.x + draggedItem.getWidth(),
            lastCommittedCell.y + draggedItem.getHeight());
        Rect newArea = new Rect(
            targetCell.x,
            targetCell.y,
            targetCell.x + draggedItem.getWidth(),
            targetCell.y + draggedItem.getHeight());
        DebugLogUtils.needle(TAG_ICON_CASCADE, "oldArea: " + oldArea + ", newArea: " + newArea);
        final boolean doTargetCellAndLastTargetCellBoundsOverlap = oldArea.intersect(newArea);
        if (displacedItemsStretchBeyondTargetBounds || doTargetCellAndLastTargetCellBoundsOverlap) {
            DebugLogUtils.needle(TAG_ICON_CASCADE, "Could find NO solution :(");
            DebugLogUtils.needle(
                TAG_ICON_CASCADE,
                "beyond target bounds = " +
                    displacedItemsStretchBeyondTargetBounds +
                    " & bounds overlap = " +
                    doTargetCellAndLastTargetCellBoundsOverlap);
            return null;
        }

        // Since there's no overlap, and no out of bounds issues, we can swap the items under
        // targetCell's region to lastTargetCell
        Set<GridViewHolder> swapChanges = new HashSet<>();
        final int xDelta = lastCommittedCell.x - targetCell.x;
        final int yDelta = lastCommittedCell.y - targetCell.y;
        for (int x = targetCell.x; x < targetCell.x + width; x++) {
            for (int y = targetCell.y; y < targetCell.y + height; y++) {
                @Nullable GridViewHolder holderUnderDrag = getItemAtIndex(y, x);
                if (holderUnderDrag != null) {
                    holderUnderDrag.queueTranslation(
                        holderUnderDrag.getItem().getX() + xDelta,
                        holderUnderDrag.getItem().getY() + yDelta);
                    swapChanges.add(holderUnderDrag);
                }
            }
        }
        DebugLogUtils.needle(TAG_ICON_CASCADE, "Found swap solution");
        dumpQueuedChanges(swapChanges);
        return swapChanges;
    }

    private void dumpQueuedChanges(Set<GridViewHolder> queuedHolders) {
        DebugLogUtils.needle(TAG_ICON_CASCADE, queuedHolders.size() + " queued changes");
        for (GridViewHolder holder : queuedHolders) {
            DebugLogUtils.needle(
                TAG_ICON_CASCADE,
                holder.getItem().toString() + " -> " + holder.getQueuedTranslation());
        }
    }

    private enum ChangeDirection {
        UP, DOWN, LEFT, RIGHT;

        static ChangeDirection fromDelta(int xDelta, int yDelta) {
            if (xDelta < 0) {
                return ChangeDirection.LEFT;
            } else if (xDelta > 0) {
                return ChangeDirection.RIGHT;
            }
            if (yDelta > 0) {
                return ChangeDirection.DOWN;
            } else if (yDelta < 0) {
                return ChangeDirection.UP;
            }
            return ChangeDirection.DOWN;
        }

        public ChangeDirection opposite() {
            if (this == UP) {
                return ChangeDirection.DOWN;
            }
            if (this == DOWN) {
                return ChangeDirection.UP;
            }
            if (this == LEFT) {
                return ChangeDirection.RIGHT;
            }
            if (this == RIGHT) {
                return ChangeDirection.LEFT;
            }
            return ChangeDirection.DOWN;
        }
    }
}
