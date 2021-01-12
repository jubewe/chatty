
package chatty.util.dnd;

import chatty.util.dnd.DockDropInfo.DropType;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Tab pane that handles dragging tabs.
 * 
 * @author tduva
 */
public class DockTabs extends JTabbedPane implements DockChild {

    private final DockExportHandler transferHandler;
    private final Map<JComponent, DockContent> assoc = new HashMap<>();
    private final DockContent.DockContentPropertyListener dockContentListener;
    
    private DockChild parent;
    private DockBase base;
    
    private boolean canStartDrag;
    private long dragStarted;
    private int dragIndex;
    
    private boolean mouseWheelScrolling = true;
    private boolean mouseWheelScrollingAnywhere = true;
    private DockSetting.TabOrder order = DockSetting.TabOrder.INSERTION;
    
    public DockTabs() {
        transferHandler = new DockExportHandler(this);
        setTransferHandler(transferHandler);
        
        addMouseMotionListener(new MouseMotionAdapter() {
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!canStartDrag) {
                    return;
                }
//                System.out.println(System.currentTimeMillis() - dragStarted);
                if (dragStarted == 0) {
                    dragStarted = System.currentTimeMillis();
                    dragIndex = getIndexForPoint(e.getPoint());
                }
                // Start actual drag only after a short delay of dragging
                if (dragIndex >= 0 && System.currentTimeMillis() - dragStarted > 120) {
                    transferHandler.drag(dragIndex, e);
                    base.requestDrag();
                    canStartDrag = false;
                }
                repaint();
            }
        });
        
        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                dragStarted = 0;
                /**
                 * Only allow a drag to start using the left mouse button. Can't
                 * use isPopupTrigger() to prevent it from dragging when opening
                 * a context menu, because the popup trigger isn't necessarily
                 * available in mousePressed().
                 */
                canStartDrag = SwingUtilities.isLeftMouseButton(e);
                openPopupMenu(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStarted = 0;
                repaint();
                base.requestStopDrag(null);
                openPopupMenu(e);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                openPopupMenu(e);
            }
            
        });
        
        addMouseWheelListener(new MouseWheelListener() {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (mouseWheelScrolling) {
                    // Only scroll if actually on tabs area
                    int index = indexAtLocation(e.getX(), e.getY());
                    if (mouseWheelScrollingAnywhere || index != -1
                            || isNearLastTab(e.getPoint())) {
                        if (e.getWheelRotation() < 0) {
                            setSelectedPrevious();
                        } else if (e.getWheelRotation() > 0) {
                            setSelectedNext();
                        }
                    }
                }
            }
        });
        
        addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                dragStarted = 0;
                repaint();
                if (base != null) {
                    base.tabChanged(DockTabs.this, getCurrentContent());
                }
                updateTabComponents();
            }
        });
        
        dockContentListener = new DockContent.DockContentPropertyListener() {
            @Override
            public void titleChanged(DockContent content) {
                int index = getIndexByContent(content);
                if (index != -1) {
                    setTitleAt(index, content.getTitle());
                }
            }

            @Override
            public void foregroundColorChanged(DockContent content) {
                System.out.println("foregroundColorChanged"+content);
                int index = getIndexByContent(content);
                if (index != -1) {
                    setForegroundAt(index, content.getForegroundColor());
                }
            }
        };
    }
    
    @Override
    public void setBase(DockBase base) {
        this.base = base;
    }
    
    public void requestStopDrag(DockTransferable t) {
        base.requestStopDrag(t);
    }
    
    @Override
    public void setDockParent(DockChild parent) {
        this.parent = parent;
    }
        
    @Override
    public DockChild getDockParent() {
        return parent;
    }
    
    @Override
    public void split(DockDropInfo info, DockContent content) {
        parent.split(info, content);
    }
    
    @Override
    public JComponent getComponent() {
        return this;
    }
    
    /**
     * Open context menu manually instead of relying on the JTabbedPane, so we
     * can check if it's the currently selected tab (since the context menu will
     * trigger actions based on the currently selected tab).
     * 
     * @param e 
     */
    private void openPopupMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        final int index = indexAtLocation(e.getX(), e.getY());
        if (index != -1) {
            DockContent content = getContent(index);
            if (content != null) {
                JPopupMenu menu = content.getContextMenu();
                if (menu != null) {
                    menu.show(this, e.getX(), e.getY());
                }
            }
        }
    }
    
    //==========================
    // Tab component
    //==========================
    /**
     * Install tab component (if necessary) for a specific content and update
     * it's tab component settings.
     * 
     * @param content 
     */
    private void updateTabComponent(DockContent content) {
        int index = getIndexByContent(content);
        if (index != -1) {
            DockTabComponent tabComp = content.getTabComponent();
            JComponent comp = tabComp != null ? tabComp.getComponent() : null;
            if (getTabComponentAt(index) != comp) {
                setTabComponentAt(index, comp);
            }
            if (tabComp != null) {
                // Update settings
                tabComp.update(this, index);
            }
        }
    }
    
    /**
     * Install tab components if necessary and update tab component settings.
     */
    private void updateTabComponents() {
        assoc.entrySet().forEach(e -> updateTabComponent(e.getValue()));
    }
    
    /**
     * When Look&Feel is changed, tab component settings have to be updated as
     * well, otherwise the colors and stuff might not be correct.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        // Prevent updating before this instance is properly created
        if (assoc != null) {
            updateTabComponents();
        }
    }
    
    //==========================
    // Add/remove content
    //==========================
    /**
     * Add content in the default tab location. A drop (see further below) is
     * another way to add content.
     * 
     * @param content 
     */
    @Override
    public void addContent(DockContent content) {
        if (assoc.containsValue(content)) {
            return;
        }
        assoc.put(content.getComponent(), content);
        insertTab(content.getTitle(), null, content.getComponent(), null,
                findInsertPosition(content.getTitle()));
        updateTabComponent(content);
        content.addListener(dockContentListener);
    }
    
    /**
     * Remove content.
     * 
     * @param content 
     */
    @Override
    public void removeContent(DockContent content) {
        assoc.remove(content.getComponent());
        remove(content.getComponent());
        content.removeListener(dockContentListener);
        if (getTabCount() == 0) {
            parent.replace(this, null);
        }
        else if (getTabCount() == 1) {
            ((DockTabsContainer)parent).updateSingleAllowed();
        }
    }
    
    //==========================
    // Current content
    //==========================
    public boolean containsContent(DockContent content) {
        return assoc.containsValue(content);
    }
    
    @Override
    public List<DockContent> getContents() {
        List<DockContent> result = new ArrayList<>();
        for (int i=0;i<getTabCount();i++) {
            result.add(getContent(i));
        }
        return result;
    }
    
    @Override
    public boolean isEmpty() {
        return getTabCount() == 0;
    }
    
    @Override
    public void setActiveContent(DockContent content) {
        JComponent comp = getComponentByContent(content);
        if (comp != null && getSelectedComponent() != comp) {
            setSelectedComponent(comp);
        }
    }
    
    public void setSelectedNext() {
        int index = getSelectedIndex();
        int count = getTabCount();
        if (index + 1 < count) {
            setSelectedIndex(index + 1);
        }
        else if (count > 0) {
            setSelectedIndex(0);
        }
    }

    public void setSelectedPrevious() {
        int index = getSelectedIndex();
        int count = getTabCount();
        if (count > 0) {
            if (index - 1 >= 0) {
                setSelectedIndex(index - 1);
            }
            else {
                setSelectedIndex(count - 1);
            }
        }
    }

    @Override
    public boolean isContentVisible(DockContent content) {
        return getSelectedComponent() == content.getComponent();
    }

    @Override
    public List<DockContent> getContentsRelativeTo(DockContent content, int direction) {
        List<DockContent> result = new ArrayList<>();
        int index = indexOfComponent(content.getComponent());
        if (index != -1) {
            if (direction == 1 || direction == 0) {
                for (int i = index+1; i < getTabCount(); i++) {
                    result.add(getContent(i));
                }
            }
            if (direction == -1 || direction == 0) {
                for (int i = index-1; i >= 0; i--) {
                    result.add(getContent(i));
                }
            }
        }
        return result;
    }
    
    @SuppressWarnings("element-type-mismatch")
    public DockContent getContent(int index) {
        return assoc.get(getComponentAt(index));
    }
    
    public JComponent getComponentByContent(DockContent content) {
        for (Map.Entry<JComponent, DockContent> entry : assoc.entrySet()) {
            if (entry.getValue() == content) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private int getIndexByContent(DockContent content) {
        for (int i=0;i<getTabCount();i++) {
            if (getContent(i) == content) {
                return i;
            }
        }
        return -1;
    }
    
    public DockContent getCurrentContent() {
        if (getSelectedIndex() == -1) {
            return null;
        }
        return getContent(getSelectedIndex());
    }
    
    //==========================
    // Drop
    //==========================
    @Override
    public DockDropInfo findDrop(DockImportInfo info) {
        int index = getDropIndexForPoint(info.getLocation(this));
        if (index >= 0) {
            if (!validDropIndex(info, index)) {
                // No drop possible
                return new DockDropInfo(this, DockDropInfo.DropType.INVALID, null, index);
            }
            else {
                // Move tab within same tab pane or from other tab pane
                return new DockDropInfo(this, DockDropInfo.DropType.TAB, getDropRectangle(index), index);
            }
        }
        if (getTabCount() == 1 && info.tf.source == this) {
            // Can't move a tab within the same tab pane if there is only one
            return null;
        }
        if (DockDropInfo.determineLocation(this, info.getLocation(this), 30, 1400, 20) == DropType.CENTER) {
            if (info.tf.source != this) {
                // Append new tab from other tab pane
                return new DockDropInfo(this, DockDropInfo.DropType.TAB, DockDropInfo.makeRect(this, DropType.CENTER, 40, 100000), -1);
            }
        }
        DockDropInfo.DropType location = DockDropInfo.determineLocation(this, info.getLocation(this), 20, 80, 20);
        if (location != null && location != DropType.CENTER) {
            // Split
            return new DockDropInfo(this, location, DockDropInfo.makeRect(this, location, 20, 20, 80), -1);
        }
        return null;
    }
    
    /**
     * If moving within the same tab pane, make sure it's not dropped in the
     * location it's already in.
     * 
     * @param info
     * @param index The target index
     * @return true if it's a valid drop location, false otherwise
     */
    private boolean validDropIndex(DockImportInfo info, int index) {
        return info.tf.source != this
                || !(info.tf.sourceIndex == index || info.tf.sourceIndex + 1 == index);
    }
    
    /**
     * Make a rectangle for inserting tab at a specific index.
     * 
     * @param index
     * @return 
     */
    private Rectangle getDropRectangle(int index) {
        boolean drawBeforeTab = true;
        if (index >= getTabCount()) {
            // If after last tab, then draw behind the last tab
            index = getTabCount() - 1;
            drawBeforeTab = false;
        }
        Rectangle bounds = getUI().getTabBounds(this, index);
        if (drawBeforeTab) {
            if (tabsAreHorizontal()) {
                return new Rectangle(bounds.x, bounds.y, 3, bounds.height);
            }
            return new Rectangle(bounds.x, bounds.y, bounds.width, 3);
        }
        if (tabsAreHorizontal()) {
            return new Rectangle(bounds.x + bounds.width - 3, bounds.y, 3, bounds.height);
        }
        return new Rectangle(bounds.x, bounds.y + bounds.height - 3, bounds.width, 3);
    }
    
    /**
     * Get the drop index for the given location. The drop index is the location
     * between the tabs to insert the dragged tab into. Basicially it's the
     * index of the tab to insert the dragged tab in front of, or the last tab
     * index + 1 if it should be inserted after the last tab.
     * 
     * @param p The location to find the drop index for
     * @return The drop index (> 0 and &lt= tab count), or -1 if none could be
     * found
     */
    private int getDropIndexForPoint(Point p) {
        int index = getIndexForPoint(p);
        if (index >= 0) {
            Rectangle bounds = getBoundsAt(index);
            boolean isCurrentTab;
            if (tabsAreHorizontal()) {
                isCurrentTab = p.x < bounds.x + bounds.width / 2;
            }
            else {
                isCurrentTab = p.y < bounds.y + bounds.height / 2;
            }
            if (isCurrentTab) {
                return index;
            } else {
                return index+1;
            }
        } else if (getTabCount() > 0) { // TODO: Drop on empty?
            /**
             * Basicially making the last tab wider to have more leeway with
             * dropping it after the last tab.
             */
            Rectangle bounds = getBoundsAt(getTabCount() - 1);
            if (tabsAreHorizontal()) {
                bounds.width += 300;
            }
            else {
                bounds.height += 300;
            }
            if (bounds.contains(p)) {
                return getTabCount();
            }
        }
        return -1;
    }

    @Override
    public void drop(DockTransferInfo info) {
        // Once a drop was initiated by the user, should reset target path
        info.importInfo.content.setTargetPath(null);
        
        if (info.dropInfo.location != DockDropInfo.DropType.TAB) {
            //--------------------------
            // Splitting
            //--------------------------
//            System.out.println("ABORT: Can only receive TAB insert");
            info.importInfo.source.removeContent(info.importInfo.content);
            split(info.dropInfo, info.importInfo.content);
            return;
        }
        //--------------------------
        // Moving TAB
        //--------------------------
        int targetIndex = info.dropInfo.index;
        if (targetIndex == -1) {
            targetIndex = findInsertPosition(info.importInfo.content.getTitle());
        }
        if (info.importInfo.source == this) {
            // Move within tab pane
            moveTab(info.importInfo.sourceIndex, targetIndex);
        }
        else {
            DockContent content = info.importInfo.content;
            DockTabsContainer container = (DockTabsContainer)parent;
            container.setSingleAllowedLocked();
            container.switchToTabs();
            info.importInfo.source.removeContent(content);
            assoc.put(content.getComponent(), content);
            insertTab(content.getTitle(), null, content.getComponent(), null, targetIndex);
            updateTabComponent(content);
            content.addListener(dockContentListener);
            setSelectedIndex(targetIndex);
            container.resetSingleAllowed();
        }
    }
    
    /**
     * Move a tab from a given index to another.
     * 
     * @param from The index of the tab to move
     * @param to The index to move the tab to
     * @throws IndexOutOfBoundsException if from or to are not in the range of
     * tab indices
     */
    private void moveTab(int from, int to) {
        if (from == to) {
            // Nothing to do here
            return;
        }
        Component comp = getComponentAt(from);
        String title = getTitleAt(from);
        String toolTip = getToolTipTextAt(from);
        Icon icon = getIconAt(from);
        Component tabComp = getTabComponentAt(from);
        removeTabAt(from);
        
        /**
         * If the source index (the tab that is moved) is in front of the target
         * index, then removing the source index will have shifted the target
         * index back by one, so adjust for that.
         */
        if (from < to) {
            to--;
        }
        insertTab(title, icon, comp, toolTip, to);
        setTabComponentAt(to, tabComp);
        setSelectedComponent(comp);
    }
    
    //==========================
    // Other / Helper
    //==========================
    
    @Override
    public String toString() {
        if (getTabCount() == 0) {
            return "EmptyTabs";
        }
        StringBuilder b = new StringBuilder();
        for (int i=0;i<getTabCount();i++) {
            b.append("[").append(getTitleAt(i)).append("]");
        }
        return b.toString();
    }

    @Override
    public void replace(DockChild old, DockChild replacement) {
        // Not applicable
    }

    @Override
    public void setSetting(DockSetting.Type setting, Object value) {
        switch (setting) {
            case TAB_SCROLL:
                mouseWheelScrolling = DockSetting.getBoolean(value);
                break;
            case TAB_SCROLL_ANYWHERE:
                mouseWheelScrollingAnywhere = DockSetting.getBoolean(value);
                break;
            case TAB_PLACEMENT:
                setTabPlacement(DockSetting.getInteger(value));
                break;
            case TAB_LAYOUT:
                setTabLayoutPolicy(DockSetting.getInteger(value));
                break;
            case TAB_ORDER:
                order = (DockSetting.TabOrder)value;
                break;
        }
    }
    
    /**
     * Determine if tabs are in horizontal position (top or bottom).
     * 
     * @return 
     */
    private boolean tabsAreHorizontal() {
        return getTabPlacement() == JTabbedPane.TOP || getTabPlacement() == JTabbedPane.BOTTOM;
    }
    
    /**
     * Returns the index this tab should be added at, depending on the current
     * order setting.
     * 
     * @param newTabName
     * @return 
     */
    private int findInsertPosition(String newTabName) {
        if (order == DockSetting.TabOrder.ALPHABETIC) {
            for (int i = 0; i < getTabCount(); i++) {
                if (newTabName.compareToIgnoreCase(getTitleAt(i)) < 0) {
                    return i;
                }
            }
        }
        return getTabCount();
    }
    
    /**
     * Get the tab index for the given location.
     * 
     * @param p
     * @return The tab index, or -1 if no tab is at the given location
     */
    private int getIndexForPoint(Point p) {
        return indexAtLocation(p.x, p.y);
    }
    
    private boolean isNearLastTab(Point p) {
        Rectangle bounds = getBoundsAt(getTabCount() - 1);
        bounds.width += 99999;
        return bounds.contains(p);
    }
    
    /**
     * Creates an Image of a single tab.
     *
     * @param index The tab index
     * @return
     */
    protected Image createScreenshot(int index) {
        Rectangle bounds = getBoundsAt(index);
        Image totalImage = new BufferedImage(getWidth(),
                getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics totalGraphics = totalImage.getGraphics();
        totalGraphics.setClip(bounds);
//        setDoubleBuffered(false);
        // paintAll() was necessary to draw custom tab components
        paintAll(totalGraphics);
//        setDoubleBuffered(true);

        Image result = new BufferedImage(bounds.width,
                bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = result.getGraphics();
        g.drawImage(totalImage, 0, 0,
                bounds.width, bounds.height,
                bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y + bounds.height,
                this);
        return result;
    }

    @Override
    public DockPath getPath() {
        return parent.getPath();
    }

    @Override
    public DockPath buildPath(DockPath path, DockChild child) {
        return parent.buildPath(path, child);
    }
    
}