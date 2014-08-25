package com.example.android.recyclerplayground;

import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.View;

public class GridLayoutManager extends RecyclerView.LayoutManager {
    //TODO: Fix fling bug with bounds

    private static final String TAG = GridLayoutManager.class.getSimpleName();

    /* Fill Direction Constants */
    private static final int DIRECTION_NONE = -1;
    private static final int DIRECTION_START = 0;
    private static final int DIRECTION_END = 1;
    private static final int DIRECTION_UP = 2;
    private static final int DIRECTION_DOWN = 3;

    /* First (top-left) position visible at any point */
    private int mFirstVisiblePosition = 0;
    /* Consistent size applied to all child views */
    private int mDecoratedChildWidth;
    private int mDecoratedChildHeight;
    /* Number of columns that exist in the grid */
    private int mTotalColumnCount;
    /* Metrics for the visible window of our data */
    private int mVisibleColumnCount;
    private int mVisibleRowCount;

    private int positionOfIndex(int childIndex) {
        return positionOfIndex(mFirstVisiblePosition, childIndex);
    }

    /*
     * Mapping between child view indices and adapter data
     * positions helps fill the proper views during scrolling.
     */
    private int positionOfIndex(int firstPosition, int childIndex) {
        int row = childIndex / mVisibleColumnCount;
        int column = childIndex % mVisibleColumnCount;

        return firstPosition + (row * getTotalColumnCount()) + column;
    }

    private int rowOfIndex(int childIndex) {
        return rowOfIndex(mFirstVisiblePosition, childIndex);
    }

    private int rowOfIndex(int firstPosition, int childIndex) {
        int position = positionOfIndex(firstPosition, childIndex);

        return position / getTotalColumnCount();
    }

    private int getFirstVisibleColumn() {
        return (mFirstVisiblePosition % getTotalColumnCount());
    }

    private int getLastVisibleColumn() {
        return getFirstVisibleColumn() + mVisibleColumnCount;
    }

    private int getFirstVisibleRow() {
        return (mFirstVisiblePosition / getTotalColumnCount());
    }

    private int getLastVisibleRow() {
        return getFirstVisibleRow() + mVisibleRowCount;
    }

    private int getVisibleChildCount() {
        return mVisibleColumnCount * mVisibleRowCount;
    }

    private int getTotalColumnCount() {
        return mTotalColumnCount;
    }

    private int getTotalRowCount(RecyclerView.State state) {
        int maxRow = state.getItemCount() / mTotalColumnCount;
        //Bump the row count if it's not exactly even
        if (state.getItemCount() % mTotalColumnCount != 0) {
            maxRow++;
        }

        return maxRow;
    }

    //This implementation always adds one to the computed count
    private void updateStaticVisibleCounts() {
        mVisibleColumnCount = (getHorizontalSpace() / mDecoratedChildWidth) + 1;
        if (getHorizontalSpace() % mDecoratedChildWidth > 0) {
            mVisibleColumnCount++;
        }
        //Allow minimum value for small data sets
        if (mVisibleColumnCount > mTotalColumnCount) {
            mVisibleColumnCount = mTotalColumnCount;
        }

        mVisibleRowCount = (getVerticalSpace() / mDecoratedChildHeight) + 1;
        if (getVerticalSpace() % mDecoratedChildHeight > 0) {
            mVisibleRowCount++;
        }
    }

    //TODO: See if we can introduce dynamic counts again in the future
    private void updateVisibleWindow() {
        int startLeftOffset = getPaddingLeft();
        int startTopOffset = getPaddingTop();

        //Set to the value of the first child for layout updates
        if (getChildCount() != 0) {
            final View topView = getChildAt(0);
            startLeftOffset = getDecoratedLeft(topView);
            startTopOffset = getDecoratedTop(topView);
        }

        mVisibleColumnCount = (getHorizontalSpace() - startLeftOffset) / mDecoratedChildWidth;
        if (getHorizontalSpace() % mDecoratedChildWidth > 0) {
            mVisibleColumnCount++;
        }
        mVisibleRowCount = (getVerticalSpace() - startTopOffset) / mDecoratedChildHeight;
        if (getVerticalSpace() % mDecoratedChildHeight > 0) {
            mVisibleRowCount++;
        }
    }

    private int lastFillPosition = 0;
    private void fillGrid(int direction, RecyclerView.Recycler recycler, RecyclerView.State state) {
        //Always range check visible position
        if (mFirstVisiblePosition < 0) mFirstVisiblePosition = 0;
        if (mFirstVisiblePosition > state.getItemCount()) mFirstVisiblePosition = state.getItemCount();

        SparseArray<View> viewCache = new SparseArray<View>(getChildCount());
        int startLeftOffset = getPaddingLeft();
        int startTopOffset = getPaddingTop();
        if (getChildCount() != 0) {
            final View topView = getChildAt(0);
            startLeftOffset = getDecoratedLeft(topView);
            startTopOffset = getDecoratedTop(topView);
            switch (direction) {
                case DIRECTION_START:
                    startLeftOffset -= mDecoratedChildWidth;
                    break;
                case DIRECTION_END:
                    startLeftOffset += mDecoratedChildWidth;
                    break;
                case DIRECTION_UP:
                    startTopOffset -= mDecoratedChildHeight;
                    break;
                case DIRECTION_DOWN:
                    startTopOffset += mDecoratedChildHeight;
                    break;
            }

            //Temporarily detach existing views, indexed by position
            for (int i=0; i < getChildCount(); i++) {
                int position = positionOfIndex(lastFillPosition, i);
                final View child = getChildAt(i);
                viewCache.put(position, child);
            }
            for (int i=0; i < viewCache.size(); i++) {
                detachView(viewCache.valueAt(i));
            }
        }

        //TODO: Can we get rid of the interim position value somehow?
        if (lastFillPosition != mFirstVisiblePosition) {
            lastFillPosition = mFirstVisiblePosition;
        }

        //Fill in views, either from cache or recycler
        int leftOffset = startLeftOffset;
        int topOffset = startTopOffset;

        for (int i = 0; i < getVisibleChildCount(); i++) {
            int nextPosition = positionOfIndex(i);

            if (nextPosition >= state.getItemCount()) {
                //Item space beyond the data set, don't attempt to add a view
                continue;
            }

            //Layout this position
            View view = viewCache.get(nextPosition);
            if (view == null) {
                view = recycler.getViewForPosition(nextPosition);
                addView(view);

                //TODO: Verify we don't need to measure/layout
                measureChildWithMargins(view, 0, 0);

                layoutDecorated(view, leftOffset, topOffset,
                        leftOffset + mDecoratedChildWidth,
                        topOffset + mDecoratedChildHeight);
            } else {
                //Reattach from cache
                attachView(view);
                viewCache.remove(nextPosition);
            }

            if (i % mVisibleColumnCount == (mVisibleColumnCount - 1)) {
                leftOffset = startLeftOffset;
                topOffset += mDecoratedChildHeight;
                //If we wrapped without setting the column count, we've reached it
            } else {
                leftOffset += mDecoratedChildWidth;
            }
        }

        //Recycle all remaining views in cache
        for (int i=0; i < viewCache.size(); i++) {
            recycler.recycleView(viewCache.valueAt(i));
        }
    }

    /*
     * This method is your initial call from the framework. You will receive it when you
     * need to start laying out the initial set of views. This method will not be called
     * repeatedly, so don't rely on it to continually process changes during user
     * interaction.
     */
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        //We have nothing to show for an empty data set
        if (state.getItemCount() == 0) return;

        //Make the grid as square as possible, column count is root of the data set
        mTotalColumnCount = (int) Math.round(Math.sqrt(state.getItemCount()));

        //Scrap measure one child
        View scrap = recycler.getViewForPosition(0);
        addView(scrap);
        measureChildWithMargins(scrap, 0, 0);

        /*
         * We make some assumptions in this code based on every child
         * view being the same size (i.e. a uniform grid). This allows
         * us to compute the following values up front because the
         * won't change.
         */

        mDecoratedChildWidth = getDecoratedMeasuredWidth(scrap);
        mDecoratedChildHeight = getDecoratedMeasuredHeight(scrap);

        detachAndScrapAttachedViews(recycler);

        updateStaticVisibleCounts();

        fillGrid(DIRECTION_NONE, recycler, state);
    }

    /*
     * Use this method to tell the RecyclerView if scrolling is even possible
     * in the horizontal direction.
     */
    @Override
    public boolean canScrollHorizontally() {
        //We do allow scrolling
        return true;
    }

    /*
     * This method describes how far RecyclerView thinks the contents should scroll horizontally.
     * You are responsible for verifying edge boundaries, and determining if this scroll
     * event somehow requires that new views be added or old views get recycled.
     */
    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        //Take leftmost measurements from the top-left child
        final View topView = getChildAt(0);
        //Take rightmost measurements from the top-right child
        final View bottomView = getChildAt(mVisibleColumnCount-1);
        int delta;
        boolean leftBoundReached = getFirstVisibleColumn() == 0;
        boolean rightBoundReached = getLastVisibleColumn() >= getTotalColumnCount();
        if (dx > 0) { // Contents are scrolling left
            //Check right bound
            if (rightBoundReached) {
                //If we've reached the last column, enforce limits
                int bottomOffset = Math.min(getHorizontalSpace() - getDecoratedRight(bottomView) + getPaddingRight(), 0);
                delta = Math.max(-dx, bottomOffset);
            } else {
                //No limits while the last column isn't visible
                delta = -dx;
            }
        } else { // Contents are scrolling right
            //Check left bound
            if (leftBoundReached) {
                int topOffset = Math.max(-getDecoratedLeft(topView) + getPaddingLeft(), 0);
                delta = Math.min(-dx, topOffset);
            } else {
                delta = -dx;
            }
        }

        offsetChildrenHorizontal(delta);

        if (dx > 0) {
            if (getDecoratedRight(topView) < 0 && !rightBoundReached) {
                mFirstVisiblePosition++;
                fillGrid(DIRECTION_END, recycler, state);
            }
        } else {
            if (getDecoratedLeft(topView) > 0 && !leftBoundReached) {
                mFirstVisiblePosition--;
                fillGrid(DIRECTION_START, recycler, state);
            }
        }

        /*
         * Return value determines if a boundary has been reached
         * (for edge effects and flings). If returned value does not
         * match original delta (passed in), RecyclerView will draw
         * an edge effect.
         */
        return (Math.abs(delta) != Math.abs(dx)) ? Math.abs(delta) : dx;
    }

    /*
     * Use this method to tell the RecyclerView if scrolling is even possible
     * in the vertical direction.
     */
    @Override
    public boolean canScrollVertically() {
        //We do allow scrolling
        return true;
    }

    /*
     * This method describes how far RecyclerView thinks the contents should scroll vertically.
     * You are responsible for verifying edge boundaries, and determining if this scroll
     * event somehow requires that new views be added or old views get recycled.
     */
    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        //Take top measurements from the top-left child
        final View topView = getChildAt(0);
        //Take bottom measurements from the bottom-right child.
        final View bottomView = getChildAt(getChildCount()-1);
        int delta;
        int maxRowCount = getTotalRowCount(state);
        boolean topBoundReached = getFirstVisibleRow() == 0;
        boolean bottomBoundReached = getLastVisibleRow() >= maxRowCount;
        if (dy > 0) { // Contents are scrolling up
            //Check against bottom bound
            if (bottomBoundReached) {
                //If we've reached the last row, enforce limits
                int remainingSpace;
                if (rowOfIndex(getChildCount() - 1) >= (maxRowCount - 1)) {
                    //We are truly at the bottom, determine how far
                    remainingSpace = getVerticalSpace() - getDecoratedBottom(bottomView)
                            + getPaddingBottom();
                } else {
                    /*
                     * Extra space added to account for allowing bottom space in the grid.
                     * This occurs when the overlap in the last row is not large enough to
                     * ensure that at least one element in that row isn't fully recycled.
                     */
                    remainingSpace = getVerticalSpace() - (getDecoratedBottom(bottomView)
                            + mDecoratedChildHeight) + getPaddingBottom();
                }
                int bottomOffset = Math.min(remainingSpace, 0);
                delta = Math.max(-dy, bottomOffset);
            } else {
                //No limits while the last row isn't visible
                delta = -dy;
            }
        } else { // Contents are scrolling down
            //Check against top bound
            if (topBoundReached) {
                int topOffset = Math.max(-getDecoratedTop(topView)+getPaddingTop(), 0);
                delta = Math.min(-dy, topOffset);
            } else {
                delta = -dy;
            }
        }

        offsetChildrenVertical(delta);

        if (dy > 0) {
            if (getDecoratedBottom(topView) < 0 && !bottomBoundReached) {
                mFirstVisiblePosition += getTotalColumnCount();
                fillGrid(DIRECTION_DOWN, recycler, state);
            }
        } else {
            if (getDecoratedTop(topView) > 0 && !topBoundReached) {
                mFirstVisiblePosition -= getTotalColumnCount();
                fillGrid(DIRECTION_UP, recycler, state);
            }
        }

        /*
         * Return value determines if a boundary has been reached
         * (for edge effects and flings). If returned value does not
         * match original delta (passed in), RecyclerView will draw
         * an edge effect.
         */
        return (Math.abs(delta) != Math.abs(dy)) ? Math.abs(delta) : dy;
    }

    private int getHorizontalSpace() {
        return getWidth() - getPaddingRight() - getPaddingLeft();
    }

    private int getVerticalSpace() {
        return getHeight() - getPaddingBottom() - getPaddingTop();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT);
    }
}
