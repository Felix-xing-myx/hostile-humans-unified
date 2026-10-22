package club.someoneice.humangunner;

/** Pure logical-pixel geometry shared by the radio and both command panels. */
final class CommandPanelLayout {
    static final int MARGIN=12,GROUP_GAP=8,BUTTON_GAP=6,BUTTON_HEIGHT=20,TITLE_HEIGHT=28;
    private CommandPanelLayout() {}
    static int width(int screenWidth){return Math.max(1,Math.min(480,screenWidth-2*MARGIN));}
    static int height(int screenHeight){return Math.max(1,screenHeight-2*MARGIN);}
    static int columnWidth(int innerWidth){return (innerWidth-GROUP_GAP)/2;}
    static boolean twoColumns(int innerWidth){return columnWidth(innerWidth)>=120;}
}
