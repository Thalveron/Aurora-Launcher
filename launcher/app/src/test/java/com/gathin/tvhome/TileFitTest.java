package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** AppAdapter.tileWidthPx with rows_below_dock: the dock, its title and the rows must fit the grid's height. */
public class TileFitTest {

    private static final float D = 2f;              // 1080p TV: 2 px per dp
    private static final int INNER = 1696;          // grid width minus padding at 1920 px
    private static final int GRID_H = 846;          // grid height under the top bar

    private static int height(int tileW, int rows, boolean title) {
        int itemH = AppAdapter.tileHeightPx(tileW) + Math.round((rows > 1 ? 20 : AppAdapter.ITEM_EXTRA_DP) * D);
        return itemH * (rows + 1) + Math.round((AppAdapter.DOCK_EXTRA_DP + (rows > 1 ? 16 : AppAdapter.BOTTOM_ROOM_DP)
                + (title ? (rows > 1 ? 56 : AppAdapter.TITLE_ROOM_DP) : 0)) * D);
    }

    @Test
    public void noRowsBelowDockKeepsTheWidthOnlySize() {
        int widthOnly = AppAdapter.tileWidthPx(INNER, 4, D);
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 0, true, true, D));
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, 0, 4, 3, true, true, D));   // not laid out yet
    }

    @Test
    public void dockTitleAndRowsFitTheHeightUnlessThatMeansTinyTiles() {
        int floor = Math.round(48 * D);
        for (int rows = 1; rows <= 3; rows++) {
            for (boolean title : new boolean[]{true, false}) {
                int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, rows, title, true, D);
                assertTrue("rows " + rows + " title " + title + " below the floor", w >= floor);
                if (w > floor) assertTrue("rows " + rows + " title " + title, height(w, rows, title) <= GRID_H + 2);
            }
        }
    }

    @Test
    public void oneRowUnderATitledDockFits() {
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, true, true, D);
        assertTrue(w < AppAdapter.tileWidthPx(INNER, 4, D));
        assertTrue(height(w, 1, true) <= GRID_H + 2);
    }

    @Test
    public void requestedExtraRowsFitWithoutChangingTheColumnLimit() {
        // Extra rows use the compact spacing; one-row sizing uses larger title/label gaps.
        int previous = AppAdapter.tileWidthPx(INNER, 4, D);
        for (int rows = 2; rows <= 3; rows++) {
            int width = AppAdapter.tileWidthPx(INNER, GRID_H, 4, rows, true, true, D);
            assertTrue(width <= previous);
            assertTrue(width >= Math.round(48 * D));
            assertTrue(height(width, rows, true) <= GRID_H + 2);
            previous = width;
        }
    }

    @Test
    public void plainGridFitsFourRowsAtSixColumns() {
        // dock_row false, rows_below_dock 3: the first row and 3 more under a 12dp top margin.
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 6, 3, true, false, D);
        int itemH = AppAdapter.tileHeightPx(w) + Math.round(20 * D);
        int needed = itemH * 4 + Math.round((AppAdapter.TOP_ROOM_DP + AppAdapter.COMPACT_BOTTOM_ROOM_DP) * D);
        assertTrue(needed + " > " + GRID_H, needed <= GRID_H + 2);
        // With the compact spacing the width limit decides, not the height: no extra whitespace between tiles.
        assertEquals(AppAdapter.tileWidthPx(INNER, 6, D), w);
    }

    @Test
    public void droppingTheTitleGivesBiggerTiles() {
        assertTrue(AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, false, true, D)
                > AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, true, true, D));
    }
}
